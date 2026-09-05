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

    @Test
    void permanentFailureIsVisibleAndCanOnlyBeRequeuedOnceByItsTenantAndRevision() {
        shippingTaskDao.enqueue(31L, "PAY-REQUEUE-1", 9002L);
        var task=shippingTaskDao.listScoped(31L,null,0,20).get(0);
        shippingTaskDao.claim(task.getId(),"retry-worker",LocalDateTime.now(),LocalDateTime.now().plusMinutes(1));
        shippingTaskDao.markPermanent(task.getId(),"retry-worker",1,"EXPRESS_COMPANY_NOT_FOUND",LocalDateTime.now());
        assertEquals(1,shippingTaskDao.countScoped(31L,"PERMANENT"));
        assertEquals(0,shippingTaskDao.countScoped(32L,"PERMANENT"));
        assertEquals(0,shippingTaskDao.retryPermanent(32L,task.getId(),1));
        assertEquals(0,shippingTaskDao.retryPermanent(31L,task.getId(),2));
        assertEquals(1,shippingTaskDao.retryPermanent(31L,task.getId(),1));
        assertEquals(0,shippingTaskDao.retryPermanent(31L,task.getId(),1));
        var refreshed=shippingTaskDao.selectScoped(31L,task.getId());
        assertEquals("PENDING",refreshed.getStatus());assertEquals(2,refreshed.getRevision());assertEquals(0,refreshed.getAttemptCount());
        assertEquals(0,shippingTaskDao.countScoped(31L,"PERMANENT"));
    }

    @Test
    void newRefundRevisionReopensSuccessfulTaskAndOldWorkerCannotLoseNewRevision() {
        shippingTaskDao.enqueue(41L,"PAY-REFUND-REVISION",9002L);
        var task=shippingTaskDao.listScoped(41L,null,0,20).get(0);
        LocalDateTime now=LocalDateTime.now();
        assertEquals(1,shippingTaskDao.claim(task.getId(),"worker1",now,now.plusMinutes(1)));
        assertEquals(1,shippingTaskDao.markSuccess(task.getId(),"worker1",1,"digest1",now));
        assertEquals("SUCCESS",shippingTaskDao.selectScoped(41L,task.getId()).getStatus());
        // 退款完成时再次enqueue使早已成功的部分发货任务重新进入待处理。
        shippingTaskDao.enqueue(41L,"PAY-REFUND-REVISION",9002L);
        assertEquals("PENDING",shippingTaskDao.selectScoped(41L,task.getId()).getStatus());
        assertEquals(1,shippingTaskDao.claim(task.getId(),"worker2",now,now.plusMinutes(1)));
        // 旧版本仍在外部同步时发生下一次本地履约更新，旧结果不得覆盖新版本。
        shippingTaskDao.enqueue(41L,"PAY-REFUND-REVISION",9002L);
        assertEquals(1,shippingTaskDao.markSuccess(task.getId(),"worker2",2,"digest2",now));
        var changed=shippingTaskDao.selectScoped(41L,task.getId());
        assertEquals(3,changed.getRevision());assertEquals(2,changed.getSyncedRevision());assertEquals("PENDING",changed.getStatus());
        assertEquals(0,shippingTaskDao.markPermanent(task.getId(),"worker2",2,"OLD_ERROR",now));
        assertEquals(1,shippingTaskDao.claim(task.getId(),"worker3",now,now.plusMinutes(1)));
        assertEquals(1,shippingTaskDao.markSuccess(task.getId(),"worker3",3,"digest3",now));
        assertEquals("SUCCESS",shippingTaskDao.selectScoped(41L,task.getId()).getStatus());
        assertEquals(1,shippingTaskDao.countScoped(41L,null));
    }
}
