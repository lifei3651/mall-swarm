package com.macro.mall.distribution.notification;

import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.DmsMessageDeliveryAttempt;
import com.macro.mall.distribution.entity.DmsMessageRecipientAuthorization;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"notification.external.enabled=false","notification.external.worker-enabled=false"})
@ActiveProfiles("test")
class ExternalNotificationEngineIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DmsMessageDeliveryTaskDao taskDao;
    @Autowired DmsMessageDeliveryAttemptDao attemptDao;
    @Autowired DmsMessageCostBudgetDao budgetDao;
    @Autowired DmsMessageRecipientAuthorizationDao authorizationDao;
    @Autowired PlatformTransactionManager transactionManager;
    private ExternalNotificationProperties properties;
    private ScriptedAdapter adapter;
    private ExternalNotificationEngine engine;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM dms_message_delivery_receipt");
        jdbc.update("DELETE FROM dms_message_delivery_attempt");
        jdbc.update("DELETE FROM dms_message_delivery_task");
        jdbc.update("DELETE FROM dms_message_recipient_authorization");
        jdbc.update("DELETE FROM dms_member_message");
        jdbc.update("DELETE FROM dms_shop_member WHERE id=99001 OR user_id=99001");
        jdbc.update("INSERT INTO dms_shop_member(id,user_id,phone,login_account,password_hash,nickname,invite_code,status,system_account,team_opt_in) VALUES(99001,99001,'13900000001','notify_test','hash','通知测试','NTF00001',1,0,0)");
        jdbc.update("UPDATE dms_message_cost_budget SET enabled=1,daily_limit=100,monthly_limit=100 WHERE tenant_id=1");
        properties=new ExternalNotificationProperties(); properties.setEnabled(true); properties.setWorkerEnabled(true);
        properties.setBaseRetrySeconds(1); properties.setMaxRetrySeconds(8); properties.setUnknownQueryLimit(1);
        adapter=new ScriptedAdapter();
        engine=new ExternalNotificationEngine(taskDao,attemptDao,budgetDao,authorizationDao,properties,List.of(adapter),transactionManager);
    }

    @Test
    void smsAuthorizationRequiresCurrentConsentAndCurrentPhone() {
        ExternalNotificationContext context = new ExternalNotificationContext();
        context.setChannel("SMS");
        context.setPhone("13900000001");
        DmsMessageRecipientAuthorization authorization = new DmsMessageRecipientAuthorization();
        authorization.setEndpointHash(ExternalNotificationEngine.sha256(context.getPhone()));

        assertFalse(engine.validAuthorization(context, authorization));
        authorization.setConsentVersion(DmsMessageRecipientAuthorization.SERVICE_SMS_CONSENT_VERSION);
        assertTrue(engine.validAuthorization(context, authorization));
        context.setPhone("13900000002");
        assertFalse(engine.validAuthorization(context, authorization));
    }

    @Test
    void concurrentClaimAndStableTaskIdempotencyCallProviderOnce() throws Exception {
        authorize(); long taskId=createTask("PENDING",null);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first=pool.submit(engine::runOnce); Future<Integer> second=pool.submit(engine::runOnce);
            first.get(5,TimeUnit.SECONDS); second.get(5,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(1,adapter.sendCount.get());
        assertEquals("ACCEPTED",status(taskId));
        assertEquals(1,count("SELECT COUNT(*) FROM dms_message_delivery_attempt WHERE task_id=?",taskId));
        assertEquals(1,count("SELECT COUNT(*) FROM dms_message_delivery_task WHERE idempotency_key=?","1:"+messageId()+":APP_PUSH"));
    }

    @Test
    void retryUsesExponentialScheduleAndStopsAtAcceptedResult() {
        authorize(); adapter.sendResults.add(DeliveryResult.retryable("TEMPORARY")); adapter.sendResults.add(DeliveryResult.accepted("provider-2",BigDecimal.ZERO));
        long taskId=createTask("PENDING",null); engine.runOnce();
        assertEquals("RETRYABLE",status(taskId)); assertNotNull(jdbc.queryForObject("SELECT next_retry_time FROM dms_message_delivery_task WHERE id=?",LocalDateTime.class,taskId));
        jdbc.update("UPDATE dms_message_delivery_task SET next_retry_time=? WHERE id=?",Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)),taskId);
        engine.runOnce();
        assertEquals("ACCEPTED",status(taskId)); assertEquals(2,adapter.sendCount.get());
        assertEquals(2,count("SELECT COUNT(*) FROM dms_message_delivery_attempt WHERE task_id=?",taskId));
    }

    @Test
    void expiredLeaseQueriesUnknownResultAndNeverBlindlyResends() {
        authorize(); adapter.queryResult=DeliveryResult.delivered("provider-existing",BigDecimal.ZERO);
        long taskId=createTask("SENDING",LocalDateTime.now().minusMinutes(1));
        insertAttempt(taskId,"SUBMITTED","provider-existing",0); engine.runOnce();
        assertEquals("DELIVERED",status(taskId)); assertEquals(0,adapter.sendCount.get()); assertEquals(1,adapter.queryCount.get());
    }

    @Test
    void unknownWithoutProviderIdRequeriesThenRequiresReviewInsteadOfResend() {
        authorize(); adapter.queryResult=DeliveryResult.unknown(null,"NO_PROVIDER_ID");
        long taskId=createTask("SENDING",LocalDateTime.now().minusMinutes(1));
        insertAttempt(taskId,"UNKNOWN",null,0); engine.runOnce();
        jdbc.update("UPDATE dms_message_delivery_task SET lease_until=? WHERE id=?",Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)),taskId);
        engine.runOnce();
        assertEquals("PERMANENT",status(taskId)); assertEquals("UNKNOWN_RESULT_REVIEW_REQUIRED",errorCode(taskId));
        assertEquals(0,adapter.sendCount.get());
    }

    @Test
    void missingAuthorizationAndExceededAnyBudgetSuppressBeforeProviderCall() {
        long noAuth=createTask("PENDING",null); engine.runOnce();
        assertEquals("SUPPRESSED",status(noAuth)); assertEquals("USER_AUTHORIZATION_OR_ENDPOINT_MISSING",errorCode(noAuth));
        resetTaskTables(); authorize();
        jdbc.update("UPDATE dms_message_cost_budget SET daily_limit=0.005,monthly_limit=0.005 WHERE tenant_id=1 AND scope_type='CHANNEL' AND scope_key='APP_PUSH'");
        long overBudget=createTask("PENDING",null); engine.runOnce();
        assertEquals("SUPPRESSED",status(overBudget)); assertTrue(errorCode(overBudget).contains("BUDGET_EXCEEDED"));
        assertEquals(0,adapter.sendCount.get());
    }

    @Test
    void sensitiveProviderMessageIsRedactedFromTaskAndAttemptRecords() {
        authorize(); adapter.sendResults.add(new DeliveryResult(DeliveryState.PERMANENT,null,BigDecimal.ZERO,"REJECTED",
                "手机号13900000001 银行卡6222021234567890 验证码 123456 金额100.00元"));
        long taskId=createTask("PENDING",null); engine.runOnce();
        String taskError=jdbc.queryForObject("SELECT error_message FROM dms_message_delivery_task WHERE id=?",String.class,taskId);
        String attemptError=jdbc.queryForObject("SELECT error_message FROM dms_message_delivery_attempt WHERE task_id=?",String.class,taskId);
        for (String value:List.of(taskError,attemptError)) {
            assertFalse(value.contains("13900000001")); assertFalse(value.contains("6222021234567890")); assertFalse(value.contains("123456")); assertFalse(value.contains("100.00"));
        }
    }

    private void authorize() { jdbc.update("INSERT INTO dms_message_recipient_authorization(tenant_id,member_id,channel,endpoint_hash,authorized,authorized_time) VALUES(1,99001,'APP_PUSH',?,1,CURRENT_TIMESTAMP)","a".repeat(64)); }
    private long createTask(String status,LocalDateTime leaseUntil) {
        long message=insertMessage(); String key="1:"+message+":APP_PUSH";
        jdbc.update("INSERT INTO dms_message_delivery_task(tenant_id,message_id,event_type,channel,idempotency_key,status,retry_count,attempt_count,max_attempts,estimated_cost,actual_cost,lease_owner,lease_until,expires_at) VALUES(1,?,'SERVICE_NOTICE','APP_PUSH',?,?,0,0,5,0.01,0,NULL,?,?)",
                message,key,status,leaseUntil==null?null:Timestamp.valueOf(leaseUntil),Timestamp.valueOf(LocalDateTime.now().plusHours(1)));
        return jdbc.queryForObject("SELECT id FROM dms_message_delivery_task WHERE idempotency_key=?",Long.class,key);
    }
    private long insertMessage() {
        String eventKey="TEST:"+System.nanoTime();
        jdbc.update("INSERT INTO dms_member_message(tenant_id,member_id,user_id,event_key,event_type,category,title,summary,content,target_type,occurred_time) VALUES(1,99001,99001,?,'SERVICE_NOTICE','SERVICE','服务通知','登录后查看','登录后查看','NONE',CURRENT_TIMESTAMP)",eventKey);
        return jdbc.queryForObject("SELECT id FROM dms_member_message WHERE event_key=?",Long.class,eventKey);
    }
    private long messageId() { return jdbc.queryForObject("SELECT message_id FROM dms_message_delivery_task LIMIT 1",Long.class); }
    private void insertAttempt(long taskId,String state,String providerId,int queryCount) {
        jdbc.update("UPDATE dms_message_delivery_task SET attempt_count=1 WHERE id=?",taskId);
        jdbc.update("INSERT INTO dms_message_delivery_attempt(tenant_id,task_id,attempt_no,idempotency_key,state,provider_code,provider_message_id,query_count,estimated_cost,actual_cost,submitted_time) VALUES(1,?,1,? ,?,'TEST_ADAPTER',?,?,0.01,0,CURRENT_TIMESTAMP)",taskId,"attempt:"+taskId,state,providerId,queryCount);
    }
    private void resetTaskTables() { jdbc.update("DELETE FROM dms_message_delivery_attempt");jdbc.update("DELETE FROM dms_message_delivery_task");jdbc.update("DELETE FROM dms_member_message");jdbc.update("DELETE FROM dms_message_recipient_authorization"); }
    private String status(long id) { return jdbc.queryForObject("SELECT status FROM dms_message_delivery_task WHERE id=?",String.class,id); }
    private String errorCode(long id) { return jdbc.queryForObject("SELECT error_code FROM dms_message_delivery_task WHERE id=?",String.class,id); }
    private int count(String sql,Object value) { return jdbc.queryForObject(sql,Integer.class,value); }

    static class ScriptedAdapter implements ExternalNotificationAdapter {
        final AtomicInteger sendCount=new AtomicInteger(); final AtomicInteger queryCount=new AtomicInteger();
        final ConcurrentLinkedQueue<DeliveryResult> sendResults=new ConcurrentLinkedQueue<>();
        volatile DeliveryResult queryResult=DeliveryResult.unknown(null,"UNKNOWN");
        @Override public String channel(){return "APP_PUSH";} @Override public String providerCode(){return "TEST_ADAPTER";}
        @Override public GateDecision readiness(ExternalNotificationContext context){return GateDecision.allow();}
        @Override public BigDecimal estimatedCost(ExternalNotificationContext context){return new BigDecimal("0.0100");}
        @Override public DeliveryResult send(ExternalNotificationContext context,String key){sendCount.incrementAndGet();DeliveryResult next=sendResults.poll();return next==null?DeliveryResult.accepted("provider-1",BigDecimal.ZERO):next;}
        @Override public DeliveryResult query(ExternalNotificationContext context,DmsMessageDeliveryAttempt attempt){queryCount.incrementAndGet();return queryResult;}
    }
}
