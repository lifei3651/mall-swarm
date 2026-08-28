package com.macro.mall.distribution.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.log.SensitiveLogSanitizer;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsBonusCalculationSnapshotDao;
import com.macro.mall.distribution.dao.DmsBonusCalculationTaskDao;
import com.macro.mall.distribution.dao.DmsCommissionRecordDao;
import com.macro.mall.distribution.dao.DmsOrderFinanceDao;
import com.macro.mall.distribution.dao.DmsOrderRelationSnapshotDao;
import com.macro.mall.distribution.dao.DmsOrderPvDetailDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.DmsBonusCalculationSnapshot;
import com.macro.mall.distribution.entity.DmsBonusCalculationTask;
import com.macro.mall.distribution.entity.DmsCommissionRecord;
import com.macro.mall.distribution.entity.DmsOrderFinance;
import com.macro.mall.distribution.entity.DmsOrderPvDetail;
import com.macro.mall.distribution.entity.DmsOrderRelationSnapshot;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.BonusCalculationTaskService;
import com.macro.mall.distribution.service.CommissionService;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import com.macro.mall.distribution.util.MemberAccountUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BonusCalculationTaskServiceImpl implements BonusCalculationTaskService {

    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final int DEFAULT_RETRY_SECONDS = 60;

    private final DmsBonusCalculationTaskDao taskDao;
    private final DmsCommissionRecordDao commissionRecordDao;
    private final DmsOrderPvDetailDao orderPvDetailDao;
    private final DmsOrderFinanceDao orderFinanceDao;
    private final DmsOrderRelationSnapshotDao relationSnapshotDao;
    private final DmsBonusCalculationSnapshotDao snapshotDao;
    private final DmsShopMemberDao shopMemberDao;
    private final CommissionService commissionService;
    private final DistributionAuditService auditService;
    private final OrderBalanceAllocationService orderBalanceAllocationService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsBonusCalculationTask enqueue(Long tenantId,
                                           Long ruleVersionId,
                                           Long orderId,
                                           String orderNo,
                                           BigDecimal orderAmount,
                                           Long orderUserId,
                                           String orderUserName) {
        if (orderId == null) {
            Asserts.fail("订单ID不能为空");
        }
        if (orderNo == null || orderNo.isBlank()) {
            Asserts.fail("订单编号不能为空");
        }
        if (orderAmount == null) {
            Asserts.fail("订单金额不能为空");
        }
        if (orderUserId == null) {
            Asserts.fail("下单用户ID不能为空");
        }
        Long currentTenantId = TenantContext.getTenantId();
        if (tenantId != null && !currentTenantId.equals(tenantId)) {
            Asserts.fail("不能为其他商城客户创建奖金任务");
        }

        DmsBonusCalculationTask latestTask = taskDao.selectLatestByOrderId(orderId);
        if (latestTask != null) {
            return latestTask;
        }

        DmsBonusCalculationTask task = new DmsBonusCalculationTask();
        task.setTenantId(currentTenantId);
        task.setRuleVersionId(ruleVersionId);
        task.setOrderId(orderId);
        task.setOrderNo(orderNo);
        task.setOrderAmount(orderAmount);
        task.setOrderUserId(orderUserId);
        task.setOrderUserName(orderUserName);
        task.setStatus(0);
        task.setRetryCount(0);
        task.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        task.setNextRetryTime(LocalDateTime.now());
        try {
            taskDao.insert(task);
        } catch (DuplicateKeyException duplicate) {
            // 数据库唯一键是最终并发防线：两个支付线程同时入队时返回同一订单任务。
            DmsBonusCalculationTask existing = taskDao.selectLatestByOrderId(orderId);
            if (existing != null) return existing;
            throw duplicate;
        }
        return taskDao.selectById(task.getId());
    }

    @Override
    public List<DmsBonusCalculationTask> listTasks(Integer status, Long orderId) {
        List<DmsBonusCalculationTask> tasks = taskDao.selectList(status, orderId);
        for (DmsBonusCalculationTask task : tasks) {
            DmsShopMember member = shopMemberDao.selectByUserId(task.getOrderUserId());
            task.setOrderMemberAccount(MemberAccountUtils.display(member));
        }
        return tasks;
    }

    @Override
    public int processPendingTasks(Integer limit) {
        int batchSize = limit == null || limit <= 0 ? 20 : limit;
        List<DmsBonusCalculationTask> tasks = taskDao.selectExecutable(batchSize);
        int successCount = 0;
        for (DmsBonusCalculationTask task : tasks) {
            if (processTask(task.getId())) {
                successCount++;
            }
        }
        return successCount;
    }

    @Override
    public boolean processTask(Long taskId) {
        DmsBonusCalculationTask task = taskDao.selectById(taskId);
        if (task == null) {
            Asserts.fail("任务不存在");
        }
        if (!Integer.valueOf(0).equals(task.getStatus()) && !Integer.valueOf(3).equals(task.getStatus())) {
            return false;
        }
        if (task.getRetryCount() != null && task.getMaxRetryCount() != null
                && task.getRetryCount() >= task.getMaxRetryCount()) {
            return false;
        }
        if (taskDao.markProcessing(taskId) <= 0) {
            return false;
        }

        try {
            List<DmsCommissionRecord> oldRecords = commissionRecordDao.selectByOrderId(task.getOrderId());
            if (oldRecords.isEmpty()) {
                commissionService.calculateAndRecordCommission(
                        task.getTenantId(),
                        task.getOrderId(),
                        task.getOrderNo(),
                        task.getOrderAmount(),
                        task.getOrderUserId(),
                        task.getOrderUserName()
                );
            } else {
                auditService.refreshOrderFinance(task.getOrderId(), task.getOrderNo(), task.getOrderAmount());
                log.info("订单已存在佣金记录，跳过重复计算: orderId={}", task.getOrderId());
            }
            orderBalanceAllocationService.prepareForOrder(task.getOrderId());
            saveSnapshot(task);
            if (taskDao.markSuccess(taskId) != 1) {
                log.warn("奖金异步计算完成但任务状态已变化，不覆盖当前状态: taskId={}, orderId={}",
                        taskId, task.getOrderId());
                return false;
            }
            int commissionCount = commissionRecordDao.selectByOrderId(task.getOrderId()).size();
            log.info("奖金异步计算成功: taskId={}, orderId={}, orderNo={}, commissionCount={}",
                    taskId, task.getOrderId(), task.getOrderNo(), commissionCount);
            return true;
        } catch (Exception e) {
            log.error("奖金异步计算失败: taskId={}, orderId={}", taskId, task.getOrderId(), e);
            String failReason = safeFailureReason(e);
            if (failReason != null && failReason.length() > 500) {
                failReason = failReason.substring(0, 500);
            }
            if (taskDao.markFailed(taskId, failReason,
                    LocalDateTime.now().plusSeconds(DEFAULT_RETRY_SECONDS)) != 1) {
                log.warn("奖金异步计算失败但任务状态已变化，不覆盖当前状态: taskId={}, orderId={}",
                        taskId, task.getOrderId());
            }
            return false;
        }
    }

    static String safeFailureReason(Exception exception) {
        String message = SensitiveLogSanitizer.sanitizeText(exception == null ? null : exception.getMessage());
        return message == null || message.isBlank()
                ? (exception == null ? "UnknownFailure" : exception.getClass().getSimpleName())
                : message;
    }

    private void saveSnapshot(DmsBonusCalculationTask task) {
        // 同一订单只保留一份不可变的计算证据，任务重试不会覆盖或重复写入历史快照。
        if (!snapshotDao.selectByOrderId(task.getOrderId()).isEmpty()) {
            return;
        }
        List<DmsCommissionRecord> records = commissionRecordDao.selectByOrderId(task.getOrderId());
        List<DmsOrderPvDetail> pvDetails = orderPvDetailDao.selectByOrderId(task.getOrderId());
        List<DmsOrderRelationSnapshot> relationSnapshots = relationSnapshotDao.selectByOrderId(task.getOrderId());
        DmsOrderFinance finance = orderFinanceDao.selectByOrderId(task.getOrderId());

        BigDecimal totalBonus = records.stream()
                .map(DmsCommissionRecord::getCommissionAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPv = pvDetails.stream()
                .map(DmsOrderPvDetail::getTotalPv)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pvCost = pvDetails.stream()
                .map(DmsOrderPvDetail::getTotalCost)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal productCost = finance != null && finance.getProductCost() != null ? finance.getProductCost() : pvCost;
        BigDecimal companyShare = finance != null && finance.getCompanyShareAmount() != null ? finance.getCompanyShareAmount() : BigDecimal.ZERO;
        BigDecimal totalOut = totalBonus.add(productCost).add(companyShare);
        BigDecimal bonusPayoutRate = task.getOrderAmount().compareTo(BigDecimal.ZERO) > 0
                ? totalBonus.divide(task.getOrderAmount(), 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        // 基座只执行“总流出不得超过实付”的通用资金不变量。
        // 客户奖金比例、级差压缩和其他制度阈值必须由客户项目自行校验，不能引用历史示例制度。
        String riskStatus = totalOut.compareTo(task.getOrderAmount()) > 0 ? "BLOCK" : "PASS";

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("orderId", task.getOrderId());
        input.put("orderNo", task.getOrderNo());
        input.put("orderAmount", task.getOrderAmount());
        input.put("orderUserId", task.getOrderUserId());
        input.put("orderUserName", task.getOrderUserName());
        input.put("ruleVersionId", task.getRuleVersionId());
        input.put("relationSnapshot", relationSnapshots);
        input.put("pvDetails", pvDetails);

        List<Map<String, Object>> commissionDetails = new ArrayList<>();
        for (DmsCommissionRecord record : records) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("recordId", record.getId());
            detail.put("recordNo", record.getRecordNo());
            detail.put("agentId", record.getAgentId());
            detail.put("agentUserId", record.getAgentUserId());
            detail.put("agentName", record.getAgentName());
            detail.put("commissionLevel", record.getCommissionLevel());
            detail.put("commissionRate", record.getCommissionRate());
            detail.put("commissionAmount", record.getCommissionAmount());
            detail.put("ruleVersionId", record.getRuleVersionId());
            detail.put("bonusType", record.getBonusType());
            detail.put("status", record.getStatus());
            commissionDetails.add(detail);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("commissionRecords", commissionDetails);
        result.put("totalBonus", totalBonus);
        result.put("bonusPayoutRate", bonusPayoutRate);
        result.put("totalPv", totalPv);
        result.put("productCost", productCost);
        result.put("companyShare", companyShare);
        result.put("totalOut", totalOut);
        result.put("riskStatus", riskStatus);

        DmsBonusCalculationSnapshot snapshot = new DmsBonusCalculationSnapshot();
        snapshot.setTenantId(task.getTenantId());
        snapshot.setRuleVersionId(task.getRuleVersionId());
        snapshot.setOrderId(task.getOrderId());
        snapshot.setOrderNo(task.getOrderNo());
        snapshot.setInputJson(toJson(input));
        snapshot.setResultJson(toJson(result));
        snapshot.setTotalPv(totalPv);
        snapshot.setTotalBonus(totalBonus);
        snapshot.setRiskStatus(riskStatus);
        snapshotDao.insert(snapshot);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("奖金计算快照序列化失败", e);
        }
    }
}
