package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dto.AgencyRuleDraft;
import com.macro.mall.distribution.entity.DmsTenant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Null mode preserves 177; explicit modes never borrow legacy qualification/bonus algorithms. */
public final class CustomerBusinessModePolicy {
    public static final String NORMAL = "NORMAL";
    public static final String AGENCY = "AGENCY";
    private static final ObjectMapper JSON = new ObjectMapper();
    private CustomerBusinessModePolicy() {}
    public static boolean legacy(DmsTenant tenant) {
        return tenant != null && (tenant.getBusinessMode() == null || tenant.getBusinessMode().isBlank());
    }
    public static boolean legacyOrMissing(DmsTenant tenant) { return tenant == null || legacy(tenant); }
    public static boolean invitation(DmsTenant tenant) {
        return legacy(tenant) && !Integer.valueOf(0).equals(tenant.getInvitationEnabled());
    }
    public static void requireLegacyQualification(DmsTenant tenant) {
        if (!legacy(tenant)) Asserts.fail("当前模式未开放代理资格；请先完成客户购买门槛及开通规则的实现和验证");
    }
    public static String validateMode(String value) {
        if (!NORMAL.equals(value) && !AGENCY.equals(value)) Asserts.fail("请选择普通商城或直销／代理模式");
        return value;
    }
    public static String encode(AgencyRuleDraft draft) {
        try { return draft == null ? null : JSON.writeValueAsString(draft); }
        catch (Exception e) { Asserts.fail("代理规则草稿格式不正确"); return null; }
    }
    public static AgencyRuleDraft draft(DmsTenant tenant) {
        try { return tenant.getAgencyRuleDraft() == null ? null : JSON.readValue(tenant.getAgencyRuleDraft(), AgencyRuleDraft.class); }
        catch (Exception e) { Asserts.fail("代理规则草稿不可读取，请核对配置"); return null; }
    }
    public static String revision(DmsTenant tenant) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (String.valueOf(tenant.getBusinessMode()) + "\n" + String.valueOf(tenant.getAgencyRuleDraft())).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static Status status(DmsTenant tenant) {
        if (legacy(tenant)) return new Status("LEGACY", List.of(), "沿用存量配置，不自动转换模式");
        if (tenant == null || NORMAL.equals(tenant.getBusinessMode())) return new Status("NOT_REQUIRED", List.of(), "普通商城不开放代理资格和新代理奖励");
        AgencyRuleDraft d = draft(tenant);
        List<String> missing = new ArrayList<>();
        if (d == null || d.getThresholdType() == null) missing.add("购买门槛种类");
        if (d == null || ("SPECIFIED_PRODUCTS".equals(d.getThresholdType())
                ? d.getProductIds() == null || d.getProductIds().isEmpty() : d.getThresholdAmount() == null)) missing.add("购买金额或指定商品");
        if (d == null || d.getOpeningMethod() == null) missing.add("资格开通方式");
        if (d == null || d.getTargetLevel() == null) missing.add("目标资格等级");
        if (d == null || blank(d.getAttributionRule())) missing.add("邀请与代理归属规则");
        if (d == null || blank(d.getRefundRule())) missing.add("退款后的资格处理");
        if (d == null || blank(d.getRewardPolicy())) missing.add("客户奖励制度");
        return new Status(d == null ? "NOT_CONFIGURED" : missing.isEmpty() ? "PENDING_IMPLEMENTATION" : "INCOMPLETE",
                List.copyOf(missing), "仅保存客户规则草稿；购买门槛、资格和奖励尚待客户实现与验证，当前不能启用");
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    public record Status(String state, List<String> missingItems, String message) {}
}
