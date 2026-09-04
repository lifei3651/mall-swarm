package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsMiniProgramSubscriptionGrantDao;
import com.macro.mall.distribution.dao.DmsWechatShippingSyncTaskDao;
import com.macro.mall.distribution.entity.DmsMiniProgramSubscriptionGrant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WeChatNotificationPersistenceIntegrationTest {
    @Autowired private DmsMiniProgramSubscriptionGrantDao grantDao;
    @Autowired private DmsWechatShippingSyncTaskDao shippingTaskDao;

    @Test
    void singleUseGrantReservationIsIdempotentAndAtomic() {
        DmsMiniProgramSubscriptionGrant grant = new DmsMiniProgramSubscriptionGrant();
        grant.setTenantId(1L); grant.setMemberId(9001L); grant.setUserId(9002L);
        grant.setTemplateIdHash("a".repeat(64)); grant.setClientRequestId("request_1234567890");
        grant.setAuthorizedTime(LocalDateTime.now());

        assertEquals(1, grantDao.insertIgnore(grant));
        assertEquals(0, grantDao.insertIgnore(grant));
        assertEquals(1, grantDao.countAvailable(1L, 9001L, "a".repeat(64)));
        var available = grantDao.selectAvailableForUpdate(1L, 9001L, "a".repeat(64));
        assertNotNull(available);
        assertEquals(1, grantDao.reserve(1L, available.getId(), 7001L));
        assertEquals(0, grantDao.reserve(1L, available.getId(), 7002L));
        assertEquals(1, grantDao.markConsumed(1L, 7001L));
        assertEquals(0, grantDao.countAvailable(1L, 9001L, "a".repeat(64)));
    }

    @Test
    void repeatedShipmentEnqueueAdvancesRevisionWithoutCreatingDuplicates() {
        assertEquals(1, shippingTaskDao.enqueue(1L, "PAY-INTEGRATION-1", 9002L));
        shippingTaskDao.enqueue(1L, "PAY-INTEGRATION-1", 9002L);
        var due = shippingTaskDao.selectDueIds(LocalDateTime.now(), 20);
        assertTrue(due.size() >= 1);
        Long taskId = due.stream().filter(id -> {
            var task = shippingTaskDao.selectById(id);
            return task != null && "PAY-INTEGRATION-1".equals(task.getPaymentOrderNo());
        }).findFirst().orElseThrow();
        var task = shippingTaskDao.selectById(taskId);
        assertEquals(2, task.getRevision());
        assertEquals(1, shippingTaskDao.claim(taskId, "integration-worker", LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(1)));
    }
}
