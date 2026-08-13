package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.constants.ShopBusinessType;
import com.macro.mall.distribution.dao.DmsFlashSaleActivityDao;
import com.macro.mall.distribution.dao.DmsFlashSaleReservationDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dao.DmsShopSkuDao;
import com.macro.mall.distribution.dto.FlashSaleActivitySaveDTO;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.entity.DmsFlashSaleActivity;
import com.macro.mall.distribution.entity.DmsFlashSaleReservation;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopSku;
import com.macro.mall.distribution.security.SecurityRateLimitService;
import com.macro.mall.distribution.service.FlashSaleService;
import com.macro.mall.distribution.service.FlashSaleStockGate;
import com.macro.mall.distribution.service.ShopBusinessModeService;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.vo.FlashSaleActivityVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.macro.mall.distribution.util.ShopPublicViewSanitizer.product;
import static com.macro.mall.distribution.util.ShopPublicViewSanitizer.sku;

@Service
@RequiredArgsConstructor
public class FlashSaleServiceImpl implements FlashSaleService {

    private final DmsFlashSaleActivityDao activityDao;
    private final DmsFlashSaleReservationDao reservationDao;
    private final DmsShopProductDao productDao;
    private final DmsShopSkuDao skuDao;
    private final ShopService shopService;
    private final ShopBusinessModeService businessModeService;
    private final FlashSaleStockGate stockGate;
    private final SecurityRateLimitService rateLimitService;

    @Override
    public List<FlashSaleActivityVO> listFront() {
        Long tenantId = TenantContext.getTenantId();
        if (businessModeService.config(tenantId, null).getFlashSaleEnabled() != 1) return List.of();
        return activityDao.selectFrontList(tenantId).stream().map(item -> toVo(item, true)).toList();
    }

