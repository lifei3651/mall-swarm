package com.macro.mall.distribution.service;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.ResultCode;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsWechatShippingSyncTaskDao;
import com.macro.mall.distribution.entity.DmsWechatShippingSyncTask;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.vo.WeChatShippingOperationsVO;
import com.macro.mall.distribution.vo.WeChatShippingTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class WeChatShippingOperationsService {
    private static final Set<String> STATUSES = Set.of("PENDING", "SENDING", "SUCCESS", "RETRYABLE", "PERMANENT");
    private final DmsWechatShippingSyncTaskDao taskDao;
    private final WeChatShippingInfoService shippingService;
    private final AdminAuthService adminAuthService;
    private final OperationLogService operationLogService;

    @Transactional(readOnly = true)
    public WeChatShippingOperationsVO list(String status, int pageNum, int pageSize) {
        Long tenantId = requirePlatformAdmin();
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) Asserts.fail("发货同步状态不正确");
        int size = Math.max(1, Math.min(100, pageSize));
        int page = Math.max(1, Math.min(100000, pageNum));
        boolean enabled = shippingService.ready();
        long total = taskDao.countScoped(tenantId, status);
        CommonPage<WeChatShippingTaskVO> result = new CommonPage<>();
        result.setPageNum(page); result.setPageSize(size); result.setTotal(total);
        result.setTotalPage((int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size));
        result.setList(taskDao.listScoped(tenantId, status, (page - 1) * size, size).stream()
                .map(task -> view(task, enabled)).toList());
        return new WeChatShippingOperationsVO(enabled, taskDao.countScoped(tenantId, "PERMANENT"), result);
    }

    @Transactional(rollbackFor = Exception.class)
    public void retry(Long id, Integer revision) {
        Long tenantId = requirePlatformAdmin();
        if (id == null || id <= 0 || revision == null || revision <= 0) Asserts.fail("任务编号或版本不正确");
        if (!shippingService.ready()) Asserts.fail("微信支付发货同步尚未开启，不能重新同步");
        DmsWechatShippingSyncTask task = taskDao.selectScoped(tenantId, id);
        if (task == null) Asserts.fail("发货同步任务不存在或无权查看");
        if (taskDao.retryPermanent(tenantId, id, revision) != 1) Asserts.fail("任务状态已变化，请刷新后再操作");
        // 只重建微信物流同步任务，不重放订单、退款、收款或用户消息，不直接调用外部接口。
        operationLogService.log("WECHAT_SHIPPING", "REQUEUE", "WECHAT_SHIPPING_TASK", String.valueOf(id),
                "status=PERMANENT;revision=" + revision, "status=PENDING;revision=" + (revision + 1),
                "管理员确认修复原因后重新同步既有发货信息");
    }

    private Long requirePlatformAdmin() {
        var admin = AdminContext.get();
        if (admin == null || admin.getMerchantId() != null) throw new ApiException(ResultCode.FORBIDDEN, "仅平台授权管理员可管理微信发货同步");
        adminAuthService.requirePermission(admin, "config:shop");
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new ApiException(ResultCode.FORBIDDEN, "客户上下文缺失");
        return tenantId;
    }

    private WeChatShippingTaskVO view(DmsWechatShippingSyncTask task, boolean enabled) {
        String number = task.getPaymentOrderNo() == null ? "" : task.getPaymentOrderNo();
        String hint = number.isBlank() ? "-" : "***" + number.substring(Math.max(0, number.length() - 6));
        String code = task.getErrorCode() == null ? "" : task.getErrorCode().replaceAll("[^A-Za-z0-9_.-]", "_");
        if (code.length() > 80) code = code.substring(0, 80);
        return new WeChatShippingTaskVO(String.valueOf(task.getId()), hint, task.getStatus(), task.getRevision(),
                task.getSyncedRevision(), task.getAttemptCount(), code, task.getNextRetryTime(),
                task.getSyncedTime(), task.getUpdateTime(), enabled && "PERMANENT".equals(task.getStatus()));
    }
}
