package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.vo.BusinessTaskMetricSnapshot;
import com.macro.mall.distribution.vo.BusinessTimeoutMetricSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BusinessOperationsMonitoringMapperTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DmsWechatShippingSyncTaskDao shippingDao;
    @Autowired private DmsWechatLogisticsFollowTaskDao logisticsDao;
    @Autowired private DmsBonusCalculationTaskDao bonusDao;
    @Autowired private DmsErpSyncTaskDao erpDao;
    @Autowired private DmsShopAfterSaleDao afterSaleDao;
    @Autowired private DmsWithdrawalPayoutDao payoutDao;
    @Autowired private DmsMerchantLedgerDao ledgerDao;

    @BeforeEach
    void resetAggregateSources() {
        jdbc.update("DELETE FROM dms_wechat_shipping_sync_task");
        jdbc.update("DELETE FROM dms_wechat_logistics_follow_task");
        jdbc.update("DELETE FROM dms_bonus_calculation_task");
        jdbc.update("DELETE FROM dms_erp_sync_task");
        jdbc.update("DELETE FROM dms_erp_integration");
        jdbc.update("DELETE FROM dms_shop_after_sale");
        jdbc.update("DELETE FROM dms_shop_order");
        jdbc.update("DELETE FROM dms_withdrawal_payout");
        jdbc.update("DELETE FROM dms_withdraw_record");
        jdbc.update("DELETE FROM dms_merchant_ledger");
        jdbc.update("DELETE FROM dms_merchant_account");
    }

    @Test
    void taskAggregatesUseEachWorkersExecutableAndTerminalStates() {
        LocalDateTime oldest = NOW.minusHours(2);
        insertShipping("SHIP-PENDING", "PENDING", null, oldest);
        insertShipping("SHIP-SENDING", "SENDING", null, NOW.minusMinutes(10));
        insertShipping("SHIP-SUCCESS", "SUCCESS", null, NOW.minusDays(1));
        insertShipping("SHIP-FAILED", "PERMANENT", "WECHAT_INVALID", NOW.minusDays(1));
        insertShipping("SHIP-CANCELLED", "PERMANENT", "LOCAL_WAYBILL_CANCELLED", NOW.minusDays(1));

        insertLogistics(201, "PENDING", NOW.minusMinutes(30));
        insertLogistics(202, "RETRYABLE", NOW.minusMinutes(20));
        insertLogistics(203, "SUCCESS", NOW.minusDays(1));
        insertLogistics(204, "PERMANENT", NOW.minusDays(1));
        insertLogistics(205, "SKIPPED", NOW.minusDays(1));

        insertBonus(301, 0, 0, 3, NOW.minusMinutes(15));
        insertBonus(302, 1, 0, 3, NOW.minusMinutes(10));
        insertBonus(303, 3, 1, 3, NOW.minusMinutes(5));
        insertBonus(304, 3, 3, 3, NOW.minusDays(1));
        insertBonus(305, 2, 0, 3, NOW.minusDays(1));

        insertErp(401, 0, NOW.minusMinutes(45));
        insertErp(402, 2, NOW.minusMinutes(30));
        insertErp(403, 1, NOW.minusDays(1));
        insertErp(404, 3, NOW.minusDays(1));

        assertTask(shippingDao.selectMonitoringMetrics(), 2, oldest, 1);
        assertTask(logisticsDao.selectMonitoringMetrics(), 2, NOW.minusMinutes(30), 1);
        assertTask(bonusDao.selectMonitoringMetrics(), 3, NOW.minusMinutes(15), 1);
        assertTask(erpDao.selectMonitoringMetrics(), 2, NOW.minusMinutes(45), 1);
    }

    @Test
    void disabledErpIntegrationDoesNotRaiseExecutableBacklogAlarm() {
        insertErp(401, 0, NOW.minusHours(2));
        insertErp(402, 0, NOW.minusMinutes(15));
        jdbc.update("""
                INSERT INTO dms_erp_integration
                  (id,tenant_id,provider_code,integration_name,enabled)
                VALUES (401,1,'MONITOR-DISABLED','停用集成',0)
                """);

        assertTask(erpDao.selectMonitoringMetrics(), 1, NOW.minusMinutes(15), 0);
    }

    @Test
    void timeoutAggregatesOnlyCountExternalRefundsAndActivePayoutsPastCutoff() {
        insertOrder(501, "MONITOR-WECHAT-OLD", "WECHAT");
        insertOrder(502, "MONITOR-WECHAT-NEW", "WECHAT");
        insertOrder(503, "MONITOR-BALANCE", "BALANCE");
        insertOrder(504, "MONITOR-COMPLETE", "ALIPAY");
        insertAfterSale(601, 501, "MONITOR-WECHAT-OLD", 6, NOW.minusHours(4));
        insertAfterSale(602, 502, "MONITOR-WECHAT-NEW", 6, NOW.minusMinutes(10));
        insertAfterSale(603, 503, "MONITOR-BALANCE", 6, NOW.minusHours(5));
        insertAfterSale(604, 504, "MONITOR-COMPLETE", 1, NOW.minusHours(6));

        insertWithdraw(701, "MONITOR-WITHDRAW-OLD", 2, NOW.minusDays(3));
        insertWithdraw(702, "MONITOR-WITHDRAW-NEW", 2, NOW.minusHours(2));
        insertWithdraw(703, "MONITOR-WITHDRAW-DONE", 3, NOW.minusDays(4));
        insertPayout(801, 701, "MONITOR-WITHDRAW-OLD", "PROCESSING", NOW.minusDays(2));
        insertPayout(802, 702, "MONITOR-WITHDRAW-NEW", "WAIT_USER_CONFIRM", NOW.minusHours(2));
        insertPayout(803, 703, "MONITOR-WITHDRAW-DONE", "UNKNOWN", NOW.minusDays(3));

        BusinessTimeoutMetricSnapshot refund = afterSaleDao.selectTimedOutRefundMetrics(NOW.minusMinutes(30));
        assertEquals(1L, refund.getTimedOutCount());
        assertEquals(NOW.minusHours(4), refund.getOldestTime());

        BusinessTimeoutMetricSnapshot payout = payoutDao.selectTimedOutMetrics(NOW.minusDays(1));
        assertEquals(1L, payout.getTimedOutCount());
        assertEquals(NOW.minusDays(2), payout.getOldestTime());
    }

    @Test
    void ledgerMismatchMatchesProductionReconciliationDimensions() {
        insertAccount(901, 10, 20, 30, 40, 50, 60);
        insertAccount(902, 10, 20, 30, 40, 50, 60);
        insertAccount(903, 10, 20, 30, 40, 50, 60);
        insertLedger(999, 901, "MONITOR-LEDGER-OLD", 0, 0, 0, 0, 0, 0);
        insertLedger(1001, 901, "MONITOR-LEDGER-1", 10, 20, 30, 40, 50, 60);
        insertLedger(1002, 902, "MONITOR-LEDGER-2", 10, 21, 30, 40, 50, 60);

        assertEquals(2L, ledgerDao.countBalanceMismatches(),
                "一个余额不同和一个未初始化账本的账户都应标记为差异");
    }

    private void insertShipping(String paymentNo, String status, String errorCode, LocalDateTime created) {
        jdbc.update("""
                INSERT INTO dms_wechat_shipping_sync_task
                  (tenant_id,payment_order_no,user_id,status,error_code,create_time,update_time)
                VALUES (1,?,1,?,?,?,?)
                """, paymentNo, status, errorCode, created, created);
    }

    private void insertLogistics(long id, String status, LocalDateTime created) {
        jdbc.update("""
                INSERT INTO dms_wechat_logistics_follow_task
                  (id,tenant_id,order_id,shipment_id,user_id,status,create_time,update_time)
                VALUES (?,1,?,?,1,?,?,?)
                """, id, id, id, status, created, created);
    }

    private void insertBonus(long id, int status, int retries, int maxRetries, LocalDateTime created) {
        jdbc.update("""
                INSERT INTO dms_bonus_calculation_task
                  (id,tenant_id,order_id,order_no,order_amount,order_user_id,status,retry_count,max_retry_count,create_time,update_time)
                VALUES (?,1,?,?,1,1,?,?,?,?,?)
                """, id, id, "MONITOR-BONUS-" + id, status, retries, maxRetries, created, created);
    }

    private void insertErp(long id, int status, LocalDateTime created) {
        jdbc.update("""
                INSERT INTO dms_erp_sync_task
                  (id,task_no,integration_id,tenant_id,provider_code,biz_type,biz_id,status,retry_count,create_time,update_time)
                VALUES (?,?,?,1,'TEST','ORDER',?,?,0,?,?)
                """, id, "MONITOR-ERP-" + id, id, String.valueOf(id), status, created, created);
    }

    private void insertOrder(long id, String orderNo, String payType) {
        jdbc.update("""
                INSERT INTO dms_shop_order
                  (id,order_no,tenant_id,user_id,receiver_name,receiver_phone,receiver_address,status,pay_type)
                VALUES (?,?,1,1,'测试收货人','13800000000','测试地址',1,?)
                """, id, orderNo, payType);
    }

    private void insertAfterSale(long id, long orderId, String orderNo, int status, LocalDateTime updated) {
        jdbc.update("""
                INSERT INTO dms_shop_after_sale
                  (id,after_sale_no,order_id,order_no,member_id,user_id,status,create_time,update_time)
                VALUES (?, ?, ?, ?, 1, 1, ?, ?, ?)
                """, id, "MONITOR-AFTER-" + id, orderId, orderNo, status, updated, updated);
    }

    private void insertWithdraw(long id, String withdrawNo, int status, LocalDateTime updated) {
        jdbc.update("""
                INSERT INTO dms_withdraw_record
                  (id,withdraw_no,user_id,withdraw_amount,status,create_time,update_time)
                VALUES (?,?,1,10,?,?,?)
                """, id, withdrawNo, status, updated, updated);
    }

    private void insertPayout(long id, long withdrawId, String withdrawNo, String state, LocalDateTime updated) {
        jdbc.update("""
                INSERT INTO dms_withdrawal_payout
                  (id,withdraw_id,withdraw_no,request_no,channel,state,amount,create_time,update_time)
                VALUES (?,?,?,?,'WECHAT',?,10,?,?)
                """, id, withdrawId, withdrawNo, "MONITOR-REQUEST-" + id, state, updated, updated);
    }

    private void insertAccount(long merchantId, int pending, int available, int frozen,
                               int deposit, int debt, int paid) {
        jdbc.update("""
                INSERT INTO dms_merchant_account
                  (tenant_id,merchant_id,pending_amount,available_amount,frozen_amount,
                   deposit_frozen_amount,debt_amount,total_paid_amount)
                VALUES (1,?,?,?,?,?,?,?)
                """, merchantId, pending, available, frozen, deposit, debt, paid);
    }

    private void insertLedger(long id, long merchantId, String ledgerNo, int pending, int available,
                              int frozen, int deposit, int debt, int paid) {
        jdbc.update("""
                INSERT INTO dms_merchant_ledger
                  (id,tenant_id,merchant_id,ledger_no,biz_type,biz_id,summary,
                   pending_after,available_after,frozen_after,deposit_after,debt_after,paid_after)
                VALUES (?,1,?,?,'MONITOR',?,'monitor',?,?,?,?,?,?)
                """, id, merchantId, ledgerNo, String.valueOf(id),
                pending, available, frozen, deposit, debt, paid);
    }

    private void assertTask(BusinessTaskMetricSnapshot snapshot, long backlog,
                            LocalDateTime oldest, long failures) {
        assertEquals(backlog, snapshot.getBacklogCount());
        assertEquals(oldest, snapshot.getOldestTime());
        assertEquals(failures, snapshot.getFailureCount());
    }
}
