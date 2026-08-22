package com.macro.mall.distribution.service;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsCommissionClawbackDao;
import com.macro.mall.distribution.dao.DmsCommissionRecordDao;
import com.macro.mall.distribution.dao.DmsCommissionSettlementBatchDao;
import com.macro.mall.distribution.dao.DmsCommissionSettlementItemDao;
import com.macro.mall.distribution.dao.DmsBonusCalculationTaskDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.entity.DmsCommissionClawback;
import com.macro.mall.distribution.entity.DmsCommissionRecord;
import com.macro.mall.distribution.entity.DmsCommissionSettlementBatch;
import com.macro.mall.distribution.entity.DmsCommissionSettlementItem;
import com.macro.mall.distribution.entity.DmsBonusCalculationTask;
import com.macro.mall.distribution.entity.DmsShopProduct;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TenantScopedCommissionAndProductDaoTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DmsCommissionRecordDao commissionRecordDao;
    @Autowired private DmsCommissionClawbackDao clawbackDao;
    @Autowired private DmsCommissionSettlementBatchDao settlementBatchDao;
    @Autowired private DmsCommissionSettlementItemDao settlementItemDao;
    @Autowired private DmsShopProductDao productDao;
    @Autowired private DmsBonusCalculationTaskDao bonusTaskDao;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void commissionReadsAndWritesCannotCrossTenantBoundary() {
        insertCommission(991001L, 1L, "TENANT-COMMISSION-1", 991001L);
        insertCommission(991002L, 2L, "TENANT-COMMISSION-2", 991002L);
        TenantContext.setTenantId(1L);

        assertNotNull(commissionRecordDao.selectById(991001L));
        assertNull(commissionRecordDao.selectById(991002L));
        assertEquals(0, commissionRecordDao.updateStatus(991002L, 1));
        assertEquals(0, commissionRecordDao.deleteById(991002L));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT status FROM dms_commission_record WHERE id=991002", Integer.class));

        DmsCommissionRecord foreignRecord = new DmsCommissionRecord();
        foreignRecord.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> commissionRecordDao.insert(foreignRecord));
    }

    @Test
    void productReadsStatusAndStockChangesCannotCrossTenantBoundary() {
        insertProduct(991011L, 1L, "TENANT-PRODUCT-1");
        insertProduct(991012L, 2L, "TENANT-PRODUCT-2");
        TenantContext.setTenantId(1L);

        assertNotNull(productDao.selectById(991011L));
        assertNull(productDao.selectById(991012L));
        assertEquals(0, productDao.updateStatus(991012L, 0));
        assertEquals(0, productDao.decreaseStock(991012L, 1));
        assertEquals(0, productDao.increaseStock(991012L, 1));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT status FROM dms_shop_product WHERE id=991012", Integer.class));
        assertEquals(10, jdbcTemplate.queryForObject(
                "SELECT stock FROM dms_shop_product WHERE id=991012", Integer.class));

        DmsShopProduct foreignProduct = new DmsShopProduct();
        foreignProduct.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> productDao.insert(foreignProduct));
    }

    @Test
    void clawbackAndSettlementSnapshotsCannotCrossTenantBoundary() {
        jdbcTemplate.update("""
                INSERT INTO dms_commission_clawback
                (id,tenant_id,refund_id,commission_record_id,order_id,order_no,agent_id,
                 original_commission_amount,clawback_amount,deducted_amount,debt_amount,clawback_type,status)
                VALUES (991021,1,991021,991020,991020,'TENANT-ORDER',991020,10,10,0,10,3,2),
                       (991022,2,991022,991020,991020,'TENANT-ORDER',991020,20,20,0,20,3,2)
                """);
        jdbcTemplate.update("""
                INSERT INTO dms_commission_settlement_batch
                (id,tenant_id,batch_no,period_start,period_end,cutoff_time,status,record_count,total_amount,
                 settled_count,skipped_count,creator_id,creator_name)
                VALUES (991031,1,'TENANT-BATCH-1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,1,10,0,0,1,'tester'),
                       (991032,2,'TENANT-BATCH-2',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,1,20,0,0,1,'tester')
                """);
        jdbcTemplate.update("""
                INSERT INTO dms_commission_settlement_item
                (id,tenant_id,batch_id,commission_record_id,agent_id,agent_name,snapshot_amount,status)
                VALUES (991041,1,991031,991021,991020,'租户一',10,0),
                       (991042,2,991031,991022,991020,'租户二',20,0)
                """);
        TenantContext.setTenantId(1L);

        assertEquals(List.of(991021L), clawbackDao.selectByOrderId(991020L).stream().map(DmsCommissionClawback::getId).toList());
        assertEquals(List.of(991021L), clawbackDao.selectPendingDebtByAgentId(991020L).stream()
                .map(DmsCommissionClawback::getId).toList());
        assertEquals(new BigDecimal("10.00"), clawbackDao.sumByCommissionRecordId(991020L));
        assertEquals(new BigDecimal("10.00"), clawbackDao.sumDebtByAgentId(991020L));
        assertEquals(0, clawbackDao.updateDebtAfterOffset(991022L, BigDecimal.ONE, BigDecimal.ONE, 2));

        assertNotNull(settlementBatchDao.selectById(991031L));
        assertNull(settlementBatchDao.selectById(991032L));
        assertTrue(settlementBatchDao.selectList(0).stream().noneMatch(row -> row.getId().equals(991032L)));
        assertEquals(0, settlementBatchDao.markExecuted(991032L, 1, 0, 1L, "tester", java.time.LocalDateTime.now()));
        assertEquals(List.of(991041L), settlementItemDao.selectByBatchId(991031L).stream()
                .map(DmsCommissionSettlementItem::getId).toList());
        assertEquals(0, settlementItemDao.updateStatus(991042L, 2, "foreign tenant"));

        DmsCommissionClawback foreignClawback = new DmsCommissionClawback();
        foreignClawback.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> clawbackDao.insert(foreignClawback));
        DmsCommissionSettlementBatch foreignBatch = new DmsCommissionSettlementBatch();
        foreignBatch.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> settlementBatchDao.insert(foreignBatch));
        DmsCommissionSettlementItem foreignItem = new DmsCommissionSettlementItem();
        foreignItem.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> settlementItemDao.insertBatch(List.of(foreignItem)));
    }

    @Test
    void bonusTaskReadsAndStateTransitionsCannotCrossTenantBoundary() {
        jdbcTemplate.update("""
                INSERT INTO dms_bonus_calculation_task
                (id,tenant_id,rule_version_id,order_id,order_no,order_amount,order_user_id,order_user_name,
                 status,retry_count,max_retry_count,next_retry_time)
                VALUES (991051,1,1,991051,'TENANT-BONUS-1',100,1001,'租户一',0,0,3,CURRENT_TIMESTAMP),
                       (991052,2,1,991052,'TENANT-BONUS-2',100,2001,'租户二',0,0,3,CURRENT_TIMESTAMP)
                """);
        TenantContext.setTenantId(1L);

        assertNotNull(bonusTaskDao.selectById(991051L));
        assertNull(bonusTaskDao.selectById(991052L));
        assertEquals(List.of(991051L), bonusTaskDao.selectList(null, null).stream()
                .map(DmsBonusCalculationTask::getId).filter(id -> id >= 991051L).toList());
        assertEquals(0, bonusTaskDao.markProcessing(991052L));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT status FROM dms_bonus_calculation_task WHERE id=991052", Integer.class));
    }

    private void insertCommission(Long id, Long tenantId, String recordNo, Long orderId) {
        jdbcTemplate.update("""
                INSERT INTO dms_commission_record
                (id,tenant_id,record_no,order_id,order_no,order_amount,order_user_id,agent_id,agent_user_id,
                 agent_level,commission_level,bonus_type,commission_rate,commission_amount,status)
                VALUES (?,?,?,?,?,10,1001,1,1001,1,1,'DIRECT_REWARD',0.1000,1,0)
                """, id, tenantId, recordNo, orderId, "ORDER-" + id);
    }

    private void insertProduct(Long id, Long tenantId, String productNo) {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_product
                (id,tenant_id,product_no,product_name,sale_price,market_price,cost_amount,pv_value,bv_value,stock,status)
                VALUES (?,?,?,'租户隔离测试商品',10,10,5,0,0,10,1)
                """, id, tenantId, productNo);
    }
}
