package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsMemberAssetAccountDao;
import com.macro.mall.distribution.dao.DmsMemberAssetFlowDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.constants.BalanceAsset;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.AssetTransferDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsMemberAssetAccount;
import com.macro.mall.distribution.entity.DmsMemberAssetFlow;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.vo.BalanceFlowVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberAssetServiceImpl implements MemberAssetService {

    private final DmsMemberAssetAccountDao accountDao;
    private final DmsMemberAssetFlowDao flowDao;
    private final DmsAgentDao agentDao;
    private final DmsShopMemberDao shopMemberDao;
    private final OperationLogService operationLogService;

    @Override
    public List<DmsMemberAssetAccount> listAccounts(Long agentId, Long userId) {
        DmsAgent agent = resolveAgent(agentId, userId);
        DmsMemberAssetAccount account = accountDao.selectByAgentIdAndAssetCode(agent.getId(), BalanceAsset.CODE);
        return account == null ? List.of() : List.of(account);
    }

    @Override
    public List<DmsMemberAssetFlow> listFlows(Long agentId, Long userId) {
        DmsAgent agent = resolveAgent(agentId, userId);
        return flowDao.selectByAgentId(agent.getId(), BalanceAsset.CODE);
    }

    @Override
    public List<BalanceFlowVO> searchBalanceFlows(String keyword, String direction, String sourceType,
                                                  LocalDateTime startTime, LocalDateTime endTime) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        return flowDao.selectBalanceFlowList(normalizedKeyword, direction, sourceType, startTime, endTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMemberAssetFlow issue(AssetChangeDTO dto) {
        DmsMemberAssetFlow flow = changeIn(dto, 1);
        operationLogService.log("ASSET", "ISSUE", "MEMBER_ASSET", String.valueOf(flow.getAgentId()),
                null, flow.toString(), assetDescription("增加", flow, dto.getRemark()));
        return flow;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMemberAssetFlow consume(AssetChangeDTO dto) {
        DmsMemberAssetFlow flow = changeOut(dto, 2);
        operationLogService.log("ASSET", "CONSUME", "MEMBER_ASSET", String.valueOf(flow.getAgentId()),
                null, flow.toString(), assetDescription("消费", flow, dto.getRemark()));
        return flow;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMemberAssetFlow deduct(AssetChangeDTO dto) {
        DmsMemberAssetFlow flow = changeOut(dto, 5);
        operationLogService.log("ASSET", "DEDUCT", "MEMBER_ASSET", String.valueOf(flow.getAgentId()),
                null, flow.toString(), assetDescription("扣减", flow, dto.getRemark()));
        return flow;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMemberAssetFlow withdraw(AssetChangeDTO dto) {
        DmsMemberAssetFlow flow = changeOut(dto, 5);
        operationLogService.log("ASSET", "WITHDRAW", "MEMBER_ASSET", String.valueOf(flow.getAgentId()),
                null, flow.toString(), assetDescription("提现扣减", flow, dto.getRemark()));
        return flow;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMemberAssetFlow issueSystem(AssetChangeDTO dto) {
        requireSystemRequestId(dto);
        String flowNo = requestFlowNo(dto.getRequestId());
        DmsMemberAssetFlow existing = flowDao.selectByFlowNo(flowNo);
        if (existing != null) return existing;
        return changeIn(dto, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMemberAssetFlow deductSystemAllowNegative(AssetChangeDTO dto) {
        requireSystemRequestId(dto);
        String flowNo = requestFlowNo(dto.getRequestId());
        DmsMemberAssetFlow existing = flowDao.selectByFlowNo(flowNo);
        if (existing != null) return existing;
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("资产数量必须大于0");
        }
        DmsAgent agent = resolveAgent(dto.getAgentId(), dto.getUserId());
        ensureAccount(agent);
        if (accountDao.subtractBalance(agent.getId(), BalanceAsset.CODE, dto.getAmount(), 1) <= 0) {
            Asserts.fail("系统余额冲回失败");
        }
        DmsMemberAssetAccount account = accountDao.selectByAgentIdAndAssetCode(agent.getId(), BalanceAsset.CODE);
        return insertFlow(agent, 5, dto.getAmount(), account.getBalance(), dto.getBizType(), dto.getBizId(),
                dto.getRequestId(), dto.getRemark(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean transfer(AssetTransferDTO dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("转赠数量必须大于0");
        }
        DmsAgent fromAgent = resolveAgent(dto.getFromAgentId(), dto.getFromUserId());
        DmsAgent toAgent = resolveAgent(dto.getToAgentId(), dto.getToUserId());
        ensureAccount(fromAgent);

        int updated = accountDao.subtractBalance(fromAgent.getId(), BalanceAsset.CODE, dto.getAmount(), 0);
        if (updated <= 0) {
            Asserts.fail("资产余额不足");
        }
        DmsMemberAssetAccount fromAccount = accountDao.selectByAgentIdAndAssetCode(fromAgent.getId(), BalanceAsset.CODE);
        insertFlow(fromAgent, 3, dto.getAmount(), fromAccount.getBalance(),
                dto.getBizType(), dto.getBizId(), null, dto.getRemark(), toAgent);

        ensureAccount(toAgent);
        accountDao.addBalance(toAgent.getId(), BalanceAsset.CODE, dto.getAmount());
        DmsMemberAssetAccount toAccount = accountDao.selectByAgentIdAndAssetCode(toAgent.getId(), BalanceAsset.CODE);
        insertFlow(toAgent, 4, dto.getAmount(), toAccount.getBalance(),
                dto.getBizType(), dto.getBizId(), null, dto.getRemark(), fromAgent);
        operationLogService.log("ASSET", "TRANSFER", "MEMBER_ASSET", fromAgent.getId() + "->" + toAgent.getId(),
                null, dto.toString(), memberLabel(fromAgent.getUserId()) + "向" + memberLabel(toAgent.getUserId())
                        + "转账" + dto.getAmount() + BalanceAsset.UNIT + BalanceAsset.NAME
                        + appendReason(dto.getRemark()));
        return true;
    }

    private DmsMemberAssetFlow changeIn(AssetChangeDTO dto, Integer changeType) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("资产数量必须大于0");
        }
        DmsAgent agent = resolveAgent(dto.getAgentId(), dto.getUserId());
        ensureAccount(agent);
        accountDao.addBalance(agent.getId(), BalanceAsset.CODE, dto.getAmount());
        DmsMemberAssetAccount account = accountDao.selectByAgentIdAndAssetCode(agent.getId(), BalanceAsset.CODE);
        return insertFlow(agent, changeType, dto.getAmount(), account.getBalance(),
                dto.getBizType(), dto.getBizId(), dto.getRequestId(), dto.getRemark(), null);
    }

    private DmsMemberAssetFlow changeOut(AssetChangeDTO dto, Integer changeType) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("资产数量必须大于0");
        }
        DmsAgent agent = resolveAgent(dto.getAgentId(), dto.getUserId());
        ensureAccount(agent);
        int updated = accountDao.subtractBalance(agent.getId(), BalanceAsset.CODE, dto.getAmount(), 0);
        if (updated <= 0) {
            Asserts.fail("资产余额不足");
        }
        DmsMemberAssetAccount account = accountDao.selectByAgentIdAndAssetCode(agent.getId(), BalanceAsset.CODE);
        return insertFlow(agent, changeType, dto.getAmount(), account.getBalance(),
                dto.getBizType(), dto.getBizId(), dto.getRequestId(), dto.getRemark(), null);
    }

    private DmsMemberAssetFlow insertFlow(DmsAgent agent, Integer changeType,
                                          BigDecimal amount, BigDecimal balanceAfter, String bizType,
                                          String bizId, String requestId, String remark, DmsAgent relatedAgent) {
        DmsMemberAssetFlow flow = new DmsMemberAssetFlow();
        flow.setFlowNo(requestId == null || requestId.isBlank()
                ? "ASF" + IdUtil.getSnowflakeNextIdStr()
                : requestFlowNo(requestId));
        flow.setAgentId(agent.getId());
        flow.setUserId(agent.getUserId());
        flow.setRelatedAgentId(relatedAgent == null ? null : relatedAgent.getId());
        flow.setRelatedUserId(relatedAgent == null ? null : relatedAgent.getUserId());
        flow.setAssetCode(BalanceAsset.CODE);
        flow.setAssetName(BalanceAsset.NAME);
        flow.setChangeType(changeType);
        flow.setAmount(amount);
        flow.setBalanceAfter(balanceAfter);
        flow.setBizType(bizType);
        flow.setBizId(bizId);
        flow.setRemark(remark);
        flowDao.insert(flow);
        return flow;
    }

    private void requireSystemRequestId(AssetChangeDTO dto) {
        if (dto == null || dto.getRequestId() == null || dto.getRequestId().isBlank()) {
            Asserts.fail("系统资金请求号不能为空");
        }
    }

    private String requestFlowNo(String requestId) {
        // 保留分隔符，避免 (allocation=2, refund=11) 与 (allocation=21, refund=1) 归一化后碰撞。
        String normalized = requestId.replaceAll("[^A-Za-z0-9_-]", "_").toUpperCase();
        if (normalized.length() > 61) normalized = normalized.substring(0, 61);
        return "ADM" + normalized;
    }

    private void ensureAccount(DmsAgent agent) {
        DmsMemberAssetAccount account = accountDao.selectByAgentIdAndAssetCode(agent.getId(), BalanceAsset.CODE);
        if (account != null) {
            return;
        }
        account = new DmsMemberAssetAccount();
        account.setAgentId(agent.getId());
        account.setUserId(agent.getUserId());
        account.setAssetCode(BalanceAsset.CODE);
        account.setAssetName(BalanceAsset.NAME);
        account.setBalance(BigDecimal.ZERO);
        account.setFrozenBalance(BigDecimal.ZERO);
        account.setTotalIn(BigDecimal.ZERO);
        account.setTotalOut(BigDecimal.ZERO);
        accountDao.insert(account);
    }

    private DmsAgent resolveAgent(Long agentId, Long userId) {
        if (agentId == null && userId == null) {
            Asserts.fail("代理ID或用户ID至少填写一个");
        }
        DmsAgent agent = agentId != null ? agentDao.selectById(agentId) : agentDao.selectByUserId(userId);
        if (agent == null) {
            Asserts.fail("代理不存在");
        }
        return agent;
    }

    private String assetDescription(String action, DmsMemberAssetFlow flow, String reason) {
        return memberLabel(flow.getUserId()) + action + flow.getAssetName() + flow.getAmount()
                + "，变动后余额" + flow.getBalanceAfter() + appendReason(reason);
    }

    private String memberLabel(Long userId) {
        DmsShopMember member = userId == null ? null : shopMemberDao.selectByUserId(userId);
        if (member == null) return "会员(userId=" + userId + ")";
        String name = member.getNickname() != null && !member.getNickname().isBlank()
                ? member.getNickname() : (member.getUsername() != null && !member.getUsername().isBlank() ? member.getUsername() : member.getPhone());
        return "会员M" + String.format("%08d", member.getId()) + "（" + name + "）";
    }

    private String appendReason(String reason) {
        return reason == null || reason.isBlank() ? "" : "；原因：" + reason;
    }
}
