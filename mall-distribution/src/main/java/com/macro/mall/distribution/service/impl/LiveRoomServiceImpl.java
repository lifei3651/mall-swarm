package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsLiveRoomDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsTenantDisplayConfigDao;
import com.macro.mall.distribution.dto.LiveRoomSaveDTO;
import com.macro.mall.distribution.dto.LiveAnchorSaveDTO;
import com.macro.mall.distribution.dto.LiveCommentSubmitDTO;
import com.macro.mall.distribution.dto.LiveEngagementDTO;
import com.macro.mall.distribution.entity.DmsLiveAnchor;
import com.macro.mall.distribution.entity.DmsLiveComment;
import com.macro.mall.distribution.entity.DmsLiveRoom;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.service.LiveRoomService;
import com.macro.mall.distribution.service.LiveStreamCredentialService;
import com.macro.mall.distribution.service.ContentModerationService;
import com.macro.mall.distribution.service.MerchantProductReviewService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.ShopCatalogCacheService;
import com.macro.mall.distribution.vo.LiveRoomVO;
import com.macro.mall.distribution.vo.LiveAnchorVO;
import com.macro.mall.distribution.vo.LiveAnalyticsVO;
import com.macro.mall.distribution.vo.LiveStreamCredentialVO;
import com.macro.mall.distribution.vo.LiveStudioVO;
import com.macro.mall.distribution.util.MemberAccountUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.dao.DuplicateKeyException;

import static com.macro.mall.distribution.util.ShopPublicViewSanitizer.product;

@Service
@RequiredArgsConstructor
public class LiveRoomServiceImpl implements LiveRoomService {

    private static final Set<Integer> VALID_STATUSES = Set.of(0, 1, 2, 3, 4);
    private static final Set<Integer> VALID_ANCHOR_STATUSES = Set.of(1, 2, 3);
    private static final Set<String> VALID_LIVE_TYPES = Set.of("PRODUCT", "PLATFORM", "FACTORY");
    private static final Set<String> VALID_PROVIDERS = Set.of("EXTERNAL", "TENCENT");

    private final DmsLiveRoomDao liveRoomDao;
    private final DmsShopProductDao productDao;
    private final DmsShopMemberDao memberDao;
    private final DmsTenantDisplayConfigDao displayConfigDao;
    private final TenantDisplayConfigSupport displayConfigSupport;
    private final MerchantProductReviewService merchantProductReviewService;
    private final ShopCatalogCacheService catalogCache;
    private final OperationLogService operationLogService;
    private final ContentModerationService contentModerationService;
    private final LiveStreamCredentialService streamCredentialService;

    @Value("${shop.live.allowed-playback-origin:}")
    private String allowedPlaybackOrigin;

    @Override
    public List<LiveRoomVO> listPublic(int limit) {
        return listPublic(TenantContext.getTenantId(), limit);
    }

    @Override
    public List<LiveRoomVO> listPublic(Long tenantId, int limit) {
        if (!isPublicEnabled(tenantId)) return List.of();
        int safeLimit = Math.max(1, Math.min(50, limit));
        return liveRoomDao.selectPublicList(tenantId, safeLimit).stream()
                .map(room -> toVo(room, true))
                .toList();
    }

    @Override
    public LiveRoomVO getPublic(Long id) {
        if (!isPublicEnabled(TenantContext.getTenantId())) {
            Asserts.fail("直播广场暂未开放");
        }
        DmsLiveRoom room = liveRoomDao.selectById(TenantContext.getTenantId(), id);
        if (room == null || room.getStatus() == null || !List.of(1, 2, 3).contains(room.getStatus())) {
            Asserts.fail("直播间不存在或暂未公开");
        }
        return toVo(room, true);
    }

    private boolean isPublicEnabled(Long tenantId) {
        return Integer.valueOf(1).equals(displayConfigSupport
                .prepareForRead(displayConfigDao.selectByTenantId(tenantId), tenantId)
                .getLiveSquareEnabled());
    }

