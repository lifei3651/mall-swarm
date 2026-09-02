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
import com.macro.mall.distribution.dao.DmsCommissionClawbackDao;
import com.macro.mall.distribution.dao.DmsCommissionRecordDao;
import com.macro.mall.distribution.dao.DmsOrderBalanceAllocationDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dao.DmsLineChangeApplicationDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.dto.AgentUpdateDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsAgentAccount;
import com.macro.mall.distribution.entity.DmsAgentChangeLog;
import com.macro.mall.distribution.entity.DmsAgentRelation;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.enums.*;
import com.macro.mall.distribution.service.AgentRelationService;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.CommissionService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.vo.AgentTeamMemberCountVO;
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

    private static final int MAX_INVITE_CODE_ATTEMPTS = 32;

    private final DmsAgentDao agentDao;
    private final DmsAgentRelationDao relationDao;
    private final DmsAgentAccountDao accountDao;
    private final DmsAgentChangeLogDao changeLogDao;
    private final DmsShopMemberDao shopMemberDao;
    private final DmsShopMemberSessionDao shopMemberSessionDao;
    private final DmsLineChangeApplicationDao lineChangeApplicationDao;
    private final DmsCommissionRecordDao commissionDao;
    private final DmsOrderBalanceAllocationDao orderBalanceAllocationDao;
    private final DmsCommissionClawbackDao clawbackDao;
    private final AgentRelationService relationService;
    private final CommissionService commissionService;
    private final PerformanceService performanceService;
    private final OperationLogService operationLogService;
    private final DmsTenantDao tenantDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentInfoVO register(AgentRegisterDTO registerDTO) {
        lockAgentMutationScope();
        if (registerDTO == null || registerDTO.getUserId() == null) {
            Asserts.fail("请选择已有商城会员后再开通推广身份");
        }
        DmsShopMember shopMember = shopMemberDao.selectByUserId(registerDTO.getUserId());
        if (shopMember == null) shopMember = shopMemberDao.selectById(registerDTO.getUserId());
        if (shopMember == null) {
            Asserts.fail("商城会员不存在，请先注册会员或从会员中心确认用户ID");
        }
        // 同一会员开通推广身份必须串行，与数据库 user_id 唯一约束共同防重。
        shopMember = shopMemberDao.selectByIdForUpdate(shopMember.getId());
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
            if (!AgentStatusEnum.NORMAL.getValue().equals(parentAgent.getStatus())) {
                Asserts.fail("邀请人当前不可绑定新会员");
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

            // 新增一名成员会影响整条祖先链，不只是直属上级。
            refreshTeamMemberCounts(collectAncestorIds(agent));
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
        List<Long> descendantIds = relations.stream().map(DmsAgentRelation::getAgentId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (descendantIds.isEmpty()) return List.of();
        return fillTreeMetrics(convertToVOList(agentDao.selectByIds(descendantIds)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean switchLine(AgentSwitchLineDTO switchLineDTO) {
        lockAgentMutationScope();
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
        if (!AgentStatusEnum.NORMAL.getValue().equals(newParentAgent.getStatus())) {
            Asserts.fail("新直属上级已停用或冻结，不能接收新团队");
        }

        // 检查是否形成循环
        if (wouldCreateCycle(agentId, newParentAgentId)) {
            Asserts.fail("不能形成循环关系");
        }

        // 记录原上级信息
        Long oldParentAgentId = agent.getParentId();
        DmsAgent oldParentAgent = oldParentAgentId != null ? agentDao.selectById(oldParentAgentId) : null;
        Set<Long> impactedTeamCountIds = new HashSet<>(collectAncestorIds(agent));
        impactedTeamCountIds.addAll(collectAncestorIds(newParentAgent));
        impactedTeamCountIds.add(newParentAgentId);

        // 先快照整棵子树；历史业绩和历史佣金保持原样，不做结算、转移或重算。
        Map<Long, DmsAgent> subtree = loadSubtree(agent);

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
            DmsAgent parent = subtree.get(descendant.getParentId());
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

        // 7. 移线会改变新旧两条祖先链的团队人数。
        refreshTeamMemberCounts(impactedTeamCountIds);

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
        DmsAgent agent = agentDao.selectById(id);
        boolean updated = agent != null && agentDao.updateStatus(id, status) > 0;
        if (updated && !AgentStatusEnum.NORMAL.getValue().equals(status)) {
            DmsShopMember member = shopMemberDao.selectByUserId(agent.getUserId());
            if (member != null) shopMemberSessionDao.disableByMemberIdAndSurface(member.getId(), "team");
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentInfoVO adjustLevel(Long id, Integer level, String reason) {
        lockAgentMutationScope();
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

        // 会员变更日志记录的是“发生了什么”，操作日志还必须能回答“调整了谁”。
        // 把业务账号、姓名/昵称和手机号一并写入前后台都能查看的审计日志，避免只留下接口路径。
        DmsShopMember member = shopMemberDao.selectByUserId(agent.getUserId());
        String account = MemberAccountUtils.display(member);
        String memberName = firstNonBlank(agent.getAgentName(), agent.getRealName(),
                member == null ? null : member.getNickname(), account);
        String phone = firstNonBlank(agent.getPhone(), member == null ? null : member.getPhone());
        String identity = "账号：" + displayValue(account) + "，姓名/昵称：" + displayValue(memberName)
                + "，手机号：" + displayValue(phone);
        String oldLevelName = levelName(oldLevel);
        String newLevelName = levelName(level);
        operationLogService.log(
                "AGENT", "LEVEL_ADJUST", "商城会员", String.valueOf(agent.getId()),
                "会员：" + identity + "；会员级别：" + oldLevelName,
                "会员：" + identity + "；会员级别：" + newLevelName,
                "调整会员级别：" + displayValue(memberName) + "（" + displayValue(account) + "）从"
                        + oldLevelName + "调整为" + newLevelName + "；原因：" + reason.trim());
        return convertToVO(agentDao.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deactivate(Long agentId, String reason) {
        lockAgentMutationScope();
        if (reason == null || reason.isBlank()) Asserts.fail("请输入取消会员资格的原因");
        DmsAgent agent = agentDao.selectById(agentId);
        if (agent == null) Asserts.fail("会员不存在");
        // 未结算完不允许取消：待结算奖金、待结算订单资金归集、退款追回欠款均视为未结清。
        if (!commissionDao.selectByAgentIdAndStatus(agentId, CommissionStatusEnum.PENDING.getValue()).isEmpty()) {
            Asserts.fail("该会员还有待结算奖金，请先结算完成后再取消会员资格");
        }
        if (orderBalanceAllocationDao.countPendingByTargetAgentId(agentId) > 0) {
            Asserts.fail("该会员还有待结算的订单资金归集，请先结算完成后再取消会员资格");
        }
        BigDecimal debt = clawbackDao.sumDebtByAgentId(agentId);
        if (debt != null && debt.compareTo(BigDecimal.ZERO) > 0) {
            Asserts.fail("该会员还有退款追回欠款，请先处理后再取消会员资格");
        }
        Long oldParentAgentId = agent.getParentId();
        DmsAgent oldParent = oldParentAgentId == null ? null : agentDao.selectById(oldParentAgentId);
        Set<Long> impactedTeamCountIds = collectAncestorIds(agent);
        List<DmsAgent> children = agentDao.selectByParentId(agentId);

        // 1. 该会员的完整下级团队整体移交其原直属上级（无上级则提升为根节点），团队关系与历史数据保持不变。
        String moveReason = reason.trim() + "（取消会员资格：" + agent.getAgentName() + "，其下级团队移交原上级）";
        for (DmsAgent child : children) {
            if (oldParent != null) {
                AgentSwitchLineDTO dto = new AgentSwitchLineDTO();
                dto.setAgentId(child.getId());
                dto.setNewParentAgentId(oldParent.getId());
                dto.setReason(moveReason);
                switchLine(dto);
            } else {
                reRootSubtree(child, moveReason);
            }
        }

        // 2. 清理该会员自己的推广身份记录；历史订单、奖金、余额流水与余额钱包保留。
        relationDao.deleteByAgentId(agentId);
        accountDao.deleteByAgentId(agentId);
        agentDao.hardDeleteById(agentId);
        DmsShopMember deactivatedMember = shopMemberDao.selectByUserId(agent.getUserId());
        if (deactivatedMember != null) {
            shopMemberSessionDao.disableByMemberIdAndSurface(deactivatedMember.getId(), "team");
        }

        // 3. 变更留痕。
        DmsAgentChangeLog changeLog = new DmsAgentChangeLog();
        changeLog.setAgentId(agentId);
        changeLog.setUserId(agent.getUserId());
        changeLog.setChangeType(ChangeTypeEnum.DEACTIVATE.getValue());
        changeLog.setOldLevel(agent.getAgentLevel());
        changeLog.setNewLevel(null);
        changeLog.setChangeReason(reason.trim());
        changeLog.setChangeDetail("{\"action\":\"deactivate_distribution\",\"effect\":\"removed_from_bonus_system\",\"historyPreserved\":true}");
        DmsAdminUser admin = AdminContext.get();
        changeLog.setOperatorId(admin == null ? 0L : admin.getId());
        changeLog.setOperatorName(admin == null ? "system" : admin.getUsername());
        changeLog.setOperatorType(admin == null ? 1 : 2);
        changeLogDao.insert(changeLog);

        // 4. 取消本人资格会影响整条原祖先链的团队人数。
        refreshTeamMemberCounts(impactedTeamCountIds);
        log.info("取消会员资格成功: agentId={}, userId={}, reason={}", agentId, agent.getUserId(), reason);
        return true;
    }

    /** 无上级可移交时，把被取消会员的直接下级子树整体提升为根节点。 */
    private void reRootSubtree(DmsAgent child, String reason) {
        Map<Long, DmsAgent> subtree = loadSubtree(child);
        relationDao.invalidRelationsByAgentIds(new ArrayList<>(subtree.keySet()), reason);

        child.setParentId(null);
        child.setAncestorIds(null);
        child.setLevelDepth(1);
        agentDao.update(child);
        DmsShopMember childMember = shopMemberDao.selectByUserId(child.getUserId());
        if (childMember != null) {
            shopMemberDao.updateInviterId(childMember.getId(), null);
        }

        List<DmsAgent> descendants = subtree.values().stream()
                .filter(item -> !item.getId().equals(child.getId()))
                .sorted(Comparator.comparing(DmsAgent::getLevelDepth, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        for (DmsAgent descendant : descendants) {
            DmsAgent parent = subtree.get(descendant.getParentId());
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
    }

    @Override
    public String generateInviteCode() {
        for (int attempt = 0; attempt < MAX_INVITE_CODE_ATTEMPTS; attempt++) {
            String code = RandomUtil.randomStringUpper(8);
            if (agentDao.selectByInviteCode(code) == null) return code;
        }
        Asserts.fail("邀请码生成失败，请稍后重试");
        return null;
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

    private Map<Long, DmsAgent> loadSubtree(DmsAgent root) {
        Map<Long, DmsAgent> subtree = new LinkedHashMap<>();
        subtree.put(root.getId(), root);
        List<Long> descendantIds = relationDao.selectAllDescendants(root.getId()).stream()
                .map(DmsAgentRelation::getAgentId)
                .filter(java.util.Objects::nonNull)
                .filter(id -> !root.getId().equals(id))
                .distinct()
                .toList();
        if (!descendantIds.isEmpty()) {
            agentDao.selectByIds(descendantIds).forEach(descendant -> subtree.put(descendant.getId(), descendant));
        }
        if (subtree.size() != descendantIds.size() + 1) {
            Asserts.fail("团队关系数据不完整，无法安全迁移，请先检查会员关系");
        }
        return subtree;
    }

    private Set<Long> collectAncestorIds(DmsAgent agent) {
        Set<Long> ids = new HashSet<>();
        if (agent == null) return ids;
        if (agent.getAncestorIds() != null && !agent.getAncestorIds().isBlank()) {
            for (String value : agent.getAncestorIds().split(",")) {
                try {
                    ids.add(Long.valueOf(value.trim()));
                } catch (NumberFormatException ignored) {
                    log.warn("忽略非法祖先ID: agentId={}, value={}", agent.getId(), value);
                }
            }
        }
        if (agent.getParentId() != null) ids.add(agent.getParentId());
        return ids;
    }

    /**
     * 代理关系树是一个整体一致性对象。所有会改变节点、层级或上下级关系的事务，
     * 先锁定当前客户租户行，保证单机和多实例部署下都按顺序执行。
     */
    private void lockAgentMutationScope() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantDao.selectByIdForUpdate(tenantId) == null) {
            Asserts.fail("商城客户配置不存在，暂不能修改会员关系");
        }
    }

    private void refreshTeamMemberCounts(Set<Long> agentIds) {
        if (agentIds == null) return;
        List<Long> ids = agentIds.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) return;

        Map<Long, Integer> countsByAgentId = new HashMap<>();
        relationDao.selectTeamMemberCounts(ids).forEach(item -> countsByAgentId.put(
                item.getAgentId(), item.getTeamMemberCount() == null ? 0 : item.getTeamMemberCount()));
        List<AgentTeamMemberCountVO> counts = ids.stream().map(id -> {
            AgentTeamMemberCountVO item = new AgentTeamMemberCountVO();
            item.setAgentId(id);
            item.setTeamMemberCount(countsByAgentId.getOrDefault(id, 0));
            return item;
        }).toList();
        accountDao.updateTotalTeamMembersBatch(counts);
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

    private String levelName(Integer level) {
        AgentLevelEnum levelEnum = AgentLevelEnum.getByValue(level);
        return levelEnum == null ? "未知级别" : levelEnum.getName();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private String displayValue(String value) {
        return value == null || value.isBlank() ? "未填写" : value;
    }

    /**
     * 转换为VO列表
     */
    private List<AgentInfoVO> convertToVOList(List<DmsAgent> agents) {
        if (agents == null || agents.isEmpty()) return new ArrayList<>();
        List<Long> userIds = agents.stream().map(DmsAgent::getUserId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, DmsShopMember> members = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (DmsShopMember member : shopMemberDao.selectByUserIds(userIds)) {
                members.put(member.getUserId(), member);
            }
        }
        Map<Long, DmsAgent> agentsById = new HashMap<>();
        agents.forEach(agent -> agentsById.put(agent.getId(), agent));
        List<Long> missingParentIds = agents.stream().map(DmsAgent::getParentId)
                .filter(java.util.Objects::nonNull).filter(id -> !agentsById.containsKey(id)).distinct().toList();
        if (!missingParentIds.isEmpty()) {
            agentDao.selectByIds(missingParentIds).forEach(parent -> agentsById.put(parent.getId(), parent));
        }
        List<AgentInfoVO> voList = new ArrayList<>(agents.size());
        for (DmsAgent agent : agents) {
            AgentInfoVO vo = new AgentInfoVO();
            BeanUtils.copyProperties(agent, vo);
            vo.setMemberAccount(MemberAccountUtils.display(members.get(agent.getUserId())));
            AgentLevelEnum levelEnum = AgentLevelEnum.getByValue(agent.getAgentLevel());
            vo.setAgentLevelName(levelEnum == null ? "未知" : levelEnum.getName());
            AgentStatusEnum statusEnum = AgentStatusEnum.getByValue(agent.getStatus());
            vo.setStatusName(statusEnum == null ? "未知" : statusEnum.getName());
            AgentSourceTypeEnum sourceTypeEnum = AgentSourceTypeEnum.getByValue(agent.getSourceType());
            vo.setSourceTypeName(sourceTypeEnum == null ? "未知" : sourceTypeEnum.getName());
            DmsAgent parent = agent.getParentId() == null ? null : agentsById.get(agent.getParentId());
            vo.setParentName(agent.getParentId() == null ? null : (parent == null ? "未知" : parent.getAgentName()));
            voList.add(vo);
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
