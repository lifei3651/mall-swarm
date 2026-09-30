package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsCommissionClawback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DirectReferralDebtMapperTest {
    @Autowired DmsCommissionClawbackDao clawbackDao;
    @Autowired DmsOrderFinanceDao financeDao;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void debtRestorationSourcePersistsAndLockedDebtCannotCrossTenant() {
        jdbc.update("""
                INSERT INTO dms_commission_clawback
                (id,tenant_id,refund_id,commission_record_id,order_id,agent_id,
                 original_commission_amount,clawback_amount,deducted_amount,debt_amount,clawback_type,status)
                VALUES (9997401,1,9997001,9997001,9997001,9997001,5,5,5,0,3,1),
                       (9997402,2,9997002,9997002,9997002,9997001,5,5,5,0,3,1)
                """);
        TenantContext.setTenantId(1L);
        DmsCommissionClawback offset = event(4, 9997401L);
        assertEquals(1, clawbackDao.insert(offset));
        assertEquals(9997401L, clawbackDao.selectByIdForUpdate(offset.getId()).getSourceClawbackId());
        assertNull(clawbackDao.selectByIdForUpdate(9997402L));
        assertEquals(0, clawbackDao.updateDebtAfterOffset(9997402L, BigDecimal.ZERO, new BigDecimal("5.00"), 2));

        DmsCommissionClawback restoration = event(5, offset.getId());
        assertEquals(1, clawbackDao.insert(restoration));
        assertEquals(offset.getId(), clawbackDao.selectByIdForUpdate(restoration.getId()).getSourceClawbackId());
        assertEquals(1, clawbackDao.updateDebtAfterOffset(9997401L, BigDecimal.ZERO, new BigDecimal("5.00"), 2));
        assertEquals(new BigDecimal("5.00"), clawbackDao.sumDebtByAgentId(9997001L));
        // Type 5 is an audit event, not another five-yuan debt row.
        assertEquals(BigDecimal.ZERO.setScale(2), clawbackDao.selectByIdForUpdate(restoration.getId()).getDebtAmount());
    }

    private DmsCommissionClawback event(int type, Long sourceId) {
        DmsCommissionClawback row = new DmsCommissionClawback();
        row.setRefundId(type == 4 ? 0L : 9997201L);
        row.setCommissionRecordId(9997201L);
        row.setSourceClawbackId(sourceId);
        row.setOrderId(9997201L);
        row.setAgentId(9997001L);
        row.setOriginalCommissionAmount(new BigDecimal("5.00"));
        row.setClawbackAmount(new BigDecimal("5.00"));
        row.setDeductedAmount(type == 4 ? new BigDecimal("5.00") : BigDecimal.ZERO);
        row.setDebtAmount(BigDecimal.ZERO);
        row.setClawbackType(type);
        row.setStatus(1);
        return row;
    }

    @Test
    void financeSummaryDirectSettledExpenseExcludesPriorPendingRefundDebtOffsetAndRestoration() {
        jdbc.update("""
                INSERT INTO dms_shop_order
                (id,order_no,tenant_id,receiver_name,receiver_phone,receiver_address,total_amount,pay_amount,status,pay_time)
                VALUES(9997101,'DIRECT-DEBT-FIN',9998,'test','13900000000','test',50,50,3,CURRENT_TIMESTAMP)
                """);
        jdbc.update("INSERT INTO dms_order_finance(order_id,order_no,pay_amount) VALUES(9997101,'DIRECT-DEBT-FIN',50)");
        jdbc.update("""
                INSERT INTO dms_commission_record
                (id,tenant_id,record_no,order_id,order_no,order_amount,order_user_id,agent_id,agent_user_id,
                 agent_level,commission_level,bonus_type,commission_rate,commission_amount,status)
                VALUES(9997101,9998,'DIRECT-DEBT-COM',9997101,'DIRECT-DEBT-FIN',50,1,1,1,1,1,'DIRECT_REFERRAL',0.1,2.5,1)
                """);
        jdbc.update("""
                INSERT INTO dms_commission_clawback
                (tenant_id,refund_id,commission_record_id,order_id,agent_id,clawback_amount,clawback_type)
                VALUES(9998,9997101,9997101,9997101,1,2.5,1),
                      (9998,0,9997101,9997101,1,1,4)
                """);
        assertEquals(new BigDecimal("2.50"), financeDao.selectSummaryScoped(9998L, null, null).getBonusAmount());
        jdbc.update("""
                INSERT INTO dms_commission_clawback
                (tenant_id,refund_id,commission_record_id,order_id,agent_id,clawback_amount,clawback_type)
                VALUES(9998,9997102,9997101,9997101,1,1,5),
                      (9998,9997102,9997101,9997101,1,2.5,2)
                """);
        assertEquals(BigDecimal.ZERO.setScale(2), financeDao.selectSummaryScoped(9998L, null, null).getBonusAmount());
    }
}
