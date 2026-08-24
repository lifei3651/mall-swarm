package com.macro.mall.distribution.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsMemberMessageDao;
import com.macro.mall.distribution.dao.DmsMessageChannelConfigDao;
import com.macro.mall.distribution.dao.DmsMessageDeliveryTaskDao;
import com.macro.mall.distribution.dao.DmsMessageTemplateDao;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.service.*;
import com.macro.mall.distribution.vo.MessageUnreadCountVO;
import com.macro.mall.distribution.vo.MessageUnreadSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberMessageServiceImpl implements MemberMessageService {
    private static final Set<String> CATEGORIES = Set.of("ORDER_LOGISTICS", "AFTER_SALE_REFUND",
            "WALLET_FUNDS", "ACCOUNT_SECURITY", "SERVICE");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{12,19}(?!\\d)");
    private static final Pattern FULL_AMOUNT = Pattern.compile("(?:[￥¥$]\\s*\\d|\\d+(?:\\.\\d{1,2})?\\s*元)");
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{|\\{\\{|\\}\\}");
    private static final Pattern VERIFICATION_CODE = Pattern.compile("(?:验证码|校验码|动态码)\\D{0,6}\\d{4,8}");
    private final MemberMessageWriter writer;
    private final DmsMemberMessageDao messageDao;
    private final DmsMessageTemplateDao templateDao;
    private final DmsMessageChannelConfigDao channelDao;
    private final DmsMessageDeliveryTaskDao deliveryDao;
    private final OperationLogService operationLogService;

    @Override
    public void publish(MemberMessageEvent event) {
        Runnable safeWrite = () -> {
            try { writer.write(event); }
            catch (RuntimeException ex) {
                // 消息是业务旁路，任何通道或消息失败都不能回滚订单、售后或资金事实。
                log.error("MEMBER_MESSAGE_WRITE_FAILED eventType={} eventKey={}",
                        event == null ? null : event.eventType(), event == null ? null : event.eventKey());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safeWrite.run(); }
            });
        } else safeWrite.run();
    }

    @Override
    public CommonPage<DmsMemberMessage> list(DmsShopMember member, String category, int pageNum, int pageSize) {
        requireMember(member); String safeCategory = optionalCategory(category);
        PageHelper.startPage(Math.max(1, pageNum), Math.min(100, Math.max(1, pageSize)));
        return CommonPage.restPage(messageDao.selectPage(TenantContext.getTenantId(), member.getId(), safeCategory));
    }

    @Override
    public DmsMemberMessage detail(DmsShopMember member, Long id) {
        requireMember(member);
        DmsMemberMessage message = messageDao.selectOwned(TenantContext.getTenantId(), member.getId(), id);
        if (message == null) Asserts.fail("消息不存在或无权查看");
        messageDao.markRead(TenantContext.getTenantId(), member.getId(), id);
        return messageDao.selectOwned(TenantContext.getTenantId(), member.getId(), id);
    }

    @Override
    public MessageUnreadSummaryVO unread(DmsShopMember member) {
        requireMember(member); MessageUnreadSummaryVO result = new MessageUnreadSummaryVO();
        for (String category : CATEGORIES) result.getCategories().put(category, 0L);
        for (MessageUnreadCountVO row : messageDao.countUnreadByCategory(TenantContext.getTenantId(), member.getId())) {
            if (row != null && CATEGORIES.contains(row.getCategory())) {
                long count = row.getUnreadCount() == null ? 0L : row.getUnreadCount();
                result.getCategories().put(row.getCategory(), count); result.setTotal(result.getTotal() + count);
            }
        }
        return result;
    }

    @Override public boolean markRead(DmsShopMember member, Long id) {
        requireMember(member);
        if (messageDao.selectOwned(TenantContext.getTenantId(), member.getId(), id) == null) Asserts.fail("消息不存在或无权操作");
        messageDao.markRead(TenantContext.getTenantId(), member.getId(), id); return true;
    }
    @Override public int markAllRead(DmsShopMember member, String category) {
        requireMember(member); return messageDao.markAllRead(TenantContext.getTenantId(), member.getId(), optionalCategory(category));
    }
    @Override public List<DmsMessageTemplate> listTemplates() { return templateDao.selectList(TenantContext.getTenantId()); }
    @Override public DmsMessageTemplate updateTemplate(Long id, DmsMessageTemplate input) {
        DmsMessageTemplate current = templateDao.selectById(TenantContext.getTenantId(), id);
        if (current == null) Asserts.fail("消息模板不存在");
        if (input == null || !hasText(input.getTitleTemplate()) || !hasText(input.getSummaryTemplate()) || !hasText(input.getContentTemplate())) Asserts.fail("模板内容不能为空");
        validatePrivacySafeTemplate(input.getTitleTemplate(), input.getSummaryTemplate(), input.getContentTemplate());
        current.setTitleTemplate(input.getTitleTemplate().trim()); current.setSummaryTemplate(input.getSummaryTemplate().trim()); current.setContentTemplate(input.getContentTemplate().trim());
        current.setEnabled(Integer.valueOf(0).equals(input.getEnabled()) ? 0 : 1); templateDao.update(current);
        operationLogService.log("MESSAGE", "TEMPLATE_UPDATED", "MESSAGE_TEMPLATE", String.valueOf(id), null, "version+1", "更新未来消息模板，不改历史消息");
        return templateDao.selectById(TenantContext.getTenantId(), id);
    }
    @Override public List<DmsMessageChannelConfig> listChannels() { return channelDao.selectList(TenantContext.getTenantId()); }
    @Override public DmsMessageChannelConfig updateInAppChannel(Long id, boolean enabled) {
        DmsMessageChannelConfig current = channelDao.selectById(TenantContext.getTenantId(), id);
        if (current == null) Asserts.fail("消息渠道配置不存在");
        channelDao.updateInApp(TenantContext.getTenantId(), id, enabled ? 1 : 0);
        operationLogService.log("MESSAGE", "CHANNEL_UPDATED", "MESSAGE_CHANNEL", String.valueOf(id), null, "inApp=" + enabled + ";external=false", "外部渠道继续保持关闭");
        return channelDao.selectById(TenantContext.getTenantId(), id);
    }
    @Override public CommonPage<DmsMessageDeliveryTask> listDeliveries(String channel, String status, int pageNum, int pageSize) {
        PageHelper.startPage(Math.max(1, pageNum), Math.min(100, Math.max(1, pageSize)));
        return CommonPage.restPage(deliveryDao.selectList(TenantContext.getTenantId(), safeFilter(channel), safeFilter(status)));
    }
    private void requireMember(DmsShopMember member) { if (member == null || member.getId() == null) Asserts.unauthorized("请先登录"); }
    private String optionalCategory(String category) { if (!hasText(category)) return null; if (!CATEGORIES.contains(category)) Asserts.fail("消息分类不正确"); return category; }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String safeFilter(String value) { return hasText(value) && value.matches("[A-Z_]{2,24}") ? value : null; }
    private void validatePrivacySafeTemplate(String... fields) {
        String joined = String.join(" ", fields);
        if (PHONE.matcher(joined).find() || BANK_CARD.matcher(joined).find()
                || FULL_AMOUNT.matcher(joined).find() || TEMPLATE_VARIABLE.matcher(joined).find()
                || VERIFICATION_CODE.matcher(joined).find()) {
            Asserts.fail("消息模板不得包含完整金额、手机号、银行卡号、验证码或动态变量");
        }
    }
}
