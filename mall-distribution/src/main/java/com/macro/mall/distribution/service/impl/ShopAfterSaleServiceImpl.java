package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderShipmentDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dao.DmsShopServiceAddressDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopSkuDao;
import com.macro.mall.distribution.dao.DmsMerchantDao;
import com.macro.mall.distribution.dto.FinanceRefundDTO;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleApplyDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleAuditDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleExchangeShipmentDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleReturnShipmentDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleItemDTO;
import com.macro.mall.distribution.dto.ShopManualRefundDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopAfterSaleItem;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopServiceAddress;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.enums.AgentSourceTypeEnum;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.ShopAfterSaleService;
import com.macro.mall.distribution.service.ShopMediaStorageService;
import com.macro.mall.distribution.service.OrderRealtimeService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import com.macro.mall.distribution.service.MerchantService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.RefundInventoryRestockService;
import com.macro.mall.distribution.service.WeChatShippingInfoService;
import com.macro.mall.distribution.util.MemberAccountUtils;
import com.macro.mall.distribution.util.ShopQuantityChecks;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.entity.DmsAdminUser;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final DmsShopOrderShipmentDao orderShipmentDao;
    private final DmsShopProductDao productDao;
    private final DmsShopServiceAddressDao serviceAddressDao;
    private final DmsShopSkuDao skuDao;
    private final DmsShopMemberDao memberDao;
    private final DistributionAuditService auditService;
    private final MemberAssetService memberAssetService;
    private final OrderBalanceAllocationService orderBalanceAllocationService;
    private final ExternalRefundCoordinator externalRefundCoordinator;
    private final ShopAfterSaleWindowPolicy afterSaleWindowPolicy;
    private final ShopAfterSaleTimelinePolicy afterSaleTimelinePolicy;
    private final RefundInventoryRestockService refundInventoryRestockService;
    private final ShopMediaStorageService mediaStorageService;
    private final MerchantService merchantService;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;
    @Autowired(required = false)
    private OrderRealtimeService orderRealtimeService;
    @Autowired(required = false)
    private DmsMerchantDao merchantDao;
    @Autowired
    private WeChatShippingInfoService weChatShippingInfoService;

    @Value("${shop.payment.simulation-enabled:false}")
    private boolean simulationPaymentEnabled;
    @Value("${shop.after-sale.return-shipment-timeout-days:7}")
    private int returnShipmentTimeoutDays;
    @Value("${shop.after-sale.exchange-auto-receive-days:15}")
    private int exchangeAutoReceiveDays;

    @Override
    public void assertCanUploadProof(DmsShopMember member, Long orderId) {
        if (member == null) Asserts.unauthorized("请先登录");
        if (orderId == null) Asserts.fail("订单ID不能为空");
        DmsShopOrder order = orderDao.selectById(orderId);
        if (order == null) Asserts.fail("订单不存在");
        assertTenantAccess(order.getTenantId());
        if (!member.getUserId().equals(order.getUserId())) Asserts.fail("不能为他人的订单上传售后凭证");
        if (Integer.valueOf(0).equals(order.getStatus()) || Integer.valueOf(4).equals(order.getStatus())) {
            Asserts.fail("当前订单状态不能申请售后");
        }
        assertWithinAfterSaleWindow(order);
        if (afterSaleDao.selectOpenByOrderId(orderId) != null) Asserts.fail("该订单已有处理中售后");
    }

    @Override
    public void assertAdminCanReadProof(Long memberId, String filename) {
        if (memberId == null || filename == null || filename.isBlank()) Asserts.fail("售后凭证信息不完整");
        Long merchantId = currentMerchantId();
        for (String proofImages : afterSaleDao.selectProofReferences(TenantContext.getTenantId(), memberId, merchantId)) {
            try {
                List<String> filenames = objectMapper.readValue(proofImages, new TypeReference<List<String>>() { });
                if (filenames.stream().anyMatch(filename::equals)) return;
            } catch (Exception e) {
                log.warn("售后凭证清单格式异常，已拒绝直接读取: memberId={}", memberId);
            }
        }
        Asserts.fail(merchantId == null ? "售后凭证不存在或不属于当前商城" : "不能读取其他商户的售后凭证");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale apply(DmsShopMember member, ShopAfterSaleApplyDTO dto) {
        if (member == null) {
            Asserts.unauthorized("请先登录");
        }
        if (dto == null || dto.getOrderId() == null) {
            Asserts.fail("订单ID不能为空");
        }
        // 同一订单的售后申请串行处理，避免并发申请重复占用可退数量和金额。
        DmsShopOrder order = orderDao.selectByIdForUpdate(dto.getOrderId());
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
        int applyType = dto.getApplyType() == null ? 1 : dto.getApplyType();
        if (applyType < 1 || applyType > 3) Asserts.fail("售后类型不正确");
        if (applyType == 3 && !Integer.valueOf(2).equals(order.getStatus())
                && !Integer.valueOf(3).equals(order.getStatus())) {
            Asserts.fail("商品完整发货后才能申请换货");
        }
        assertWithinAfterSaleWindow(order);
        if (afterSaleDao.selectOpenByOrderId(order.getId()) != null) {
            Asserts.fail("该订单已有处理中售后");
        }
        String proofImages = normalizeProofImages(member.getId(), dto.getProofImages());

        if (dto.getItems() == null || dto.getItems().isEmpty()) Asserts.fail("请选择实际退回的商品和数量");
        List<DmsShopOrderItem> orderItems = orderItemDao.selectByOrderId(order.getId());
        Map<Long, DmsShopOrderItem> byId = new LinkedHashMap<>();
        for (DmsShopOrderItem item : orderItems) byId.put(item.getId(), item);
        Map<Long, Integer> selected = ShopQuantityChecks.refundSelection(dto.getItems());

        validateRefundHistory(order.getId());

        long totalRemainingQuantity = 0;
        for (DmsShopOrderItem item : orderItems) {
            int reserved = afterSaleItemDao.sumReservedQuantityByOrderItemId(item.getId());
            totalRemainingQuantity += ShopQuantityChecks.remaining(item.getQuantity(), reserved);
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
            if (source == null) Asserts.fail("售后商品不属于当前订单");
            int reserved = afterSaleItemDao.sumReservedQuantityByOrderItemId(source.getId());
            int remaining = ShopQuantityChecks.remaining(source.getQuantity(), reserved);
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
            refundQuantity = ShopQuantityChecks.add(refundQuantity, entry.getValue());
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
        boolean notShipped = Integer.valueOf(1).equals(order.getStatus())
                && order.getDeliveryTime() == null
                && orderShipmentDao.sumQuantityByOrderId(order.getId()) == 0;
        BigDecimal freightRefund = notShipped && refundAllRemaining ? nullToZero(order.getFreightAmount()) : BigDecimal.ZERO;
        BigDecimal amount = productRefund.add(freightRefund).setScale(2, java.math.RoundingMode.HALF_UP);
        if (applyType == 3) {
            refundItems.forEach(item -> item.setRefundAmount(BigDecimal.ZERO.setScale(2)));
            productRefund = BigDecimal.ZERO.setScale(2);
            freightRefund = BigDecimal.ZERO.setScale(2);
            amount = BigDecimal.ZERO.setScale(2);
        }

        DmsShopAfterSale afterSale = new DmsShopAfterSale();
        afterSale.setAfterSaleNo(generateAfterSaleNo());
        afterSale.setOrderId(order.getId());
        afterSale.setOrderNo(order.getOrderNo());
        afterSale.setMemberId(member.getId());
        afterSale.setUserId(member.getUserId());
        afterSale.setApplyType(applyType);
        afterSale.setRefundAmount(amount);
        afterSale.setProductRefundAmount(productRefund);
        afterSale.setFreightRefundAmount(freightRefund);
        afterSale.setRefundQuantity(refundQuantity);
        afterSale.setReason(dto.getReason());
        afterSale.setProofImages(proofImages);
        afterSale.setStatus(0);
        populateReturnAddress(afterSale, order, refundItems);
        if (afterSaleDao.insert(afterSale) != 1) Asserts.fail("订单不属于当前租户");
        for (DmsShopAfterSaleItem item : refundItems) item.setAfterSaleId(afterSale.getId());
        afterSaleItemDao.insertBatch(refundItems);
        commitProofImages(member.getId(), proofImages);
        notifyOrderChanged(order, "AFTER_SALE_APPLIED", afterSale.getId());
        return hydrate(afterSaleDao.selectById(afterSale.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale cancel(DmsShopMember member, Long id) {
        if (member == null) {
            Asserts.unauthorized("请先登录");
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
        if (!Integer.valueOf(0).equals(afterSale.getStatus()) && !Integer.valueOf(4).equals(afterSale.getStatus())) {
            Asserts.fail("售后申请已进入寄回或退款阶段，不能取消");
        }
        afterSale.setStatus(3);
        afterSale.setAuditRemark("客户主动取消售后申请");
        afterSale.setAuditUserId(null);
        afterSale.setAuditUserName("客户本人");
        if (afterSaleDao.updateAudit(afterSale) != 1) Asserts.fail("售后状态已变化，请刷新后重试");
        notifyOrderChanged(order, "AFTER_SALE_CANCELLED", afterSale.getId());
        return hydrate(afterSaleDao.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale submitReturnShipment(DmsShopMember member, Long id, ShopAfterSaleReturnShipmentDTO dto) {
        if (member == null) Asserts.unauthorized("请先登录");
        if (id == null || dto == null || dto.getDeliveryCompany() == null || dto.getDeliveryCompany().isBlank()
                || dto.getDeliveryNo() == null || dto.getDeliveryNo().isBlank()) {
            Asserts.fail("请填写退货物流公司和运单号");
        }
        if (dto.getDeliveryCompany().trim().length() > 50) {
            Asserts.fail("物流公司名称不能超过50个字");
        }
        DmsShopAfterSale afterSale = afterSaleDao.selectByIdForUpdate(id);
        if (afterSale == null || !member.getUserId().equals(afterSale.getUserId())) Asserts.fail("售后申请不存在");
        DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
        if (order == null) Asserts.fail("订单不存在");
        assertTenantAccess(order.getTenantId());
        if (Integer.valueOf(5).equals(afterSale.getStatus())
                && dto.getDeliveryCompany().trim().equals(afterSale.getReturnDeliveryCompany())
                && dto.getDeliveryNo().trim().equals(afterSale.getReturnDeliveryNo())) {
            return hydrate(afterSale);
        }
        if ((!Integer.valueOf(2).equals(afterSale.getApplyType()) && !Integer.valueOf(3).equals(afterSale.getApplyType()))
                || (!Integer.valueOf(4).equals(afterSale.getStatus()) && !Integer.valueOf(5).equals(afterSale.getStatus()))) {
            Asserts.fail("当前售后状态不能填写或修改退货物流");
        }
        afterSale.setReturnDeliveryCompany(dto.getDeliveryCompany().trim());
        afterSale.setReturnDeliveryNo(dto.getDeliveryNo().trim());
        afterSale.setReturnShippedAt(LocalDateTime.now());
        afterSale.setStatus(5);
        if (afterSaleDao.updateReturnShipment(afterSale) != 1) Asserts.fail("售后状态已变化，请刷新后重试");
        notifyOrderChanged(order, "AFTER_SALE_RETURN_SHIPPED", afterSale.getId());
        return hydrate(afterSaleDao.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireWaitingReturnShipments(int limit) {
        if (returnShipmentTimeoutDays <= 0) return 0;
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int timeoutDays = Math.min(returnShipmentTimeoutDays, 365);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(timeoutDays);
        int expired = 0;
        for (Long id : afterSaleDao.selectExpiredWaitingReturnIds(cutoff, safeLimit)) {
            DmsShopAfterSale afterSale = afterSaleDao.selectByIdForUpdate(id);
            if (afterSale == null || !Integer.valueOf(4).equals(afterSale.getStatus())
                    || afterSale.getAuditTime() == null || afterSale.getAuditTime().isAfter(cutoff)) continue;
            afterSale.setStatus(3);
            afterSale.setAuditRemark("已超过" + timeoutDays + "天退货寄回期限，系统自动关闭");
            afterSale.setAuditUserId(0L);
            afterSale.setAuditUserName("系统");
            if (afterSaleDao.updateAudit(afterSale) != 1) Asserts.fail("超时退货售后关闭失败");
            DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
            if (order != null) notifyOrderChanged(order, "AFTER_SALE_RETURN_TIMEOUT", afterSale.getId());
            expired++;
        }
        return expired;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale manualRefund(Long orderId, ShopManualRefundDTO dto) {
        assertPlatformExceptionalOperation("人工退款");
        if (orderId == null) Asserts.fail("订单ID不能为空");
        // 后台退款与客户售后共用订单锁，确保剩余可退数量和金额只计算一次。
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
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
        Map<Long, Integer> selected = ShopQuantityChecks.refundSelection(dto.getItems());

        validateRefundHistory(orderId);

        BigDecimal grossOrderAmount = orderItems.stream().map(DmsShopOrderItem::getTotalAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal productBase = nullToZero(order.getTotalAmount()).subtract(nullToZero(order.getDiscountAmount())).max(BigDecimal.ZERO);
        if (grossOrderAmount.compareTo(BigDecimal.ZERO) <= 0 || productBase.compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("订单商品金额异常，不能退款");
        }

        long totalRemainingQuantity = 0;
        for (DmsShopOrderItem item : orderItems) {
            int reserved = afterSaleItemDao.sumReservedQuantityByOrderItemId(item.getId());
            totalRemainingQuantity += ShopQuantityChecks.remaining(item.getQuantity(), reserved);
        }
        List<DmsShopAfterSaleItem> refundItems = new ArrayList<>();
        int refundQuantity = 0;
        BigDecimal selectedGross = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : selected.entrySet()) {
            DmsShopOrderItem source = byId.get(entry.getKey());
            if (source == null) Asserts.fail("退款商品不属于当前订单");
            int reserved = afterSaleItemDao.sumReservedQuantityByOrderItemId(source.getId());
            int remaining = ShopQuantityChecks.remaining(source.getQuantity(), reserved);
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
            refundQuantity = ShopQuantityChecks.add(refundQuantity, entry.getValue());
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
        if (afterSaleDao.insert(afterSale) != 1) Asserts.fail("订单不属于当前租户");
        for (DmsShopAfterSaleItem item : refundItems) item.setAfterSaleId(afterSale.getId());
        afterSaleItemDao.insertBatch(refundItems);

        ShopAfterSaleAuditDTO audit = new ShopAfterSaleAuditDTO();
        audit.setStatus(1);
        audit.setAuditRemark("后台超期退款：" + ("AMOUNT".equals(mode) ? "按金额" : "按盒数比例"));
        DmsAdminUser actor = AdminContext.get();
        audit.setAuditUserId(actor == null ? dto.getOperatorId() : actor.getId());
        audit.setAuditUserName(actor == null ? dto.getOperatorName()
                : (actor.getNickname() == null || actor.getNickname().isBlank() ? actor.getUsername() : actor.getNickname()));
        return audit(afterSale.getId(), audit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelPendingShipment(Long orderId, Long operatorId, String operatorName) {
        assertPlatformExceptionalOperation("取消待发货订单");
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
        return true;
    }

    private void assertWithinAfterSaleWindow(DmsShopOrder order) {
        ShopAfterSaleWindowPolicy.Window window = afterSaleWindowPolicy.resolve(order.getTenantId());
        if (window.days() == 0) {
            Asserts.fail("商城已关闭客户自助售后入口，请联系商城客服由后台处理");
        }
        if (afterSaleWindowPolicy.isExpired(order, LocalDateTime.now(), window)) {
            Asserts.fail("订单已超过" + afterSaleWindowPolicy.label(window) + "售后期限，请联系商城客服由后台处理");
        }
    }

    private String normalizeProofImages(Long memberId, String rawProofImages) {
        if (rawProofImages == null || rawProofImages.isBlank()) return null;
        try {
            List<String> parsed = objectMapper.readValue(rawProofImages, new TypeReference<List<String>>() { });
            LinkedHashSet<String> unique = new LinkedHashSet<>(parsed);
            if (unique.size() > 6) Asserts.fail("售后凭证最多上传6张");
            for (String filename : unique) {
                if (filename == null || filename.isBlank()
                        || mediaStorageService.loadAfterSaleProof(memberId, filename) == null) {
                    Asserts.fail("售后凭证无效或不属于当前会员，请重新上传");
                }
            }
            return unique.isEmpty() ? null : objectMapper.writeValueAsString(unique);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            Asserts.fail("售后凭证格式不正确，请重新上传");
            return null;
        }
    }

    private void commitProofImages(Long memberId, String proofImages) {
        if (proofImages == null || proofImages.isBlank()) return;
        try {
            List<String> filenames = objectMapper.readValue(proofImages, new TypeReference<List<String>>() { });
            mediaStorageService.commitAfterSaleProofs(memberId, filenames);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("提交售后凭证失败 memberId={}", memberId, e);
            Asserts.fail("售后凭证保存失败，请重新提交");
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
        return afterSaleDao.selectList(TenantContext.getTenantId(), keyword, status, currentMerchantId()).stream()
                .filter(this::canAccessAfterSale)
                .map(this::hydrate)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale audit(Long id, ShopAfterSaleAuditDTO dto) {
        applyAuthenticatedAdmin(dto);
        DmsShopAfterSale afterSale = afterSaleDao.selectByIdForUpdate(id);
        if (afterSale == null) {
            Asserts.fail("售后单不存在");
        }
        DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
        if (order == null) {
            Asserts.fail("订单不存在");
        }
        assertTenantAccess(order.getTenantId());
        assertMerchantAfterSaleAccess(order);
        if (!Integer.valueOf(0).equals(afterSale.getStatus())) {
            Asserts.fail("售后单已审核");
        }
        Integer status = dto == null || dto.getStatus() == null ? 1 : dto.getStatus();
        if (!Integer.valueOf(1).equals(status) && !Integer.valueOf(2).equals(status)
                && !Integer.valueOf(3).equals(status)) {
            Asserts.fail("审核状态不正确");
        }
        String auditRemark = dto == null || dto.getAuditRemark() == null ? "" : dto.getAuditRemark().trim();
        if ((Integer.valueOf(2).equals(status) || Integer.valueOf(3).equals(status)) && auditRemark.isEmpty()) {
            Asserts.fail(Integer.valueOf(2).equals(status) ? "拒绝售后必须填写原因" : "关闭售后必须填写原因");
        }
        // 退货退款与换货均先进入“待寄回”；换货不会进入退款或奖金冲销链路。
        if (Integer.valueOf(1).equals(status)) {
            ShopQuantityChecks.refundLines(afterSaleItemDao.selectByAfterSaleId(afterSale.getId()));
            validateRefundHistory(order.getId());
        }
        boolean returnAddressConfigured = afterSale.getReturnAddress() != null
                && !afterSale.getReturnAddress().isBlank();
        boolean physicalReturn = Integer.valueOf(2).equals(afterSale.getApplyType())
                || Integer.valueOf(3).equals(afterSale.getApplyType());
        if (Integer.valueOf(1).equals(status) && physicalReturn && !returnAddressConfigured) {
            Asserts.fail("请先为该订单商品配置可用退货地址，再通过售后");
        }
        boolean requiresExternalRefund = Integer.valueOf(1).equals(status) && requiresExternalRefund(order, afterSale);
        if (Integer.valueOf(1).equals(status) && physicalReturn && returnAddressConfigured) {
            afterSale.setStatus(4);
        } else if (requiresExternalRefund) {
            afterSale.setStatus(6);
        } else {
            afterSale.setStatus(status);
        }
        afterSale.setAuditRemark(auditRemark.isEmpty() ? null : auditRemark);
        afterSale.setAuditUserId(dto == null ? null : dto.getAuditUserId());
        afterSale.setAuditUserName(dto == null ? null : dto.getAuditUserName());
        if (afterSaleDao.updateAudit(afterSale) != 1) Asserts.fail("售后状态已变化，请刷新后重试");

        if (Integer.valueOf(1).equals(status) && physicalReturn && returnAddressConfigured) {
            notifyOrderChanged(order, "AFTER_SALE_AUDITED", afterSale.getId());
            return hydrate(afterSaleDao.selectById(id));
        }
        if (Integer.valueOf(1).equals(status)) {
            completeRefund(afterSale, order);
            if (requiresExternalRefund) scheduleExternalRefund(afterSale.getId());
        }
        notifyOrderChanged(order, "AFTER_SALE_AUDITED", afterSale.getId());
        return hydrate(afterSaleDao.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale confirmReturnReceived(Long id, ShopAfterSaleAuditDTO dto) {
        applyAuthenticatedAdmin(dto);
        DmsShopAfterSale afterSale = afterSaleDao.selectByIdForUpdate(id);
        if (afterSale == null) Asserts.fail("售后单不存在");
        DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
        if (order == null) Asserts.fail("订单不存在");
        assertTenantAccess(order.getTenantId());
        assertMerchantAfterSaleAccess(order);
        if (afterSale.getReturnReceivedAt() != null
                && ((Integer.valueOf(2).equals(afterSale.getApplyType()) && Integer.valueOf(1).equals(afterSale.getStatus()))
                    || (Integer.valueOf(3).equals(afterSale.getApplyType())
                        && List.of(1, 7, 8).contains(afterSale.getStatus())))) {
            return hydrate(afterSale);
        }
        if (Integer.valueOf(6).equals(afterSale.getStatus())) {
            if (!requiresExternalRefund(order, afterSale)) Asserts.fail("当前退款状态不需要调用外部支付渠道");
            scheduleExternalRefund(afterSale.getId());
            return hydrate(afterSale);
        }
        if ((!Integer.valueOf(2).equals(afterSale.getApplyType()) && !Integer.valueOf(3).equals(afterSale.getApplyType()))
                || !Integer.valueOf(5).equals(afterSale.getStatus())) {
            Asserts.fail("客户尚未提交退货物流，不能确认收货");
        }
        boolean exchange = Integer.valueOf(3).equals(afterSale.getApplyType());
        afterSale.setStatus(exchange ? 7 : 6);
        afterSale.setReturnReceivedAt(LocalDateTime.now());
        afterSale.setAuditRemark(dto == null ? (exchange ? "商家确认收到换货退件" : "商家确认收到退货") : dto.getAuditRemark());
        afterSale.setAuditUserId(dto == null ? null : dto.getAuditUserId());
        afterSale.setAuditUserName(dto == null ? null : dto.getAuditUserName());
        if (afterSaleDao.updateReturnReceived(afterSale) != 1) Asserts.fail("售后状态已变化，请刷新后重试");
        if (exchange) {
            notifyOrderChanged(order, "AFTER_SALE_EXCHANGE_RETURN_RECEIVED", afterSale.getId());
            return hydrate(afterSaleDao.selectById(id));
        }
        completeRefund(afterSale, order);
        if (requiresExternalRefund(order, afterSale)) {
            scheduleExternalRefund(afterSale.getId());
        } else {
            afterSale.setStatus(1);
            if (afterSaleDao.updateAudit(afterSale) != 1) Asserts.fail("售后完成状态保存失败，请刷新后重试");
        }
        notifyOrderChanged(order, "AFTER_SALE_COMPLETED", afterSale.getId());
        return hydrate(afterSaleDao.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale shipExchangeReplacement(Long id, ShopAfterSaleExchangeShipmentDTO dto) {
        if (id == null || dto == null) Asserts.fail("换货物流信息不能为空");
        DmsShopAfterSale afterSale = afterSaleDao.selectByIdForUpdate(id);
        if (afterSale == null) Asserts.fail("售后单不存在");
        DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
        if (order == null) Asserts.fail("订单不存在");
        assertTenantAccess(order.getTenantId());
        assertMerchantAfterSaleAccess(order);
        String company = dto.getDeliveryCompany() == null ? "" : dto.getDeliveryCompany().trim();
        String deliveryNo = dto.getDeliveryNo() == null ? "" : dto.getDeliveryNo().trim();
        if (Integer.valueOf(8).equals(afterSale.getStatus())
                && Integer.valueOf(3).equals(afterSale.getApplyType())
                && company.equals(afterSale.getExchangeDeliveryCompany())
                && deliveryNo.equals(afterSale.getExchangeDeliveryNo())) {
            return hydrate(afterSale);
        }
        if (!Integer.valueOf(3).equals(afterSale.getApplyType()) || !Integer.valueOf(7).equals(afterSale.getStatus())) {
            Asserts.fail("当前售后状态不能发出换货商品");
        }
        if (company.isBlank() || company.length() > 50 || deliveryNo.length() < 4 || deliveryNo.length() > 64
                || !deliveryNo.matches("^[A-Za-z0-9_-]+$")) {
            Asserts.fail("请填写正确的换货物流公司和运单号");
        }
        List<DmsShopAfterSaleItem> items = afterSaleItemDao.selectByAfterSaleId(afterSale.getId());
        if (items.isEmpty()) Asserts.fail("换货商品明细为空");
        for (DmsShopAfterSaleItem item : items) {
            int quantity = item.getRefundQuantity() == null ? 0 : item.getRefundQuantity();
            if (quantity <= 0 || productDao.decreaseStockForExchange(item.getProductId(), quantity) != 1) {
                Asserts.fail((item.getProductName() == null ? "商品" : item.getProductName()) + "可售库存不足，暂不能发出换货商品");
            }
            if (item.getSkuId() != null && skuDao.decreaseStockForExchange(item.getSkuId(), quantity) != 1) {
                Asserts.fail((item.getSkuName() == null ? "商品规格" : item.getSkuName()) + "库存不足，暂不能发出换货商品");
            }
        }
        afterSale.setExchangeDeliveryCompany(company);
        afterSale.setExchangeDeliveryNo(deliveryNo);
        afterSale.setExchangeShippedAt(LocalDateTime.now());
        afterSale.setStatus(8);
        if (afterSaleDao.updateExchangeShipment(afterSale) != 1) Asserts.fail("换货状态已变化，请刷新后重试");
        operationLogService.log("SHOP_AFTER_SALE", "EXCHANGE_SHIPMENT", "SHOP_AFTER_SALE", String.valueOf(id),
                "status=7", "status=8", "同规格换货已发出；售后单=" + afterSale.getAfterSaleNo());
        notifyOrderChanged(order, "AFTER_SALE_EXCHANGE_SHIPPED", afterSale.getId());
        return hydrate(afterSaleDao.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAfterSale confirmExchangeReceived(DmsShopMember member, Long id) {
        if (member == null) Asserts.unauthorized("请先登录");
        DmsShopAfterSale afterSale = afterSaleDao.selectByIdForUpdate(id);
        if (afterSale == null || !member.getUserId().equals(afterSale.getUserId())) Asserts.fail("换货售后单不存在");
        DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
        if (order == null) Asserts.fail("订单不存在");
        assertTenantAccess(order.getTenantId());
        if (Integer.valueOf(1).equals(afterSale.getStatus()) && afterSale.getExchangeReceivedAt() != null) {
            return hydrate(afterSale);
        }
        if (!Integer.valueOf(3).equals(afterSale.getApplyType()) || !Integer.valueOf(8).equals(afterSale.getStatus())) {
            Asserts.fail("替换商品尚未发出或换货已经完成");
        }
        LocalDateTime receivedAt = LocalDateTime.now();
        if (afterSaleDao.completeExchange(afterSale.getId(), receivedAt) != 1) Asserts.fail("换货状态已变化，请刷新后重试");
        notifyOrderChanged(order, "AFTER_SALE_COMPLETED", afterSale.getId());
        return hydrate(afterSaleDao.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoCompleteExpiredExchangeReceipts(int limit) {
        if (exchangeAutoReceiveDays <= 0) return 0;
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int safeDays = Math.min(exchangeAutoReceiveDays, 365);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeDays);
        int completed = 0;
        for (Long id : afterSaleDao.selectExpiredExchangeShipmentIds(cutoff, safeLimit)) {
            DmsShopAfterSale afterSale = afterSaleDao.selectByIdForUpdate(id);
            if (afterSale == null || !Integer.valueOf(3).equals(afterSale.getApplyType())
                    || !Integer.valueOf(8).equals(afterSale.getStatus()) || afterSale.getExchangeShippedAt() == null
                    || afterSale.getExchangeShippedAt().isAfter(cutoff)) continue;
            if (afterSaleDao.completeExchange(id, LocalDateTime.now()) != 1) continue;
            DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
            if (order != null) notifyOrderChanged(order, "AFTER_SALE_COMPLETED", id);
            operationLogService.log("SHOP_AFTER_SALE", "EXCHANGE_AUTO_RECEIVE", "SHOP_AFTER_SALE", String.valueOf(id),
                    "status=8", "status=1", "替换商品发出满" + safeDays + "天，系统自动完成换货");
            completed++;
        }
        return completed;
    }

    /** HTTP 请求中的操作人只信任服务端认证上下文，不信任客户端可编辑字段。 */
    static void applyAuthenticatedAdmin(ShopAfterSaleAuditDTO dto) {
        if (dto == null) return;
        DmsAdminUser actor = AdminContext.get();
        if (actor == null) return;
        dto.setAuditUserId(actor.getId());
        dto.setAuditUserName(actor.getNickname() == null || actor.getNickname().isBlank()
                ? actor.getUsername() : actor.getNickname());
    }

    private void validateRefundHistory(Long orderId) {
        if (afterSaleItemDao.countInvalidReservedItemsByOrderId(orderId) != 0) {
            Asserts.fail("历史售后数量或商品归属异常，请联系平台核查，禁止继续退款");
        }
    }

    private void completeRefund(DmsShopAfterSale afterSale, DmsShopOrder order) {
            List<DmsShopAfterSaleItem> items = afterSaleItemDao.selectByAfterSaleId(afterSale.getId());
            ShopQuantityChecks.refundLines(items);
            validateRefundHistory(order.getId());
            List<DmsShopOrderItem> orderItems = orderItemDao.selectByOrderId(order.getId());
            Map<Long, DmsShopOrderItem> orderItemsById = new LinkedHashMap<>();
            for (DmsShopOrderItem orderItem : orderItems) orderItemsById.put(orderItem.getId(), orderItem);
            BigDecimal bonusRefundAmount = items.stream()
                    .filter(item -> isBonusEligibleOrderItem(orderItemsById.get(item.getOrderItemId())))
                    .map(DmsShopAfterSaleItem::getRefundAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int bonusRefundQuantity = items.stream()
                    .filter(item -> isBonusEligibleOrderItem(orderItemsById.get(item.getOrderItemId())))
                    .map(DmsShopAfterSaleItem::getRefundQuantity).filter(Objects::nonNull)
                    .reduce(0, ShopQuantityChecks::add);
            FinanceRefundDTO refundDTO = new FinanceRefundDTO();
            refundDTO.setOrderId(afterSale.getOrderId());
            refundDTO.setOrderNo(afterSale.getOrderNo());
            refundDTO.setRefundNo(afterSale.getAfterSaleNo());
            refundDTO.setRefundAmount(afterSale.getRefundAmount());
            refundDTO.setProductRefundAmount(afterSale.getProductRefundAmount());
            refundDTO.setFreightRefundAmount(afterSale.getFreightRefundAmount());
            refundDTO.setRefundQuantity(afterSale.getRefundQuantity());
            refundDTO.setBonusBaseAmount(calculateBonusBase(order, orderItems));
            refundDTO.setBonusRefundAmount(bonusRefundAmount);
            refundDTO.setBonusRefundQuantity(bonusRefundQuantity);
            refundDTO.setCumulativeBonusRefundAmount(
                    nullToZero(afterSaleItemDao.sumApprovedBonusRefundByOrderId(order.getId())));
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
                balanceRefund.setRequestId("BALANCE_PAYMENT_REFUND-" + afterSale.getId());
                balanceRefund.setRemark("余额支付售后退款：" + afterSale.getAfterSaleNo());
                memberAssetService.issue(balanceRefund);
            }
            merchantService.reverseAfterSaleItems(items);
            boolean externalRefundPending = requiresExternalRefund(order, afterSale);
            // 余额及模拟支付在当前事务内已完成退款，可立即回补。
            // 支付宝/微信必须等渠道确认成功，再由 ExternalRefundCoordinator 在完成事务中回补。
            if (!externalRefundPending) refundInventoryRestockService.restoreAfterRefundCompleted(afterSale, order);
            int originalQuantity = orderItems.stream()
                    .map(item -> ShopQuantityChecks.positive(item.getQuantity())).reduce(0, ShopQuantityChecks::add);
            if (!externalRefundPending) reconcileOrderStateAfterRefund(order, originalQuantity);
            // 退款后退回非会员：名下已无有效支付订单时自动取消推广资格（含其下级团队自动移交）。
            if (!externalRefundPending) autoDemoteMemberAfterFullRefund(order);
    }

    /**
     * 分批发货后再退掉未发部分时，已发数量可能已经覆盖新的可发数量。
     * 此时订单应进入“已发货”，否则会永久停在待发货且会员无法确认收货。
     */
    private void reconcileOrderStateAfterRefund(DmsShopOrder order, int originalQuantity) {
        int refundedQuantity = afterSaleItemDao.sumApprovedQuantityByOrderId(order.getId());
        ShopQuantityChecks.remaining(originalQuantity, refundedQuantity);
        if (refundedQuantity >= originalQuantity) {
            orderDao.closeAfterSale(order.getId());
            order.setStatus(4);
            return;
        }
        if (!Integer.valueOf(1).equals(order.getStatus())) return;
        int shippableQuantity = Math.max(0, originalQuantity - refundedQuantity);
        if (orderShipmentDao.sumQuantityByOrderId(order.getId()) < shippableQuantity) return;
        List<DmsShopOrderShipment> shipments = orderShipmentDao.selectByOrderId(order.getId());
        if (shipments == null || shipments.isEmpty()) return;
        DmsShopOrderShipment latest = shipments.get(shipments.size() - 1);
        if (orderDao.ship(order.getId(), latest.getDeliveryCompany(), latest.getDeliveryNo()) != 1) {
            Asserts.fail("退款后订单发货状态同步失败，请刷新后重试");
        }
        order.setStatus(2);
        order.setDeliveryCompany(latest.getDeliveryCompany());
        order.setDeliveryNo(latest.getDeliveryNo());
        order.setDeliveryTime(latest.getDeliveryTime());
        // 零元赠品退款无需调用支付退款接口，但仍可能结束微信实付订单的分批发货。
        if (!simulationPaymentEnabled && "WECHAT".equalsIgnoreCase(order.getPayType())) {
            weChatShippingInfoService.enqueue(order);
        }
    }

    private BigDecimal calculateBonusBase(DmsShopOrder order, List<DmsShopOrderItem> items) {
        if (items == null || items.isEmpty()) {
            BigDecimal productAmount = order.getTotalAmount() == null
                    ? nullToZero(order.getPayAmount()).subtract(nullToZero(order.getFreightAmount()))
                    : nullToZero(order.getTotalAmount());
            return productAmount.subtract(nullToZero(order.getDiscountAmount())).max(BigDecimal.ZERO)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }
        BigDecimal gross = items.stream().map(DmsShopOrderItem::getTotalAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal eligible = items.stream().filter(this::isBonusEligibleOrderItem)
                .map(DmsShopOrderItem::getTotalAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (gross.compareTo(BigDecimal.ZERO) <= 0 || eligible.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal eligibleDiscount = nullToZero(order.getDiscountAmount()).multiply(eligible)
                .divide(gross, 2, java.math.RoundingMode.HALF_UP);
        return eligible.subtract(eligibleDiscount).max(BigDecimal.ZERO)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private boolean isBonusEligibleOrderItem(DmsShopOrderItem item) {
        String mode = item == null ? null : item.getTeamBonusMode();
        return mode == null || mode.isBlank() || "INHERIT".equalsIgnoreCase(mode)
                || "STANDARD".equalsIgnoreCase(mode);
    }

    private boolean requiresExternalRefund(DmsShopOrder order, DmsShopAfterSale afterSale) {
        return !simulationPaymentEnabled && order != null && afterSale != null
                && ("ALIPAY".equalsIgnoreCase(order.getPayType())
                    || "WECHAT".equalsIgnoreCase(order.getPayType()))
                && afterSale.getRefundAmount() != null
                && afterSale.getRefundAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    private void scheduleExternalRefund(Long afterSaleId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    externalRefundCoordinator.process(afterSaleId);
                }
            });
        } else {
            externalRefundCoordinator.process(afterSaleId);
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
        if (afterSale == null || order == null
                || (!Integer.valueOf(2).equals(afterSale.getApplyType()) && !Integer.valueOf(3).equals(afterSale.getApplyType()))) return;
        DmsShopServiceAddress defaultAddress = serviceAddressDao.selectDefaultForMerchant(
                order.getTenantId(), order.getMerchantId(), 2);
        Map<Long, DmsShopServiceAddress> addressesById = new LinkedHashMap<>();
        boolean hasMissingAddress = false;
        for (Long productId : (refundItems == null ? List.<DmsShopAfterSaleItem>of() : refundItems).stream()
                .map(DmsShopAfterSaleItem::getProductId).filter(Objects::nonNull).distinct().toList()) {
            DmsShopServiceAddress address = resolveProductReturnAddress(productId, defaultAddress);
            if (address == null) hasMissingAddress = true;
            else addressesById.putIfAbsent(address.getId(), address);
        }
        if (addressesById.size() > 1 || (hasMissingAddress && !addressesById.isEmpty())) {
            Asserts.fail("所选商品使用不同退货地址，请按退货地址分别提交售后申请");
        }
        DmsShopServiceAddress address = addressesById.isEmpty() ? defaultAddress
                : addressesById.values().iterator().next();
        if (address == null) return;
        afterSale.setReturnAddressId(address.getId());
        afterSale.setReturnAddress(joinServiceAddress(address));
    }

    private DmsShopServiceAddress resolveProductReturnAddress(Long productId,
                                                               DmsShopServiceAddress defaultAddress) {
        DmsShopProduct product = productDao.selectById(productId);
        if (product == null || product.getReturnAddressId() == null) return defaultAddress;
        DmsShopServiceAddress address = serviceAddressDao.selectById(product.getReturnAddressId());
        if (address == null || !Integer.valueOf(2).equals(address.getAddressType())
                || !Integer.valueOf(1).equals(address.getStatus())) return defaultAddress;
        return address;
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
            afterSaleTimelinePolicy.enrich(afterSale);
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
        if (order == null || !TenantContext.getTenantId().equals(order.getTenantId() == null ? 1L : order.getTenantId())) return false;
        Long merchantId = currentMerchantId();
        return merchantId == null || merchantId.equals(order.getMerchantId());
    }

    private Long currentMerchantId() {
        return AdminContext.get() == null ? null : AdminContext.get().getMerchantId();
    }

    private void assertPlatformExceptionalOperation(String operation) {
        if (currentMerchantId() != null) {
            Asserts.fail("商户不能执行平台" + operation + "，请处理正常客户售后或联系平台");
        }
    }

    private void assertMerchantAfterSaleAccess(DmsShopOrder order) {
        Long merchantId = currentMerchantId();
        if (merchantId == null) return;
        if (order == null || !merchantId.equals(order.getMerchantId())) Asserts.fail("不能处理其他商户的售后");
        if (merchantDao == null) return;
        DmsMerchant merchant = merchantDao.selectById(merchantId);
        if (merchant == null || !"ENABLED".equals(merchant.getFulfillmentStatus())) {
            Asserts.fail("商户履约权限已由平台接管或冻结，不能处理售后");
        }
    }

    private void assertTenantAccess(Long tenantId) {
        Long dataTenantId = tenantId == null ? 1L : tenantId;
        if (!TenantContext.getTenantId().equals(dataTenantId)) {
            Asserts.fail("无权访问当前租户数据");
        }
    }

    private void notifyOrderChanged(DmsShopOrder order, String changeType, Long afterSaleId) {
        if (orderRealtimeService != null) orderRealtimeService.orderChanged(order, changeType, afterSaleId);
    }
}
