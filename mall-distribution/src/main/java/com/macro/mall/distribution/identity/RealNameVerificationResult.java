package com.macro.mall.distribution.identity;

public record RealNameVerificationResult(boolean matched, String resultCode, String requestId) {
}
