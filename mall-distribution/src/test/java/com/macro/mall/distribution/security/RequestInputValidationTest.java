package com.macro.mall.distribution.security;

import com.macro.mall.distribution.controller.AdminAuthController;
import com.macro.mall.distribution.controller.AgentController;
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
import com.macro.mall.distribution.dto.WithdrawConfirmPayDTO;
import com.macro.mall.distribution.dto.SmsCodeRequestDTO;
import com.macro.mall.distribution.dto.LineChangeAuditDTO;
import com.macro.mall.distribution.entity.DmsShopProduct;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

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
        assertMessages(login, "后台账号不能超过64个字符", "后台密码不能超过64位");

        SmsCodeRequestDTO sms = new SmsCodeRequestDTO();
        sms.setPhone("123");
        sms.setBizType(99);
        sms.setCode("12a456");
        assertMessages(sms, "请输入正确的11位手机号", "短信业务类型不正确", "短信验证码必须是6位数字");
    }

    @Test
    void validatesNestedProductPayloadLengths() {
        DmsShopProduct product = new DmsShopProduct();
        product.setProductName("商".repeat(61));
        ProductPublishDTO publish = new ProductPublishDTO();
        publish.setProduct(product);
        assertMessages(publish, "商品名称不能超过60个字");
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
        payment.setAdminPassword("short");
        assertMessages(payment, "请输入打款流水号", "当前管理员登录密码长度不正确");
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
}
