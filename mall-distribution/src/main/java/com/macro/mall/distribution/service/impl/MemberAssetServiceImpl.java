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
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.MemberMessageService;
import com.macro.mall.distribution.service.MemberMessageEvent;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.vo.BalanceFlowVO;
import com.macro.mall.distribution.vo.BalanceFlowSummaryVO;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.util.MemberAccountUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberAssetServiceImpl implements MemberAssetService {

    private final DmsMemberAssetAccountDao accountDao;
    private final DmsMemberAssetFlowDao flowDao;
    private final DmsAgentDao agentDao;
    private final DmsShopMemberDao shopMemberDao;
    private final OperationLogService operationLogService;
    private final MemberMessageService memberMessageService;

    @Override
    public List<DmsMemberAssetAccount> listAccounts(Long agentId, Long userId) {
        WalletOwner owner = resolveWalletOwner(agentId, userId);
        DmsMemberAssetAccount account = currentAccount(owner);
        return account == null ? List.of() : List.of(account);
    }

    @Override
    public List<DmsMemberAssetFlow> listFlows(Long agentId, Long userId) {
        WalletOwner owner = resolveWalletOwner(agentId, userId);
        // 流水始终按商城用户查询，保证账号成为正式会员前后的余额记录连续可见。
        Long ownerUserId = owner.agent != null ? owner.agent.getUserId() : owner.member.getUserId();
        return flowDao.selectByUserId(ownerUserId, BalanceAsset.CODE);
    }

    @Override
    public List<BalanceFlowVO> searchBalanceFlows(String keyword, String relatedNo, String direction, String sourceType,
                                                  LocalDateTime startTime, LocalDateTime endTime) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        String normalizedRelatedNo = relatedNo == null ? null : relatedNo.trim();
        List<BalanceFlowVO> rows = flowDao.selectBalanceFlowList(
                normalizedKeyword, normalizedRelatedNo, direction, sourceType, startTime, endTime);
        for (BalanceFlowVO row : rows) {
            String rawUsername = row.getMemberUsername();
            String rawPhone = row.getMemberPhone();
            if (row.getMemberName() != null && (row.getMemberName().equals(rawUsername)
                    || row.getMemberName().equals(rawPhone))) {
                row.setMemberName(row.getMemberName().equals(rawPhone)
                        ? MemberAccountUtils.maskPhone(row.getMemberName())
                        : MemberAccountUtils.maskAccount(row.getMemberName()));
            }
            row.setMemberUsername(MemberAccountUtils.maskAccount(rawUsername));
            row.setMemberPhone(MemberAccountUtils.maskPhone(rawPhone));
        }
        return rows;
    }

    @Override
    public BalanceFlowSummaryVO summarizeBalanceFlows(String keyword, String relatedNo, String direction, String sourceType,
                                                      LocalDateTime startTime, LocalDateTime endTime) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        String normalizedRelatedNo = relatedNo == null ? null : relatedNo.trim();
        BalanceFlowSummaryVO summary = flowDao.selectBalanceFlowSummary(
                normalizedKeyword, normalizedRelatedNo, direction, sourceType, startTime, endTime);
        if (summary == null) summary = new BalanceFlowSummaryVO();
        if (summary.getTotalRechargeAmount() == null) summary.setTotalRechargeAmount(BigDecimal.ZERO);
        if (summary.getTotalIncomeAmount() == null) summary.setTotalIncomeAmount(BigDecimal.ZERO);
        if (summary.getTotalExpenseAmount() == null) summary.setTotalExpenseAmount(BigDecimal.ZERO);
        return summary;
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
        return changeIn(dto, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMemberAssetFlow deductSystemAllowNegative(AssetChangeDTO dto) {
        requireSystemRequestId(dto);
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("资产数量必须大于0");
        }
        DmsAgent agent = resolveAgent(dto.getAgentId(), dto.getUserId());
        WalletOwner owner = ownerOf(agent);
        DmsMemberAssetFlow existing = findExistingFlow(dto, 5, owner);
        if (existing != null) return existing;
        ensureAccount(owner);
        if (accountDao.subtractBalance(agent.getId(), BalanceAsset.CODE, dto.getAmount(), 1) <= 0) {
            Asserts.fail("系统余额冲回失败");
        }
        DmsMemberAssetAccount account = accountDao.selectByAgentIdAndAssetCode(agent.getId(), BalanceAsset.CODE);
        return insertFlow(owner, 5, dto.getAmount(), account.getBalance().add(dto.getAmount()), account.getBalance(), dto.getBizType(), dto.getBizId(),
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
        ensureAccount(ownerOf(fromAgent));

        int updated = accountDao.subtractBalance(fromAgent.getId(), BalanceAsset.CODE, dto.getAmount(), 0);
        if (updated <= 0) {
            Asserts.fail("资产余额不足");
        }
        DmsMemberAssetAccount fromAccount = accountDao.selectByAgentIdAndAssetCode(fromAgent.getId(), BalanceAsset.CODE);
        insertFlow(ownerOf(fromAgent), 3, dto.getAmount(), fromAccount.getBalance().add(dto.getAmount()), fromAccount.getBalance(),
                dto.getBizType(), dto.getBizId(), null, dto.getRemark(), toAgent);

        ensureAccount(ownerOf(toAgent));
        accountDao.addBalance(toAgent.getId(), BalanceAsset.CODE, dto.getAmount());
        DmsMemberAssetAccount toAccount = accountDao.selectByAgentIdAndAssetCode(toAgent.getId(), BalanceAsset.CODE);
        insertFlow(ownerOf(toAgent), 4, dto.getAmount(), toAccount.getBalance().subtract(dto.getAmount()), toAccount.getBalance(),
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
        WalletOwner owner = resolveWalletOwner(dto.getAgentId(), dto.getUserId());
        DmsMemberAssetFlow existing = findExistingFlow(dto, changeType, owner);
        if (existing != null) return existing;
        ensureAccount(owner);
        if (owner.agent != null) {
            accountDao.addBalance(owner.agent.getId(), BalanceAsset.CODE, dto.getAmount());
        } else {
            accountDao.addBalanceByUserId(owner.member.getUserId(), BalanceAsset.CODE, dto.getAmount());
        }
        DmsMemberAssetAccount account = currentAccount(owner);
        return insertFlow(owner, changeType, dto.getAmount(), account.getBalance().subtract(dto.getAmount()), account.getBalance(),
                dto.getBizType(), dto.getBizId(), dto.getRequestId(), dto.getRemark(), null);
    }

    private DmsMemberAssetFlow changeOut(AssetChangeDTO dto, Integer changeType) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("资产数量必须大于0");
        }
        WalletOwner owner = resolveWalletOwner(dto.getAgentId(), dto.getUserId());
        DmsMemberAssetFlow existing = findExistingFlow(dto, changeType, owner);
        if (existing != null) return existing;
        ensureAccount(owner);
        int updated = owner.agent != null
                ? accountDao.subtractBalance(owner.agent.getId(), BalanceAsset.CODE, dto.getAmount(), 0)
                : accountDao.subtractBalanceByUserId(owner.member.getUserId(), BalanceAsset.CODE, dto.getAmount(), 0);
        if (updated <= 0) {
            Asserts.fail("资产余额不足");
        }
        DmsMemberAssetAccount account = currentAccount(owner);
        return insertFlow(owner, changeType, dto.getAmount(), account.getBalance().add(dto.getAmount()), account.getBalance(),
                dto.getBizType(), dto.getBizId(), dto.getRequestId(), dto.getRemark(), null);
    }

    private DmsMemberAssetFlow insertFlow(WalletOwner owner, Integer changeType,
                                          BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter, String bizType,
                                          String bizId, String requestId, String remark, DmsAgent relatedAgent) {
        DmsMemberAssetFlow flow = new DmsMemberAssetFlow();
        flow.setFlowNo(requestId == null || requestId.isBlank()
                ? "ASF" + IdUtil.getSnowflakeNextIdStr()
                : requestFlowNo(requestId));
        flow.setAgentId(owner.agent == null ? null : owner.agent.getId());
        flow.setUserId(owner.agent != null ? owner.agent.getUserId() : owner.member.getUserId());
        flow.setRelatedAgentId(relatedAgent == null ? null : relatedAgent.getId());
        flow.setRelatedUserId(relatedAgent == null ? null : relatedAgent.getUserId());
        flow.setAssetCode(BalanceAsset.CODE);
        flow.setAssetName(BalanceAsset.NAME);
        flow.setChangeType(changeType);
        flow.setAmount(amount);
        flow.setBalanceBefore(balanceBefore);
        flow.setBalanceAfter(balanceAfter);
        DmsAdminUser admin = AdminContext.get();
        flow.setOperatorId(admin == null ? 0L : admin.getId());
        flow.setOperatorName(admin == null ? "system" : admin.getUsername());
        flow.setBizType(bizType);
        flow.setBizId(bizId);
        flow.setRemark(remark);
        flowDao.insert(flow);
        memberMessageService.publish(new MemberMessageEvent(TenantContext.getTenantId(), flow.getUserId(),
                "WALLET_FLOW:" + flow.getFlowNo(), "WALLET_FLOW", "WALLET_FUNDS", "WALLET",
                flow.getId(), null, LocalDateTime.now()));
        return flow;
    }

    private void requireSystemRequestId(AssetChangeDTO dto) {
        if (dto == null || dto.getRequestId() == null || dto.getRequestId().isBlank()) {
            Asserts.fail("系统资金请求号不能为空");
        }
    }

    private DmsMemberAssetFlow findExistingFlow(AssetChangeDTO dto, Integer changeType, WalletOwner owner) {
        if (dto.getRequestId() == null || dto.getRequestId().isBlank()) return null;
        DmsMemberAssetFlow existing = flowDao.selectByFlowNo(requestFlowNo(dto.getRequestId()));
        if (existing == null) return null;
        Long ownerUserId = owner.agent != null ? owner.agent.getUserId() : owner.member.getUserId();
        boolean sameRequest = Objects.equals(ownerUserId, existing.getUserId())
                && Objects.equals(changeType, existing.getChangeType())
                && existing.getAmount() != null
                && existing.getAmount().compareTo(dto.getAmount()) == 0
                && Objects.equals(dto.getBizType(), existing.getBizType())
                && Objects.equals(dto.getBizId(), existing.getBizId());
        if (!sameRequest) Asserts.fail("资金请求号已被其他操作使用");
        return existing;
    }

    private String requestFlowNo(String requestId) {
        // 保留分隔符，避免 (allocation=2, refund=11) 与 (allocation=21, refund=1) 归一化后碰撞。
        String normalized = requestId.replaceAll("[^A-Za-z0-9_-]", "_").toUpperCase();
        if (normalized.length() > 61) normalized = normalized.substring(0, 61);
        return "ADM" + normalized;
    }

    private void ensureAccount(WalletOwner owner) {
        DmsMemberAssetAccount account = owner.agent == null
                ? accountDao.selectByUserIdAndAssetCode(owner.member.getUserId(), BalanceAsset.CODE)
                : accountDao.selectByAgentIdAndAssetCode(owner.agent.getId(), BalanceAsset.CODE);
        if (account != null) {
            return;
        }
        if (owner.agent != null) {
            // 商城账号可能先持有余额、后完成首单成为正式会员。此时应沿用原账户，
            // 仅补齐代理关联，不能再插入一条相同 user_id 的钱包账户。
            account = accountDao.selectByUserIdAndAssetCode(owner.agent.getUserId(), BalanceAsset.CODE);
            if (account != null) {
                accountDao.bindAgentIfMissing(account.getId(), owner.agent.getId());
                return;
            }
        }
        account = new DmsMemberAssetAccount();
        account.setAgentId(owner.agent == null ? null : owner.agent.getId());
        account.setUserId(owner.agent != null ? owner.agent.getUserId() : owner.member.getUserId());
        account.setAssetCode(BalanceAsset.CODE);
        account.setAssetName(BalanceAsset.NAME);
        account.setBalance(BigDecimal.ZERO);
        account.setFrozenBalance(BigDecimal.ZERO);
        account.setTotalIn(BigDecimal.ZERO);
        account.setTotalOut(BigDecimal.ZERO);
        accountDao.insert(account);
    }

    private DmsMemberAssetAccount currentAccount(WalletOwner owner) {
        if (owner.agent == null) {
            return accountDao.selectByUserIdAndAssetCode(owner.member.getUserId(), BalanceAsset.CODE);
        }
        DmsMemberAssetAccount account = accountDao.selectByAgentIdAndAssetCode(owner.agent.getId(), BalanceAsset.CODE);
        return account != null ? account
                : accountDao.selectByUserIdAndAssetCode(owner.agent.getUserId(), BalanceAsset.CODE);
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

    /**
     * 余额钱包归属人：优先奖金体系代理；尚未进入奖金体系的商城账号也能持有余额。
     * 转账、提现等需要真实推广身份的能力仍通过 resolveAgent 强制要求代理记录。
     */
    private WalletOwner resolveWalletOwner(Long agentId, Long userId) {
        if (agentId == null && userId == null) {
            Asserts.fail("代理ID或用户ID至少填写一个");
        }
        if (agentId != null) {
            DmsAgent agent = agentDao.selectById(agentId);
            if (agent == null) {
                Asserts.fail("代理不存在");
            }
            return new WalletOwner(agent, null);
        }
        DmsAgent agent = agentDao.selectByUserId(userId);
        if (agent != null) {
            return new WalletOwner(agent, null);
        }
        DmsShopMember member = shopMemberDao.selectByUserId(userId);
        if (member == null) {
            Asserts.fail("商城会员不存在");
        }
        return new WalletOwner(null, member);
    }

    private static final class WalletOwner {
        private final DmsAgent agent;
        private final DmsShopMember member;

        private WalletOwner(DmsAgent agent, DmsShopMember member) {
            this.agent = agent;
            this.member = member;
        }
    }

    private static WalletOwner ownerOf(DmsAgent agent) {
        return new WalletOwner(agent, null);
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
