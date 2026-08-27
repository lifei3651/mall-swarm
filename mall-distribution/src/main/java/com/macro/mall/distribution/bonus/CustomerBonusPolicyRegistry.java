package com.macro.mall.distribution.bonus;

import com.macro.mall.common.exception.Asserts;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 按客户项目启用的规则版本找到对应奖金程序。 */
@Component
public class CustomerBonusPolicyRegistry {

    private final Map<String, CustomerBonusPolicy> policies = new LinkedHashMap<>();

    public CustomerBonusPolicyRegistry(List<CustomerBonusPolicy> candidates) {
        for (CustomerBonusPolicy candidate : candidates) {
            String code = normalize(candidate.policyCode());
            if (code.isBlank()) {
                throw new IllegalStateException("客户奖金程序编码不能为空");
            }
            if (policies.putIfAbsent(code, candidate) != null) {
                throw new IllegalStateException("客户奖金程序编码重复: " + code);
            }
        }
    }

    public CustomerBonusPolicy require(String policyCode) {
        String code = normalize(policyCode);
        CustomerBonusPolicy policy = policies.get(code);
        if (policy == null) {
            Asserts.fail("当前客户奖金程序尚未接入，已阻止产生不确定奖金");
        }
        return policy;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
