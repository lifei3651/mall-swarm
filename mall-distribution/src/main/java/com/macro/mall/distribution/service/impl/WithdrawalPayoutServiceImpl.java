package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.dao.DmsWithdrawRecordDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsWithdrawRecord;
import com.macro.mall.distribution.service.WithdrawalPayoutGateway;
import com.macro.mall.distribution.service.WithdrawalPayoutService;
import com.macro.mall.distribution.vo.WithdrawalPayoutVO;
import com.macro.mall.distribution.vo.WechatTransferConfirmationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WithdrawalPayoutServiceImpl implements WithdrawalPayoutService {
    private final List<WithdrawalPayoutGateway> gateways;
    private final WithdrawalPayoutTransactionService transactions;
    private final DmsWithdrawRecordDao withdrawDao;
    private final WeChatPayProperties weChatPayProperties;
    private final WeChatMiniProgramProperties miniProgramProperties;

    @Override
    public WithdrawalPayoutVO start(Long withdrawId) {
        WithdrawalPayoutGateway gateway = configuredGateway(withdrawId);
        WithdrawalPayoutTransactionService.Reservation reservation = transactions.reserve(withdrawId);
        if ("SUCCESS".equals(reservation.current().getState())) return reservation.current();
        WithdrawalPayoutGateway.PayoutResult result = reservation.initiate()
                ? gateway.initiate(reservation.command())
                : gateway.query(reservation.command(), reservation.providerOrderNo());
        return transactions.apply(withdrawId, result);
    }

    @Override
    public WithdrawalPayoutVO reconcile(Long withdrawId) {
        WithdrawalPayoutGateway gateway = configuredGateway(withdrawId);
        WithdrawalPayoutTransactionService.Reservation reservation = transactions.reserve(withdrawId, false);
        if (reservation.initiate()) Asserts.fail("渠道打款尚未发起，请先执行发起操作");
        if ("SUCCESS".equals(reservation.current().getState())) return reservation.current();
        return transactions.apply(withdrawId,
                gateway.query(reservation.command(), reservation.providerOrderNo()));
    }

    @Override
    public WithdrawalPayoutVO get(Long withdrawId) {
        return transactions.get(withdrawId);
    }

    @Override
    public WechatTransferConfirmationVO prepareWechatConfirmation(DmsShopMember member, Long withdrawId) {
        requireOwnedWechatWithdrawal(member, withdrawId);
        WithdrawalPayoutGateway gateway = configuredGateway(withdrawId);
        WithdrawalPayoutTransactionService.Reservation reservation = transactions.reserve(withdrawId, false);
        if (!"SUCCESS".equals(reservation.current().getState())) {
            transactions.apply(withdrawId, gateway.query(reservation.command(), reservation.providerOrderNo()));
        }
        WithdrawalPayoutTransactionService.MemberConfirmation confirmation =
                transactions.memberConfirmation(withdrawId, member.getUserId());
        WechatTransferConfirmationVO vo = new WechatTransferConfirmationVO();
        vo.setWithdrawId(confirmation.withdrawId());
        vo.setRequestNo(confirmation.requestNo());
        vo.setState(confirmation.state());
        if (confirmation.packageInfo() != null) {
            vo.setMchId(weChatPayProperties.getMchId().trim());
            vo.setAppId(miniProgramProperties.getAppId().trim());
            vo.setPackageInfo(confirmation.packageInfo());
        }
        return vo;
    }

    private WithdrawalPayoutGateway gateway(Integer type) {
        return gateways.stream().filter(item -> item.supports(type)).findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到提现渠道适配器"));
    }

    private WithdrawalPayoutGateway configuredGateway(Long withdrawId) {
        DmsWithdrawRecord withdraw = withdrawDao.selectById(withdrawId);
        if (withdraw == null) Asserts.fail("提现记录不存在");
        if (Integer.valueOf(1).equals(withdraw.getWithdrawType())) {
            Asserts.fail("银行卡人工流水号确认入口已停用，请接入可核验的银行回单通道");
        }
        WithdrawalPayoutGateway gateway = gateway(withdraw.getWithdrawType());
        if (!gateway.configured()) {
            Asserts.fail((Integer.valueOf(2).equals(withdraw.getWithdrawType()) ? "微信" : "支付宝")
                    + "奖金转账尚未完成客户签约与密钥配置，当前保持安全关闭");
        }
        return gateway;
    }

    private void requireOwnedWechatWithdrawal(DmsShopMember member, Long withdrawId) {
        DmsWithdrawRecord withdraw = withdrawDao.selectById(withdrawId);
        if (member == null || member.getUserId() == null || withdraw == null
                || !member.getUserId().equals(withdraw.getUserId())
                || !Integer.valueOf(2).equals(withdraw.getWithdrawType())) {
            Asserts.fail("微信提现记录不存在");
        }
    }
}
