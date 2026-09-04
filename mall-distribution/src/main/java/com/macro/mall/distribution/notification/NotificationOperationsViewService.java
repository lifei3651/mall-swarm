package com.macro.mall.distribution.notification;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.dao.DmsMessageCostBudgetDao;
import com.macro.mall.distribution.dao.DmsMessageDeliveryAttemptDao;
import com.macro.mall.distribution.dao.DmsMessageDeliveryTaskDao;
import com.macro.mall.distribution.entity.DmsMessageCostBudget;
import com.macro.mall.distribution.entity.DmsMessageDeliveryAttempt;
import com.macro.mall.distribution.entity.DmsMessageDeliveryTask;
import com.macro.mall.distribution.service.WeChatSubscriptionService;
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
    private final ServiceSmsReadinessService serviceSmsReadinessService;
    private final WeChatMiniProgramProperties miniProgramProperties;
    private final WeChatPayProperties weChatPayProperties;
    private final WeChatSubscriptionService subscriptionService;

    public List<DmsMessageCostBudget> budgets() { return budgetDao.selectList(TenantContext.getTenantId()); }
    public List<DmsMessageDeliveryAttempt> attempts(Long taskId) {
        DmsMessageDeliveryTask task=taskDao.selectById(taskId);
        if (task==null||!TenantContext.getTenantId().equals(task.getTenantId())) Asserts.fail("发送任务不存在");
        return attemptDao.selectByTask(TenantContext.getTenantId(),taskId);
    }
    public NotificationRuntimeStatusVO runtime() {
        NotificationRuntimeStatusVO view=new NotificationRuntimeStatusVO();
        view.setExternalEnabled(external.isEnabled()); view.setWorkerEnabled(external.isWorkerEnabled());
        var readiness=serviceSmsReadinessService.evaluate(TenantContext.getTenantId());
        view.setServiceSmsReadiness(readiness);
        view.setSmsStatus(readiness.isReadyForMemberOptIn()?"已满足会员自主开启条件":"尚未开放（查看下方接入进度）");
        view.setAppPushStatus("仅有统一内核模拟适配器，未选择真实供应商");
        view.setMiniProgramStatus(miniProgramStatus());
        view.setBudgetStatus("租户、事件、渠道三层日/月上限均需大于零且未超额");
        view.setAuthorizationStatus("每位用户对应渠道必须存在有效授权和合格终端摘要");
        return view;
    }

    private String miniProgramStatus() {
        boolean subscriptionReady = subscriptionService.ready();
        boolean shippingReady = miniProgramProperties.shippingInfoReady() && weChatPayProperties.isConfigured();
        if (subscriptionReady && shippingReady) return "订阅提醒与微信支付发货同步均已就绪";
        if (subscriptionReady) return "订阅提醒已就绪；微信支付发货同步未开启";
        if (shippingReady) return "微信支付发货同步已就绪；订阅提醒未开启";
        if (!miniProgramProperties.loginReady()) return "待完成小程序正式密钥配置";
        if (!external.isEnabled() || !external.isWorkerEnabled()) return "小程序已接入；外部发送总门禁关闭";
        return "待填写并开启微信订阅模板";
    }
}
