package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsWithdrawalPayoutDao;
import com.macro.mall.distribution.dao.DmsWithdrawRecordDao;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsWithdrawalPayout;
import com.macro.mall.distribution.entity.DmsWithdrawRecord;
import com.macro.mall.distribution.enums.WithdrawStatusEnum;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AgentAccountService;
import com.macro.mall.distribution.service.MemberMessageEvent;
import com.macro.mall.distribution.service.MemberMessageService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.WithdrawalPayoutGateway;
import com.macro.mall.distribution.vo.WithdrawalPayoutVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** 把渠道调用前后的状态变化锁在短事务中，外部网络请求不会占用数据库事务。 */
@Service
@RequiredArgsConstructor
public class WithdrawalPayoutTransactionService {
    private final DmsWithdrawRecordDao withdrawDao;
    private final DmsWithdrawalPayoutDao payoutDao;
    private final DmsShopMemberDao memberDao;
    private final AgentAccountService accountService;
    private final OperationLogService operationLogService;
    private final MemberMessageService memberMessageService;

    @Transactional(rollbackFor = Exception.class)
    public Reservation reserve(Long withdrawId) {
        return reserve(withdrawId, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Reservation reserve(Long withdrawId, boolean retryFailed) {
        DmsWithdrawRecord withdraw = withdrawDao.selectByIdForUpdate(withdrawId);
        if (withdraw == null) Asserts.fail("提现记录不存在");
        if (Integer.valueOf(1).equals(withdraw.getWithdrawType())) {
            Asserts.fail("银行卡人工流水号确认入口已停用，请接入可核验的银行回单通道");
        }
        if (!Integer.valueOf(2).equals(withdraw.getWithdrawType())
                && !Integer.valueOf(3).equals(withdraw.getWithdrawType())) {
            Asserts.fail("该提现方式暂不支持渠道打款");
        }
        DmsAdminUser actor = AdminContext.get();
        if (actor != null && Objects.equals(actor.getId(), withdraw.getAuditUserId())) {
            Asserts.fail("提现审核与渠道打款必须由不同后台账号完成");
        }

        DmsWithdrawalPayout payout = payoutDao.selectByWithdrawIdForUpdate(withdrawId);
        boolean initiate = false;
        if (payout == null) {
            if (!retryFailed) Asserts.fail("渠道打款尚未发起，请先执行发起操作");
            if (!WithdrawStatusEnum.AUDIT_PASSED.getValue().equals(withdraw.getStatus())) {
                Asserts.fail("提现状态不正确，无法发起渠道打款");
            }
            payout = new DmsWithdrawalPayout();
            payout.setWithdrawId(withdraw.getId());
            payout.setWithdrawNo(withdraw.getWithdrawNo());
            payout.setAttemptNo(1);
            payout.setRequestNo(requestNo(withdraw.getWithdrawNo(), 1));
            payout.setChannel(channel(withdraw.getWithdrawType()));
            payout.setState("PROCESSING");
            payout.setAmount(withdraw.getWithdrawAmount());
            if (payoutDao.insert(payout) != 1) Asserts.fail("渠道打款请求创建失败");
            initiate = true;
        } else if ("SUCCESS".equals(payout.getState())) {
            return reservation(withdraw, payout, false);
        } else if ("FAILED".equals(payout.getState())) {
            if (!retryFailed) Asserts.fail("该次渠道打款已明确失败，如需重试请重新发起渠道打款");
            if (!WithdrawStatusEnum.AUDIT_PASSED.getValue().equals(withdraw.getStatus())) {
                Asserts.fail("失败打款单状态异常，请财务核对");
            }
            int nextAttempt = Math.max(1, payout.getAttemptNo() == null ? 1 : payout.getAttemptNo()) + 1;
            payout.setAttemptNo(nextAttempt);
            payout.setRequestNo(requestNo(withdraw.getWithdrawNo(), nextAttempt));
            payout.setState("PROCESSING");
            payout.setProviderStatus(null);
            payout.setProviderOrderNo(null);
            payout.setRecipientHash(null);
            payout.setResponseDigest(null);
            payout.setFailureCode(null);
            payout.setConfirmationPackage(null);
            payoutDao.update(payout);
            initiate = true;
        } else if (!WithdrawStatusEnum.PAYING.getValue().equals(withdraw.getStatus())) {
            Asserts.fail("提现与渠道打款状态不一致，请财务核对");
        }

        if (initiate) {
            withdraw.setStatus(WithdrawStatusEnum.PAYING.getValue());
            if (withdrawDao.update(withdraw) != 1) Asserts.fail("提现打款状态更新失败");
        }
        return reservation(withdraw, payout, initiate);
    }

    @Transactional(rollbackFor = Exception.class)
    public WithdrawalPayoutVO apply(Long withdrawId, WithdrawalPayoutGateway.PayoutResult result) {
        DmsWithdrawRecord withdraw = withdrawDao.selectByIdForUpdate(withdrawId);
        DmsWithdrawalPayout payout = payoutDao.selectByWithdrawIdForUpdate(withdrawId);
        if (withdraw == null || payout == null) Asserts.fail("渠道打款记录不存在");

        WithdrawalPayoutGateway.State state = validateEvidence(payout, result);
        payout.setState(state.name());
        payout.setProviderStatus(result == null ? "UNKNOWN" : result.providerStatus());
        payout.setProviderOrderNo(result == null ? payout.getProviderOrderNo()
                : first(result.providerOrderNo(), payout.getProviderOrderNo()));
        payout.setRecipientHash(result == null ? payout.getRecipientHash() : first(result.recipientHash(), payout.getRecipientHash()));
        payout.setResponseDigest(result == null ? null : result.responseDigest());
        payout.setFailureCode(result == null ? "EMPTY_RESULT" : result.failureCode());
        if (result != null && present(result.confirmationPackage())) {
            payout.setConfirmationPackage(result.confirmationPackage());
        } else if (state == WithdrawalPayoutGateway.State.SUCCESS
                || state == WithdrawalPayoutGateway.State.FAILED) {
            payout.setConfirmationPackage(null);
        }
        payoutDao.update(payout);

        if (state == WithdrawalPayoutGateway.State.SUCCESS) {
            if (!WithdrawStatusEnum.PAYING.getValue().equals(withdraw.getStatus())) {
                Asserts.fail("提现状态已变化，不能重复确认渠道成功");
            }
            withdraw.setStatus(WithdrawStatusEnum.PAY_SUCCESS.getValue());
            withdraw.setPayTime(LocalDateTime.now());
            withdraw.setPayNo(result.providerOrderNo());
            if (!accountService.addWithdrawnAmount(withdraw.getAgentId(), withdraw.getWithdrawAmount())) {
                Asserts.fail("累计提现金额更新失败");
            }
            if (withdrawDao.update(withdraw) != 1) Asserts.fail("提现成功状态更新失败");
            memberMessageService.publish(new MemberMessageEvent(TenantContext.getTenantId(), withdraw.getUserId(),
                    "WITHDRAW_PAID:" + withdraw.getId(), "WITHDRAW_PAID", "WALLET_FUNDS",
                    "WITHDRAWAL", withdraw.getId(), null, LocalDateTime.now()));
            operationLogService.log("WITHDRAW", "CHANNEL_PAY_VERIFIED", "WITHDRAW_RECORD",
                    String.valueOf(withdraw.getId()), "status=2", "status=3;channel=" + payout.getChannel()
                            + ";providerOrderNo=" + result.providerOrderNo(), "官方渠道核验提现打款成功");
        } else if (state == WithdrawalPayoutGateway.State.FAILED) {
            withdraw.setStatus(WithdrawStatusEnum.AUDIT_PASSED.getValue());
            withdraw.setPayNo(null);
            withdraw.setPayTime(null);
            if (withdrawDao.update(withdraw) != 1) Asserts.fail("提现失败状态更新失败");
        } else if (!WithdrawStatusEnum.PAYING.getValue().equals(withdraw.getStatus())) {
            Asserts.fail("提现状态已变化，请刷新后核对");
        }
        return toVO(payout);
    }

    public WithdrawalPayoutVO get(Long withdrawId) {
        DmsWithdrawalPayout payout = payoutDao.selectByWithdrawId(withdrawId);
        return payout == null ? null : toVO(payout);
    }

    @Transactional(readOnly = true)
    public MemberConfirmation memberConfirmation(Long withdrawId, Long userId) {
        DmsWithdrawRecord withdraw = withdrawDao.selectById(withdrawId);
        if (withdraw == null || userId == null || !userId.equals(withdraw.getUserId())
                || !Integer.valueOf(2).equals(withdraw.getWithdrawType())) {
            Asserts.fail("微信提现记录不存在");
        }
        DmsWithdrawalPayout payout = payoutDao.selectByWithdrawId(withdrawId);
        if (payout == null || !"WECHAT".equals(payout.getChannel())
                || !withdraw.getWithdrawNo().equals(payout.getWithdrawNo())) {
            Asserts.fail("微信渠道打款尚未发起");
        }
        String packageInfo = "WAIT_USER_CONFIRM".equals(payout.getState())
                ? payout.getConfirmationPackage() : null;
        if ("WAIT_USER_CONFIRM".equals(payout.getState()) && !present(packageInfo)) {
            Asserts.fail("微信确认收款参数缺失，请联系平台重新核对渠道状态");
        }
        return new MemberConfirmation(withdrawId, payout.getRequestNo(), payout.getState(), packageInfo);
    }

    private WithdrawalPayoutGateway.State validateEvidence(DmsWithdrawalPayout payout,
                                                            WithdrawalPayoutGateway.PayoutResult result) {
        if (result == null || result.state() == null || !payout.getRequestNo().equals(result.requestNo())) {
            return WithdrawalPayoutGateway.State.UNKNOWN;
        }
        if (result.state() != WithdrawalPayoutGateway.State.SUCCESS) return result.state();
        boolean valid = result.amount() != null && result.amount().compareTo(payout.getAmount()) == 0
                && present(result.providerOrderNo()) && present(result.recipientHash())
                && present(result.responseDigest())
                && (!present(payout.getRecipientHash()) || payout.getRecipientHash().equals(result.recipientHash()));
        return valid ? WithdrawalPayoutGateway.State.SUCCESS : WithdrawalPayoutGateway.State.UNKNOWN;
    }

    private Reservation reservation(DmsWithdrawRecord withdraw, DmsWithdrawalPayout payout, boolean initiate) {
        DmsShopMember member = memberDao.selectByUserId(withdraw.getUserId());
        if (member == null) Asserts.fail("提现会员账号不存在");
        WithdrawalPayoutGateway.PayoutCommand command = new WithdrawalPayoutGateway.PayoutCommand(
                withdraw.getId(), payout.getRequestNo(), member.getId(), withdraw.getUserId(),
                withdraw.getWithdrawAmount(), withdraw.getBankAccount(), withdraw.getAccountName());
        return new Reservation(command, withdraw.getWithdrawType(), payout.getProviderOrderNo(), initiate, toVO(payout));
    }

    private WithdrawalPayoutVO toVO(DmsWithdrawalPayout payout) {
        WithdrawalPayoutVO vo = new WithdrawalPayoutVO();
        vo.setWithdrawId(payout.getWithdrawId());
        vo.setRequestNo(payout.getRequestNo());
        vo.setChannel(payout.getChannel());
        vo.setState(payout.getState());
        vo.setProviderStatus(payout.getProviderStatus());
        vo.setProviderOrderNo(payout.getProviderOrderNo());
        vo.setAmount(payout.getAmount());
        vo.setFailureCode(payout.getFailureCode());
        vo.setUserConfirmationRequired("WAIT_USER_CONFIRM".equals(payout.getState())
                && present(payout.getConfirmationPackage()));
        vo.setUpdateTime(payout.getUpdateTime());
        return vo;
    }

    private String requestNo(String withdrawNo, int attempt) {
        return attempt <= 1 ? withdrawNo : withdrawNo + "R" + attempt;
    }

    private String channel(Integer type) {
        return Integer.valueOf(2).equals(type) ? "WECHAT" : "ALIPAY";
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private String first(String primary, String fallback) {
        return present(primary) ? primary : fallback;
    }

    public record Reservation(WithdrawalPayoutGateway.PayoutCommand command, Integer withdrawType,
                              String providerOrderNo, boolean initiate, WithdrawalPayoutVO current) {
    }

    public record MemberConfirmation(Long withdrawId, String requestNo, String state, String packageInfo) {
    }
}
