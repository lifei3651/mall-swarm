package com.macro.mall.distribution.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopServiceTicketDao;
import com.macro.mall.distribution.dao.DmsShopServiceTicketReplyDao;
import com.macro.mall.distribution.dto.ShopServiceTicketAdminActionDTO;
import com.macro.mall.distribution.dto.ShopServiceTicketCreateDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopServiceTicket;
import com.macro.mall.distribution.entity.DmsShopServiceTicketReply;
import com.macro.mall.distribution.service.ContentModerationService;
import com.macro.mall.distribution.service.MemberMessageEvent;
import com.macro.mall.distribution.service.MemberMessageService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.ShopServiceTicketService;
import com.macro.mall.distribution.util.MemberAccountUtils;
import com.macro.mall.distribution.vo.ShopServiceTicketDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopServiceTicketServiceImpl implements ShopServiceTicketService {
    private static final Set<String> TYPES = Set.of("CONSULTATION", "COMPLAINT", "AFTER_SALE_DISPUTE", "ACCOUNT", "OTHER");
    private static final Set<String> STATUSES = Set.of("OPEN", "PROCESSING", "WAITING_MEMBER", "RESOLVED", "CLOSED");
    private static final Set<String> ADMIN_NEXT_STATUSES = Set.of("PROCESSING", "WAITING_MEMBER", "RESOLVED", "CLOSED");
    private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DmsShopServiceTicketDao ticketDao;
    private final DmsShopServiceTicketReplyDao replyDao;
    private final DmsShopOrderDao orderDao;
    private final DmsShopAfterSaleDao afterSaleDao;
    private final DmsShopMemberDao memberDao;
    private final ContentModerationService contentModerationService;
    private final MemberMessageService memberMessageService;
    private final OperationLogService operationLogService;

    @Value("${shop.service-ticket.first-response-hours:24}")
    private int configuredFirstResponseHours;

    @Override
    public CommonPage<DmsShopServiceTicket> listMember(DmsShopMember member, String status, int pageNum, int pageSize) {
        requireMember(member);
        String safeStatus = optionalStatus(status);
        PageHelper.startPage(safePage(pageNum), safeSize(pageSize));
        CommonPage<DmsShopServiceTicket> page = CommonPage.restPage(
                ticketDao.selectOwnedList(tenantId(), member.getId(), safeStatus));
        page.getList().forEach(ticket -> hydrateTicket(ticket, member, false));
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopServiceTicketDetailVO create(DmsShopMember member, ShopServiceTicketCreateDTO input) {
        requireMember(member);
        DmsShopMember lockedMember = memberDao.selectByIdForUpdate(member.getId());
        if (lockedMember == null || !member.getUserId().equals(lockedMember.getUserId())
                || !Integer.valueOf(1).equals(lockedMember.getStatus())) Asserts.fail("会员账号不可用");
        if (ticketDao.countActiveOwned(tenantId(), member.getId()) >= 5) {
            Asserts.fail("您已有5个处理中工单，请先等待客服处理或关闭已解决工单");
        }
        String type = requiredType(input == null ? null : input.getType());
        String subject = requiredText("问题标题", input == null ? null : input.getSubject(), 100);
        String content = requiredText("问题说明", input == null ? null : input.getContent(), 1000);
        contentModerationService.assertAllowed("问题标题", subject);
        contentModerationService.assertAllowed("问题说明", content);

        DmsShopOrder order = null;
        DmsShopAfterSale afterSale = null;
        if ("ACCOUNT".equals(type) && (input.getOrderId() != null || input.getAfterSaleId() != null)) {
            Asserts.fail("账号问题不能关联订单或售后，请由平台客服处理");
        }
        Long orderId = input.getOrderId();
        if (input.getAfterSaleId() != null) {
            afterSale = afterSaleDao.selectByIdScoped(tenantId(), input.getAfterSaleId());
            if (afterSale == null || !member.getId().equals(afterSale.getMemberId())
                    || !member.getUserId().equals(afterSale.getUserId())) Asserts.fail("售后记录不存在或无权关联");
            if (orderId != null && !orderId.equals(afterSale.getOrderId())) Asserts.fail("订单与售后记录不匹配");
            orderId = afterSale.getOrderId();
            if (ticketDao.countActiveByAfterSale(tenantId(), member.getId(), afterSale.getId()) > 0) {
                Asserts.fail("该售后已有处理中客服工单，请在原工单继续沟通");
            }
        }
        if (orderId != null) {
            order = orderDao.selectByIdScoped(tenantId(), orderId);
            if (order == null || !member.getUserId().equals(order.getUserId())) Asserts.fail("订单不存在或无权关联");
        }
        if ("AFTER_SALE_DISPUTE".equals(type) && afterSale == null) Asserts.fail("售后争议必须选择一条售后记录");

        LocalDateTime now = LocalDateTime.now();
        DmsShopServiceTicket ticket = new DmsShopServiceTicket();
        ticket.setTicketNo(newTicketNo(now));
        ticket.setTenantId(tenantId());
        ticket.setMerchantId(order == null ? null : order.getMerchantId());
        ticket.setMemberId(member.getId());
        ticket.setUserId(member.getUserId());
        ticket.setType(type);
        ticket.setSubject(subject);
        ticket.setStatus("OPEN");
        ticket.setOrderId(order == null ? null : order.getId());
        ticket.setOrderNo(order == null ? null : order.getOrderNo());
        ticket.setAfterSaleId(afterSale == null ? null : afterSale.getId());
        ticket.setAfterSaleNo(afterSale == null ? null : afterSale.getAfterSaleNo());
        ticket.setLastReplyBy("MEMBER");
        ticket.setLastReplyTime(now);
        ticket.setFirstResponseDeadline(now.plusHours(firstResponseHours()));
        if (ticketDao.insert(ticket) != 1 || ticket.getId() == null) Asserts.fail("客服工单创建失败");
        insertReply(ticket, "MEMBER", member.getId(), "会员", content);
        return detail(ticket, member, false);
    }

    @Override
    public ShopServiceTicketDetailVO memberDetail(DmsShopMember member, Long id) {
        requireMember(member);
        DmsShopServiceTicket ticket = ticketDao.selectOwned(tenantId(), member.getId(), positiveId(id));
        if (ticket == null) Asserts.fail("客服工单不存在或无权查看");
        return detail(ticket, member, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopServiceTicketDetailVO memberReply(DmsShopMember member, Long id, String rawContent) {
        requireMember(member);
        String content = requiredText("回复内容", rawContent, 1000);
        contentModerationService.assertAllowed("回复内容", content);
        DmsShopServiceTicket ticket = ticketDao.selectOwnedForUpdate(tenantId(), member.getId(), positiveId(id));
        if (ticket == null) Asserts.fail("客服工单不存在或无权操作");
        if ("CLOSED".equals(ticket.getStatus())) Asserts.fail("已关闭工单不能继续回复，请重新提交工单");
        LocalDateTime now = LocalDateTime.now();
        insertReply(ticket, "MEMBER", member.getId(), "会员", content);
        if (ticketDao.updateAfterMemberReply(tenantId(), member.getId(), ticket.getId(), now) != 1) {
            Asserts.fail("工单状态已变化，请刷新后重试");
        }
        return memberDetail(member, ticket.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopServiceTicketDetailVO memberClose(DmsShopMember member, Long id) {
        requireMember(member);
        DmsShopServiceTicket ticket = ticketDao.selectOwnedForUpdate(tenantId(), member.getId(), positiveId(id));
        if (ticket == null) Asserts.fail("客服工单不存在或无权操作");
        if (!"CLOSED".equals(ticket.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            insertReply(ticket, "SYSTEM", null, "系统", "会员确认问题已解决并关闭工单");
            if (ticketDao.closeOwned(tenantId(), member.getId(), ticket.getId(), now) != 1) {
                Asserts.fail("工单状态已变化，请刷新后重试");
            }
        }
        return memberDetail(member, ticket.getId());
    }

    @Override
    public CommonPage<DmsShopServiceTicket> listAdmin(DmsAdminUser admin, String keyword, String status, String type,
                                                       int pageNum, int pageSize) {
        requireAdmin(admin);
        String safeKeyword = optionalText(keyword, 80);
        String safeStatus = optionalStatus(status);
        String safeType = optionalType(type);
        PageHelper.startPage(safePage(pageNum), safeSize(pageSize));
        CommonPage<DmsShopServiceTicket> page = CommonPage.restPage(
                ticketDao.selectAdminList(tenantId(), admin.getMerchantId(), safeKeyword, safeStatus, safeType));
        page.getList().forEach(ticket -> hydrateTicket(ticket, memberDao.selectById(ticket.getMemberId()), true));
        return page;
    }

    @Override
    public ShopServiceTicketDetailVO adminDetail(DmsAdminUser admin, Long id) {
        requireAdmin(admin);
        DmsShopServiceTicket ticket = ticketDao.selectAdmin(tenantId(), admin.getMerchantId(), positiveId(id));
        if (ticket == null) Asserts.fail("客服工单不存在或无权查看");
        return detail(ticket, memberDao.selectById(ticket.getMemberId()), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopServiceTicketDetailVO adminReply(DmsAdminUser admin, Long id, ShopServiceTicketAdminActionDTO input) {
        requireAdmin(admin);
        String content = requiredText("回复内容", input == null ? null : input.getContent(), 1000);
        contentModerationService.assertAllowed("回复内容", content);
        String nextStatus = normalize(input == null ? null : input.getNextStatus());
        if (!ADMIN_NEXT_STATUSES.contains(nextStatus)) Asserts.fail("请选择正确的下一处理状态");
        DmsShopServiceTicket ticket = ticketDao.selectAdminForUpdate(tenantId(), admin.getMerchantId(), positiveId(id));
        if (ticket == null) Asserts.fail("客服工单不存在或无权操作");
        if ("CLOSED".equals(ticket.getStatus())) Asserts.fail("已关闭工单不能继续回复");
        LocalDateTime now = LocalDateTime.now();
        String adminName = optionalText(admin.getNickname(), 64);
        if (adminName == null) adminName = optionalText(admin.getUsername(), 64);
        if (adminName == null) adminName = "商城客服";
        insertReply(ticket, "ADMIN", admin.getId(), adminName, content);
        if (ticketDao.updateAfterAdminReply(tenantId(), admin.getMerchantId(), ticket.getId(), nextStatus,
                admin.getId(), adminName, now) != 1) Asserts.fail("工单状态已变化，请刷新后重试");
        operationLogService.log("SERVICE_TICKET", "REPLY", "SERVICE_TICKET", String.valueOf(ticket.getId()),
                "status=" + ticket.getStatus(), "status=" + nextStatus, "客服回复会员工单");
        memberMessageService.publish(new MemberMessageEvent(tenantId(), ticket.getUserId(),
                "SERVICE_TICKET_UPDATED:" + ticket.getId() + ":" + now,
                "SERVICE_NOTICE", "SERVICE", "SERVICE_TICKET", ticket.getId(), null, now));
        return adminDetail(admin, ticket.getId());
    }

    private ShopServiceTicketDetailVO detail(DmsShopServiceTicket ticket, DmsShopMember member, boolean adminView) {
        hydrateTicket(ticket, member, adminView);
        List<DmsShopServiceTicketReply> replies = replyDao.selectByTicketId(tenantId(), ticket.getId());
        for (DmsShopServiceTicketReply reply : replies) {
            if ("MEMBER".equals(reply.getSenderType())) reply.setSenderLabel(adminView ? "会员" : "我");
            else if ("ADMIN".equals(reply.getSenderType())) reply.setSenderLabel(adminView ? reply.getSenderName() : "商城客服");
            else reply.setSenderLabel("系统");
        }
        ShopServiceTicketDetailVO result = new ShopServiceTicketDetailVO();
        result.setTicket(ticket);
        result.setReplies(replies);
        return result;
    }

    private void hydrateTicket(DmsShopServiceTicket ticket, DmsShopMember member, boolean adminView) {
        if (ticket == null) return;
        ticket.setMemberAccount(adminView ? MemberAccountUtils.maskAccount(MemberAccountUtils.display(member)) : null);
        ticket.setHandlerName(adminView ? ticket.getAssignedAdminName() : "商城客服");
        boolean overdue = ticket.getFirstResponseAt() == null && ticket.getFirstResponseDeadline() != null
                && ticket.getFirstResponseDeadline().isBefore(LocalDateTime.now()) && !"CLOSED".equals(ticket.getStatus());
        ticket.setFirstResponseOverdue(overdue);
        switch (ticket.getStatus()) {
            case "OPEN", "PROCESSING" -> {
                ticket.setNextActionParty("CUSTOMER_SERVICE");
                ticket.setNextActionHint(overdue ? "已超过首次响应目标，平台客服将优先处理" : "等待商城客服处理");
            }
            case "WAITING_MEMBER" -> {
                ticket.setNextActionParty("MEMBER");
                ticket.setNextActionHint("等待您补充信息或确认处理结果");
            }
            case "RESOLVED" -> {
                ticket.setNextActionParty("MEMBER");
                ticket.setNextActionHint("客服已给出处理结果；仍有问题可以继续回复");
            }
            default -> {
                ticket.setNextActionParty("NONE");
                ticket.setNextActionHint("工单已关闭");
            }
        }
    }

    private void insertReply(DmsShopServiceTicket ticket, String senderType, Long senderId,
                             String senderName, String content) {
        DmsShopServiceTicketReply reply = new DmsShopServiceTicketReply();
        reply.setTenantId(ticket.getTenantId());
        reply.setTicketId(ticket.getId());
        reply.setSenderType(senderType);
        reply.setSenderId(senderId);
        reply.setSenderName(senderName);
        reply.setContent(content);
        if (replyDao.insert(reply) != 1) Asserts.fail("工单回复保存失败");
    }

    private void requireMember(DmsShopMember member) {
        if (member == null || member.getId() == null || member.getUserId() == null) Asserts.unauthorized("请先登录");
        if (!Integer.valueOf(1).equals(member.getStatus())) Asserts.fail("会员账号不可用");
        if (Integer.valueOf(1).equals(member.getSystemAccount())) Asserts.fail("系统内部账户不能提交客服工单");
    }

    private void requireAdmin(DmsAdminUser admin) {
        if (admin == null || admin.getId() == null) Asserts.unauthorized("请先登录管理后台");
        if (!Integer.valueOf(1).equals(admin.getStatus())) Asserts.fail("后台账号不可用");
    }

    private Long tenantId() { return TenantContext.getTenantId(); }
    private int firstResponseHours() { return Math.max(1, Math.min(168, configuredFirstResponseHours)); }
    private int safePage(int page) { return Math.max(1, page); }
    private int safeSize(int size) { return Math.max(1, Math.min(100, size)); }
    private Long positiveId(Long id) { if (id == null || id <= 0) Asserts.fail("记录编号不正确"); return id; }
    private String newTicketNo(LocalDateTime now) {
        return "ST" + now.format(NUMBER_TIME) + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
    private String normalize(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    private String requiredType(String value) { String type = normalize(value); if (!TYPES.contains(type)) Asserts.fail("请选择正确的问题类型"); return type; }
    private String optionalType(String value) { if (value == null || value.isBlank()) return null; return requiredType(value); }
    private String optionalStatus(String value) { if (value == null || value.isBlank()) return null; String status = normalize(value); if (!STATUSES.contains(status)) Asserts.fail("工单状态不正确"); return status; }
    private String requiredText(String field, String value, int max) { String text = optionalText(value, max); if (text == null) Asserts.fail("请填写" + field); return text; }
    private String optionalText(String value, int max) { if (value == null || value.isBlank()) return null; String text = value.trim(); if (text.length() > max) Asserts.fail("内容长度不能超过" + max + "字"); return text; }
}
