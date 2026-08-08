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
import com.macro.mall.distribution.dao.DmsShopServiceAddressDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopSkuDao;
import com.macro.mall.distribution.dto.FinanceRefundDTO;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleApplyDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleAuditDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleReturnShipmentDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleItemDTO;
import com.macro.mall.distribution.dto.ShopManualRefundDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopAfterSaleItem;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopServiceAddress;
import com.macro.mall.distribution.enums.AgentSourceTypeEnum;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.AlipayService;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.ShopAfterSaleService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import com.macro.mall.distribution.util.MemberAccountUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

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
    private final DmsShopServiceAddressDao serviceAddressDao;
    private final DmsShopSkuDao skuDao;
    private final DmsShopMemberDao memberDao;
    private final DistributionAuditService auditService;
    private final MemberAssetService memberAssetService;
    private final OrderBalanceAllocationService orderBalanceAllocationService;
    private final AlipayService alipayService;

    @Value("${shop.order.after-sale-window-days:7}")
    private long afterSaleWindowDays;

    @Value("${shop.payment.simulation-enabled:false}")
    private boolean simulationPaymentEnabled;

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
        assertWithinAfterSaleWindow(order);
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
        populateReturnAddress(afterSale, order, refundItems);
        afterSaleDao.insert(afterSale);
        for (DmsShopAfterSaleItem item : refundItems) item.setAfterSaleId(afterSale.getId());
        afterSaleItemDao.insertBatch(refundItems);
        return hydrate(afterSaleDao.selectById(afterSale.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale cancel(DmsShopMember member, Long id) {
        if (member == null) {
            Asserts.fail("请先登录");
        }
        if (id == null) {
            Asserts.fail("售后申请ID不能为空");
        }
        DmsShopAfterSale afterSale = afterSaleDao.selectByIdForUpdate(id);
        if (afterSale == null) {
            Asserts.fail("售后申请不存在");
        }
        if (!member.getUserId().equals(afterSale.getUserId())) {
            Asserts.fail("不能取消他人的售后申请");
        }
        DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
        if (order == null) {
            Asserts.fail("订单不存在");
        }
        assertTenantAccess(order.getTenantId());
        if (!Integer.valueOf(0).equals(afterSale.getStatus())) {
            Asserts.fail("售后申请已处理，不能取消");
        }
        afterSale.setStatus(3);
        afterSale.setAuditRemark("客户主动取消售后申请");
        afterSale.setAuditUserId(null);
        afterSale.setAuditUserName("客户本人");
        afterSaleDao.updateAudit(afterSale);
        return hydrate(afterSaleDao.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale submitReturnShipment(DmsShopMember member, Long id, ShopAfterSaleReturnShipmentDTO dto) {
        if (member == null) Asserts.fail("请先登录");
        if (id == null || dto == null || dto.getDeliveryCompany() == null || dto.getDeliveryCompany().isBlank()
                || dto.getDeliveryNo() == null || dto.getDeliveryNo().isBlank()) {
            Asserts.fail("请填写退货物流公司和运单号");
        }
        DmsShopAfterSale afterSale = afterSaleDao.selectByIdForUpdate(id);
        if (afterSale == null || !member.getUserId().equals(afterSale.getUserId())) Asserts.fail("售后申请不存在");
        DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
        if (order == null) Asserts.fail("订单不存在");
        assertTenantAccess(order.getTenantId());
        if (!Integer.valueOf(2).equals(afterSale.getApplyType()) || !Integer.valueOf(4).equals(afterSale.getStatus())) {
            Asserts.fail("当前售后状态不能填写退货物流");
        }
        afterSale.setReturnDeliveryCompany(dto.getDeliveryCompany().trim());
        afterSale.setReturnDeliveryNo(dto.getDeliveryNo().trim());
        afterSale.setReturnShippedAt(LocalDateTime.now());
        afterSale.setStatus(5);
        afterSaleDao.updateReturnShipment(afterSale);
        return hydrate(afterSaleDao.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale manualRefund(Long orderId, ShopManualRefundDTO dto) {
        if (orderId == null) Asserts.fail("订单ID不能为空");
        DmsShopOrder order = orderDao.selectById(orderId);
        if (order == null) Asserts.fail("订单不存在");
        assertTenantAccess(order.getTenantId());
        if (Integer.valueOf(0).equals(order.getStatus()) || Integer.valueOf(4).equals(order.getStatus())) {
            Asserts.fail("当前订单状态不能退款");
        }
        if (afterSaleDao.selectOpenByOrderId(orderId) != null) {
            Asserts.fail("该订单已有处理中售后，请先处理后再后台退款");
        }
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            Asserts.fail("请选择本次退款涉及的商品和盒数");
        }

        List<DmsShopOrderItem> orderItems = orderItemDao.selectByOrderId(orderId);
        Map<Long, DmsShopOrderItem> byId = new LinkedHashMap<>();
        for (DmsShopOrderItem item : orderItems) byId.put(item.getId(), item);
        Map<Long, Integer> selected = new LinkedHashMap<>();
        dto.getItems().forEach(item -> {
            if (item == null || item.getOrderItemId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                Asserts.fail("退款商品和盒数不正确");
            }
            selected.merge(item.getOrderItemId(), item.getQuantity(), Integer::sum);
        });

        BigDecimal grossOrderAmount = orderItems.stream().map(DmsShopOrderItem::getTotalAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal productBase = nullToZero(order.getTotalAmount()).subtract(nullToZero(order.getDiscountAmount())).max(BigDecimal.ZERO);
        if (grossOrderAmount.compareTo(BigDecimal.ZERO) <= 0 || productBase.compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("订单商品金额异常，不能退款");
        }

        int totalRemainingQuantity = 0;
        for (DmsShopOrderItem item : orderItems) {
            int reserved = afterSaleItemDao.sumReservedQuantityByOrderItemId(item.getId());
            totalRemainingQuantity += Math.max(0, nullToZero(item.getQuantity()) - reserved);
        }
        List<DmsShopAfterSaleItem> refundItems = new ArrayList<>();
        int refundQuantity = 0;
        BigDecimal selectedGross = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : selected.entrySet()) {
            DmsShopOrderItem source = byId.get(entry.getKey());
            if (source == null) Asserts.fail("退款商品不属于当前订单");
            int reserved = afterSaleItemDao.sumReservedQuantityByOrderItemId(source.getId());
            int remaining = Math.max(0, nullToZero(source.getQuantity()) - reserved);
            if (entry.getValue() > remaining) Asserts.fail(source.getProductName() + "最多可退" + remaining + "盒");
            DmsShopAfterSaleItem item = new DmsShopAfterSaleItem();
            item.setOrderId(orderId);
            item.setOrderItemId(source.getId());
            item.setProductId(source.getProductId());
            item.setSkuId(source.getSkuId());
            item.setProductName(source.getProductName());
            item.setSkuName(source.getSkuName());
            item.setRefundQuantity(entry.getValue());
            BigDecimal grossRefund = nullToZero(source.getTotalAmount())
                    .multiply(BigDecimal.valueOf(entry.getValue()))
                    .divide(BigDecimal.valueOf(Math.max(1, nullToZero(source.getQuantity()))), 8, java.math.RoundingMode.HALF_UP);
            item.setRefundAmount(grossRefund);
            refundItems.add(item);
            selectedGross = selectedGross.add(grossRefund);
            refundQuantity += entry.getValue();
        }
        if (refundQuantity <= 0) Asserts.fail("请选择本次退款涉及的商品盒数");
        if (selectedGross.compareTo(BigDecimal.ZERO) <= 0) Asserts.fail("所选商品金额异常，不能退款");

        BigDecimal approvedProductRefund = nullToZero(afterSaleItemDao.sumApprovedProductRefundByOrderId(orderId));
        BigDecimal remainingProductRefund = productBase.subtract(approvedProductRefund).max(BigDecimal.ZERO);
        String mode = dto.getRefundMode() == null ? "QUANTITY" : dto.getRefundMode().trim().toUpperCase(java.util.Locale.ROOT);
        BigDecimal productRefund;
        if ("AMOUNT".equals(mode)) {
            productRefund = nullToZero(dto.getProductRefundAmount()).setScale(2, java.math.RoundingMode.HALF_UP);
            if (productRefund.compareTo(BigDecimal.ZERO) <= 0) Asserts.fail("请输入大于0的商品退款金额");
            if (productRefund.compareTo(remainingProductRefund) > 0) {
                Asserts.fail("商品退款超过订单剩余可退金额，可退金额：" + remainingProductRefund.setScale(2, java.math.RoundingMode.HALF_UP));
            }
        } else if ("QUANTITY".equals(mode)) {
            productRefund = refundItems.stream()
                    .map(item -> nullToZero(item.getRefundAmount()).multiply(productBase)
                            .divide(grossOrderAmount, 2, java.math.RoundingMode.HALF_UP))
                    .reduce(BigDecimal.ZERO, BigDecimal::add).min(remainingProductRefund);
            if (refundQuantity == totalRemainingQuantity) productRefund = remainingProductRefund;
            productRefund = productRefund.setScale(2, java.math.RoundingMode.HALF_UP);
        } else {
            Asserts.fail("退款方式不正确");
            return null;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < refundItems.size(); i++) {
            DmsShopAfterSaleItem item = refundItems.get(i);
            BigDecimal allocation;
            if (i == refundItems.size() - 1) {
                allocation = productRefund.subtract(allocated).setScale(2, java.math.RoundingMode.HALF_UP);
            } else if ("AMOUNT".equals(mode)) {
                allocation = productRefund.multiply(nullToZero(item.getRefundAmount()))
                        .divide(selectedGross, 2, java.math.RoundingMode.HALF_UP);
            } else {
                allocation = nullToZero(item.getRefundAmount()).multiply(productBase)
                        .divide(grossOrderAmount, 2, java.math.RoundingMode.HALF_UP);
                allocation = allocation.min(productRefund.subtract(allocated).max(BigDecimal.ZERO));
            }
            item.setRefundAmount(allocation);
            allocated = allocated.add(allocation);
        }

        DmsShopMember member = memberDao.selectByUserId(order.getUserId());
        DmsShopAfterSale afterSale = new DmsShopAfterSale();
        afterSale.setAfterSaleNo(generateAfterSaleNo());
        afterSale.setOrderId(orderId);
        afterSale.setOrderNo(order.getOrderNo());
        afterSale.setMemberId(member == null ? null : member.getId());
        afterSale.setUserId(order.getUserId());
        afterSale.setApplyType(dto.getApplyType() == null ? 1 : dto.getApplyType());
        afterSale.setProductRefundAmount(productRefund);
        afterSale.setFreightRefundAmount(BigDecimal.ZERO);
        afterSale.setRefundAmount(productRefund);
        afterSale.setRefundQuantity(refundQuantity);
        afterSale.setReason(dto.getReason() == null || dto.getReason().isBlank() ? "后台超期退款" : dto.getReason().trim());
        afterSale.setStatus(0);
        populateReturnAddress(afterSale, order, refundItems);
        afterSaleDao.insert(afterSale);
        for (DmsShopAfterSaleItem item : refundItems) item.setAfterSaleId(afterSale.getId());
        afterSaleItemDao.insertBatch(refundItems);

        ShopAfterSaleAuditDTO audit = new ShopAfterSaleAuditDTO();
        audit.setStatus(1);
        audit.setAuditRemark("后台超期退款：" + ("AMOUNT".equals(mode) ? "按金额" : "按盒数比例"));
        audit.setAuditUserId(dto.getOperatorId());
        audit.setAuditUserName(dto.getOperatorName());
        return audit(afterSale.getId(), audit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelPendingShipment(Long orderId, Long operatorId, String operatorName) {
        if (orderId == null) Asserts.fail("订单ID不能为空");
        DmsShopOrder order = orderDao.selectById(orderId);
        if (order == null) Asserts.fail("订单不存在");
        assertTenantAccess(order.getTenantId());
        if (!Integer.valueOf(1).equals(order.getStatus())) {
            Asserts.fail("只有待发货订单可以取消");
        }
        List<ShopAfterSaleItemDTO> items = orderItemDao.selectByOrderId(orderId).stream().map(item -> {
            ShopAfterSaleItemDTO dto = new ShopAfterSaleItemDTO();
            dto.setOrderItemId(item.getId());
            dto.setQuantity(item.getQuantity());
            return dto;
        }).toList();
        if (items.isEmpty()) Asserts.fail("订单商品为空，不能取消");
        ShopManualRefundDTO refund = new ShopManualRefundDTO();
        refund.setRefundMode("QUANTITY");
        refund.setApplyType(1);
        refund.setItems(items);
        refund.setReason("后台取消待发货订单");
        refund.setOperatorId(operatorId);
        refund.setOperatorName(operatorName);
        manualRefund(orderId, refund);
        // 待发货订单尚未出库，整单退款后释放下单时预占的 SKU 与商品库存。
        for (DmsShopOrderItem item : orderItemDao.selectByOrderId(orderId)) {
            if (item.getSkuId() != null) skuDao.increaseStock(item.getSkuId(), item.getQuantity());
            productDao.increaseStock(item.getProductId(), item.getQuantity());
        }
        return true;
    }

    private void assertWithinAfterSaleWindow(DmsShopOrder order) {
        if (order.getCreateTime() == null) return;
        long days = Math.max(1, afterSaleWindowDays);
        if (!LocalDateTime.now().isBefore(order.getCreateTime().plusDays(days))) {
            Asserts.fail("订单已超过下单后" + days + "天售后期限，请联系商城客服由后台处理");
        }
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
        // 退货退款先进入“待寄回”，客户提交物流后再由商家确认收货并退款。
        boolean returnAddressConfigured = afterSale.getReturnAddress() != null
                && !afterSale.getReturnAddress().isBlank();
        if (Integer.valueOf(1).equals(status) && Integer.valueOf(2).equals(afterSale.getApplyType())
                && returnAddressConfigured) {
            afterSale.setStatus(4);
        } else {
            afterSale.setStatus(status);
        }
        afterSale.setAuditRemark(dto == null ? null : dto.getAuditRemark());
        afterSale.setAuditUserId(dto == null ? null : dto.getAuditUserId());
        afterSale.setAuditUserName(dto == null ? null : dto.getAuditUserName());
        afterSaleDao.updateAudit(afterSale);

        if (Integer.valueOf(1).equals(status) && Integer.valueOf(2).equals(afterSale.getApplyType())
                && returnAddressConfigured) {
            return hydrate(afterSaleDao.selectById(id));
        }
        if (Integer.valueOf(1).equals(status)) {
            completeRefund(afterSale, order);
        }
        return hydrate(afterSaleDao.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale confirmReturnReceived(Long id, ShopAfterSaleAuditDTO dto) {
        DmsShopAfterSale afterSale = afterSaleDao.selectByIdForUpdate(id);
        if (afterSale == null) Asserts.fail("售后单不存在");
        DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
        if (order == null) Asserts.fail("订单不存在");
        assertTenantAccess(order.getTenantId());
        if (!Integer.valueOf(2).equals(afterSale.getApplyType()) || !Integer.valueOf(5).equals(afterSale.getStatus())) {
            Asserts.fail("客户尚未提交退货物流，不能确认收货");
        }
        afterSale.setStatus(6);
        afterSale.setReturnReceivedAt(LocalDateTime.now());
        afterSale.setAuditRemark(dto == null ? "商家确认收到退货" : dto.getAuditRemark());
        afterSale.setAuditUserId(dto == null ? null : dto.getAuditUserId());
        afterSale.setAuditUserName(dto == null ? null : dto.getAuditUserName());
        afterSaleDao.updateReturnReceived(afterSale);
        completeRefund(afterSale, order);
        afterSale.setStatus(1);
        afterSaleDao.updateAudit(afterSale);
        return hydrate(afterSaleDao.selectById(id));
    }

    private void completeRefund(DmsShopAfterSale afterSale, DmsShopOrder order) {
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
            // 余额支付的退款原路退回商城余额；支付宝在本事务内完成原路退款，微信按接入配置处理。
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
            // 所有内部售后、财务、奖金和库存变更完成后再调用外部退款，外部失败时可整体回滚，避免先退款后落账。
            refundExternalPayment(order, afterSale);
    }

    private void refundExternalPayment(DmsShopOrder order, DmsShopAfterSale afterSale) {
        if (simulationPaymentEnabled || order == null || afterSale == null
                || afterSale.getRefundAmount() == null
                || afterSale.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if ("ALIPAY".equalsIgnoreCase(order.getPayType())) {
            if (!alipayService.isConfigured()) {
                Asserts.fail("支付宝退款未配置，请先完成支付宝密钥配置");
            }
            boolean success = alipayService.refund(order.getOrderNo(), afterSale.getAfterSaleNo(),
                    afterSale.getRefundAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                    "商城售后退款：" + (afterSale.getReason() == null ? "后台处理" : afterSale.getReason()));
            if (!success) Asserts.fail("支付宝退款失败，请核对支付宝订单状态后重试");
        }
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

    private void populateReturnAddress(DmsShopAfterSale afterSale, DmsShopOrder order,
                                       List<DmsShopAfterSaleItem> refundItems) {
        if (afterSale == null || order == null || !Integer.valueOf(2).equals(afterSale.getApplyType())) return;
        Long productId = refundItems == null || refundItems.isEmpty() ? null : refundItems.get(0).getProductId();
        DmsShopServiceAddress address = null;
        if (productId != null) {
            DmsShopProduct product = productDao.selectById(productId);
            if (product != null && product.getReturnAddressId() != null) {
                address = serviceAddressDao.selectById(product.getReturnAddressId());
                if (address != null && (!Integer.valueOf(2).equals(address.getAddressType())
                        || !Integer.valueOf(1).equals(address.getStatus()))) address = null;
            }
        }
        if (address == null) address = serviceAddressDao.selectDefault(order.getTenantId(), 2);
        if (address == null) return;
        afterSale.setReturnAddressId(address.getId());
        afterSale.setReturnAddress(joinServiceAddress(address));
    }

    private String joinServiceAddress(DmsShopServiceAddress address) {
        return java.util.stream.Stream.of(
                        address.getContactName() + " " + address.getContactPhone(),
                        address.getProvince(), address.getCity(), address.getDistrict(), address.getDetailAddress())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
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

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
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
