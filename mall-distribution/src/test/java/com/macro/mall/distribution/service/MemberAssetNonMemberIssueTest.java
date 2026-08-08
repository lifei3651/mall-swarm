package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsMemberAssetAccountDao;
import com.macro.mall.distribution.dao.DmsMemberAssetFlowDao;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.entity.DmsMemberAssetAccount;
import com.macro.mall.distribution.entity.DmsMemberAssetFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 非会员（未进入奖金体系）余额钱包测试：
 * 后台可直接给无代理记录的商城账号增加/扣减余额，且不需要激活推广身份。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnableAutoConfiguration(exclude = {
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class
})
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
    com.macro.mall.distribution.config.RedisConfig.class,
    com.macro.mall.distribution.config.ScheduleTask.class
}))
class MemberAssetNonMemberIssueTest {

    private static final String PHONE = "19900000001";

    @Autowired
    private MemberAssetService memberAssetService;

    @Autowired
    private DmsMemberAssetAccountDao assetAccountDao;

    @Autowired
    private DmsMemberAssetFlowDao assetFlowDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO dms_shop_member (user_id, phone, login_account, password_hash, nickname, invite_code, status, system_account) "
                        + "VALUES (900000000000000101, ?, 'nonmember01', 'x', '非会员测试', 'NMBR0001', 1, 0)",
                PHONE);
        userId = 900000000000000101L;
    }

    @Test
    void issueAndDeductForNonActivatedMember() {
        AssetChangeDTO issue = new AssetChangeDTO();
        issue.setUserId(userId);
        issue.setAmount(new BigDecimal("50000"));
        issue.setBizType("MANUAL_MEMBER_ADJUST");
        issue.setBizId("MEMBER-7");
        issue.setRequestId("TEST-NONMEMBER-ISSUE-001");
        issue.setRemark("后台人工增加余额");

        DmsMemberAssetFlow issueFlow = memberAssetService.issue(issue);
        assertNotNull(issueFlow.getId());
        assertNull(issueFlow.getAgentId());
        assertEquals(userId, issueFlow.getUserId());
        assertEquals(1, issueFlow.getChangeType());
        assertEquals(0, new BigDecimal("50000.00").compareTo(issueFlow.getBalanceAfter()));

        DmsMemberAssetAccount account = memberAssetService.listAccounts(null, userId).get(0);
        assertNull(account.getAgentId());
        assertEquals(userId, account.getUserId());
        assertEquals(0, new BigDecimal("50000.00").compareTo(account.getBalance()));
        assertEquals(0, new BigDecimal("50000.00").compareTo(account.getTotalIn()));

        AssetChangeDTO deduct = new AssetChangeDTO();
        deduct.setUserId(userId);
        deduct.setAmount(new BigDecimal("10000"));
        deduct.setBizType("MANUAL_MEMBER_ADJUST");
        deduct.setBizId("MEMBER-7");
        deduct.setRequestId("TEST-NONMEMBER-DEDUCT-001");
        deduct.setRemark("后台人工扣减余额");

        DmsMemberAssetFlow deductFlow = memberAssetService.deduct(deduct);
        assertNotNull(deductFlow.getId());
        assertNull(deductFlow.getAgentId());
        assertEquals(5, deductFlow.getChangeType());
        assertEquals(0, new BigDecimal("40000.00").compareTo(deductFlow.getBalanceAfter()));

        DmsMemberAssetAccount afterDeduct = memberAssetService.listAccounts(null, userId).get(0);
        assertEquals(0, new BigDecimal("40000.00").compareTo(afterDeduct.getBalance()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(afterDeduct.getTotalOut()));

        List<DmsMemberAssetFlow> flows = memberAssetService.listFlows(null, userId);
        assertEquals(2, flows.size());
        assertNull(assetAccountDao.selectByAgentIdAndAssetCode(999999L, "CASH_BONUS"));
        assertNotNull(assetAccountDao.selectByUserIdAndAssetCode(userId, "CASH_BONUS"));
        assertEquals(2, assetFlowDao.selectByUserId(userId, "CASH_BONUS").size());
    }
}
