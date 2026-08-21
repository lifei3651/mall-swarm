package com.macro.mall.distribution.service;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsCommissionRecordDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.entity.DmsCommissionRecord;
import com.macro.mall.distribution.entity.DmsShopProduct;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TenantScopedCommissionAndProductDaoTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DmsCommissionRecordDao commissionRecordDao;
    @Autowired private DmsShopProductDao productDao;

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
