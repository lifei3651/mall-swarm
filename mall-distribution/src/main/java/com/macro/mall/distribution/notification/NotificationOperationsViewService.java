package com.macro.mall.distribution.notification;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsMessageCostBudgetDao;
import com.macro.mall.distribution.dao.DmsMessageDeliveryAttemptDao;
import com.macro.mall.distribution.dao.DmsMessageDeliveryTaskDao;
import com.macro.mall.distribution.entity.DmsMessageCostBudget;
import com.macro.mall.distribution.entity.DmsMessageDeliveryAttempt;
import com.macro.mall.distribution.entity.DmsMessageDeliveryTask;
import com.macro.mall.distribution.vo.NotificationRuntimeStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationOperationsViewService {
    private final DmsMessageCostBudgetDao budgetDao;
    private final DmsMessageDeliveryAttemptDao attemptDao;
    private final DmsMessageDeliveryTaskDao taskDao;
    private final ExternalNotificationProperties external;
    private final AliyunNotificationSmsProperties sms;

    public List<DmsMessageCostBudget> budgets() { return budgetDao.selectList(TenantContext.getTenantId()); }
    public List<DmsMessageDeliveryAttempt> attempts(Long taskId) {
        DmsMessageDeliveryTask task=taskDao.selectById(taskId);
        if (task==null||!TenantContext.getTenantId().equals(task.getTenantId())) Asserts.fail("发送任务不存在");
        return attemptDao.selectByTask(TenantContext.getTenantId(),taskId);
    }
    public NotificationRuntimeStatusVO runtime() {
        NotificationRuntimeStatusVO view=new NotificationRuntimeStatusVO();
        view.setExternalEnabled(external.isEnabled()); view.setWorkerEnabled(external.isWorkerEnabled());
        view.setSmsStatus(!external.isEnabled()?"关闭（默认安全状态）":sms.isEnabled()?"配置门禁已通过，仍需预算与用户授权":"等待客户短信账号和审核模板");
        view.setAppPushStatus("仅有统一内核模拟适配器，未选择真实供应商");
        view.setMiniProgramStatus("仅有统一内核模拟适配器，未创建小程序前端");
        view.setBudgetStatus("租户、事件、渠道三层日/月上限均需大于零且未超额");
        view.setAuthorizationStatus("每位用户对应渠道必须存在有效授权和合格终端摘要");
        return view;
    }
}
