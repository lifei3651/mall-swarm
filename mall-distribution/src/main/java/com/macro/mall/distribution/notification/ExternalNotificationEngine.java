package com.macro.mall.distribution.notification;

import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@Slf4j
public class ExternalNotificationEngine {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> TERMINAL = Set.of("ACCEPTED","DELIVERED","PERMANENT","SUPPRESSED","EXPIRED");
    private final DmsMessageDeliveryTaskDao taskDao;
    private final DmsMessageDeliveryAttemptDao attemptDao;
    private final DmsMessageCostBudgetDao budgetDao;
    private final DmsMessageRecipientAuthorizationDao authorizationDao;
    private final ExternalNotificationProperties properties;
    private final Map<String, ExternalNotificationAdapter> adapters;
    private final TransactionTemplate transactions;
    private final String workerId = "worker-" + UUID.randomUUID();

    public ExternalNotificationEngine(DmsMessageDeliveryTaskDao taskDao, DmsMessageDeliveryAttemptDao attemptDao,
                                      DmsMessageCostBudgetDao budgetDao, DmsMessageRecipientAuthorizationDao authorizationDao,
                                      ExternalNotificationProperties properties, List<ExternalNotificationAdapter> adapters,
                                      PlatformTransactionManager transactionManager) {
        this.taskDao=taskDao; this.attemptDao=attemptDao; this.budgetDao=budgetDao; this.authorizationDao=authorizationDao;
        this.properties=properties; this.transactions=new TransactionTemplate(transactionManager);
        Map<String,ExternalNotificationAdapter> byChannel=new HashMap<>();
        for (ExternalNotificationAdapter adapter:adapters) {
            if (byChannel.put(adapter.channel(),adapter)!=null) throw new IllegalStateException("同一通知渠道只能注册一个适配器");
        }
        this.adapters=Map.copyOf(byChannel);
    }

    @Scheduled(fixedDelayString = "${notification.external.scan-interval-ms:5000}", initialDelayString = "${notification.external.initial-delay-ms:15000}")
    public void scheduledScan() { runOnce(); }

    public int runOnce() {
        if (!properties.isEnabled() || !properties.isWorkerEnabled()) return 0;
        LocalDateTime now=LocalDateTime.now(BUSINESS_ZONE);
        List<Long> candidates=taskDao.selectDueIds(now, Math.max(1,Math.min(properties.getBatchSize(),100)));
        int claimed=0;
        for (Long id:candidates) {
            if (id==null || taskDao.claim(id,workerId,now,now.plusSeconds(leaseSeconds()))!=1) continue;
            claimed++;
            try { processClaimed(id); }
            catch (RuntimeException ignored) {
                // 不记录手机号、消息正文、供应商原始异常；租约到期后由恢复流程接管。
                log.error("EXTERNAL_NOTIFICATION_PROCESS_FAILED taskId={}",id);
            }
        }
        return claimed;
    }

