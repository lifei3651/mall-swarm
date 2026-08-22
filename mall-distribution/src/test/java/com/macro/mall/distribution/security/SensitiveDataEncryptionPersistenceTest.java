package com.macro.mall.distribution.security;

import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsErpIntegrationDao;
import com.macro.mall.distribution.config.SensitiveDataEncryptionMigrator;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsErpIntegration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SensitiveDataEncryptionPersistenceTest {

    @Autowired private DmsAgentDao agentDao;
    @Autowired private DmsErpIntegrationDao erpIntegrationDao;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SensitiveDataEncryptionMigrator migrator;

    @Test
    void agentIdentityAndBankAccountAreEncryptedAtRestAndTransparentToBusinessCode() {
        DmsAgent agent = new DmsAgent();
        agent.setUserId(980001L);
        agent.setAgentCode("ENC980001");
        agent.setAgentName("加密测试会员");
        agent.setAgentLevel(1);
        agent.setLevelDepth(1);
        agent.setInviteCode("ENC98001");
        agent.setPhone("13800009801");
        agent.setIdCard("430102199001011234");
        agent.setBankAccount("6222020202020202020");
        agent.setStatus(1);
        agent.setSourceType(3);
        agentDao.insert(agent);

        String storedIdCard = jdbcTemplate.queryForObject(
                "SELECT id_card FROM dms_agent WHERE id=?", String.class, agent.getId());
        String storedBankAccount = jdbcTemplate.queryForObject(
                "SELECT bank_account FROM dms_agent WHERE id=?", String.class, agent.getId());
        assertTrue(storedIdCard.startsWith(EncryptedStringTypeHandler.PREFIX));
        assertTrue(storedBankAccount.startsWith(EncryptedStringTypeHandler.PREFIX));
        assertNotEquals(agent.getIdCard(), storedIdCard);
        assertEquals(agent.getIdCard(), agentDao.selectById(agent.getId()).getIdCard());
        assertEquals(agent.getBankAccount(), agentDao.selectById(agent.getId()).getBankAccount());
    }

    @Test
    void erpCredentialsAreEncryptedAtRestAndTransparentToAdapters() {
        DmsErpIntegration integration = new DmsErpIntegration();
        integration.setTenantId(1L);
        integration.setProviderCode("ENC_TEST");
        integration.setIntegrationName("加密测试ERP");
        integration.setEnabled(0);
        integration.setEnvironment("TEST");
        integration.setAppKey("public-app-key");
        integration.setAppSecret("private-app-secret");
        integration.setCallbackToken("callback-token-secret");
        erpIntegrationDao.insert(integration);

        String storedSecret = jdbcTemplate.queryForObject(
                "SELECT app_secret FROM dms_erp_integration WHERE id=?", String.class, integration.getId());
        String storedToken = jdbcTemplate.queryForObject(
                "SELECT callback_token FROM dms_erp_integration WHERE id=?", String.class, integration.getId());
        assertTrue(storedSecret.startsWith(EncryptedStringTypeHandler.PREFIX));
        assertTrue(storedToken.startsWith(EncryptedStringTypeHandler.PREFIX));
        DmsErpIntegration reloaded = erpIntegrationDao.selectById(integration.getId());
        assertEquals("private-app-secret", reloaded.getAppSecret());
        assertEquals("callback-token-secret", reloaded.getCallbackToken());
    }

    @Test
    void migratesLegacyPlaintextWithoutChangingBusinessValues() {
        jdbcTemplate.update("""
                INSERT INTO dms_agent(user_id,agent_code,agent_name,agent_level,level_depth,invite_code,
                    id_card,bank_account,status,source_type)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, 980002L, "ENC980002", "历史明文会员", 1, 1, "ENC98002",
                "430102199002022345", "6222020202020202021", 1, 3);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM dms_agent WHERE agent_code='ENC980002'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO dms_withdraw_record(withdraw_no,agent_id,user_id,withdraw_amount,bank_account,status)
                VALUES('ENC-W-980002',?,?,100,'6222020202020202022',0)
                """, id, 980002L);
        jdbcTemplate.update("""
                INSERT INTO dms_merchant(tenant_id,merchant_no,merchant_name,bank_account_no)
                VALUES(1,'ENC-M-980002','历史明文商户','6222020202020202023')
                """);
        Long merchantId = jdbcTemplate.queryForObject(
                "SELECT id FROM dms_merchant WHERE merchant_no='ENC-M-980002'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO dms_merchant_withdrawal(tenant_id,withdrawal_no,merchant_id,
                    bank_account_no_snapshot,requested_amount)
                VALUES(1,'ENC-MW-980002',?,'6222020202020202024',100)
                """, merchantId);

        migrator.run(null);

        String stored = jdbcTemplate.queryForObject(
                "SELECT id_card FROM dms_agent WHERE id=?", String.class, id);
        assertTrue(stored.startsWith(EncryptedStringTypeHandler.PREFIX));
        DmsAgent migrated = agentDao.selectById(id);
        assertEquals("430102199002022345", migrated.getIdCard());
        assertEquals("6222020202020202021", migrated.getBankAccount());
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT bank_account FROM dms_withdraw_record WHERE withdraw_no='ENC-W-980002'", String.class)
                .startsWith(EncryptedStringTypeHandler.PREFIX));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT bank_account_no FROM dms_merchant WHERE id=?", String.class, merchantId)
                .startsWith(EncryptedStringTypeHandler.PREFIX));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT bank_account_no_snapshot FROM dms_merchant_withdrawal WHERE withdrawal_no='ENC-MW-980002'",
                String.class).startsWith(EncryptedStringTypeHandler.PREFIX));
    }
}
