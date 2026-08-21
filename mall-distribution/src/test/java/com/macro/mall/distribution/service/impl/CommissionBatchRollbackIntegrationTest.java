package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.service.AgentAccountService;
import com.macro.mall.distribution.service.CommissionService;
import com.macro.mall.distribution.service.MemberAssetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CommissionBatchRollbackIntegrationTest {

    private static final long FIRST_RECORD_ID = 950001L;
    private static final long SECOND_RECORD_ID = 950002L;

    @Autowired private CommissionService commissionService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private AgentAccountService accountService;
    @MockitoBean private MemberAssetService memberAssetService;

    @BeforeEach
    void seedPendingRecords() {
        deleteFixtures();
        insertRecord(FIRST_RECORD_ID, 951001L, "ROLLBACK-FIRST");
        insertRecord(SECOND_RECORD_ID, 951002L, "ROLLBACK-SECOND");
    }

    @AfterEach
    void deleteFixtures() {
        jdbcTemplate.update("DELETE FROM dms_commission_record WHERE id IN (?, ?)", FIRST_RECORD_ID, SECOND_RECORD_ID);
    }

    @Test
    void nthAccountingFailureRollsBackWholeSettleCommissionBatch() {
        BigDecimal amount = new BigDecimal("10.00");
        when(accountService.settleCommission(951001L, amount)).thenReturn(true);
        when(accountService.settleCommission(951002L, amount))
                .thenThrow(new IllegalStateException("模拟第二笔账户结算失败"));

        assertThrows(IllegalStateException.class,
                () -> commissionService.settleCommissionBatch(List.of(FIRST_RECORD_ID, SECOND_RECORD_ID)));

        assertEquals(List.of(0, 0), jdbcTemplate.queryForList(
                "SELECT status FROM dms_commission_record WHERE id IN (?, ?) ORDER BY id",
                Integer.class, FIRST_RECORD_ID, SECOND_RECORD_ID));
        verify(accountService).settleCommission(951001L, amount);
        verify(accountService).settleCommission(951002L, amount);
        verify(memberAssetService).issue(any(AssetChangeDTO.class));
    }

    private void insertRecord(long id, long agentId, String recordNo) {
        jdbcTemplate.update("""
                INSERT INTO dms_commission_record
                (id, tenant_id, record_no, order_id, order_no, order_amount, order_user_id,
                 agent_id, agent_user_id, agent_level, commission_level, bonus_type,
                 commission_rate, commission_amount, status)
                VALUES (?, 1, ?, ?, ?, 100, 1, ?, ?, 1, 1, 'DIRECT_REWARD', 0.1000, 10, 0)
                """, id, recordNo, id, "ORDER-" + id, agentId, agentId);
    }
}