    private void processClaimed(Long id) {
        DmsMessageDeliveryTask task=taskDao.selectById(id);
        ExternalNotificationContext context=taskDao.selectContext(id);
        if (task==null || TERMINAL.contains(task.getStatus())) return;
        if (context==null) { finalizeWithoutAttempt(task,"SUPPRESSED","MESSAGE_CONTEXT_MISSING"); return; }
        ExternalNotificationAdapter adapter=adapters.get(task.getChannel());
        if (adapter==null) { finalizeWithoutAttempt(task,"SUPPRESSED","ADAPTER_NOT_REGISTERED"); return; }
        LocalDateTime now=LocalDateTime.now(BUSINESS_ZONE);
        if (task.getExpiresAt()!=null && !task.getExpiresAt().isAfter(now)) { finalizeWithoutAttempt(task,"EXPIRED","TASK_EXPIRED"); return; }
        GateDecision gate=adapter.readiness(context);
        if (!gate.allowed()) { finalizeWithoutAttempt(task,"SUPPRESSED",gate.code()); return; }
        DmsMessageRecipientAuthorization authorization=authorizationDao.selectActive(task.getTenantId(),context.getMemberId(),task.getChannel(),now);
        if (!validAuthorization(context,authorization)) { finalizeWithoutAttempt(task,"SUPPRESSED","USER_AUTHORIZATION_OR_ENDPOINT_MISSING"); return; }
        DmsMessageDeliveryAttempt latest=attemptDao.selectLatest(task.getTenantId(),task.getId());
        if (latest!=null && ("SUBMITTED".equals(latest.getState()) || "UNKNOWN".equals(latest.getState()) || "ACCEPTED".equals(latest.getState()))) {
            recoverUnknown(task,context,adapter,latest); return;
        }
        if (latest!=null && "PREPARED".equals(latest.getState())) {
            markSubmitted(latest);
            applyResult(task,latest,adapter,adapter.send(context,latest.getIdempotencyKey()),false);
            return;
        }
        if (task.getAttemptCount()!=null && task.getMaxAttempts()!=null && task.getAttemptCount()>=task.getMaxAttempts()) {
            finalizeWithoutAttempt(task,"PERMANENT","MAX_ATTEMPTS_REACHED"); return;
        }
        PreparedAttempt prepared=prepareAttempt(task,context,adapter);
        if (prepared==null) return;
        markSubmitted(prepared.attempt());
        DeliveryResult result=adapter.send(context,prepared.attempt().getIdempotencyKey());
        applyResult(task,prepared.attempt(),adapter,result,false);
    }

    private PreparedAttempt prepareAttempt(DmsMessageDeliveryTask snapshot, ExternalNotificationContext context,
                                            ExternalNotificationAdapter adapter) {
        return transactions.execute(status -> {
            DmsMessageDeliveryTask task=taskDao.selectByIdForUpdate(snapshot.getId());
            if (task==null || !"SENDING".equals(task.getStatus()) || !workerId.equals(task.getLeaseOwner())) return null;
            BigDecimal cost=nonNegative(adapter.estimatedCost(context));
            String budgetFailure=checkBudgets(task,cost);
            if (budgetFailure!=null) {
                taskDao.markFinal(task.getId(),workerId,"SUPPRESSED",adapter.providerCode(),null,BigDecimal.ZERO,
                        budgetFailure,"费用硬上限未配置或已用尽",LocalDateTime.now(BUSINESS_ZONE));
                return null;
            }
            int attemptNo=(task.getAttemptCount()==null?0:task.getAttemptCount())+1;
            DmsMessageDeliveryAttempt attempt=new DmsMessageDeliveryAttempt();
            attempt.setTenantId(task.getTenantId()); attempt.setTaskId(task.getId()); attempt.setAttemptNo(attemptNo);
            attempt.setIdempotencyKey(task.getIdempotencyKey()+":"+attemptNo); attempt.setState("PREPARED");
            attempt.setProviderCode(adapter.providerCode()); attempt.setQueryCount(0); attempt.setEstimatedCost(cost);
            attempt.setActualCost(BigDecimal.ZERO); attempt.setSubmittedTime(LocalDateTime.now(BUSINESS_ZONE));
            attemptDao.insert(attempt); taskDao.incrementAttempt(task.getId(),workerId);
            return new PreparedAttempt(attempt);
        });
    }

    private void markSubmitted(DmsMessageDeliveryAttempt attempt) {
        transactions.executeWithoutResult(status -> attemptDao.updateResult(attempt.getTenantId(),attempt.getId(),"SUBMITTED",
                null,BigDecimal.ZERO,null,null,null));
        attempt.setState("SUBMITTED");
    }

