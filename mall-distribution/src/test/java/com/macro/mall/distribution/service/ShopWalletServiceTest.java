package com.macro.mall.distribution.service;

import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.config.RedisConfig;
import com.macro.mall.distribution.config.ScheduleTask;
import com.macro.mall.distribution.dao.DmsMemberAssetAccountDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.BalancePayDTO;
import com.macro.mall.distribution.dto.BalanceTransferDTO;
import com.macro.mall.distribution.dto.PaymentPasswordDTO;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.dto.ShopWithdrawalApplyDTO;
import com.macro.mall.distribution.dto.WithdrawAuditDTO;
import com.macro.mall.distribution.entity.DmsMemberAssetAccount;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.BalanceRecipientVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.vo.PersonProfileVO;
import com.macro.mall.distribution.vo.WithdrawRecordVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnableAutoConfiguration(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        RedisConfig.class,
        ScheduleTask.class
}))
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShopWalletServiceTest {

    @Autowired private ShopWalletService walletService;
    @Autowired private MemberAssetService memberAssetService;
    @Autowired private ShopService shopService;
    @Autowired private DmsShopMemberDao memberDao;
    @Autowired private DmsMemberAssetAccountDao assetAccountDao;
    @Autowired private DistributionAuditService auditService;
    @Autowired private WithdrawService withdrawService;
    @MockBean private SmsVerificationService smsVerificationService;

    @Test
    @Order(1)
    void balanceTransferAndOrderPaymentUseOneWalletWithIndependentPassword() {
        DmsShopMember payer = createMember(1001L, "13988220001", "付款会员");
        DmsShopMember recipient = createMember(1002L, "13988220002", "收款会员");

        PersonProfileVO byMemberAccount = auditService.getPersonProfile(null, null, payer.getUsername());
        PersonProfileVO byPhone = auditService.getPersonProfile(null, null, payer.getPhone());
        assertEquals(1L, byMemberAccount.getAgent().getId());
        assertEquals(payer.getId(), byPhone.getMember().getId());

        PaymentPasswordDTO password = new PaymentPasswordDTO();
        password.setNewPassword("246810");
        password.setLoginPassword("login123");
        password.setSmsCode("123456");
        assertTrue(walletService.setPaymentPassword(payer, password));
        verify(smsVerificationService).verifyAndConsume(payer.getPhone(), "123456", 7);
        assertTrue(walletService.getSummary(payer).getHasPaymentPassword());

        AssetChangeDTO issue = new AssetChangeDTO();
        issue.setAgentId(1L);
        issue.setAmount(new BigDecimal("500.00"));
        issue.setBizType("WALLET_TEST");
        memberAssetService.issue(issue);

        BalanceRecipientVO found = walletService.findRecipient(payer, recipient.getPhone());
        assertEquals("收款会员", found.getMemberName());

        BalanceTransferDTO transfer = new BalanceTransferDTO();
        transfer.setRecipientPhone(recipient.getPhone());
        transfer.setAmount(new BigDecimal("40.00"));
        transfer.setPaymentPassword("246810");
        assertTrue(walletService.transfer(payer, transfer));
        assertMoney("460.00", balance(1L));
        assertMoney("40.00", balance(2L));

        ShopOrderVO pending = shopService.submitOrder(orderRequest(), payer);
        BalancePayDTO wrong = new BalancePayDTO();
        wrong.setPaymentPassword("000000");
        assertThrows(ApiException.class, () -> walletService.payOrder(payer, pending.getOrder().getId(), wrong));
        assertMoney("460.00", balance(1L));

        BalancePayDTO correct = new BalancePayDTO();
        correct.setPaymentPassword("246810");
        ShopOrderVO paid = walletService.payOrder(payer, pending.getOrder().getId(), correct);
        assertEquals(1, paid.getOrder().getStatus());
        assertEquals("BALANCE", paid.getOrder().getPayType());
        assertMoney("161.00", balance(1L));

        // 幂等保护：重复请求不再次扣款。
        walletService.payOrder(payer, pending.getOrder().getId(), correct);
        assertMoney("161.00", balance(1L));
    }

    @Test
    @Order(2)
    void memberWithdrawalRequiresPasswordAndSmsAndRejectedAmountReturnsToSameBalance() {
        DmsShopMember member = createMember(1004L, "13988220004", "提现会员");
        PaymentPasswordDTO password = new PaymentPasswordDTO();
        password.setNewPassword("135790");
        password.setLoginPassword("login123");
        password.setSmsCode("654321");
        walletService.setPaymentPassword(member, password);

        AssetChangeDTO issue = new AssetChangeDTO();
        issue.setAgentId(4L);
        issue.setAmount(new BigDecimal("300.00"));
        issue.setBizType("WITHDRAW_TEST");
        memberAssetService.issue(issue);

        ShopWithdrawalApplyDTO apply = new ShopWithdrawalApplyDTO();
        apply.setWithdrawAmount(new BigDecimal("120.00"));
        apply.setWithdrawType(3);
        apply.setBankAccount("alipay@example.com");
        apply.setAccountName("提现会员");
        apply.setPaymentPassword("135790");
        apply.setSmsCode("123456");

        WithdrawRecordVO record = walletService.applyWithdrawal(member, apply);
        assertEquals(0, record.getStatus());
        assertMoney("180.00", balance(4L));
        verify(smsVerificationService).verifyAndConsume(member.getPhone(), "123456", 5);
        assertEquals(record.getId(), walletService.listWithdrawals(member).get(0).getId());

        WithdrawAuditDTO reject = new WithdrawAuditDTO();
        reject.setId(record.getId());
        reject.setStatus(4);
        reject.setAuditUserId(1L);
        reject.setAuditRemark("收款账号需要重新确认");
        assertTrue(withdrawService.auditWithdraw(reject));
        assertMoney("300.00", balance(4L));
        assertEquals("审核拒绝", walletService.listWithdrawals(member).get(0).getStatusName());
    }

    @Test
    @Order(3)
    void settingAndChangingPaymentPasswordBothRequireSms() {
        DmsShopMember member = createMember(1005L, "13988220005", "安全会员");
        PaymentPasswordDTO wrongLogin = new PaymentPasswordDTO();
        wrongLogin.setNewPassword("112233");
        wrongLogin.setLoginPassword("wrong-password");
        wrongLogin.setSmsCode("123456");

        assertThrows(ApiException.class, () -> walletService.setPaymentPassword(member, wrongLogin));
        assertFalse(walletService.getSummary(member).getHasPaymentPassword());

        PaymentPasswordDTO valid = new PaymentPasswordDTO();
        valid.setNewPassword("112233");
        valid.setLoginPassword("login123");
        valid.setSmsCode("123456");
        assertTrue(walletService.setPaymentPassword(member, valid));
        verify(smsVerificationService).verifyAndConsume(member.getPhone(), "123456", 7);
        assertTrue(walletService.getSummary(member).getHasPaymentPassword());

        PaymentPasswordDTO change = new PaymentPasswordDTO();
        change.setOldPassword("112233");
        change.setNewPassword("445566");
        change.setSmsCode("654321");
        assertTrue(walletService.setPaymentPassword(member, change));
        verify(smsVerificationService).verifyAndConsume(member.getPhone(), "654321", 7);
        assertTrue(BCrypt.checkpw("445566", memberDao.selectById(member.getId()).getPayPasswordHash()));
    }

    @Test
    @Order(4)
    void duplicateManualBalanceRequestCannotChangeBalanceTwice() {
        BigDecimal before = balance(1L);
        AssetChangeDTO issue = new AssetChangeDTO();
        issue.setAgentId(1L);
        issue.setAmount(new BigDecimal("88.00"));
        issue.setBizType("MANUAL_MEMBER_ADJUST");
        issue.setBizId("TEST_MEMBER");
        issue.setRequestId("8af17f6d-e310-4c99-bb0c-a8c7f6e8f001");

        memberAssetService.issue(issue);
        BigDecimal once = before.add(new BigDecimal("88.00"));
        assertEquals(0, once.compareTo(balance(1L)));
        assertThrows(RuntimeException.class, () -> memberAssetService.issue(issue));
        assertEquals(0, once.compareTo(balance(1L)));
    }

    @Test
    @Order(5)
    void simultaneousBalancePaymentsOnlyDeductOnce() throws Exception {
        DmsShopMember payer = memberDao.selectByUserId(1001L);
        AssetChangeDTO issue = new AssetChangeDTO();
        issue.setAgentId(1L);
        issue.setAmount(new BigDecimal("500.00"));
        issue.setBizType("CONCURRENT_PAYMENT_TEST");
        issue.setBizId("CONCURRENT_PAYMENT_TEST_1");
        memberAssetService.issue(issue);

        ShopOrderVO pending = shopService.submitOrder(orderRequest(), payer);
        BigDecimal before = balance(1L);
        BigDecimal expected = before.subtract(pending.getOrder().getPayAmount());
        BalancePayDTO payment = new BalancePayDTO();
        payment.setPaymentPassword("246810");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ShopOrderVO> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return walletService.payOrder(payer, pending.getOrder().getId(), payment);
            });
            Future<ShopOrderVO> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return walletService.payOrder(payer, pending.getOrder().getId(), payment);
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, first.get(10, TimeUnit.SECONDS).getOrder().getStatus());
            assertEquals(1, second.get(10, TimeUnit.SECONDS).getOrder().getStatus());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(0, expected.compareTo(balance(1L)));
    }

    private DmsShopMember createMember(Long userId, String phone, String nickname) {
        DmsShopMember member = new DmsShopMember();
        member.setUserId(userId);
        member.setPhone(phone);
        member.setUsername("member_" + phone);
        member.setPasswordHash(BCrypt.hashpw("login123"));
        member.setNickname(nickname);
        member.setInviteCode(phone.substring(phone.length() - 8));
        member.setStatus(1);
        memberDao.insert(member);
        return memberDao.selectById(member.getId());
    }

    private ShopOrderSubmitDTO orderRequest() {
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(1L);
        item.setSkuId(1L);
        item.setQuantity(1);
        ShopOrderSubmitDTO dto = new ShopOrderSubmitDTO();
        dto.setReceiverName("测试收货人");
        dto.setReceiverPhone("13988220001");
        dto.setReceiverProvince("湖南省");
        dto.setReceiverCity("长沙市");
        dto.setReceiverDistrict("岳麓区");
        dto.setReceiverDetailAddress("测试地址1号");
        dto.setPayType("BALANCE");
        dto.setItems(List.of(item));
        return dto;
    }

    private BigDecimal balance(Long agentId) {
        DmsMemberAssetAccount account = assetAccountDao.selectByAgentIdAndAssetCode(agentId, "CASH_BONUS");
        return account == null ? BigDecimal.ZERO : account.getBalance();
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