    @Override
    public List<FlashSaleActivityVO> listAdmin(Integer status) {
        return activityDao.selectList(TenantContext.getTenantId(), status).stream().map(item -> toVo(item, false)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsFlashSaleActivity save(Long id, FlashSaleActivitySaveDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        if (businessModeService.config(tenantId, null).getFlashSaleEnabled() != 1) Asserts.fail("请先在商城业务模式中启用秒杀");
        if (dto.getStartTime() == null || dto.getEndTime() == null || !dto.getEndTime().isAfter(dto.getStartTime())) {
            Asserts.fail("秒杀结束时间必须晚于开始时间");
        }
        DmsShopProduct product = productDao.selectById(dto.getProductId());
        if (product == null || !tenantId.equals(product.getTenantId()) || !Integer.valueOf(1).equals(product.getStatus())) {
            Asserts.fail("秒杀商品不存在或已下架");
        }
        DmsShopSku sku = null;
        int physicalStock = product.getStock() == null ? 0 : product.getStock();
        BigDecimal regularPrice = product.getSalePrice();
        if (dto.getSkuId() != null) {
            sku = skuDao.selectById(dto.getSkuId());
            if (sku == null || !product.getId().equals(sku.getProductId()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                Asserts.fail("秒杀SKU不存在或已停用");
            }
            physicalStock = sku.getStock() == null ? 0 : sku.getStock();
            regularPrice = sku.getSalePrice();
        }
        if (dto.getTotalStock() > physicalStock) Asserts.fail("秒杀库存不能超过当前可售库存");
        if (dto.getFlashPrice().compareTo(regularPrice) > 0) Asserts.fail("秒杀价不能高于普通售价");
        BigDecimal flashPv = dto.getFlashPv() == null ? BigDecimal.ZERO : dto.getFlashPv();
        if (flashPv.compareTo(dto.getFlashPrice()) > 0) Asserts.fail("秒杀PV不能高于秒杀价");

        DmsFlashSaleActivity activity = id == null ? new DmsFlashSaleActivity() : activityDao.selectById(id);
        if (id != null && (activity == null || !tenantId.equals(activity.getTenantId()))) Asserts.fail("秒杀活动不存在");
        if (id != null && !activity.getAvailableStock().equals(activity.getTotalStock())) {
            Asserts.fail("活动已有抢购记录，不能再修改商品、价格或库存；可停用后新建活动");
        }
        activity.setId(id);
        activity.setTenantId(tenantId);
        activity.setActivityName(dto.getActivityName().trim());
        activity.setProductId(dto.getProductId());
        activity.setSkuId(dto.getSkuId());
        activity.setFlashPrice(dto.getFlashPrice());
        activity.setFlashPv(flashPv);
        activity.setTotalStock(dto.getTotalStock());
        activity.setAvailableStock(dto.getTotalStock());
        activity.setPerUserLimit(dto.getPerUserLimit());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        if (!List.of(0, 1, 2).contains(activity.getStatus())) Asserts.fail("秒杀活动状态不正确");
        if (id == null) activityDao.insert(activity); else activityDao.update(activity);
        stockGate.reset(activity);
        return activityDao.selectById(activity.getId());
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        DmsFlashSaleActivity activity = activityDao.selectById(id);
        if (activity == null || !TenantContext.getTenantId().equals(activity.getTenantId())) Asserts.fail("秒杀活动不存在");
        if (status == null || !List.of(0, 1, 2).contains(status)) Asserts.fail("秒杀活动状态不正确");
        boolean updated = activityDao.updateStatus(id, status) > 0;
        if (updated) stockGate.reset(activity);
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopOrderVO submit(Long activityId, ShopOrderSubmitDTO dto, DmsShopMember member) {
        if (member == null || member.getUserId() == null) Asserts.fail("请先登录后参与秒杀");
        DmsFlashSaleActivity activity = activityDao.selectById(activityId);
        Long tenantId = TenantContext.getTenantId();
        if (activity == null || !tenantId.equals(activity.getTenantId())) Asserts.fail("秒杀活动不存在");
        businessModeService.requireEnabled(tenantId, ShopBusinessType.FLASH_SALE, member);
        LocalDateTime now = LocalDateTime.now();
        if (!Integer.valueOf(1).equals(activity.getStatus()) || now.isBefore(activity.getStartTime())) Asserts.fail("秒杀尚未开始");
        if (!now.isBefore(activity.getEndTime())) Asserts.fail("秒杀已结束");
        if (dto == null || dto.getItems() == null || dto.getItems().size() != 1) Asserts.fail("秒杀订单只能包含一个活动商品");
        ShopOrderItemDTO item = dto.getItems().get(0);
        int quantity = item.getQuantity() == null || item.getQuantity() <= 0 ? 1 : item.getQuantity();
        if (!activity.getProductId().equals(item.getProductId()) || !java.util.Objects.equals(activity.getSkuId(), item.getSkuId())) {
            Asserts.fail("秒杀商品与活动不一致");
        }
        if (quantity > activity.getPerUserLimit()) Asserts.fail("本活动每人限购 " + activity.getPerUserLimit() + " 件");
        String rateKey = "rate:flash:" + tenantId + ":" + activityId + ":" + member.getUserId();
        if (!rateLimitService.tryAcquire(rateKey, 5, 2)) Asserts.fail("抢购过于频繁，请稍后再试");

        FlashSaleStockGate.Result gateResult = stockGate.acquire(activity, member.getUserId(), quantity);
        if (gateResult == FlashSaleStockGate.Result.DUPLICATE) Asserts.fail("您已抢到本场秒杀，请勿重复提交");
        if (gateResult == FlashSaleStockGate.Result.SOLD_OUT) Asserts.fail("秒杀商品已抢完");
        boolean gated = gateResult == FlashSaleStockGate.Result.ACQUIRED;
        try {
            DmsFlashSaleReservation reservation = reservationDao.selectByActivityAndUser(activityId, member.getUserId());
            if (reservation != null && !"RELEASED".equals(reservation.getStatus())) Asserts.fail("您已参加过本场秒杀");
            if (activityDao.decreaseStock(activityId, quantity) <= 0) Asserts.fail("秒杀商品已抢完或活动已结束");
            if (reservation == null) {
                reservation = new DmsFlashSaleReservation();
                reservation.setTenantId(tenantId);
                reservation.setActivityId(activityId);
                reservation.setUserId(member.getUserId());
                reservation.setQuantity(quantity);
                reservation.setStatus("RESERVED");
                reservationDao.insert(reservation);
            } else {
                reservation.setQuantity(quantity);
                if (reservationDao.reactivate(reservation) <= 0) Asserts.fail("秒杀资格恢复失败，请重试");
            }
            dto.setBusinessType(ShopBusinessType.FLASH_SALE);
            dto.setBusinessSourceId(activityId);
            ShopOrderVO order = shopService.submitReservedFlashSaleOrder(dto, member);
            reservation.setOrderId(order.getOrder().getId());
            reservation.setOrderNo(order.getOrder().getOrderNo());
            if (reservationDao.bindOrder(reservation) <= 0) Asserts.fail("秒杀订单绑定失败，请重试");
            return order;
        } catch (RuntimeException ex) {
            if (gated) stockGate.release(activity, member.getUserId(), quantity);
            throw ex;
        }
    }

    private FlashSaleActivityVO toVo(DmsFlashSaleActivity activity, boolean publicView) {
        FlashSaleActivityVO vo = new FlashSaleActivityVO();
        vo.setActivity(activity);
        vo.setProduct(productDao.selectById(activity.getProductId()));
        vo.setSku(activity.getSkuId() == null ? null : skuDao.selectById(activity.getSkuId()));
        if (publicView) {
            product(vo.getProduct(), false);
            sku(vo.getSku(), false);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!Integer.valueOf(1).equals(activity.getStatus())) vo.setActivityState("DISABLED");
        else if (!now.isBefore(activity.getEndTime())) vo.setActivityState("ENDED");
        else if (activity.getAvailableStock() == null || activity.getAvailableStock() <= 0) vo.setActivityState("SOLD_OUT");
        else if (now.isBefore(activity.getStartTime())) vo.setActivityState("UPCOMING");
        else vo.setActivityState("ACTIVE");
        return vo;
    }
}
