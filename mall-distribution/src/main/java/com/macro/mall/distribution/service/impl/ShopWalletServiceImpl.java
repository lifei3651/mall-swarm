package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.sms.SmsBusinessType;
import com.macro.mall.distribution.constants.BalanceAsset;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsMemberAssetAccountDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopTradeDao;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.AssetTransferDTO;
import com.macro.mall.distribution.dto.BalancePayDTO;
import com.macro.mall.distribution.dto.BalanceTransferDTO;
import com.macro.mall.distribution.dto.PaymentPasswordDTO;
import com.macro.mall.distribution.dto.ShopWithdrawalApplyDTO;
import com.macro.mall.distribution.dto.WithdrawApplyDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsMemberAssetAccount;
import com.macro.mall.distribution.entity.DmsMemberAssetFlow;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopTrade;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.PaymentPasswordAttemptService;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.service.ShopWalletService;
import com.macro.mall.distribution.service.SmsVerificationService;
import com.macro.mall.distribution.service.WithdrawService;
import com.macro.mall.distribution.vo.BalanceRecipientVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.vo.ShopWalletSummaryVO;
import com.macro.mall.distribution.vo.WithdrawRecordVO;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import com.macro.mall.distribution.util.MoneyValidationUtils;
import com.macro.mall.distribution.enums.ShopOrderStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopWalletServiceImpl implements ShopWalletService {

    private static final int MAX_FAILED_PAY_PASSWORD_COUNT = 5;
    private static final int PAY_PASSWORD_LOCK_MINUTES = 30;
    private static final BigDecimal MAX_TRANSFER_AMOUNT = new BigDecimal("999999999999.99");
    private static final BigDecimal MAX_ORDER_PAYMENT_AMOUNT = new BigDecimal("9999999999.99");
    private static final BigDecimal MAX_WITHDRAW_AMOUNT = new BigDecimal("99999999.99");

    private final DmsShopMemberDao memberDao;
    private final DmsAgentDao agentDao;
    private final DmsMemberAssetAccountDao assetAccountDao;
    private final DmsShopOrderDao orderDao;
    private final DmsShopTradeDao tradeDao;
    private final MemberAssetService memberAssetService;
    private final ShopService shopService;
    private final PaymentPasswordAttemptService passwordAttemptService;
    private final SmsVerificationService smsVerificationService;
    private final WithdrawService withdrawService;

    @Override
    public ShopWalletSummaryVO getSummary(DmsShopMember member) {
        DmsShopMember current = requireCurrentMember(member);
        DmsAgent agent = agentDao.selectByUserId(current.getUserId());
        DmsMemberAssetAccount account = agent == null ? null
                : assetAccountDao.selectByAgentIdAndAssetCode(agent.getId(), BalanceAsset.CODE);
        if (account == null) {
            account = assetAccountDao.selectByUserIdAndAssetCode(current.getUserId(), BalanceAsset.CODE);
        }
        ShopWalletSummaryVO summary = new ShopWalletSummaryVO();
        summary.setBalance(account == null || account.getBalance() == null ? BigDecimal.ZERO : account.getBalance());
        summary.setHasPaymentPassword(hasText(current.getPayPasswordHash()));
        boolean paymentPasswordLocked = isPaymentPasswordLocked(current);
        summary.setPaymentPasswordLocked(paymentPasswordLocked);
        summary.setPaymentPasswordLockRemainingSeconds(paymentPasswordLocked
                ? remainingLockSeconds(current.getPayPasswordLockTime()) : 0);
        summary.setDistributionActivated(agent != null && Integer.valueOf(1).equals(agent.getStatus()));
        return summary;
    }

    @Override
    public BalanceRecipientVO findRecipient(DmsShopMember member, String phone) {
        DmsShopMember current = requireCurrentMember(member);
        phone = PhoneNumberUtils.normalize(phone);
        if (!PhoneNumberUtils.isValidMainlandMobile(phone)) {
            Asserts.fail("请输入正确的收款会员手机号");
        }
        DmsShopMember recipient = memberDao.selectByPhone(phone);
        if (recipient == null || !Integer.valueOf(1).equals(recipient.getStatus())) {
            Asserts.fail("没有找到可收款的会员");
        }
        if (recipient.getId().equals(current.getId())) {
            Asserts.fail("不能给自己转账");
        }
        DmsAgent recipientAgent = agentDao.selectByUserId(recipient.getUserId());
        if (recipientAgent == null || !Integer.valueOf(1).equals(recipientAgent.getStatus())) {
            Asserts.fail("该账号尚未成为会员，暂不能接收余额");
        }
        BalanceRecipientVO vo = new BalanceRecipientVO();
        vo.setMemberName(firstText(recipient.getNickname(), recipientAgent.getAgentName(), recipient.getUsername(), recipient.getPhone()));
        vo.setMaskedPhone(maskPhone(recipient.getPhone()));
        vo.setMaskedLoginAccount(maskLoginAccount(recipient.getUsername()));
        vo.setMemberNo(String.format("M%07d", recipient.getId()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setPaymentPassword(DmsShopMember member, PaymentPasswordDTO dto) {
        DmsShopMember current = requireCurrentMember(member);
        if (dto == null || dto.getNewPassword() == null || !dto.getNewPassword().matches("^\\d{6}$")) {
            Asserts.fail("支付密码必须是6位数字");
        }
        if (hasText(current.getPayPasswordHash())) {
            verifyPaymentPassword(current, dto.getOldPassword());
            current = memberDao.selectById(current.getId());
        } else {
            if (!hasText(current.getPasswordHash())) Asserts.fail("请先设置登录密码，再设置支付密码");
            if (!hasText(dto.getLoginPassword()) || !BCrypt.checkpw(dto.getLoginPassword(), current.getPasswordHash())) {
                Asserts.fail("当前登录密码不正确");
            }
        }
        smsVerificationService.verifyAndConsume(current.getPhone(), dto.getSmsCode(),
                SmsBusinessType.SET_PAYMENT_PASSWORD);
        boolean hadPaymentPassword = hasText(current.getPayPasswordHash());
        boolean updated = memberDao.updatePayPassword(current.getId(), BCrypt.hashpw(dto.getNewPassword())) > 0;
        if (updated) {
            log.info("会员支付密码已{}: memberId={}, userId={}",
                    hadPaymentPassword ? "修改" : "设置", current.getId(), current.getUserId());
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean transfer(DmsShopMember member, BalanceTransferDTO dto) {
        DmsShopMember current = requireCurrentMember(member);
        if (dto == null) Asserts.fail("转账信息不能为空");
        String recipientPhone = PhoneNumberUtils.normalize(dto.getRecipientPhone());
        BalanceRecipientVO ignored = findRecipient(current, recipientPhone);
        verifyPaymentPassword(current, dto.getPaymentPassword());
        if (dto.getAmount() == null || dto.getAmount().stripTrailingZeros().scale() > 0) {
            Asserts.fail("转账金额只能为整数");
        }
        BigDecimal amount = MoneyValidationUtils.requirePositiveAmount(
                dto.getAmount(), "转账金额", MAX_TRANSFER_AMOUNT);

        DmsShopMember recipient = memberDao.selectByPhone(recipientPhone);
        AssetTransferDTO transfer = new AssetTransferDTO();
        transfer.setFromUserId(current.getUserId());
        transfer.setToUserId(recipient.getUserId());
        transfer.setAmount(amount);
        transfer.setBizType("MEMBER_BALANCE_TRANSFER");
        transfer.setBizId("BT" + IdUtil.getSnowflakeNextIdStr());
        String remark = dto.getRemark() == null || dto.getRemark().isBlank() ? "会员余额转账" : dto.getRemark().trim();
        transfer.setRemark(remark + "（收款人：" + ignored.getMemberName() + "）");
        boolean transferred = memberAssetService.transfer(transfer);
        if (transferred) {
            log.info("会员余额转账成功: bizId={}, memberId={}, userId={}, recipientMemberId={}, recipientUserId={}, amount={}",
                    transfer.getBizId(), current.getId(), current.getUserId(), recipient.getId(), recipient.getUserId(), amount);
        }
        return transferred;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopOrderVO payOrder(DmsShopMember member, Long orderId, BalancePayDTO dto) {
        DmsShopMember current = requireCurrentMember(member);
        if (orderId == null) Asserts.fail("订单ID不能为空");
        if (dto == null) Asserts.fail("请输入支付密码");

        // 只先读取父交易ID，不能在加锁前读取完整订单。否则并发第二个请求等待行锁后，
        // MyBatis 一级缓存仍可能把刚才的“待支付”快照作为已支付结果返回给前端。
        Long tradeId = orderDao.selectTradeIdById(orderId);
        if (tradeId != null) return payCheckout(current, tradeId, dto);

        // 旧订单和单商户订单继续锁住订单行支付。
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
        if (order == null) return payCheckout(current, orderId, dto);
        if (!current.getUserId().equals(order.getUserId())) Asserts.fail("不能支付他人的订单");
        if (!ShopOrderStatusEnum.PENDING_PAYMENT.matches(order.getStatus())) {
            if (ShopOrderStatusEnum.isPaidLifecycle(order.getStatus())) return shopService.getOrder(orderId);
            Asserts.fail("当前订单状态不能支付");
        }
        if (!"BALANCE".equalsIgnoreCase(order.getPayType())) {
            Asserts.fail("该订单选择的不是余额支付");
        }

        verifyPaymentPassword(current, dto.getPaymentPassword());
        BigDecimal amount = MoneyValidationUtils.requirePositiveAmount(
                order.getPayAmount(), "订单实付金额", MAX_ORDER_PAYMENT_AMOUNT);
        AssetChangeDTO consume = new AssetChangeDTO();
        consume.setUserId(current.getUserId());
        consume.setAmount(amount);
        consume.setBizType("ORDER_BALANCE_PAYMENT");
        consume.setBizId(String.valueOf(order.getId()));
        consume.setRequestId("ORDER_BALANCE_PAYMENT-" + order.getId());
        consume.setRemark("余额支付订单：" + order.getOrderNo());
        memberAssetService.consume(consume);
        ShopOrderVO paidOrder = shopService.markOrderPaid(orderId, "BALANCE");
        log.info("会员余额支付成功: memberId={}, userId={}, orderId={}, orderNo={}, amount={}",
                current.getId(), current.getUserId(), orderId, order.getOrderNo(), amount);
        return paidOrder;
    }

    private ShopOrderVO payCheckout(DmsShopMember current, Long checkoutId, BalancePayDTO dto) {
        DmsShopTrade trade = tradeDao.selectByIdForUpdate(checkoutId);
        if (trade == null) Asserts.fail("订单或支付交易不存在");
        if (!current.getUserId().equals(trade.getUserId())) Asserts.fail("不能支付他人的订单");
        if (!Integer.valueOf(0).equals(trade.getStatus())) {
            if (Integer.valueOf(1).equals(trade.getStatus())) return shopService.markCheckoutPaid(checkoutId, "BALANCE");
            Asserts.fail("当前交易状态不能支付");
        }
        if (!"BALANCE".equalsIgnoreCase(trade.getPayType())) Asserts.fail("该交易选择的不是余额支付");

        verifyPaymentPassword(current, dto.getPaymentPassword());
        BigDecimal amount = MoneyValidationUtils.requirePositiveAmount(
                trade.getPayAmount(), "交易实付金额", MAX_ORDER_PAYMENT_AMOUNT);
        AssetChangeDTO consume = new AssetChangeDTO();
        consume.setUserId(current.getUserId());
        consume.setAmount(amount);
        consume.setBizType("SHOP_TRADE_BALANCE_PAYMENT");
        consume.setBizId(String.valueOf(trade.getId()));
        consume.setRequestId("SHOP_TRADE_BALANCE_PAYMENT-" + trade.getId());
        consume.setRemark("余额支付跨商户交易：" + trade.getTradeNo());
        memberAssetService.consume(consume);
        ShopOrderVO paid = shopService.markCheckoutPaid(checkoutId, "BALANCE");
        log.info("会员余额支付跨商户交易成功: memberId={}, userId={}, tradeId={}, tradeNo={}, amount={}",
                current.getId(), current.getUserId(), checkoutId, trade.getTradeNo(), amount);
        return paid;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WithdrawRecordVO applyWithdrawal(DmsShopMember member, ShopWithdrawalApplyDTO dto) {
        DmsShopMember current = requireCurrentMember(member);
        if (dto == null) Asserts.fail("提现信息不能为空");
        DmsAgent agent = agentDao.selectByUserId(current.getUserId());
        if (agent == null || !Integer.valueOf(1).equals(agent.getStatus())) Asserts.fail("完成首笔有效订单后才可以提现");

        BigDecimal amount = MoneyValidationUtils.requirePositiveAmount(
                dto.getWithdrawAmount(), "提现金额", MAX_WITHDRAW_AMOUNT);
        withdrawService.validateWithdrawalLimits(agent.getId(), amount);
        DmsMemberAssetAccount balanceAccount = assetAccountDao.selectByAgentIdAndAssetCode(agent.getId(), BalanceAsset.CODE);
        BigDecimal balance = balanceAccount == null || balanceAccount.getBalance() == null ? BigDecimal.ZERO : balanceAccount.getBalance();
        if (balance.compareTo(amount) < 0) Asserts.fail("余额不足");
        if (dto.getWithdrawType() == null || !List.of(1, 2, 3).contains(dto.getWithdrawType())) Asserts.fail("请选择正确的提现方式");
        String accountName = requiredText(dto.getAccountName(), "请填写收款人姓名");
        String account = requiredText(dto.getBankAccount(), "请填写收款账号");
        String channelName = dto.getWithdrawType() == 1
                ? requiredText(dto.getBankName(), "请填写开户银行")
                : (dto.getWithdrawType() == 2 ? "微信" : "支付宝");

        verifyPaymentPassword(current, dto.getPaymentPassword());
        smsVerificationService.verifyAndConsume(current.getPhone(), dto.getSmsCode(), 5);

        WithdrawApplyDTO apply = new WithdrawApplyDTO();
        apply.setAgentId(agent.getId());
        apply.setWithdrawAmount(amount);
        apply.setWithdrawType(dto.getWithdrawType());
        apply.setBankName(channelName);
        apply.setBankAccount(account);
        apply.setAccountName(accountName);
        return withdrawService.applyWithdraw(apply);
    }

    @Override
    public List<WithdrawRecordVO> listWithdrawals(DmsShopMember member) {
        DmsShopMember current = requireCurrentMember(member);
        DmsAgent agent = agentDao.selectByUserId(current.getUserId());
        return agent == null ? List.of() : withdrawService.getWithdrawsByAgentId(agent.getId());
    }

    @Override
    public List<DmsMemberAssetFlow> listBalanceFlows(DmsShopMember member) {
        DmsShopMember current = requireCurrentMember(member);
        DmsAgent agent = agentDao.selectByUserId(current.getUserId());
        return agent == null
                ? memberAssetService.listFlows(null, current.getUserId())
                : memberAssetService.listFlows(agent.getId(), null);
    }

    private DmsShopMember requireCurrentMember(DmsShopMember member) {
        if (member == null || member.getId() == null) Asserts.unauthorized("请先登录");
        DmsShopMember current = memberDao.selectById(member.getId());
        if (current == null || !Integer.valueOf(1).equals(current.getStatus())) Asserts.fail("登录账号不可用");
        return current;
    }

    private void verifyPaymentPassword(DmsShopMember member, String paymentPassword) {
        DmsShopMember current = memberDao.selectById(member.getId());
        if (!hasText(current.getPayPasswordHash())) Asserts.fail("请先设置支付密码");
        if (isPaymentPasswordLocked(current)) {
            log.warn("会员支付密码验证被锁定拦截: memberId={}, userId={}", current.getId(), current.getUserId());
            Asserts.fail("支付密码连续错误5次，已锁定30分钟");
        }
        if (current.getPayPasswordLockTime() != null) {
            int expiredLockCount = current.getPayPasswordFailedCount() == null ? 0 : current.getPayPasswordFailedCount();
            passwordAttemptService.clearIfUnchanged(current.getId(), expiredLockCount);
            current = memberDao.selectById(current.getId());
            // 清理过期锁期间可能出现新的失败尝试；重新读取后必须再次执行锁定判断。
            if (isPaymentPasswordLocked(current)) {
                Asserts.fail("支付密码连续错误5次，已锁定30分钟");
            }
        }
        int observedFailedCount = current.getPayPasswordFailedCount() == null ? 0 : current.getPayPasswordFailedCount();
        if (paymentPassword == null || !BCrypt.checkpw(paymentPassword, current.getPayPasswordHash())) {
            passwordAttemptService.recordFailure(current.getId(), MAX_FAILED_PAY_PASSWORD_COUNT);
            DmsShopMember refreshed = memberDao.selectById(current.getId());
            int failedCount = refreshed == null || refreshed.getPayPasswordFailedCount() == null
                    ? observedFailedCount + 1 : refreshed.getPayPasswordFailedCount();
            log.warn("会员支付密码验证失败: memberId={}, userId={}, failedCount={}, locked={}",
                    current.getId(), current.getUserId(), failedCount, isPaymentPasswordLocked(refreshed));
            if (isPaymentPasswordLocked(refreshed)) Asserts.fail("支付密码连续错误5次，已锁定30分钟");
            Asserts.fail("支付密码错误");
        }
        if (!passwordAttemptService.clearIfUnchanged(current.getId(), observedFailedCount)) {
            DmsShopMember refreshed = memberDao.selectById(current.getId());
            if (isPaymentPasswordLocked(refreshed)) {
                log.warn("会员支付密码在并发校验期间被锁定: memberId={}, userId={}",
                        current.getId(), current.getUserId());
                Asserts.fail("支付密码连续错误5次，已锁定30分钟");
            }
        }
    }

    private boolean isPaymentPasswordLocked(DmsShopMember member) {
        return member != null && member.getPayPasswordLockTime() != null
                && member.getPayPasswordLockTime().plusMinutes(PAY_PASSWORD_LOCK_MINUTES).isAfter(LocalDateTime.now());
    }

    private int remainingLockSeconds(LocalDateTime lockTime) {
        if (lockTime == null) return 0;
        long millis = Duration.between(LocalDateTime.now(), lockTime.plusMinutes(PAY_PASSWORD_LOCK_MINUTES)).toMillis();
        return millis <= 0 ? 0 : (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (millis + 999L) / 1000L));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstText(String... values) {
        for (String value : values) if (hasText(value)) return value;
        return "会员";
    }

    private String requiredText(String value, String message) {
        if (!hasText(value)) Asserts.fail(message);
        return value.trim();
    }

    private String maskPhone(String phone) {
        if (phone == null || !phone.matches("^\\d{11}$")) return "-";
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private String maskLoginAccount(String account) {
        if (!hasText(account)) return "未设置";
        String value = account.trim();
        if (value.length() <= 2) return value.substring(0, 1) + "*";
        if (value.length() <= 4) return value.substring(0, 1) + "**" + value.substring(value.length() - 1);
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}
