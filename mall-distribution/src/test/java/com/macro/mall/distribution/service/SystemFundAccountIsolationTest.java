package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.AdminDashboardDao;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SystemFundAccountIsolationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AdminDashboardDao dashboardDao;
    @Autowired private DmsShopMemberDao memberDao;
    @Autowired private DmsAgentDao agentDao;
    @Autowired private SqlSessionTemplate sqlSessionTemplate;

    @Test
    void internalFundAccountsDoNotBecomeCustomersOrPromotionMembers() {
        long membersBefore = dashboardDao.countMembers();
        long promotionMembersBefore = dashboardDao.countPromotionMembers();

        insertMember(9901L, -9901L, "SYS-FUND-TEST", "SYSFUND1", 0, 1);
        insertAgent(9901L, -9901L, "SYS_FUND_TEST", "SYSFUND1", 2);

        assertThat(dashboardDao.countMembers()).isEqualTo(membersBefore);
        assertThat(dashboardDao.countPromotionMembers()).isEqualTo(promotionMembersBefore);
        assertThat(memberDao.selectByAccount("SYS-FUND-TEST")).isNull();
        assertThat(memberDao.selectByInviteCode("SYSFUND1")).isNull();
        assertThat(memberDao.selectAdminList(null, null, null, null))
                .noneMatch(member -> Long.valueOf(9901L).equals(member.getId()));
        assertThat(agentDao.selectByInviteCode("SYSFUND1")).isNull();
        assertThat(agentDao.selectAll())
                .noneMatch(agent -> Long.valueOf(-9901L).equals(agent.getUserId()));

        insertMember(9902L, 9902L, "13900009902", "NORMAL1", 1, 0);
        insertAgent(9902L, 9902L, "NORMAL_MEMBER_TEST", "NORMAL1", 1);
        sqlSessionTemplate.clearCache();

        assertThat(dashboardDao.countMembers()).isEqualTo(membersBefore + 1);
        assertThat(dashboardDao.countPromotionMembers()).isEqualTo(promotionMembersBefore + 1);
        assertThat(memberDao.selectByAccount("13900009902")).isNotNull();
        assertThat(memberDao.selectByInviteCode("NORMAL1")).isNotNull();
    }

    @Test
    void dashboardCommissionQueriesUseExplicitTenantScope() {
        BigDecimal tenantOneBefore = dashboardDao.sumUnsettledCommission(1L);
        long tenantOneCountBefore = dashboardDao.countUnsettledCommission(1L);
        jdbcTemplate.update("""
                INSERT INTO dms_commission_record
                    (id, tenant_id, record_no, order_id, order_no, order_amount, order_user_id,
                     agent_id, agent_user_id, agent_level, commission_level, bonus_type,
                     commission_rate, commission_amount, status)
                VALUES (990001, 2, 'COM-DASHBOARD-TENANT-2', 990001, 'ORDER-DASHBOARD-TENANT-2',
                        100, 1001, 1, 1001, 1, 1, 'DIRECT_REWARD', 0.1000, 10, 0)
                """);
        sqlSessionTemplate.clearCache();

        assertThat(dashboardDao.sumUnsettledCommission(1L)).isEqualByComparingTo(tenantOneBefore);
        assertThat(dashboardDao.countUnsettledCommission(1L)).isEqualTo(tenantOneCountBefore);
        assertThat(dashboardDao.selectLatestCommissions(1L, 100))
                .noneMatch(row -> "ORDER-DASHBOARD-TENANT-2".equals(row.getOrderNo()));
        assertThat(dashboardDao.sumUnsettledCommission(2L)).isEqualByComparingTo("10.00");
        assertThat(dashboardDao.countUnsettledCommission(2L)).isEqualTo(1L);
    }

    private void insertMember(long id, long userId, String phone, String inviteCode,
                              int status, int systemAccount) {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_member
                    (id, user_id, phone, password_hash, nickname, invite_code, status, system_account)
                VALUES (?, ?, ?, 'disabled-test-hash', ?, ?, ?, ?)
                """, id, userId, phone, phone, inviteCode, status, systemAccount);
    }

    private void insertAgent(long id, long userId, String code, String inviteCode, int status) {
        jdbcTemplate.update("""
                INSERT INTO dms_agent
                    (id, user_id, agent_code, agent_name, agent_level, level_depth,
                     invite_code, status, source_type)
                VALUES (?, ?, ?, ?, 1, 1, ?, ?, 3)
                """, id, userId, code, code, inviteCode, status);
    }
}
