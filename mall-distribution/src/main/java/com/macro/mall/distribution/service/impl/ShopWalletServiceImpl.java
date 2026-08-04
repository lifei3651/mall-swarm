package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.constants.BalanceAsset;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsMemberAssetAccountDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopWalletServiceImpl implements ShopWalletService {

    private static final int SMS_BIZ_TYPE_SET_PAYMENT_PASSWORD = 7;

    private static final int MAX_FAILED_PAY_PASSWORD_COUNT = 5;
    private static final int PAY_PASSWORD_LOCK_MINUTES = 30;

    private final DmsShopMemberDao memberDao;
    private final DmsAgentDao agentDao;
    private final DmsMemberAssetAccountDao assetAccountDao;
    private final DmsShopOrderDao orderDao;
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
        summary.setPaymentPasswordLocked(isPaymentPasswordLocked(current));
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
        vo.setPhone(recipient.getPhone());
        vo.setMemberName(firstText(recipient.getNickname(), recipientAgent.getAgentName(), recipient.getUsername(), recipient.getPhone()));
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
                SMS_BIZ_TYPE_SET_PAYMENT_PASSWORD);
        return memberDao.updatePayPassword(current.getId(), BCrypt.hashpw(dto.getNewPassword())) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean transfer(DmsShopMember member, BalanceTransferDTO dto) {
        DmsShopMember current = requireCurrentMember(member);
        if (dto == null) Asserts.fail("转账信息不能为空");
        BalanceRecipientVO ignored = findRecipient(current, dto.getRecipientPhone());
        verifyPaymentPassword(current, dto.getPaymentPassword());
        if (dto.getAmount() == null || dto.getAmount().stripTrailingZeros().scale() > 0) {
            Asserts.fail("转账金额只能为整数");
        }
        BigDecimal amount = normalizeAmount(dto.getAmount(), "转账金额");

        DmsShopMember recipient = memberDao.selectByPhone(dto.getRecipientPhone());
        AssetTransferDTO transfer = new AssetTransferDTO();
        transfer.setFromUserId(current.getUserId());
        transfer.setToUserId(recipient.getUserId());
        transfer.setAmount(amount);
        transfer.setBizType("MEMBER_BALANCE_TRANSFER");
        transfer.setBizId("BT" + IdUtil.getSnowflakeNextIdStr());
        String remark = dto.getRemark() == null || dto.getRemark().isBlank() ? "会员余额转账" : dto.getRemark().trim();
        transfer.setRemark(remark + "（收款人：" + ignored.getMemberName() + "）");
        return memberAssetService.transfer(transfer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopOrderVO payOrder(DmsShopMember member, Long orderId, BalancePayDTO dto) {
        DmsShopMember current = requireCurrentMember(member);
        if (orderId == null) Asserts.fail("订单ID不能为空");
        if (dto == null) Asserts.fail("请输入支付密码");

        // 锁住订单行，避免用户重复点击或并发请求造成余额重复扣款。
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
        if (order == null) Asserts.fail("订单不存在");
        if (!current.getUserId().equals(order.getUserId())) Asserts.fail("不能支付他人的订单");
        if (!Integer.valueOf(0).equals(order.getStatus())) {
            if (Integer.valueOf(1).equals(order.getStatus()) || Integer.valueOf(2).equals(order.getStatus())
                    || Integer.valueOf(3).equals(order.getStatus())) return shopService.getOrder(orderId);
            Asserts.fail("当前订单状态不能支付");
        }
        if (!"BALANCE".equalsIgnoreCase(order.getPayType())) {
            Asserts.fail("该订单选择的不是余额支付");
        }

        verifyPaymentPassword(current, dto.getPaymentPassword());
        BigDecimal amount = normalizeAmount(order.getPayAmount(), "订单实付金额");
        AssetChangeDTO consume = new AssetChangeDTO();
        consume.setUserId(current.getUserId());
        consume.setAmount(amount);
        consume.setBizType("ORDER_BALANCE_PAYMENT");
        consume.setBizId(String.valueOf(order.getId()));
        consume.setRemark("余额支付订单：" + order.getOrderNo());
        memberAssetService.consume(consume);
        return shopService.markOrderPaid(orderId, "BALANCE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WithdrawRecordVO applyWithdrawal(DmsShopMember member, ShopWithdrawalApplyDTO dto) {
        DmsShopMember current = requireCurrentMember(member);
        if (dto == null) Asserts.fail("提现信息不能为空");
        DmsAgent agent = agentDao.selectByUserId(current.getUserId());
        if (agent == null || !Integer.valueOf(1).equals(agent.getStatus())) Asserts.fail("完成首笔有效订单后才可以提现");

        BigDecimal amount = normalizeAmount(dto.getWithdrawAmount(), "提现金额");
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
        if (member == null || member.getId() == null) Asserts.fail("请先登录");
        DmsShopMember current = memberDao.selectById(member.getId());
        if (current == null || !Integer.valueOf(1).equals(current.getStatus())) Asserts.fail("会员账号不可用");
        return current;
    }

    private void verifyPaymentPassword(DmsShopMember member, String paymentPassword) {
        DmsShopMember current = memberDao.selectById(member.getId());
        if (!hasText(current.getPayPasswordHash())) Asserts.fail("请先设置支付密码");
        if (isPaymentPasswordLocked(current)) {
            Asserts.fail("支付密码连续错误5次，已锁定30分钟");
        }
        if (current.getPayPasswordLockTime() != null) {
            passwordAttemptService.clear(current.getId());
        }
        if (paymentPassword == null || !BCrypt.checkpw(paymentPassword, current.getPayPasswordHash())) {
            passwordAttemptService.recordFailure(current.getId(), MAX_FAILED_PAY_PASSWORD_COUNT);
            DmsShopMember refreshed = memberDao.selectById(current.getId());
            if (isPaymentPasswordLocked(refreshed)) Asserts.fail("支付密码连续错误5次，已锁定30分钟");
            Asserts.fail("支付密码错误");
        }
        passwordAttemptService.clear(current.getId());
    }

    private boolean isPaymentPasswordLocked(DmsShopMember member) {
        return member.getPayPasswordLockTime() != null
                && member.getPayPasswordLockTime().plusMinutes(PAY_PASSWORD_LOCK_MINUTES).isAfter(LocalDateTime.now());
    }

    private BigDecimal normalizeAmount(BigDecimal amount, String name) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) Asserts.fail(name + "必须大于0");
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) Asserts.fail(name + "至少为0.01元");
        return normalized;
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
}
