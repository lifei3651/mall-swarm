package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsErpSyncTaskDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ErpTaskTenantDaoTest {
    @Autowired private DmsErpSyncTaskDao taskDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void adminTaskQueriesCannotReadAnotherTenant() {
        insert("ERP-TENANT-SCOPE-1", 1L, 901L);
        insert("ERP-TENANT-SCOPE-2", 2L, 902L);
        Long firstId = jdbcTemplate.queryForObject(
                "SELECT id FROM dms_erp_sync_task WHERE task_no=?", Long.class, "ERP-TENANT-SCOPE-1");
        Long secondId = jdbcTemplate.queryForObject(
                "SELECT id FROM dms_erp_sync_task WHERE task_no=?", Long.class, "ERP-TENANT-SCOPE-2");

        assertEquals(List.of(firstId), taskDao.selectList(1L, null, null).stream().map(task -> task.getId()).toList());
        assertEquals(List.of(secondId), taskDao.selectList(2L, null, null).stream().map(task -> task.getId()).toList());
        assertNull(taskDao.selectById(1L, secondId));
        assertNull(taskDao.selectById(2L, firstId));
        assertNotNull(taskDao.selectById(1L, firstId));
        assertNotNull(taskDao.selectById(2L, secondId));
    }

    private void insert(String taskNo, Long tenantId, Long integrationId) {
        jdbcTemplate.update("""
                INSERT INTO dms_erp_sync_task(task_no,integration_id,tenant_id,provider_code,biz_type,biz_id,status,retry_count)
                VALUES(?,?,?,'TEST_ERP','ORDER_PUSH',?,0,0)
                """, taskNo, integrationId, tenantId, taskNo);
    }
}
