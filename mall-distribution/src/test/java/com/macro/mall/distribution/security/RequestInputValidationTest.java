package com.macro.mall.distribution.security;

import com.macro.mall.distribution.controller.AdminAuthController;
import com.macro.mall.distribution.controller.ShopController;
import com.macro.mall.distribution.controller.SmsController;
import com.macro.mall.distribution.dto.AdminLoginDTO;
import com.macro.mall.distribution.dto.ProductPublishDTO;
import com.macro.mall.distribution.dto.ErpShipmentCallbackDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleReturnShipmentDTO;
import com.macro.mall.distribution.dto.SmsCodeRequestDTO;
import com.macro.mall.distribution.entity.DmsShopProduct;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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
        callback.setDeliveryCompany("顺丰速运");
        callback.setDeliveryNo("SF12345678");
        assertMessages(callback, "ERP客户租户不能为空");

        ShopAfterSaleReturnShipmentDTO shipment = new ShopAfterSaleReturnShipmentDTO();
        shipment.setDeliveryCompany("顺丰速运");
        shipment.setDeliveryNo("../bad tracking no");
        assertMessages(shipment, "退货运单号只能包含字母、数字、下划线和短横线");
    }

    @Test
    void controllersActivateBeanValidationBeforeBusinessServices() {
        assertValidParameter(AdminAuthController.class, "login", AdminLoginDTO.class);
        assertValidParameter(SmsController.class, "sendCode", SmsCodeRequestDTO.class);
        assertValidParameter(ShopController.class, "publishProduct", ProductPublishDTO.class);
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
