package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsErpSyncTask;
import com.macro.mall.distribution.entity.DmsMerchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DynamicSqlWhitespaceRegressionTest {

    @Autowired private DmsMerchantDao merchantDao;
    @Autowired private DmsErpSyncTaskDao erpSyncTaskDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedConditionalQueryRows() {
        jdbcTemplate.update("""
                INSERT INTO dms_merchant
                (id, tenant_id, merchant_no, merchant_name, status)
                VALUES (990001, 1, 'SQL-SPACE-MERCHANT', '条件查询商户', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO dms_erp_sync_task
                (id, task_no, integration_id, tenant_id, provider_code, biz_type, biz_id, status, retry_count)
                VALUES (990002, 'SQL-SPACE-ERP', 990003, 1, 'TEST', 'ORDER', '990004', 0, 0)
                """);
    }

    @Test
    void merchantListCanCombineTenantStatusAndKeywordConditions() {
        List<DmsMerchant> rows = merchantDao.selectList(1L, "SQL-SPACE", 1);

        assertEquals(List.of("SQL-SPACE-MERCHANT"), rows.stream().map(DmsMerchant::getMerchantNo).toList());
    }

    @Test
    void erpTaskListCanCombineIntegrationAndStatusConditions() {
        List<DmsErpSyncTask> rows = erpSyncTaskDao.selectList(990003L, 0);

        assertEquals(List.of("SQL-SPACE-ERP"), rows.stream().map(DmsErpSyncTask::getTaskNo).toList());
    }
}