    private String checkBudgets(DmsMessageDeliveryTask task, BigDecimal nextCost) {
        // 微信订阅消息等零成本官方通道不受短信费用预算阻断，但仍受授权、模板和总开关约束。
        if (nextCost == null || nextCost.signum() == 0) return null;
        List<BudgetKey> keys=List.of(new BudgetKey("TENANT","*"),new BudgetKey("EVENT",task.getEventType()),new BudgetKey("CHANNEL",task.getChannel()));
        LocalDate today=LocalDate.now(BUSINESS_ZONE);
        LocalDateTime dayStart=today.atStartOfDay();
        LocalDateTime monthStart=today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        for (BudgetKey key:keys) {
            DmsMessageCostBudget budget=budgetDao.selectForUpdate(task.getTenantId(),key.type(),key.key());
            if (budget==null || !Integer.valueOf(1).equals(budget.getEnabled()) || positive(budget.getDailyLimit())==null || positive(budget.getMonthlyLimit())==null)
                return "BUDGET_NOT_CONFIGURED_"+key.type();
            BigDecimal day=zero(budgetDao.sumReserved(task.getTenantId(),key.type(),key.key(),dayStart)).add(nextCost);
            if (day.compareTo(budget.getDailyLimit())>0) return "DAILY_BUDGET_EXCEEDED_"+key.type();
            BigDecimal month=zero(budgetDao.sumReserved(task.getTenantId(),key.type(),key.key(),monthStart)).add(nextCost);
            if (month.compareTo(budget.getMonthlyLimit())>0) return "MONTHLY_BUDGET_EXCEEDED_"+key.type();
        }
        return null;
    }

    private void recoverUnknown(DmsMessageDeliveryTask task, ExternalNotificationContext context,
                                ExternalNotificationAdapter adapter, DmsMessageDeliveryAttempt attempt) {
        if (attempt.getQueryCount()!=null && attempt.getQueryCount()>=Math.max(1,properties.getUnknownQueryLimit())) {
            transactions.executeWithoutResult(s -> {
                boolean wasAccepted="ACCEPTED".equals(attempt.getState());
                String finalState=wasAccepted?"ACCEPTED":"PERMANENT";
                String finalCode=wasAccepted?"DELIVERY_CONFIRMATION_TIMEOUT":"UNKNOWN_RESULT_REVIEW_REQUIRED";
                attemptDao.updateResult(task.getTenantId(),attempt.getId(),finalState,attempt.getProviderMessageId(),
                        BigDecimal.ZERO,finalCode,wasAccepted?"供应商已受理但未确认送达":"未知结果达到查询上限，禁止自动重发",LocalDateTime.now(BUSINESS_ZONE));
                taskDao.markFinal(task.getId(),workerId,finalState,adapter.providerCode(),attempt.getProviderMessageId(),
                        BigDecimal.ZERO,finalCode,wasAccepted?"供应商已受理但未确认送达":"未知结果需人工核对",LocalDateTime.now(BUSINESS_ZONE));
            });
            return;
        }
        attemptDao.incrementQuery(task.getTenantId(),attempt.getId());
        DeliveryResult result=adapter.query(context,attempt);
        applyResult(task,attempt,adapter,result,true);
    }