    @Override
    public List<LiveRoomVO> listAdmin(Integer status) {
        assertPlatformOperator();
        if (status != null && !VALID_STATUSES.contains(status)) Asserts.fail("直播状态不正确");
        return liveRoomDao.selectAdminList(TenantContext.getTenantId(), status).stream()
                .map(room -> toVo(room, false))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LiveRoomVO save(Long id, LiveRoomSaveDTO dto) {
        assertPlatformOperator();
        Long tenantId = TenantContext.getTenantId();
        DmsLiveRoom existing = id == null ? null : liveRoomDao.selectByIdForUpdate(tenantId, id);
        if (id != null && existing == null) Asserts.fail("直播间不存在或已被删除");
        Integer beforeStatus = existing == null ? null : existing.getStatus();

        int status = dto.getStatus() == null ? 0 : dto.getStatus();
        if (!VALID_STATUSES.contains(status)) Asserts.fail("直播状态不正确");
        if (dto.getScheduledEndTime() != null && !dto.getScheduledEndTime().isAfter(dto.getScheduledStartTime())) {
            Asserts.fail("计划结束时间必须晚于开播时间");
        }
        String coverUrl = normalizeMediaUrl(dto.getCoverUrl(), "直播封面");
        String watchUrl = normalizeWatchUrl(dto.getWatchUrl());
        List<Long> productIds = normalizeProductIds(dto.getProductIds());
        if ((status == 1 || status == 2) && productIds.isEmpty()) {
            Asserts.fail("公开预告或直播中状态至少要关联一个在售商品");
        }
        if (status == 2 && (existing == null || !Integer.valueOf(2).equals(existing.getStatus()))) {
            Asserts.fail("请由已授权主播在主播工作台开始直播");
        }
        if (existing != null && Integer.valueOf(2).equals(existing.getStatus()) && status != 2) {
            Asserts.fail("直播进行中请使用主播结束直播或平台强制停播");
        }
        if (existing != null && Integer.valueOf(2).equals(existing.getStatus())
                && (!java.util.Objects.equals(existing.getAnchorId(), dto.getAnchorId())
                || !java.util.Objects.equals(existing.getProviderCode(),
                normalizeEnum(dto.getProviderCode(), "EXTERNAL", VALID_PROVIDERS, "直播服务类型不正确")))) {
            Asserts.fail("直播进行中不能更换主播或视频服务");
        }
        validateProducts(tenantId, productIds, status == 1 || status == 2);

        DmsLiveRoom room = existing == null ? new DmsLiveRoom() : existing;
        room.setTenantId(tenantId);
        room.setTitle(dto.getTitle().trim());
        room.setSubtitle(trimToNull(dto.getSubtitle()));
        room.setCoverUrl(coverUrl);
        room.setAnchorName(trimToNull(dto.getAnchorName()));
        room.setAnchorId(dto.getAnchorId());
        room.setLiveType(normalizeEnum(dto.getLiveType(), "PRODUCT", VALID_LIVE_TYPES, "直播类型不正确"));
        room.setProviderCode(normalizeEnum(dto.getProviderCode(), "EXTERNAL", VALID_PROVIDERS, "直播服务类型不正确"));
        if (existing == null) {
            room.setStreamName("lq" + tenantId + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        }
        room.setWatchUrl(watchUrl);
        room.setCommentEnabled(dto.getCommentEnabled() == null ? 1 : dto.getCommentEnabled());
        room.setShareEnabled(dto.getShareEnabled() == null ? 1 : dto.getShareEnabled());
        room.setScheduledStartTime(dto.getScheduledStartTime());
        room.setScheduledEndTime(dto.getScheduledEndTime());
        room.setStatus(status);
        room.setViewerCount(dto.getViewerCount() == null ? 0 : dto.getViewerCount());
        room.setHeatCount(dto.getHeatCount() == null ? 0 : dto.getHeatCount());
        room.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());

        if ((status == 1 || status == 2) && room.getAnchorId() == null) {
            Asserts.fail("公开直播必须选择平台已授权的主播账号");
        }
        if (room.getAnchorId() != null) {
            DmsLiveAnchor anchor = liveRoomDao.selectAnchorById(tenantId, room.getAnchorId());
            if (anchor == null || !Integer.valueOf(1).equals(anchor.getStatus())) Asserts.fail("主播账号不存在或当前不可开播");
            room.setAnchorName(anchor.getDisplayName());
        }

        if (existing == null) {
            liveRoomDao.insert(room);
        } else if (liveRoomDao.update(room) <= 0) {
            Asserts.fail("直播间已被其他管理员更新，请刷新后重试");
        }
        replaceProducts(tenantId, room.getId(), productIds);
        catalogCache.invalidateAfterCommit(tenantId);
        operationLogService.log("LIVE_ROOM", existing == null ? "CREATE" : "UPDATE", "LIVE_ROOM",
                String.valueOf(room.getId()),
                beforeStatus == null ? null : "status=" + beforeStatus,
                "status=" + status + ",products=" + productIds.size(),
                existing == null ? "创建直播间" : "更新直播间资料");
        return toVo(liveRoomDao.selectById(tenantId, room.getId()), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long id, Integer status) {
        assertPlatformOperator();
        if (status == null || !VALID_STATUSES.contains(status)) Asserts.fail("直播状态不正确");
        if (status == 2) Asserts.fail("请由已授权主播在主播工作台开始直播");
        Long tenantId = TenantContext.getTenantId();
        DmsLiveRoom room = liveRoomDao.selectByIdForUpdate(tenantId, id);
        if (room == null) Asserts.fail("直播间不存在或已被删除");
        List<Long> productIds = liveRoomDao.selectProductIds(tenantId, id);
        if ((status == 1 || status == 2) && productIds.isEmpty()) {
            Asserts.fail("公开预告或直播中状态至少要关联一个在售商品");
        }
        validateProducts(tenantId, productIds, status == 1 || status == 2);
        int before = room.getStatus() == null ? 0 : room.getStatus();
        if (before == status) return true;
        if (liveRoomDao.updateStatus(tenantId, id, status, room.getVersion()) <= 0) {
            Asserts.fail("直播间状态已发生变化，请刷新后重试");
        }
        catalogCache.invalidateAfterCommit(tenantId);
        operationLogService.log("LIVE_ROOM", "STATUS", "LIVE_ROOM", String.valueOf(id),
                "status=" + before, "status=" + status, "更新直播间状态");
        return true;
    }

    private LiveRoomVO toVo(DmsLiveRoom room, boolean publicView) {
        Long tenantId = room.getTenantId();
        List<Long> productIds = liveRoomDao.selectProductIds(tenantId, room.getId());
        List<DmsShopProduct> products = new ArrayList<>();
        for (Long productId : productIds) {
            DmsShopProduct item = productDao.selectByIdScoped(tenantId, productId);
            if (item == null) continue;
            if (publicView && (!Integer.valueOf(1).equals(item.getStatus())
                    || !Integer.valueOf(1).equals(item.getNormalSaleEnabled()))) continue;
            if (publicView) product(item, false);
            products.add(item);
        }
        LiveRoomVO vo = new LiveRoomVO();
        vo.setRoom(room);
        vo.setRoomState(resolveState(room));
        vo.setProducts(products);
        vo.setProductIds(publicView ? null : productIds);
        if (publicView && "LIVE".equals(vo.getRoomState())) {
            Long current = liveRoomDao.countCurrentViewers(tenantId, room.getId(), LocalDateTime.now().minusSeconds(90));
            room.setViewerCount(Math.toIntExact(Math.min(Integer.MAX_VALUE, current == null ? 0L : current)));
        }
        if (publicView) {
            room.setTenantId(null);
            room.setVersion(null);
            room.setCreateTime(null);
            room.setUpdateTime(null);
            room.setStreamName(null);
            room.setStopReason(null);
            room.setAnchorId(null);
            if ("UPCOMING".equals(vo.getRoomState())) room.setWatchUrl(null);
        }
        return vo;
    }

    private String resolveState(DmsLiveRoom room) {
        Integer status = room.getStatus();
        LocalDateTime now = LocalDateTime.now();
        if (status == null || status == 0) return "DRAFT";
        if (status == 4) return "DISABLED";
        if (status == 3 || (status == 2 && room.getScheduledEndTime() != null
                && !now.isBefore(room.getScheduledEndTime()))) return "ENDED";
        if (status == 2 && "TENCENT".equalsIgnoreCase(room.getProviderCode())
                && room.getActualStartTime() == null) return "CONNECTING";
        if (status == 2) return "LIVE";
        return "UPCOMING";
    }

    private void replaceProducts(Long tenantId, Long roomId, List<Long> productIds) {
        liveRoomDao.deleteProducts(tenantId, roomId);
        for (int i = 0; i < productIds.size(); i++) {
            liveRoomDao.insertProduct(tenantId, roomId, productIds.get(i), i + 1);
        }
    }

    private void validateProducts(Long tenantId, List<Long> productIds, boolean requireActive) {
        for (Long productId : productIds) {
            DmsShopProduct product = productDao.selectByIdScoped(tenantId, productId);
            if (product == null) Asserts.fail("关联商品不存在：" + productId);
            if (requireActive && (!Integer.valueOf(1).equals(product.getStatus())
                    || !Integer.valueOf(1).equals(product.getNormalSaleEnabled()))) {
                Asserts.fail("公开直播只能关联正常商城的在售商品：" + product.getProductName());
            }
        }
    }

    private List<Long> normalizeProductIds(List<Long> source) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (source != null) {
            for (Long id : source) {
                if (id == null || id <= 0) Asserts.fail("关联商品编号不正确");
                ids.add(id);
            }
        }
        if (ids.size() > 20) Asserts.fail("单个直播间最多关联20个商品");
        return new ArrayList<>(ids);
    }

    private String normalizeMediaUrl(String raw, String fieldName) {
        String value = trimToNull(raw);
        if (value == null) Asserts.fail(fieldName + "不能为空");
        if (value.startsWith("/api/shop/media/images/") || value.startsWith("/shop/media/images/")) return value;
        return requireHttpsUrl(value, fieldName);
    }

    private String normalizeWatchUrl(String raw) {
        String value = trimToNull(raw);
        if (value == null) return null;
        String normalized = requireHttpsUrl(value, "观看地址");
        String allowed = trimToNull(allowedPlaybackOrigin);
        if (allowed != null) {
            try {
                URI source = URI.create(normalized);
                URI configured = URI.create(allowed);
                int sourcePort = source.getPort() < 0 ? 443 : source.getPort();
                int configuredPort = configured.getPort() < 0 ? 443 : configured.getPort();
                if (!"https".equalsIgnoreCase(configured.getScheme())
                        || !java.util.Objects.equals(source.getHost(), configured.getHost())
                        || sourcePort != configuredPort) {
                    Asserts.fail("观看地址必须使用部署时批准的直播播放域名");
                }
            } catch (IllegalArgumentException exception) {
                Asserts.fail("直播播放域名配置不正确");
            }
        }
        return normalized;
    }

    private String requireHttpsUrl(String value, String fieldName) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT))
                    || uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
                Asserts.fail(fieldName + "必须是无账号信息的 HTTPS 地址");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException ex) {
            Asserts.fail(fieldName + "格式不正确");
            return null;
        }
    }

    private void assertPlatformOperator() {
        if (merchantProductReviewService.currentMerchantId() != null) {
            Asserts.fail("直播广场当前由平台统一运营，商户账号无权修改");
        }
    }

    @Override
    public List<LiveAnchorVO> listAnchors(Integer status) {
        assertPlatformOperator();
        if (status != null && !VALID_ANCHOR_STATUSES.contains(status)) Asserts.fail("主播账号状态不正确");
        Long tenantId = TenantContext.getTenantId();
        return liveRoomDao.selectAnchors(tenantId, status).stream().map(this::toAnchorVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LiveAnchorVO saveAnchor(Long id, LiveAnchorSaveDTO dto) {
        assertPlatformOperator();
        Long tenantId = TenantContext.getTenantId();
        DmsLiveAnchor existing = id == null ? null : liveRoomDao.selectAnchorByIdForUpdate(tenantId, id);
        if (id != null && existing == null) Asserts.fail("直播账号不存在");
        DmsShopMember member = existing == null
                ? memberDao.selectByAccount(dto.getMemberAccount().trim())
                : memberDao.selectByUserId(existing.getMemberUserId());
        if (member == null || !Integer.valueOf(0).equals(member.getSystemAccount())) Asserts.fail("未找到可登录的商城账号");
        if (!Integer.valueOf(1).equals(member.getStatus())) Asserts.fail("该商城账号已停用，不能授予直播权限");
        DmsLiveAnchor duplicate = liveRoomDao.selectAnchorByMember(tenantId, member.getUserId());
        if (duplicate != null && (existing == null || !duplicate.getId().equals(existing.getId()))) {
            Asserts.fail("该商城账号已经是直播账号");
        }
        String type = normalizeEnum(dto.getAnchorType(), "PRODUCT", VALID_LIVE_TYPES, "直播账号类型不正确");
        DmsLiveAnchor anchor = existing == null ? new DmsLiveAnchor() : existing;
        anchor.setTenantId(tenantId);
        anchor.setMemberUserId(member.getUserId());
        anchor.setDisplayName(dto.getDisplayName().trim());
        anchor.setAnchorType(type);
        anchor.setCompanyName(trimToNull(dto.getCompanyName()));
        anchor.setBio(trimToNull(dto.getBio()));
        contentModerationService.assertAllowed("主播资料", anchor.getDisplayName() + " "
                + (anchor.getCompanyName() == null ? "" : anchor.getCompanyName()) + " "
                + (anchor.getBio() == null ? "" : anchor.getBio()));
        if (existing == null) {
            anchor.setStatus(1);
            liveRoomDao.insertAnchor(anchor);
        } else if (liveRoomDao.updateAnchor(anchor) <= 0) {
            Asserts.fail("直播账号已被其他管理员更新，请刷新后重试");
        }
        operationLogService.log("LIVE_ANCHOR", existing == null ? "CREATE" : "UPDATE", "LIVE_ANCHOR",
                String.valueOf(anchor.getId()), null, "type=" + type,
                existing == null ? "平台开通直播账号" : "更新直播账号资料");
        return toAnchorVo(liveRoomDao.selectAnchorById(tenantId, anchor.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAnchorStatus(Long id, Integer status) {
        assertPlatformOperator();
        if (status == null || !VALID_ANCHOR_STATUSES.contains(status)) Asserts.fail("主播账号状态不正确");
        Long tenantId = TenantContext.getTenantId();
        DmsLiveAnchor anchor = liveRoomDao.selectAnchorByIdForUpdate(tenantId, id);
        if (anchor == null) Asserts.fail("直播账号不存在");
        int before = anchor.getStatus() == null ? 1 : anchor.getStatus();
        if (before == status) return true;
        if (status != 1) {
            for (DmsLiveRoom room : liveRoomDao.selectByAnchorId(tenantId, id)) {
                if (Integer.valueOf(2).equals(room.getStatus())) {
                    liveRoomDao.stopRoom(tenantId, room.getId(), 4,
                            status == 2 ? "主播权限已暂停" : "主播权限已收回", room.getVersion());
                }
            }
        }
        if (liveRoomDao.updateAnchorStatus(tenantId, id, status, anchor.getVersion()) <= 0) {
            Asserts.fail("直播账号状态已变化，请刷新后重试");
        }
        catalogCache.invalidateAfterCommit(tenantId);
        operationLogService.log("LIVE_ANCHOR", "STATUS", "LIVE_ANCHOR", String.valueOf(id),
                "status=" + before, "status=" + status,
                status == 1 ? "恢复直播权限" : status == 2 ? "暂停直播权限" : "收回直播权限");
        return true;
    }

    @Override
    public LiveStudioVO getStudio(DmsShopMember member) {
        if (member == null) Asserts.unauthorized("请先登录");
        Long tenantId = TenantContext.getTenantId();
        DmsLiveAnchor anchor = liveRoomDao.selectAnchorByMember(tenantId, member.getUserId());
        if (anchor == null) Asserts.fail("当前账号尚未开通直播权限");
        LiveStudioVO result = new LiveStudioVO();
        LiveAnchorVO anchorVo = toAnchorVo(anchor);
        anchorVo.getAnchor().setTenantId(null);
        anchorVo.getAnchor().setMemberUserId(null);
        anchorVo.getAnchor().setVersion(null);
        anchorVo.getAnchor().setCreateTime(null);
        anchorVo.getAnchor().setUpdateTime(null);
        result.setAnchor(anchorVo);
        result.setRooms(liveRoomDao.selectByAnchorId(tenantId, anchor.getId()).stream()
                .map(room -> toVo(room, true)).toList());
        result.setCanStart(Integer.valueOf(1).equals(anchor.getStatus()));
        result.setStatusMessage(Integer.valueOf(1).equals(anchor.getStatus()) ? "直播权限正常"
                : Integer.valueOf(2).equals(anchor.getStatus()) ? "直播权限已被平台暂停" : "直播权限已被平台收回");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LiveStreamCredentialVO start(Long roomId, DmsShopMember member) {
        if (member == null) Asserts.unauthorized("请先登录");
        Long tenantId = TenantContext.getTenantId();
        DmsLiveAnchor anchor = liveRoomDao.selectAnchorByMember(tenantId, member.getUserId());
        if (anchor == null || !Integer.valueOf(1).equals(anchor.getStatus())) Asserts.fail("当前账号没有可用的直播权限");
        DmsLiveRoom room = liveRoomDao.selectByIdForUpdate(tenantId, roomId);
        if (room == null || !anchor.getId().equals(room.getAnchorId())) Asserts.fail("该直播间不属于当前主播账号");
        if (Integer.valueOf(4).equals(room.getStatus())) Asserts.fail("该直播间已被平台停用");
        if (Integer.valueOf(2).equals(room.getStatus())) Asserts.fail("直播间已经在直播中");
        List<Long> productIds = liveRoomDao.selectProductIds(tenantId, roomId);
        if (productIds.isEmpty()) Asserts.fail("开播前至少关联一个在售商品");
        validateProducts(tenantId, productIds, true);
        LiveStreamCredentialVO credential = streamCredentialService.issue(room);
        if (liveRoomDao.startRoom(tenantId, roomId, anchor.getId(), credential.getPlaybackUrl(), room.getVersion()) <= 0) {
            Asserts.fail("直播状态已变化，请刷新后重试");
        }
        liveRoomDao.touchAnchorLiveTime(tenantId, anchor.getId());
        catalogCache.invalidateAfterCommit(tenantId);
        operationLogService.log("LIVE_ROOM", "START", "LIVE_ROOM", String.valueOf(roomId),
                "status=" + room.getStatus(), "status=2,provider=" + credential.getProviderCode(), "主播开始直播");
        return credential;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean stop(Long roomId, DmsShopMember member) {
        if (member == null) Asserts.unauthorized("请先登录");
        Long tenantId = TenantContext.getTenantId();
        DmsLiveAnchor anchor = liveRoomDao.selectAnchorByMember(tenantId, member.getUserId());
        if (anchor == null) Asserts.fail("当前账号没有直播权限");
        DmsLiveRoom room = liveRoomDao.selectByIdForUpdate(tenantId, roomId);
        if (room == null || !anchor.getId().equals(room.getAnchorId())) Asserts.fail("该直播间不属于当前主播账号");
        if (!Integer.valueOf(2).equals(room.getStatus())) return true;
        if (liveRoomDao.stopRoom(tenantId, roomId, 3, null, room.getVersion()) <= 0) Asserts.fail("直播状态已变化，请刷新后重试");
        catalogCache.invalidateAfterCommit(tenantId);
        operationLogService.log("LIVE_ROOM", "STOP", "LIVE_ROOM", String.valueOf(roomId), "status=2", "status=3", "主播结束直播");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean forceStop(Long roomId, String reason) {
        assertPlatformOperator();
        String normalizedReason = trimToNull(reason);
        if (normalizedReason == null) Asserts.fail("请填写平台停播原因");
        if (normalizedReason.length() > 200) Asserts.fail("平台停播原因不能超过200个字");
        Long tenantId = TenantContext.getTenantId();
        DmsLiveRoom room = liveRoomDao.selectByIdForUpdate(tenantId, roomId);
        if (room == null) Asserts.fail("直播间不存在");
        if (!Integer.valueOf(2).equals(room.getStatus())) return true;
        if (liveRoomDao.stopRoom(tenantId, roomId, 4, normalizedReason, room.getVersion()) <= 0) Asserts.fail("直播状态已变化，请刷新后重试");
        catalogCache.invalidateAfterCommit(tenantId);
        operationLogService.log("LIVE_ROOM", "FORCE_STOP", "LIVE_ROOM", String.valueOf(roomId),
                "status=2", "status=4", "平台强制停播：" + normalizedReason);
        return true;
    }

    @Override
    public List<DmsLiveComment> listComments(Long roomId, Long afterId, int limit) {
        requirePublicRoom(roomId);
        return liveRoomDao.selectPublicComments(TenantContext.getTenantId(), roomId, afterId,
                Math.max(1, Math.min(100, limit))).stream().map(this::sanitizePublicComment).toList();
    }

    @Override
    public List<DmsLiveComment> listAdminComments(Long roomId, Integer status, int limit) {
        assertPlatformOperator();
        if (status != null && !Set.of(1, 2).contains(status)) Asserts.fail("评论状态不正确");
        if (liveRoomDao.selectById(TenantContext.getTenantId(), roomId) == null) Asserts.fail("直播间不存在");
        return liveRoomDao.selectAdminComments(TenantContext.getTenantId(), roomId, status,
                Math.max(1, Math.min(200, limit)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsLiveComment submitComment(Long roomId, DmsShopMember member, LiveCommentSubmitDTO dto) {
        if (member == null) Asserts.unauthorized("登录后才能参与直播评论");
        DmsLiveRoom room = requirePublicRoom(roomId);
        if (!Integer.valueOf(2).equals(room.getStatus())) Asserts.fail("只有直播进行中可以评论");
        if (!Integer.valueOf(1).equals(room.getCommentEnabled())) Asserts.fail("当前直播间已关闭评论");
        String content = dto.getContent().trim();
        contentModerationService.assertAllowed("直播评论", content);
        DmsLiveComment comment = new DmsLiveComment();
        comment.setTenantId(TenantContext.getTenantId());
        comment.setLiveRoomId(roomId);
        comment.setUserId(member.getUserId());
        comment.setDisplayName(maskCommentName(member));
        comment.setContent(content);
        comment.setStatus(1);
        liveRoomDao.insertComment(comment);
        liveRoomDao.insertEvent(comment.getTenantId(), roomId, dto.getVisitorId(), member.getUserId(), "COMMENT", null);
        return sanitizePublicComment(comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCommentStatus(Long commentId, Integer status) {
        assertPlatformOperator();
        if (status == null || !Set.of(1, 2).contains(status)) Asserts.fail("评论状态不正确");
        if (liveRoomDao.updateCommentStatus(TenantContext.getTenantId(), commentId, status) <= 0) Asserts.fail("评论不存在");
        operationLogService.log("LIVE_COMMENT", "STATUS", "LIVE_COMMENT", String.valueOf(commentId),
                null, "status=" + status, status == 2 ? "隐藏直播评论" : "恢复直播评论");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean recordEngagement(Long roomId, DmsShopMember member, LiveEngagementDTO dto) {
        DmsLiveRoom room = requirePublicRoom(roomId);
        String eventType = dto.getEventType();
        Long userId = member == null ? null : member.getUserId();
        int duration = dto.getDurationSeconds() == null ? 0 : dto.getDurationSeconds();
        if ("PRODUCT_CLICK".equals(eventType)
                && (dto.getProductId() == null || !liveRoomDao.selectProductIds(room.getTenantId(), roomId).contains(dto.getProductId()))) {
            Asserts.fail("直播商品不存在或已移除");
        }
        if ("SHARE".equals(eventType) && !Integer.valueOf(1).equals(room.getShareEnabled())) Asserts.fail("当前直播间已关闭分享");
        if (Set.of("ENTER", "HEARTBEAT", "LEAVE").contains(eventType)) {
            if (liveRoomDao.updateViewSession(room.getTenantId(), roomId, dto.getVisitorId(), userId, duration) <= 0) {
                try {
                    liveRoomDao.insertViewSession(room.getTenantId(), roomId, dto.getVisitorId(), userId, duration);
                } catch (DuplicateKeyException duplicate) {
                    liveRoomDao.updateViewSession(room.getTenantId(), roomId, dto.getVisitorId(), userId, duration);
                }
            }
        }
        if (!"HEARTBEAT".equals(eventType)) {
            liveRoomDao.insertEvent(room.getTenantId(), roomId, dto.getVisitorId(), userId, eventType, dto.getProductId());
        }
        return true;
    }

    @Override
    public List<Long> listReservations(DmsShopMember member) {
        if (member == null) Asserts.unauthorized("登录后才能查看直播预约");
        if (!isPublicEnabled(TenantContext.getTenantId())) return List.of();
        return liveRoomDao.selectReservedRoomIds(TenantContext.getTenantId(), member.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reserve(Long roomId, DmsShopMember member) {
        if (member == null) Asserts.unauthorized("登录后才能预约直播");
        DmsLiveRoom room = requirePublicRoom(roomId);
        if (!"UPCOMING".equals(resolveState(room))) Asserts.fail("只有直播预告可以预约");
        if (liveRoomDao.upsertReservation(room.getTenantId(), roomId, member.getUserId()) <= 0) {
            Asserts.fail("直播预约保存失败");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelReservation(Long roomId, DmsShopMember member) {
        if (member == null) Asserts.unauthorized("登录后才能取消直播预约");
        requirePublicRoom(roomId);
        liveRoomDao.cancelReservation(TenantContext.getTenantId(), roomId, member.getUserId());
        return true;
    }

    @Override
    public LiveAnalyticsVO getAnalytics(Long roomId) {
        assertPlatformOperator();
        LiveAnalyticsVO result = liveRoomDao.selectAnalytics(TenantContext.getTenantId(), roomId,
                LocalDateTime.now().minusSeconds(90));
        if (result == null) Asserts.fail("直播间不存在");
        long viewers = result.getUniqueViewers() == null ? 0 : result.getUniqueViewers();
        long clicks = result.getProductClickCount() == null ? 0 : result.getProductClickCount();
        long orders = result.getPaidOrderCount() == null ? 0 : result.getPaidOrderCount();
        result.setViewerToClickRate(rate(clicks, viewers));
        result.setClickToPaidRate(rate(orders, clicks));
        return result;
    }

    @Override
    public Long resolveRecentAttribution(Long tenantId, Long userId, List<Long> productIds) {
        if (tenantId == null || userId == null || productIds == null || productIds.isEmpty()) return null;
        return liveRoomDao.selectRecentAttributionRoom(tenantId, userId, productIds, LocalDateTime.now().minusHours(24));
    }

    private DmsLiveRoom requirePublicRoom(Long roomId) {
        if (!isPublicEnabled(TenantContext.getTenantId())) Asserts.fail("直播广场暂未开放");
        DmsLiveRoom room = liveRoomDao.selectById(TenantContext.getTenantId(), roomId);
        if (room == null || room.getStatus() == null || !Set.of(1, 2, 3).contains(room.getStatus())) {
            Asserts.fail("直播间不存在或暂未公开");
        }
        return room;
    }

    private LiveAnchorVO toAnchorVo(DmsLiveAnchor anchor) {
        LiveAnchorVO result = new LiveAnchorVO();
        result.setAnchor(anchor);
        DmsShopMember member = memberDao.selectByUserId(anchor.getMemberUserId());
        result.setMemberAccount(MemberAccountUtils.maskAccount(MemberAccountUtils.display(member)));
        List<DmsLiveRoom> rooms = liveRoomDao.selectByAnchorId(anchor.getTenantId(), anchor.getId());
        result.setLiveRoomCount(rooms.size());
        result.setLiveRoomLiveCount((int) rooms.stream().filter(room -> Integer.valueOf(2).equals(room.getStatus())).count());
        result.setStatusLabel(Integer.valueOf(1).equals(anchor.getStatus()) ? "可开播"
                : Integer.valueOf(2).equals(anchor.getStatus()) ? "已暂停" : "已收回");
        return result;
    }

    private String maskCommentName(DmsShopMember member) {
        String value = trimToNull(member.getNickname());
        if (value == null) value = MemberAccountUtils.display(member);
        return MemberAccountUtils.maskAccount(value == null ? "商城用户" : value);
    }

    private DmsLiveComment sanitizePublicComment(DmsLiveComment comment) {
        comment.setTenantId(null);
        comment.setUserId(null);
        comment.setUpdateTime(null);
        return comment;
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private String normalizeEnum(String value, String fallback, Set<String> allowed, String error) {
        String normalized = trimToNull(value);
        normalized = normalized == null ? fallback : normalized.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) Asserts.fail(error);
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
