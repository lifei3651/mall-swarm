package com.macro.mall.distribution.config;

import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsErpIntegrationDao;
import com.macro.mall.distribution.dao.DmsMerchantDao;
import com.macro.mall.distribution.dao.DmsMerchantWithdrawalDao;
import com.macro.mall.distribution.dao.DmsWithdrawRecordDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsErpIntegration;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.entity.DmsMerchantWithdrawal;
import com.macro.mall.distribution.entity.DmsWithdrawRecord;
import com.macro.mall.distribution.security.EncryptedStringTypeHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** 启动后分批把历史明文敏感字段改写为版本化密文；中断后可安全重试。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "security.data-encryption", name = "migrate-on-startup",
        havingValue = "true", matchIfMissing = true)
public class SensitiveDataEncryptionMigrator implements ApplicationRunner {

    private static final int BATCH_SIZE = 200;

    private final DmsAgentDao agentDao;
    private final DmsErpIntegrationDao erpIntegrationDao;
    private final DmsWithdrawRecordDao withdrawRecordDao;
    private final DmsMerchantDao merchantDao;
    private final DmsMerchantWithdrawalDao merchantWithdrawalDao;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!EncryptedStringTypeHandler.isWriteEnabled()) {
            log.info("敏感字段加密写入尚未启用，跳过历史明文迁移（用于既有环境两阶段安全发布）");
            return;
        }
        if (!EncryptedStringTypeHandler.isKeyConfigured()) {
            log.info("未配置敏感字段落库加密密钥，跳过历史明文迁移（生产环境启动门禁会拒绝该状态）");
            return;
        }
        int agents = migrateAgents();
        int erpIntegrations = migrateErpIntegrations();
        int withdrawals = migrateWithdrawals();
        int merchants = migrateMerchants();
        int merchantWithdrawals = migrateMerchantWithdrawals();
        if (agents > 0 || erpIntegrations > 0 || withdrawals > 0 || merchants > 0 || merchantWithdrawals > 0) {
            log.info("历史敏感字段加密迁移完成：代理资料={}，ERP配置={}，会员提现={}，商户资料={}，商户提现={}",
                    agents, erpIntegrations, withdrawals, merchants, merchantWithdrawals);
        }
    }

    private int migrateAgents() {
        int total = 0;
        while (true) {
            Integer updated = transactionTemplate.execute(status -> {
                List<DmsAgent> rows = agentDao.selectSensitivePlaintextCandidates(BATCH_SIZE);
                int count = 0;
                for (DmsAgent row : rows) {
                    count += agentDao.encryptSensitiveFields(row.getId(), row.getIdCard(), row.getBankAccount());
                }
                return count;
            });
            int count = updated == null ? 0 : updated;
            if (count == 0) return total;
            total += count;
        }
    }

    private int migrateErpIntegrations() {
        int total = 0;
        while (true) {
            Integer updated = transactionTemplate.execute(status -> {
                List<DmsErpIntegration> rows = erpIntegrationDao.selectSensitivePlaintextCandidates(BATCH_SIZE);
                int count = 0;
                for (DmsErpIntegration row : rows) {
                    count += erpIntegrationDao.encryptSensitiveFields(row.getId(), row.getAppSecret(), row.getCallbackToken());
                }
                return count;
            });
            int count = updated == null ? 0 : updated;
            if (count == 0) return total;
            total += count;
        }
    }

    private int migrateWithdrawals() {
        int total = 0;
        while (true) {
            Integer updated = transactionTemplate.execute(status -> {
                List<DmsWithdrawRecord> rows = withdrawRecordDao.selectSensitivePlaintextCandidates(BATCH_SIZE);
                int count = 0;
                for (DmsWithdrawRecord row : rows) {
                    count += withdrawRecordDao.encryptSensitiveFields(row.getId(), row.getBankAccount());
                }
                return count;
            });
            int count = updated == null ? 0 : updated;
            if (count == 0) return total;
            total += count;
        }
    }

    private int migrateMerchants() {
        int total = 0;
        while (true) {
            Integer updated = transactionTemplate.execute(status -> {
                List<DmsMerchant> rows = merchantDao.selectSensitivePlaintextCandidates(BATCH_SIZE);
                int count = 0;
                for (DmsMerchant row : rows) {
                    count += merchantDao.encryptSensitiveFields(row.getId(), row.getBankAccountNo());
                }
                return count;
            });
            int count = updated == null ? 0 : updated;
            if (count == 0) return total;
            total += count;
        }
    }

    private int migrateMerchantWithdrawals() {
        int total = 0;
        while (true) {
            Integer updated = transactionTemplate.execute(status -> {
                List<DmsMerchantWithdrawal> rows = merchantWithdrawalDao.selectSensitivePlaintextCandidates(BATCH_SIZE);
                int count = 0;
                for (DmsMerchantWithdrawal row : rows) {
                    count += merchantWithdrawalDao.encryptSensitiveFields(row.getId(), row.getBankAccountNoSnapshot());
                }
                return count;
            });
            int count = updated == null ? 0 : updated;
            if (count == 0) return total;
            total += count;
        }
    }
}
