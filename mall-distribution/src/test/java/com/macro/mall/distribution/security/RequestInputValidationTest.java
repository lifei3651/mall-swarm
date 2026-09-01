package com.macro.mall.distribution.security;

import com.macro.mall.distribution.controller.AdminAuthController;
import com.macro.mall.distribution.controller.AgentController;
import com.macro.mall.distribution.controller.BonusEngineConfigController;
import com.macro.mall.distribution.controller.CommissionController;
import com.macro.mall.distribution.controller.MemberAssetController;
import com.macro.mall.distribution.controller.ShopController;
import com.macro.mall.distribution.controller.SmsController;
import com.macro.mall.distribution.dto.AdminAssetChangeDTO;
import com.macro.mall.distribution.dto.AdminMemberPasswordResetDTO;
import com.macro.mall.distribution.dto.AdminMemberPhoneUpdateDTO;
import com.macro.mall.distribution.dto.AdminLoginDTO;
import com.macro.mall.distribution.dto.AgentLevelAdjustDTO;
import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.dto.AgentUpdateDTO;
import com.macro.mall.distribution.dto.ProductPublishDTO;
import com.macro.mall.distribution.dto.ErpShipmentCallbackDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleReturnShipmentDTO;
import com.macro.mall.distribution.dto.ShopOrderShipDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.dto.CommissionQueryDTO;
import com.macro.mall.distribution.dto.CommissionCancelDTO;
import com.macro.mall.distribution.dto.CommissionSettlementBatchCreateDTO;
import com.macro.mall.distribution.dto.BonusSimulationDTO;
import com.macro.mall.distribution.dto.WithdrawConfirmPayDTO;
import com.macro.mall.distribution.dto.SmsCodeRequestDTO;
import com.macro.mall.distribution.dto.LineChangeAuditDTO;
import com.macro.mall.distribution.entity.DmsShopProduct;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestInputValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsOversizedAndMalformedPublicInputs() {
        AdminLoginDTO login = new AdminLoginDTO();
        login.setUsername("a".repeat(65));
        login.setPassword("p".repeat(65));
        login.setPortal("UNKNOWN");
        assertMessages(login, "后台账号不能超过64个字符", "后台密码不能超过64位", "后台登录入口不正确");

        AdminLoginDTO missingPortal = new AdminLoginDTO();
        missingPortal.setUsername("operator");
        missingPortal.setPassword("Valid-password-123");
        assertMessages(missingPortal, "后台登录入口不能为空");

        SmsCodeRequestDTO sms = new SmsCodeRequestDTO();
        sms.setPhone("123");
        sms.setBizType(99);
        sms.setCode("12a456");
        assertMessages(sms, "请输入正确的11位手机号", "短信业务类型不正确", "短信验证码必须是6位数字");
    }

    @Test
    void rejectsMalformedCommissionAdministrationInputs() {
        CommissionCancelDTO cancel = new CommissionCancelDTO();
        cancel.setCancelReason(" ");
        assertMessages(cancel, "请输入取消原因");
        cancel.setCancelReason("原".repeat(201));
        assertMessages(cancel, "取消原因不能超过200个字符");

        CommissionSettlementBatchCreateDTO batch = new CommissionSettlementBatchCreateDTO();
        batch.setRemark("备".repeat(501));
        assertMessages(batch, "结算批次备注不能超过500个字符");

        BonusSimulationDTO simulation = new BonusSimulationDTO();
        simulation.setTenantId(0L);
        simulation.setOrderUserId(-1L);
        simulation.setOrderMemberKey("会".repeat(65));
        simulation.setOrderAmount(new BigDecimal("-0.01"));
        assertMessages(simulation, "客户编号不正确", "下单会员编号不正确",
                "会员登录账号或手机号不能超过64个字符", "订单金额必须大于0");
    }

    @Test
    void validatesNestedProductPayloadLengths() {
        DmsShopProduct product = new DmsShopProduct();
        product.setProductName("商".repeat(61));
        ProductPublishDTO publish = new ProductPublishDTO();
        publish.setProduct(product);
        assertMessages(publish, "商品名称不能超过60个字");

        ShopOrderSubmitDTO order = new ShopOrderSubmitDTO();
        order.setReceiverName("收".repeat(31));
        order.setReceiverAddress("址".repeat(513));
        order.setReceiverDetailAddress("详".repeat(201));
        order.setRemark("备".repeat(501));
        assertMessages(order, "收货人不能超过30个字", "收货地址不能超过512个字",
                "详细地址不能超过200个字", "订单备注不能超过500个字");

        CommissionQueryDTO commission = new CommissionQueryDTO();
        commission.setMemberKey("会".repeat(65));
        commission.setOrderNo("O".repeat(65));
        assertMessages(commission, "会员查询条件不能超过64个字符", "订单编号不能超过64个字符");
    }

    @Test
    void rejectsUnscopedErpCallbacksAndMalformedReturnTrackingNumbers() {
        ErpShipmentCallbackDTO callback = new ErpShipmentCallbackDTO();
        callback.setProviderCode("JUSHUITAN");
        callback.setToken("token");
        callback.setOrderNo("ORDER-1");
        callback.setDeliveryCompany("物流公司".repeat(20));
        callback.setDeliveryNo("SF12345678");
        assertMessages(callback, "ERP客户租户不能为空", "物流公司名称不能超过50个字");

        ShopAfterSaleReturnShipmentDTO shipment = new ShopAfterSaleReturnShipmentDTO();
        shipment.setDeliveryCompany("物流公司".repeat(20));
        shipment.setDeliveryNo("../bad tracking no");
        assertMessages(shipment, "物流公司名称不能超过50个字", "退货运单号只能包含字母、数字、下划线和短横线");

        ShopOrderShipDTO outbound = new ShopOrderShipDTO();
        outbound.setDeliveryCompany("物流公司".repeat(20));
        outbound.setDeliveryNo("../bad tracking no");
        assertMessages(outbound, "物流公司名称不能超过50个字", "物流单号只能包含字母、数字、下划线和短横线");

        WithdrawConfirmPayDTO payment = new WithdrawConfirmPayDTO();
        payment.setPayNo(" ");
        assertMessages(payment, "请输入打款流水号");
    }

    @Test
    void controllersActivateBeanValidationBeforeBusinessServices() {
        assertValidParameter(AdminAuthController.class, "login", AdminLoginDTO.class);
        assertValidParameter(SmsController.class, "sendCode", SmsCodeRequestDTO.class);
        assertValidParameter(ShopController.class, "publishProduct", ProductPublishDTO.class);
        assertValidParameter(AgentController.class, "register", AgentRegisterDTO.class);
        assertValidParameter(AgentController.class, "updateAgent", AgentUpdateDTO.class);
        assertValidParameter(AgentController.class, "adjustLevel", AgentLevelAdjustDTO.class);
        assertValidParameter(AgentController.class, "switchLine", AgentSwitchLineDTO.class);
        assertValidParameter(AgentController.class, "auditLineChange", LineChangeAuditDTO.class);
        assertValidParameter(ShopController.class, "updateMemberPhone", AdminMemberPhoneUpdateDTO.class);
        assertValidParameter(ShopController.class, "resetMemberLoginPassword", AdminMemberPasswordResetDTO.class);
        assertValidParameter(ShopController.class, "adjustMemberLevel", AgentLevelAdjustDTO.class);
        assertValidParameter(MemberAssetController.class, "issue", AdminAssetChangeDTO.class);
        assertValidParameter(MemberAssetController.class, "deduct", AdminAssetChangeDTO.class);
        assertValidParameter(CommissionController.class, "getCommissionRecords", CommissionQueryDTO.class);
        assertValidParameter(CommissionController.class, "cancelCommission", CommissionCancelDTO.class);
        assertValidParameter(CommissionController.class, "createSettlementBatch", CommissionSettlementBatchCreateDTO.class);
        assertValidParameter(BonusEngineConfigController.class, "simulate", BonusSimulationDTO.class);
        assertModelAttributeParameter(CommissionController.class, "cancelCommission", CommissionCancelDTO.class);
        assertTrue(MemberAssetController.class.isAnnotationPresent(Validated.class),
                "MemberAssetController must activate request parameter constraints");
        assertSizedStringParameters(MemberAssetController.class, "searchFlows", 100, 2);
        assertSizedStringParameters(MemberAssetController.class, "summarizeFlows", 100, 2);
    }

    @Test
    void rejectsMalformedAdministrativeMemberAndMoneyCommands() {
        AgentRegisterDTO register = new AgentRegisterDTO();
        register.setUserId(0L);
        register.setPhone("123");
        register.setInitialLevel(99);
        register.setSourceType(0);
        assertMessages(register, "商城账号编号不正确", "请输入正确的11位手机号",
                "初始会员级别不正确", "会员来源类型不正确");

        AgentUpdateDTO update = new AgentUpdateDTO();
        update.setPhone("123");
        update.setIdCard("not-an-id-card");
        assertMessages(update, "请输入正确的11位手机号", "请输入正确的15位或18位身份证号");

        AgentLevelAdjustDTO level = new AgentLevelAdjustDTO();
        level.setLevel(99);
        level.setReason(" ");
        assertMessages(level, "会员级别不正确", "请输入调级原因");

        AgentSwitchLineDTO switchLine = new AgentSwitchLineDTO();
        switchLine.setAgentId(-1L);
        switchLine.setNewParentAgentId(0L);
        assertMessages(switchLine, "移线会员编号不正确", "新直属上级编号不正确", "移线原因不能为空");

        LineChangeAuditDTO audit = new LineChangeAuditDTO();
        audit.setStatus(9);
        assertMessages(audit, "审批结果无效", "审批意见不能为空");

        AdminMemberPhoneUpdateDTO phone = new AdminMemberPhoneUpdateDTO();
        phone.setPhone("10086");
        assertMessages(phone, "请输入正确的11位手机号", "请填写修改手机号的原因",
                "请输入当前管理员登录密码");

        AdminMemberPasswordResetDTO password = new AdminMemberPasswordResetDTO();
        password.setNewPassword(" ");
        assertMessages(password, "请输入新登录密码", "请填写重置登录密码的原因",
                "请输入当前管理员登录密码");

        AdminAssetChangeDTO asset = new AdminAssetChangeDTO();
        asset.setAmount(new BigDecimal("-1"));
        asset.setRequestId("bad-request-id");
        assertMessages(asset, "资产数量必须大于0", "余额调整请求号无效，请关闭窗口后重试",
                "余额调整必须填写原因", "请输入当前管理员登录密码", "请选择需要调整余额的会员");
    }

    private void assertMessages(Object input, String... expected) {
        Set<String> actual = validator.validate(input).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());
        for (String message : expected) {
            assertTrue(actual.contains(message), "missing validation message: " + message + ", actual=" + actual);
        }
    }

    private void assertValidParameter(Class<?> controller, String methodName, Class<?> dtoType) {
        Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .filter(candidate -> java.util.Arrays.stream(candidate.getParameterTypes()).anyMatch(dtoType::equals))
                .findFirst()
                .orElseThrow();
        Parameter parameter = java.util.Arrays.stream(method.getParameters())
                .filter(candidate -> candidate.getType().equals(dtoType))
                .findFirst()
                .orElseThrow();
        assertNotNull(parameter.getAnnotation(Valid.class),
                controller.getSimpleName() + "." + methodName + " must validate " + dtoType.getSimpleName());
    }

    private void assertSizedStringParameters(Class<?> controller, String methodName, int max, int expectedCount) {
        Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        long count = java.util.Arrays.stream(method.getParameters())
                .filter(parameter -> parameter.getType().equals(String.class))
                .map(parameter -> parameter.getAnnotation(Size.class))
                .filter(java.util.Objects::nonNull)
                .filter(size -> size.max() == max)
                .count();
        assertTrue(count >= expectedCount,
                controller.getSimpleName() + "." + methodName + " must limit query text length");
    }

    private void assertModelAttributeParameter(Class<?> controller, String methodName, Class<?> dtoType) {
        Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Parameter parameter = java.util.Arrays.stream(method.getParameters())
                .filter(candidate -> candidate.getType().equals(dtoType))
                .findFirst()
                .orElseThrow();
        assertNotNull(parameter.getAnnotation(ModelAttribute.class),
                controller.getSimpleName() + "." + methodName + " must bind the query model used by the admin client");
    }
}