    private void applyResult(DmsMessageDeliveryTask task, DmsMessageDeliveryAttempt attempt,
                             ExternalNotificationAdapter adapter, DeliveryResult raw, boolean query) {
        DeliveryResult result=raw==null?DeliveryResult.unknown(null,"EMPTY_ADAPTER_RESULT"):raw;
        LocalDateTime now=LocalDateTime.now(BUSINESS_ZONE);
        transactions.executeWithoutResult(status -> {
            String code=safeCode(result.errorCode()); String message=safeMessage(result.safeErrorMessage());
            BigDecimal cost=nonNegative(result.actualCost());
            switch (result.state()) {
                case ACCEPTED,DELIVERED -> {
                    attemptDao.updateResult(task.getTenantId(),attempt.getId(),result.state().name(),result.providerMessageId(),cost,code,message,now);
                    taskDao.markFinal(task.getId(),workerId,result.state().name(),adapter.providerCode(),result.providerMessageId(),cost,code,message,now);
                    if (result.state()==DeliveryState.ACCEPTED) taskDao.scheduleAcceptedQuery(task.getId(),now.plusMinutes(5));
                }
                case RETRYABLE -> {
                    int retry=(task.getRetryCount()==null?0:task.getRetryCount())+1;
                    boolean exhausted=(task.getAttemptCount()==null?0:task.getAttemptCount())>=Math.max(1,task.getMaxAttempts()==null?properties.getMaxAttempts():task.getMaxAttempts());
                    attemptDao.updateResult(task.getTenantId(),attempt.getId(),exhausted?"PERMANENT":"RETRYABLE",result.providerMessageId(),cost,code,message,now);
                    if (exhausted) taskDao.markFinal(task.getId(),workerId,"PERMANENT",adapter.providerCode(),result.providerMessageId(),cost,"MAX_ATTEMPTS_REACHED","已达到最大尝试次数",now);
                    else taskDao.markRetryable(task.getId(),workerId,retry,now.plusSeconds(backoffSeconds(retry)),code,message);
                }
                case PERMANENT,SUPPRESSED,EXPIRED -> {
                    attemptDao.updateResult(task.getTenantId(),attempt.getId(),result.state().name(),result.providerMessageId(),cost,code,message,now);
                    taskDao.markFinal(task.getId(),workerId,result.state().name(),adapter.providerCode(),result.providerMessageId(),cost,code,message,now);
                }
                default -> {
                    attemptDao.updateResult(task.getTenantId(),attempt.getId(),"UNKNOWN",result.providerMessageId(),cost,code,message,null);
                    taskDao.markUnknown(task.getId(),workerId,adapter.providerCode(),result.providerMessageId(),code,message,now.plusSeconds(query?300:60));
                }
            }
        });
    }

    private void finalizeWithoutAttempt(DmsMessageDeliveryTask task,String state,String code) {
        taskDao.markFinal(task.getId(),workerId,state,null,null,BigDecimal.ZERO,safeCode(code),safeMessage(code),LocalDateTime.now(BUSINESS_ZONE));
    }
    boolean validAuthorization(ExternalNotificationContext context,DmsMessageRecipientAuthorization authorization) {
        if (authorization==null || authorization.getEndpointHash()==null || !authorization.getEndpointHash().matches("[a-f0-9]{64}")) return false;
        if (!"SMS".equals(context.getChannel())) return true;
        return DmsMessageRecipientAuthorization.SERVICE_SMS_CONSENT_VERSION.equals(authorization.getConsentVersion())
                && MessageDigest.isEqual(authorization.getEndpointHash().getBytes(StandardCharsets.US_ASCII),sha256(context.getPhone()).getBytes(StandardCharsets.US_ASCII));
    }
    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value==null?"":value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ignored) { throw new IllegalStateException("SHA-256不可用"); }
    }
    private long leaseSeconds() { return Math.max(30,Math.min(properties.getLeaseSeconds(),900)); }
    private long backoffSeconds(int retry) { long base=Math.max(1,properties.getBaseRetrySeconds()); long factor=1L<<Math.min(20,Math.max(0,retry-1)); return Math.min(Math.max(base,properties.getMaxRetrySeconds()),base*factor); }
    private BigDecimal nonNegative(BigDecimal value) { return value==null||value.signum()<0?BigDecimal.ZERO:value; }
    private BigDecimal zero(BigDecimal value) { return value==null?BigDecimal.ZERO:value; }
    private BigDecimal positive(BigDecimal value) { return value!=null&&value.signum()>0?value:null; }
    private String safeCode(String code) { if (code==null||code.isBlank()) return null; String safe=code.replaceAll("[^A-Za-z0-9_.-]","_"); return safe.substring(0,Math.min(64,safe.length())); }
    private String safeMessage(String message) { if (message==null||message.isBlank()) return null; String safe=message.replaceAll("1[3-9]\\d{9}|\\d{12,19}|\\d+(?:\\.\\d+)?元|验证码\\D{0,6}\\d{4,8}","[已脱敏]").replaceAll("[\\r\\n\\t]"," "); return safe.substring(0,Math.min(255,safe.length())); }
    private record BudgetKey(String type,String key) { }
    private record PreparedAttempt(DmsMessageDeliveryAttempt attempt) { }
}
