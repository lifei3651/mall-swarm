package com.macro.mall.distribution.identity;

public interface RealNameVerificationProvider {
    RealNameVerificationResult verify(String realName, String idCard);
}
