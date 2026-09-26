package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExternalRefundRecoverySelectionTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DmsShopAfterSaleDao afterSaleDao;

    @Test
    void selectsOldProcessingWechatAndAlipayOnlyWithinTenantAndLimit() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(2).withNano(0);
        insert(981001L, "WECHAT", 1L, 6, cutoff.minusMinutes(1));
        insert(981002L, "ALIPAY", 1L, 6, cutoff.minusMinutes(3));
        insert(981003L, "BALANCE", 1L, 6, cutoff.minusMinutes(1));
        insert(981004L, "ALIPAY", 1L, 6, cutoff.plusMinutes(1));
        insert(981005L, "ALIPAY", 1L, 1, cutoff.minusMinutes(1));
        insert(981006L, "ALIPAY", 2L, 6, cutoff.minusMinutes(1));

        List<Long> tenantOne = afterSaleDao.selectProcessingExternalRefundsScoped(1L, cutoff, null, null, 100)
                .stream().map(DmsShopAfterSale::getId).toList();
        assertThat(tenantOne).containsExactly(981002L, 981001L)
                .doesNotContain(981003L, 981004L, 981005L, 981006L);
        assertThat(afterSaleDao.selectProcessingExternalRefundsScoped(1L, cutoff, null, null, 1)
                .stream().map(DmsShopAfterSale::getId).toList()).containsExactly(981002L);
        assertThat(afterSaleDao.selectProcessingExternalRefundsScoped(1L, cutoff, cutoff.minusMinutes(3), 981002L, 100)
                .stream().map(DmsShopAfterSale::getId).toList()).containsExactly(981001L);
        assertThat(afterSaleDao.selectProcessingExternalRefundsScoped(2L, cutoff, null, null, 100)
                .stream().map(DmsShopAfterSale::getId).toList())
                .contains(981006L).doesNotContain(981001L, 981002L);
    }

    private void insert(long id, String payType, long tenantId, int saleStatus, LocalDateTime updatedAt) {
        jdbc.update("""
                INSERT INTO dms_shop_order
                    (id, order_no, tenant_id, user_id, receiver_name, receiver_phone,
                     receiver_address, pay_type, status)
                VALUES (?, ?, ?, 1001, '测试买家', '15500000000', '测试地址', ?, 1)
                """, id, "RECOVERY-ORDER-" + id, tenantId, payType);
        jdbc.update("""
                INSERT INTO dms_shop_after_sale
                    (id, after_sale_no, order_id, order_no, member_id, user_id,
                     apply_type, refund_amount, refund_quantity, status, update_time)
                VALUES (?, ?, ?, ?, 1, 1001, 4, 0.01, 1, ?, ?)
                """, id, "RECOVERY-SALE-" + id, id, "RECOVERY-ORDER-" + id,
                saleStatus, Timestamp.valueOf(updatedAt));
    }
}
