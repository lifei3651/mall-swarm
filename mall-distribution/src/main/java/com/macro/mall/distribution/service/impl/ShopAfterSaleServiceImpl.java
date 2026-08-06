package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopSkuDao;
import com.macro.mall.distribution.dto.FinanceRefundDTO;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleApplyDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleAuditDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopAfterSaleItem;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.enums.AgentSourceTypeEnum;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.ShopAfterSaleService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import com.macro.mall.distribution.util.MemberAccountUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShopAfterSaleServiceImpl implements ShopAfterSaleService {

    private final DmsAgentDao agentDao;
    private final AgentService agentService;
    private final DmsShopAfterSaleDao afterSaleDao;
    private final DmsShopAfterSaleItemDao afterSaleItemDao;
    private final DmsShopOrderDao orderDao;
    private final DmsShopOrderItemDao orderItemDao;
    private final DmsShopProductDao productDao;
    private final DmsShopSkuDao skuDao;
    private final DmsShopMemberDao memberDao;
    private final DistributionAuditService auditService;
    private final MemberAssetService memberAssetService;
    private final OrderBalanceAllocationService orderBalanceAllocationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale apply(DmsShopMember member, ShopAfterSaleApplyDTO dto) {
        if (member == null) {
            Asserts.fail("请先登录");
        }
        if (dto == null || dto.getOrderId() == null) {
            Asserts.fail("订单ID不能为空");
        }
        DmsShopOrder order = orderDao.selectById(dto.getOrderId());
        if (order == null) {
            Asserts.fail("订单不存在");
        }
        assertTenantAccess(order.getTenantId());
        if (!member.getUserId().equals(order.getUserId())) {
            Asserts.fail("不能申请他人的订单售后");
        }
        if (Integer.valueOf(0).equals(order.getStatus()) || Integer.valueOf(4).equals(order.getStatus())) {
            Asserts.fail("当前订单状态不能申请售后");
        }
        if (afterSaleDao.selectOpenByOrderId(order.getId()) != null) {
            Asserts.fail("该订单已有处理中售后");
        }

        if (dto.getItems() == null || dto.getItems().isEmpty()) Asserts.fail("请选择实际退回的商品和数量");
        List<DmsShopOrderItem> orderItems = orderItemDao.selectByOrderId(order.getId());
        Map<Long, DmsShopOrderItem> byId = new LinkedHashMap<>();
        for (DmsShopOrderItem item : orderItems) byId.put(item.getId(), item);
        Map<Long, Integer> selected = new LinkedHashMap<>();
        dto.getItems().forEach(item -> {
            if (item == null || item.getOrderItemId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                Asserts.fail("退货商品和数量不正确");
            }
            selected.merge(item.getOrderItemId(), item.getQuantity(), Integer::sum);
        });

        int totalRemainingQuantity = 0;
        for (DmsShopOrderItem item : orderItems) {
            int reserved = afterSaleItemDao.sumReservedQuantityByOrderItemId(item.getId());
            totalRemainingQuantity += Math.max(0, item.getQuantity() - reserved);
        }
        List<DmsShopAfterSaleItem> refundItems = new ArrayList<>();
        int refundQuantity = 0;
        BigDecimal grossOrderAmount = orderItems.stream().map(DmsShopOrderItem::getTotalAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal productBase = nullToZero(order.getTotalAmount()).subtract(nullToZero(order.getDiscountAmount())).max(BigDecimal.ZERO);
        if (grossOrderAmount.compareTo(BigDecimal.ZERO) <= 0 || productBase.compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("订单商品金额异常，不能申请退款");
        }
        for (Map.Entry<Long, Integer> entry : selected.entrySet()) {
            DmsShopOrderItem source = byId.get(entry.getKey());
            if (source == null) Asserts.fail("退款商品不属于当前订单");
            int reserved = afterSaleItemDao.sumReservedQuantityByOrderItemId(source.getId());
            int remaining = Math.max(0, source.getQuantity() - reserved);
            if (entry.getValue() > remaining) Asserts.fail(source.getProductName() + "最多可退" + remaining + "件");
            DmsShopAfterSaleItem item = new DmsShopAfterSaleItem();
            item.setOrderId(order.getId()); item.setOrderItemId(source.getId());
            item.setProductId(source.getProductId()); item.setSkuId(source.getSkuId());
            item.setProductName(source.getProductName()); item.setSkuName(source.getSkuName());
            item.setRefundQuantity(entry.getValue());
            BigDecimal grossRefund = nullToZero(source.getTotalAmount())
                    .multiply(BigDecimal.valueOf(entry.getValue()))
                    .divide(BigDecimal.valueOf(source.getQuantity()), 8, java.math.RoundingMode.HALF_UP);
            item.setRefundAmount(grossRefund.multiply(productBase)
                    .divide(grossOrderAmount, 2, java.math.RoundingMode.HALF_UP));
            refundItems.add(item);
            refundQuantity += entry.getValue();
        }
        if (refundQuantity <= 0) Asserts.fail("请选择实际退回的商品数量");
        BigDecimal approvedProductRefund = nullToZero(afterSaleItemDao.sumApprovedProductRefundByOrderId(order.getId()));
        BigDecimal remainingProductRefund = productBase.subtract(approvedProductRefund).max(BigDecimal.ZERO);
        BigDecimal productRefund = refundItems.stream().map(DmsShopAfterSaleItem::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).min(remainingProductRefund);
        boolean refundAllRemaining = refundQuantity == totalRemainingQuantity;
        if (refundAllRemaining) productRefund = remainingProductRefund;
        BigDecimal allocated = refundItems.stream().map(DmsShopAfterSaleItem::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocationDifference = productRefund.subtract(allocated);
        DmsShopAfterSaleItem lastItem = refundItems.get(refundItems.size() - 1);
        lastItem.setRefundAmount(lastItem.getRefundAmount().add(allocationDifference));

        // 未发货且退完剩余全部商品时自动退还运费；一旦发货，原发货运费锁定为不可退。
        boolean notShipped = Integer.valueOf(1).equals(order.getStatus()) && order.getDeliveryTime() == null;
        BigDecimal freightRefund = notShipped && refundAllRemaining ? nullToZero(order.getFreightAmount()) : BigDecimal.ZERO;
        BigDecimal amount = productRefund.add(freightRefund).setScale(2, java.math.RoundingMode.HALF_UP);

        DmsShopAfterSale afterSale = new DmsShopAfterSale();
        afterSale.setAfterSaleNo(generateAfterSaleNo());
        afterSale.setOrderId(order.getId());
        afterSale.setOrderNo(order.getOrderNo());
        afterSale.setMemberId(member.getId());
        afterSale.setUserId(member.getUserId());
        afterSale.setApplyType(dto.getApplyType() == null ? 1 : dto.getApplyType());
        afterSale.setRefundAmount(amount);
        afterSale.setProductRefundAmount(productRefund);
        afterSale.setFreightRefundAmount(freightRefund);
        afterSale.setRefundQuantity(refundQuantity);
        afterSale.setReason(dto.getReason());
        afterSale.setProofImages(dto.getProofImages());
        afterSale.setStatus(0);
        afterSaleDao.insert(afterSale);
        for (DmsShopAfterSaleItem item : refundItems) item.setAfterSaleId(afterSale.getId());
        afterSaleItemDao.insertBatch(refundItems);
        return hydrate(afterSaleDao.selectById(afterSale.getId()));
    }

    @Override
    public List<DmsShopAfterSale> listByMember(DmsShopMember member) {
        return afterSaleDao.selectByMemberId(member.getId()).stream()
                .filter(this::canAccessAfterSale)
                .map(this::hydrate)
                .toList();
    }

    @Override
    public List<DmsShopAfterSale> listAdmin(String keyword, Integer status) {
        return afterSaleDao.selectList(keyword, status).stream()
                .filter(this::canAccessAfterSale)
                .map(this::hydrate)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale audit(Long id, ShopAfterSaleAuditDTO dto) {
        DmsShopAfterSale afterSale = afterSaleDao.selectByIdForUpdate(id);
        if (afterSale == null) {
            Asserts.fail("售后单不存在");
        }
        DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
        if (order == null) {
            Asserts.fail("订单不存在");
        }
        assertTenantAccess(order.getTenantId());
        if (!Integer.valueOf(0).equals(afterSale.getStatus())) {
            Asserts.fail("售后单已审核");
        }
        Integer status = dto == null || dto.getStatus() == null ? 1 : dto.getStatus();
        if (!Integer.valueOf(1).equals(status) && !Integer.valueOf(2).equals(status)
                && !Integer.valueOf(3).equals(status)) {
            Asserts.fail("审核状态不正确");
        }
        afterSale.setStatus(status);
        afterSale.setAuditRemark(dto == null ? null : dto.getAuditRemark());
        afterSale.setAuditUserId(dto == null ? null : dto.getAuditUserId());
        afterSale.setAuditUserName(dto == null ? null : dto.getAuditUserName());
        afterSaleDao.updateAudit(afterSale);

        if (Integer.valueOf(1).equals(status)) {
            FinanceRefundDTO refundDTO = new FinanceRefundDTO();
            refundDTO.setOrderId(afterSale.getOrderId());
            refundDTO.setOrderNo(afterSale.getOrderNo());
            refundDTO.setRefundAmount(afterSale.getRefundAmount());
            refundDTO.setProductRefundAmount(afterSale.getProductRefundAmount());
            refundDTO.setFreightRefundAmount(afterSale.getFreightRefundAmount());
            refundDTO.setRefundQuantity(afterSale.getRefundQuantity());
            refundDTO.setClawbackBonus(1);
            refundDTO.setReason("售后退款：" + afterSale.getReason());
            refundDTO.setOperatorId(afterSale.getAuditUserId());
            refundDTO.setOperatorName(afterSale.getAuditUserName());
            auditService.saveRefund(refundDTO);
            // 奖金冲减和账务重算完成后，再按新的净商品款/净成本冲回公司资金归集。
            orderBalanceAllocationService.recalculateAfterRefund(afterSale.getOrderId(), afterSale.getId());
            // 余额支付的退款原路退回商城余额；微信/支付宝退款由各自支付回调处理。
            if ("BALANCE".equalsIgnoreCase(order.getPayType())
                    && afterSale.getRefundAmount() != null
                    && afterSale.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
                AssetChangeDTO balanceRefund = new AssetChangeDTO();
                balanceRefund.setUserId(order.getUserId());
                balanceRefund.setAmount(afterSale.getRefundAmount());
                balanceRefund.setBizType("BALANCE_PAYMENT_REFUND");
                balanceRefund.setBizId(String.valueOf(afterSale.getId()));
                balanceRefund.setRemark("余额支付售后退款：" + afterSale.getAfterSaleNo());
                memberAssetService.issue(balanceRefund);
            }
            List<DmsShopAfterSaleItem> items = afterSaleItemDao.selectByAfterSaleId(afterSale.getId());
            if (Integer.valueOf(2).equals(afterSale.getApplyType())) {
                for (DmsShopAfterSaleItem item : items) {
                    if (item.getSkuId() != null) skuDao.increaseStock(item.getSkuId(), item.getRefundQuantity());
                    productDao.increaseStock(item.getProductId(), item.getRefundQuantity());
                }
            }
            int originalQuantity = orderItemDao.selectByOrderId(order.getId()).stream()
                    .map(DmsShopOrderItem::getQuantity).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
            if (afterSaleItemDao.sumApprovedQuantityByOrderId(order.getId()) >= originalQuantity) {
                orderDao.closeAfterSale(afterSale.getOrderId());
            }
            // 退款后退回非会员：名下已无有效支付订单时自动取消推广资格（含其下级团队自动移交）。
            autoDemoteMemberAfterFullRefund(order);
        }
        return hydrate(afterSaleDao.selectById(id));
    }

    /**
     * 退款后自动取消会员资格：仅处理“首单支付激活”的会员；名下还有其他有效支付订单
     * 或存在未结算奖金/欠款时保持不变，由后台人工处理，不阻塞本次退款。
     */
    private void autoDemoteMemberAfterFullRefund(DmsShopOrder order) {
        if (order == null || order.getUserId() == null) return;
        DmsAgent agent = agentDao.selectByUserId(order.getUserId());
        if (agent == null || !AgentSourceTypeEnum.SELF_REGISTER.getValue().equals(agent.getSourceType())) return;
        if (orderDao.countValidPaidOrdersByUserId(order.getUserId()) > 0) return;
        try {
            agentService.deactivate(agent.getId(),
                    "退款后退回非会员：名下已无有效支付订单，订单：" + order.getOrderNo());
            log.info("退款后自动取消会员资格: userId={}, orderNo={}", order.getUserId(), order.getOrderNo());
        } catch (ApiException e) {
            log.warn("退款后自动取消会员资格未执行: userId={}, reason={}", order.getUserId(), e.getMessage());
        }
    }

    private String generateAfterSaleNo() {
        return "AS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + IdUtil.getSnowflakeNextIdStr().substring(12);
    }

    private DmsShopAfterSale hydrate(DmsShopAfterSale afterSale) {
        if (afterSale != null) {
            afterSale.setItems(afterSaleItemDao.selectByAfterSaleId(afterSale.getId()));
            DmsShopMember member = memberDao.selectByUserId(afterSale.getUserId());
            afterSale.setMemberAccount(MemberAccountUtils.display(member));
        }
        return afterSale;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean canAccessAfterSale(DmsShopAfterSale afterSale) {
        if (afterSale == null || afterSale.getOrderId() == null) {
            return false;
        }
        DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
        return order != null && TenantContext.getTenantId().equals(order.getTenantId() == null ? 1L : order.getTenantId());
    }

    private void assertTenantAccess(Long tenantId) {
        Long dataTenantId = tenantId == null ? 1L : tenantId;
        if (!TenantContext.getTenantId().equals(dataTenantId)) {
            Asserts.fail("无权访问当前租户数据");
        }
    }
}
