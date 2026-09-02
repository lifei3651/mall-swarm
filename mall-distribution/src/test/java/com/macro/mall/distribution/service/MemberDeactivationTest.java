package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.AgentInfoVO;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 取消会员资格（调整为非会员）测试：
 * 1. 余额钱包与历史数据保留；
 * 2. 有下级时下级自动移交原上级（无上级则成为根节点）；
 * 3. 有未结算奖金时不允许取消。
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
class MemberDeactivationTest {

    private static final long USER_A = 910000000000000001L;
    private static final long USER_B = 910000000000000002L;

    @Autowired private ShopAuthService shopAuthService;
    @Autowired private ShopService shopService;
    @Autowired private AgentService agentService;
    @Autowired private MemberAssetService memberAssetService;
    @Autowired private DmsShopMemberDao shopMemberDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long memberAId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO dms_shop_member (user_id, phone, login_account, password_hash, nickname, invite_code, status, system_account) "
                        + "VALUES (?, '19900001001', 'deact_a', 'x', '待取消会员A', 'DCT00001', 1, 0)",
                USER_A);
        memberAId = jdbcTemplate.queryForObject("SELECT id FROM dms_shop_member WHERE user_id = ?", Long.class, USER_A);
        shopAuthService.activateMember(USER_A, 1, "测试激活A");
    }

    @Test
    void deactivateKeepsWalletAndHistory() {
        memberAssetService.issue(change(USER_A, new BigDecimal("500"), "MEMBER-A", "TEST-DEACT-ISSUE-1"));

        shopAuthService.adjustMemberLevel(memberAId, 0, "测试取消会员资格");

        assertNull(agentService.getAgentByUserId(USER_A));
        DmsShopMember member = shopMemberDao.selectByUserId(USER_A);
        assertNotNull(member);
        assertEquals(1, memberAssetService.listAccounts(null, USER_A).size());
        assertEquals(0, new BigDecimal("500.00").compareTo(
                memberAssetService.listAccounts(null, USER_A).get(0).getBalance()));
        assertEquals(1, memberAssetService.listFlows(null, USER_A).size());
    }

    @Test
    void deactivateReparentsChildrenToRootWhenNoParent() {
        jdbcTemplate.update(
                "INSERT INTO dms_shop_member (user_id, phone, login_account, password_hash, nickname, invite_code, inviter_id, status, system_account) "
                        + "VALUES (?, '19900001002', 'deact_b', 'x', '下级会员B', 'DCT00002', ?, 1, 0)",
                USER_B, USER_A);
        shopAuthService.activateMember(USER_B, 1, "测试激活B");
        AgentInfoVO before = agentService.getAgentByUserId(USER_B);
        assertNotNull(before.getParentId());

        agentService.deactivate(before.getParentId(), "测试取消上级A");

        AgentInfoVO after = agentService.getAgentByUserId(USER_B);
        assertNotNull(after);
        assertNull(after.getParentId());
        DmsShopMember memberB = shopMemberDao.selectByUserId(USER_B);
        assertNull(memberB.getInviterId());
        assertNull(agentService.getAgentByUserId(USER_A));
    }

    @Test
    void deactivateRefusedWhenPendingCommissionExists() {
        Long agentAId = agentService.getAgentByUserId(USER_A).getId();
        jdbcTemplate.update(
                "INSERT INTO dms_commission_record (record_no, order_id, order_no, order_amount, order_user_id, "
                        + "agent_id, agent_user_id, agent_level, commission_level, bonus_type, commission_rate, "
                        + "commission_amount, status) "
                        + "VALUES ('DCT-PENDING-1', 910001, 'DCT-ORDER-1', 100.00, ?, ?, ?, 1, 1, 'DIRECT_REWARD', 0.1000, 10.00, 0)",
                USER_A, agentAId, USER_A);

        ApiException error = assertThrows(ApiException.class,
                () -> agentService.deactivate(agentAId, "测试取消"));
        assertTrue(error.getMessage().contains("待结算奖金"));
        assertNotNull(agentService.getAgentByUserId(USER_A));
    }

    @Test
    void inactiveAgentCannotReadTeamPerformanceThroughRetainedShoppingSession() {
        jdbcTemplate.update("UPDATE dms_agent SET status = 2 WHERE user_id = ?", USER_A);

        var profile = shopService.getProfilePerformance(shopMemberDao.selectByUserId(USER_A));

        assertFalse(profile.getCanViewTeamPerformance());
        assertNull(profile.getPerformance());
        assertNull(profile.getAgent());
    }

    private AssetChangeDTO change(Long userId, BigDecimal amount, String bizId, String requestId) {
        AssetChangeDTO dto = new AssetChangeDTO();
        dto.setUserId(userId);
        dto.setAmount(amount);
        dto.setBizType("MANUAL_MEMBER_ADJUST");
        dto.setBizId(bizId);
        dto.setRequestId(requestId);
        dto.setRemark("测试余额");
        return dto;
    }
}
