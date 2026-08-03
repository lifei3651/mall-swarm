package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.macro.mall.distribution.dao.DmsAgentAccountDao;
import com.macro.mall.distribution.dao.DmsAgentChangeLogDao;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsAgentRelationDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsLineChangeApplicationDao;
import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.dto.AgentUpdateDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsAgentAccount;
import com.macro.mall.distribution.entity.DmsAgentChangeLog;
import com.macro.mall.distribution.entity.DmsAgentRelation;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.enums.*;
import com.macro.mall.distribution.service.AgentRelationService;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.CommissionService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.util.MemberAccountUtils;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.LinkedHashMap;

/**
 * 代理服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final DmsAgentDao agentDao;
    private final DmsAgentRelationDao relationDao;
    private final DmsAgentAccountDao accountDao;
    private final DmsAgentChangeLogDao changeLogDao;
    private final DmsShopMemberDao shopMemberDao;
    private final DmsLineChangeApplicationDao lineChangeApplicationDao;
    private final AgentRelationService relationService;
    private final CommissionService commissionService;
    private final PerformanceService performanceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentInfoVO register(AgentRegisterDTO registerDTO) {
        if (registerDTO == null || registerDTO.getUserId() == null) {
            Asserts.fail("请选择已有商城会员后再开通推广身份");
        }
        DmsShopMember shopMember = shopMemberDao.selectByUserId(registerDTO.getUserId());
        if (shopMember == null) shopMember = shopMemberDao.selectById(registerDTO.getUserId());
        if (shopMember == null) {
            Asserts.fail("商城会员不存在，请先注册会员或从会员中心确认用户ID");
        }
        registerDTO.setUserId(shopMember.getUserId());
        // 检查用户是否已经是代理
        DmsAgent existAgent = agentDao.selectByUserId(registerDTO.getUserId());
        if (existAgent != null) {
            Asserts.fail("该用户已经进入会员关系体系");
        }

        // 创建代理
        DmsAgent agent = new DmsAgent();
        agent.setUserId(registerDTO.getUserId());
        agent.setAgentCode(generateAgentCode());
        agent.setAgentName(registerDTO.getAgentName() == null || registerDTO.getAgentName().isBlank()
                ? (shopMember.getNickname() == null || shopMember.getNickname().isBlank() ? shopMember.getPhone() : shopMember.getNickname())
                : registerDTO.getAgentName());
        int initialLevel = registerDTO.getInitialLevel() == null
                ? AgentLevelEnum.MEMBER.getValue() : registerDTO.getInitialLevel();
        if (AgentLevelEnum.getByValue(initialLevel) == null) {
            Asserts.fail("初始会员级别不正确");
        }
        agent.setAgentLevel(initialLevel);
        String agentPhone = registerDTO.getPhone() == null || registerDTO.getPhone().isBlank()
                ? shopMember.getPhone() : PhoneNumberUtils.normalize(registerDTO.getPhone());
        if (!PhoneNumberUtils.isValidMainlandMobile(agentPhone)) {
            Asserts.fail("请输入正确的11位手机号");
        }
        agent.setPhone(agentPhone);
        agent.setRealName(registerDTO.getRealName());
        agent.setIdCard(registerDTO.getIdCard());
        String memberInviteCode = shopMember.getInviteCode() == null
                ? null : shopMember.getInviteCode().trim().toUpperCase(java.util.Locale.ROOT);
        // 新账号只保留一套邀请码；若遇到历史碰撞才生成备用码。
        agent.setInviteCode(memberInviteCode != null && !memberInviteCode.isBlank()
                && agentDao.selectByInviteCode(memberInviteCode) == null
                ? memberInviteCode : generateInviteCode());
        agent.setStatus(AgentStatusEnum.NORMAL.getValue());
        agent.setSourceType(registerDTO.getSourceType() != null ? registerDTO.getSourceType() : AgentSourceTypeEnum.SELF_REGISTER.getValue());
        agent.setLevelDepth(1);

        // 如果有邀请码，绑定上级关系
        DmsAgent parentAgent = null;
        if (registerDTO.getInviteCode() != null && !registerDTO.getInviteCode().isEmpty()) {
            parentAgent = agentDao.selectByInviteCode(registerDTO.getInviteCode().trim().toUpperCase(java.util.Locale.ROOT));
            if (parentAgent == null) {
                Asserts.fail("邀请码无效");
            }
            agent.setParentId(parentAgent.getId());
            agent.setAncestorIds(parentAgent.getAncestorIds() != null ?
                    parentAgent.getAncestorIds() + "," + parentAgent.getId() :
                    String.valueOf(parentAgent.getId()));
            agent.setLevelDepth(parentAgent.getLevelDepth() + 1);
        }

        agentDao.insert(agent);

        // 初始化代理账户
        DmsAgentAccount account = new DmsAgentAccount();
        account.setAgentId(agent.getId());
        account.setUserId(agent.getUserId());
        account.setTotalCommission(BigDecimal.ZERO);
        account.setSettledCommission(BigDecimal.ZERO);
        account.setUnsettledCommission(BigDecimal.ZERO);
        account.setFrozenCommission(BigDecimal.ZERO);
        account.setWithdrawnAmount(BigDecimal.ZERO);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setTotalOrders(0);
        account.setTotalTeamMembers(0);
        accountDao.insert(account);

        // 创建推广身份本身也是一次重要会员变更；即使初始级别为一级也必须留痕。
        DmsAgentChangeLog activationLog = new DmsAgentChangeLog();
        activationLog.setAgentId(agent.getId());
        activationLog.setUserId(agent.getUserId());
        activationLog.setChangeType(ChangeTypeEnum.INFO_CHANGE.getValue());
        activationLog.setOldLevel(null);
        activationLog.setNewLevel(initialLevel);
        activationLog.setChangeReason(registerDTO.getReason() == null || registerDTO.getReason().isBlank()
                ? "创建推广身份" : registerDTO.getReason().trim());
        activationLog.setChangeDetail("{\"action\":\"activate_distribution\",\"effect\":\"future_orders_only\",\"historyRecalculated\":false}");
        DmsAdminUser activationAdmin = AdminContext.get();
        activationLog.setOperatorId(activationAdmin == null ? 0L : activationAdmin.getId());
        activationLog.setOperatorName(activationAdmin == null ? "system" : activationAdmin.getUsername());
        activationLog.setOperatorType(activationAdmin == null ? 1 : 2);
        changeLogDao.insert(activationLog);

        // 如果有上级，绑定关系
        if (parentAgent != null) {
            relationService.bindRelation(
                    agent.getUserId(),
                    agent.getId(),
                    parentAgent.getUserId(),
                    parentAgent.getId(),
                    BindTypeEnum.INVITE_CODE.getValue()
            );

            // 更新上级的团队人数
            updateTeamMemberCount(agent.getParentId());
        }

        log.info("代理注册成功: agentId={}, userId={}", agent.getId(), agent.getUserId());
        return convertToVO(agent);
    }

    @Override
    public AgentInfoVO getAgentById(Long id) {
        DmsAgent agent = agentDao.selectById(id);
        return agent != null ? fillTreeMetric(convertToVO(agent)) : null;
    }

    @Override
    public AgentInfoVO getAgentByUserId(Long userId) {
        DmsAgent agent = agentDao.selectByUserId(userId);
        return agent != null ? fillTreeMetric(convertToVO(agent)) : null;
    }

    @Override
    public AgentInfoVO getAgentByAgentCode(String agentCode) {
        DmsAgent agent = agentDao.selectByAgentCode(agentCode);
        return agent != null ? fillTreeMetric(convertToVO(agent)) : null;
    }

    @Override
    public AgentInfoVO getAgentByInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) return null;
        DmsAgent agent = agentDao.selectByInviteCode(inviteCode.trim().toUpperCase(java.util.Locale.ROOT));
        return agent != null ? fillTreeMetric(convertToVO(agent)) : null;
    }

    @Override
    public List<AgentInfoVO> listAgents(String keyword, Integer status) {
        return fillPendingLineChange(convertToVOList(agentDao.search(keyword, status)));
    }

    @Override
    public List<AgentInfoVO> listAgents(String keyword, Integer status, Integer agentLevel) {
        return fillPendingLineChange(convertToVOList(agentDao.searchWithLevel(keyword, status, agentLevel)));
    }

    @Override
    public List<AgentInfoVO> getRootAgents() {
        return fillTreeMetrics(convertToVOList(agentDao.selectRoots()));
    }

    @Override
    public List<AgentInfoVO> getChildrenAgents(Long parentId) {
        List<DmsAgent> agents = agentDao.selectByParentId(parentId);
        return fillTreeMetrics(convertToVOList(agents));
    }

    @Override
    public List<AgentInfoVO> getAllDescendants(Long agentId) {
        List<DmsAgentRelation> relations = relationDao.selectAllDescendants(agentId);
        List<AgentInfoVO> result = new ArrayList<>();
        for (DmsAgentRelation relation : relations) {
            DmsAgent agent = agentDao.selectById(relation.getAgentId());
            if (agent != null) {
                AgentInfoVO vo = convertToVO(agent);
                vo.setParentId(relation.getParentAgentId());
                result.add(vo);
            }
        }
        return fillTreeMetrics(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean switchLine(AgentSwitchLineDTO switchLineDTO) {
        Long agentId = switchLineDTO.getAgentId();
        Long newParentAgentId = switchLineDTO.getNewParentAgentId();

        // 查询代理信息
        DmsAgent agent = agentDao.selectById(agentId);
        if (agent == null) {
            Asserts.fail("移线会员不存在");
        }

        // 查询新上级代理信息
        DmsAgent newParentAgent = agentDao.selectById(newParentAgentId);
        if (newParentAgent == null) {
            Asserts.fail("新直属上级会员不存在");
        }

        // 检查是否形成循环
        if (wouldCreateCycle(agentId, newParentAgentId)) {
            Asserts.fail("不能形成循环关系");
        }

        // 记录原上级信息
        Long oldParentAgentId = agent.getParentId();
        DmsAgent oldParentAgent = oldParentAgentId != null ? agentDao.selectById(oldParentAgentId) : null;

        // 先快照整棵子树；历史业绩和历史佣金保持原样，不做结算、转移或重算。
        Map<Long, DmsAgent> subtree = new LinkedHashMap<>();
        subtree.put(agent.getId(), agent);
        for (DmsAgentRelation relation : relationDao.selectAllDescendants(agentId)) {
            DmsAgent descendant = agentDao.selectById(relation.getAgentId());
            if (descendant != null) subtree.putIfAbsent(descendant.getId(), descendant);
        }

        // 同一事务内作废子树全部旧关系，随后按新树位置完整重建。
        relationDao.invalidRelationsByAgentIds(new ArrayList<>(subtree.keySet()), switchLineDTO.getReason());

        // 更新被移线根节点
        agent.setParentId(newParentAgentId);
        agent.setAncestorIds(newParentAgent.getAncestorIds() != null ?
                newParentAgent.getAncestorIds() + "," + newParentAgentId :
                String.valueOf(newParentAgentId));
        agent.setLevelDepth(newParentAgent.getLevelDepth() + 1);
        agentDao.update(agent);
        DmsShopMember movedMember = shopMemberDao.selectByUserId(agent.getUserId());
        if (movedMember != null) {
            // 切线就是变更本人的直属邀请人；下级各自的直属邀请关系不变。
            shopMemberDao.updateInviterId(movedMember.getId(), newParentAgent.getUserId());
        }

        // 根节点先绑定，再按原深度从浅到深重建下级的全部祖先关系（不限层级）。
        relationService.bindRelation(agent.getUserId(), agent.getId(), newParentAgent.getUserId(),
                newParentAgent.getId(), BindTypeEnum.ADMIN_BIND.getValue());
        List<DmsAgent> descendants = subtree.values().stream()
                .filter(item -> !item.getId().equals(agentId))
                .sorted(Comparator.comparing(DmsAgent::getLevelDepth, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        for (DmsAgent descendant : descendants) {
            DmsAgent parent = agentDao.selectById(descendant.getParentId());
            if (parent == null) Asserts.fail("下级会员的直属上级不存在，无法重建关系");
            descendant.setAncestorIds(parent.getAncestorIds() == null || parent.getAncestorIds().isBlank()
                    ? String.valueOf(parent.getId()) : parent.getAncestorIds() + "," + parent.getId());
            descendant.setLevelDepth(parent.getLevelDepth() + 1);
            agentDao.update(descendant);
            if (!relationService.bindRelation(descendant.getUserId(), descendant.getId(), parent.getUserId(),
                    parent.getId(), BindTypeEnum.ADMIN_BIND.getValue())) {
                Asserts.fail("下级会员关系重建失败");
            }
        }

        // 6. 记录变更日志
        DmsAgentChangeLog changeLog = new DmsAgentChangeLog();
        changeLog.setAgentId(agentId);
        changeLog.setUserId(agent.getUserId());
        changeLog.setChangeType(ChangeTypeEnum.SWITCH_LINE.getValue());
        changeLog.setOldParentAgentId(oldParentAgentId);
        changeLog.setOldParentName(oldParentAgent != null ? oldParentAgent.getAgentName() : null);
        changeLog.setNewParentAgentId(newParentAgentId);
        changeLog.setNewParentName(newParentAgent.getAgentName());
        changeLog.setChangeReason(switchLineDTO.getReason());
        DmsAdminUser currentAdmin = AdminContext.get();
        changeLog.setOperatorId(currentAdmin == null ? 0L : currentAdmin.getId());
        changeLog.setOperatorName(currentAdmin == null ? "system" : currentAdmin.getUsername());
        changeLog.setOperatorType(1);
        changeLogDao.insert(changeLog);

        // 7. 更新新旧上级的团队人数
        if (oldParentAgentId != null) {
            updateTeamMemberCount(oldParentAgentId);
        }
        updateTeamMemberCount(newParentAgentId);

        log.info("代理切线成功: agentId={}, oldParentId={}, newParentId={}", agentId, oldParentAgentId, newParentAgentId);
        return true;
    }

    @Override
    public boolean updateAgent(DmsAgent agent) {
        return agentDao.update(agent) > 0;
    }

    @Override
    public boolean updateAgentInfo(Long id, AgentUpdateDTO updateDTO) {
        DmsAgent agent = agentDao.selectById(id);
        if (agent == null) {
            Asserts.fail("会员不存在");
        }
        // 仅更新允许的字段
        if (updateDTO.getAgentName() != null) {
            agent.setAgentName(updateDTO.getAgentName());
        }
        if (updateDTO.getRealName() != null) {
            agent.setRealName(updateDTO.getRealName());
        }
        if (updateDTO.getPhone() != null) {
            if (!PhoneNumberUtils.isValidMainlandMobile(updateDTO.getPhone())) {
                Asserts.fail("请输入正确的11位手机号");
            }
            agent.setPhone(PhoneNumberUtils.normalize(updateDTO.getPhone()));
        }
        if (updateDTO.getIdCard() != null) {
            agent.setIdCard(updateDTO.getIdCard());
        }
        if (updateDTO.getRemark() != null) {
            agent.setRemark(updateDTO.getRemark());
        }
        return agentDao.update(agent) > 0;
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        return agentDao.updateStatus(id, status) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentInfoVO adjustLevel(Long id, Integer level, String reason) {
        DmsAgent agent = agentDao.selectById(id);
        if (agent == null) Asserts.fail("会员不存在");
        if (AgentLevelEnum.getByValue(level) == null) Asserts.fail("会员卡级不正确");
        if (reason == null || reason.isBlank()) Asserts.fail("请输入调级原因");
        int oldLevel = agent.getAgentLevel() == null ? 1 : agent.getAgentLevel();
        if (oldLevel == level) return convertToVO(agent);
        agent.setAgentLevel(level);
        agentDao.update(agent);

        DmsAgentChangeLog changeLog = new DmsAgentChangeLog();
        changeLog.setAgentId(agent.getId()); changeLog.setUserId(agent.getUserId());
        changeLog.setChangeType(level > oldLevel ? ChangeTypeEnum.UPGRADE.getValue() : ChangeTypeEnum.DOWNGRADE.getValue());
        changeLog.setOldLevel(oldLevel); changeLog.setNewLevel(level); changeLog.setChangeReason(reason.trim());
        changeLog.setChangeDetail("{\"effect\":\"future_orders_only\",\"historyRecalculated\":false}");
        DmsAdminUser admin = AdminContext.get();
        changeLog.setOperatorId(admin == null ? 0L : admin.getId());
        changeLog.setOperatorName(admin == null ? "system" : admin.getUsername());
        changeLog.setOperatorType(admin == null ? 1 : 2);
        changeLogDao.insert(changeLog);
        return convertToVO(agentDao.selectById(id));
    }

    @Override
    public String generateInviteCode() {
        String code;
        do {
            code = RandomUtil.randomStringUpper(8);
        } while (agentDao.selectByInviteCode(code) != null);
        return code;
    }

    @Override
    public String generateQrCodeUrl(Long agentId) {
        DmsAgent agent = agentDao.selectById(agentId);
        if (agent == null) {
            Asserts.fail("会员不存在");
        }

        String content = "mall://distribution/register?agentId=" + agent.getId()
                + "&inviteCode=" + agent.getInviteCode();
        String dataUrl = createQrCodeDataUrl(content);
        agent.setQrCodeUrl(dataUrl);
        agentDao.update(agent);
        return dataUrl;
    }

    private String createQrCodeDataUrl(String content) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 280, 280, hints);
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF111827 : 0xFFFFFFFF);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.error("生成代理二维码失败", e);
            Asserts.fail("生成二维码失败");
            return null;
        }
    }

    /**
     * 生成代理编号
     */
    private String generateAgentCode() {
        return "AG" + IdUtil.getSnowflakeNextIdStr();
    }

    /**
     * 检查是否会形成循环关系
     */
    private boolean wouldCreateCycle(Long agentId, Long newParentAgentId) {
        if (agentId.equals(newParentAgentId)) {
            return true;
        }
        // 检查新上级是否是当前代理的下级
        List<DmsAgentRelation> descendants = relationDao.selectAllDescendants(agentId);
        for (DmsAgentRelation relation : descendants) {
            if (relation.getAgentId().equals(newParentAgentId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 更新下级代理的ancestorIds
     */
    private void updateDescendantAncestorIds(DmsAgent agent) {
        List<DmsAgentRelation> descendants = relationDao.selectAllDescendants(agent.getId());
        for (DmsAgentRelation relation : descendants) {
            DmsAgent descendant = agentDao.selectById(relation.getAgentId());
            if (descendant != null) {
                DmsAgent parent = agentDao.selectById(relation.getParentAgentId());
                if (parent != null) {
                    descendant.setAncestorIds(parent.getAncestorIds() != null ?
                            parent.getAncestorIds() + "," + parent.getId() :
                            String.valueOf(parent.getId()));
                    descendant.setLevelDepth(parent.getLevelDepth() + 1);
                    agentDao.update(descendant);
                }
            }
        }
    }

    /**
     * 更新团队成员数
     */
    private void updateTeamMemberCount(Long agentId) {
        int count = relationService.getTeamMemberCount(agentId);
        accountDao.updateTotalTeamMembers(agentId, count);
    }

    /**
     * 转换为VO
     */
    private AgentInfoVO convertToVO(DmsAgent agent) {
        AgentInfoVO vo = new AgentInfoVO();
        BeanUtils.copyProperties(agent, vo);
        DmsShopMember member = shopMemberDao.selectByUserId(agent.getUserId());
        vo.setMemberAccount(MemberAccountUtils.display(member));

        // 设置等级名称
        AgentLevelEnum levelEnum = AgentLevelEnum.getByValue(agent.getAgentLevel());
        vo.setAgentLevelName(levelEnum != null ? levelEnum.getName() : "未知");

        // 设置状态名称
        AgentStatusEnum statusEnum = AgentStatusEnum.getByValue(agent.getStatus());
        vo.setStatusName(statusEnum != null ? statusEnum.getName() : "未知");

        // 设置来源名称
        AgentSourceTypeEnum sourceTypeEnum = AgentSourceTypeEnum.getByValue(agent.getSourceType());
        vo.setSourceTypeName(sourceTypeEnum != null ? sourceTypeEnum.getName() : "未知");

        // 设置上级名称
        if (agent.getParentId() != null) {
            DmsAgent parent = agentDao.selectById(agent.getParentId());
            vo.setParentName(parent != null ? parent.getAgentName() : "未知");
        }

        return vo;
    }

    /**
     * 转换为VO列表
     */
    private List<AgentInfoVO> convertToVOList(List<DmsAgent> agents) {
        List<AgentInfoVO> voList = new ArrayList<>();
        for (DmsAgent agent : agents) {
            voList.add(convertToVO(agent));
        }
        return voList;
    }

    private List<AgentInfoVO> fillPendingLineChange(List<AgentInfoVO> members) {
        if (members.isEmpty()) return members;
        List<Long> memberIds = members.stream().map(AgentInfoVO::getId).toList();
        Set<Long> pendingIds = new HashSet<>(lineChangeApplicationDao.selectPendingAgentIds(memberIds));
        members.forEach(member -> member.setHasPendingLineChange(pendingIds.contains(member.getId())));
        return members;
    }

    private AgentInfoVO fillTreeMetric(AgentInfoVO member) {
        fillTreeMetrics(new ArrayList<>(List.of(member)));
        return member;
    }

    private List<AgentInfoVO> fillTreeMetrics(List<AgentInfoVO> members) {
        if (members == null || members.isEmpty()) return members;
        List<Long> ids = members.stream().map(AgentInfoVO::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return members;
        Map<Long, AgentInfoVO> metrics = new HashMap<>();
        for (AgentInfoVO metric : agentDao.selectTreeMetrics(ids, LocalDate.now().withDayOfMonth(1).atStartOfDay())) {
            metrics.put(metric.getId(), metric);
        }
        for (AgentInfoVO member : members) {
            AgentInfoVO metric = metrics.get(member.getId());
            member.setCurrentMonthPerformance(metric == null || metric.getCurrentMonthPerformance() == null
                    ? BigDecimal.ZERO : metric.getCurrentMonthPerformance());
            member.setTotalCommission(metric == null || metric.getTotalCommission() == null
                    ? BigDecimal.ZERO : metric.getTotalCommission());
            member.setTeamMemberCount(metric == null || metric.getTeamMemberCount() == null
                    ? 0 : metric.getTeamMemberCount());
        }
        return members;
    }
}
