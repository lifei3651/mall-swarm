package com.macro.mall.distribution.security;

import com.macro.mall.common.annotation.Idempotent;
import com.macro.mall.distribution.controller.ShopController;
import com.macro.mall.distribution.controller.ShopWalletController;
import com.macro.mall.distribution.dto.BalancePayDTO;
import com.macro.mall.distribution.dto.BalanceTransferDTO;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.dto.ShopWithdrawalApplyDTO;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticalRequestProtectionTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMalformedRegistrationBeforeBusinessExecution() {
        ShopRegisterDTO dto = new ShopRegisterDTO();
        dto.setPhone("123");
        dto.setUsername("中文账号");
        dto.setPassword("123");
        dto.setSmsCode("12345");

        Set<String> messages = validator.validate(dto).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());

        assertTrue(messages.contains("请输入正确的11位手机号"));
        assertTrue(messages.contains("登录账号需为4至20位，必须以英文字母开头且仅支持字母、数字和下划线"));
        assertTrue(messages.contains("登录密码需为6至32位"));
        assertTrue(messages.contains("短信验证码必须是6位数字"));
    }

    @Test
    void validatesNestedOrderItems() {
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setQuantity(0);
        ShopOrderSubmitDTO dto = new ShopOrderSubmitDTO();
        dto.setItems(List.of(item));

        Set<String> messages = validator.validate(dto).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());

        assertTrue(messages.contains("商品不能为空"));
        assertTrue(messages.contains("商品数量必须大于0"));
    }

    @Test
    void criticalControllersEnableBoundaryValidation() {
        assertValidParameter(ShopController.class, "register", ShopRegisterDTO.class);
        assertValidParameter(ShopController.class, "submitOrder", ShopOrderSubmitDTO.class);
        assertValidParameter(ShopWalletController.class, "transfer", BalanceTransferDTO.class);
        assertValidParameter(ShopWalletController.class, "payOrder", BalancePayDTO.class);
        assertValidParameter(ShopWalletController.class, "applyWithdrawal", ShopWithdrawalApplyDTO.class);
    }

    @Test
    void criticalMoneyWritesKeepIdempotencyProtection() {
        assertIdempotent(ShopController.class, "submitOrder");
        assertIdempotent(ShopWalletController.class, "transfer");
        assertIdempotent(ShopWalletController.class, "payOrder");
        assertIdempotent(ShopWalletController.class, "applyWithdrawal");
    }

    private void assertValidParameter(Class<?> controller, String methodName, Class<?> dtoType) {
        Method method = findMethod(controller, methodName);
        Parameter parameter = java.util.Arrays.stream(method.getParameters())
                .filter(candidate -> candidate.getType().equals(dtoType))
                .findFirst()
                .orElseThrow();
        assertNotNull(parameter.getAnnotation(Valid.class),
                controller.getSimpleName() + "." + methodName + " must validate " + dtoType.getSimpleName());
    }

    private void assertIdempotent(Class<?> controller, String methodName) {
        assertNotNull(findMethod(controller, methodName).getAnnotation(Idempotent.class),
                controller.getSimpleName() + "." + methodName + " must remain idempotent");
    }

    private Method findMethod(Class<?> controller, String methodName) {
        return java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }
}
