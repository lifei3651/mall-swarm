package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.constants.ShopBusinessType;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.vo.ShopBusinessConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ShopBusinessModeService {

    private final DmsTenantDao tenantDao;
    private final DmsAgentDao agentDao;

    public ShopBusinessConfigVO config(Long tenantId, DmsShopMember member) {
        DmsTenant tenant = tenantDao.selectById(tenantId);
        boolean eligible = isRepurchaseEligible(tenant, member);
        return new ShopBusinessConfigVO(
                enabled(tenant == null ? null : tenant.getFlashSaleEnabled()) ? 1 : 0,
                mode(tenant == null ? null : tenant.getFlashSaleBonusMode(), "NONE"),
                enabled(tenant == null ? null : tenant.getRepurchaseMallEnabled()) ? 1 : 0,
                mode(tenant == null ? null : tenant.getRepurchaseEligibilityMode(), "PAID_MEMBER"),
                mode(tenant == null ? null : tenant.getRepurchaseBonusMode(), "NONE"),
                eligible,
                repurchaseHint(tenant, eligible));
    }

    public DmsTenant requireEnabled(Long tenantId, String businessType, DmsShopMember member) {
        DmsTenant tenant = tenantDao.selectById(tenantId);
        String type = normalizeType(businessType);
        if (ShopBusinessType.FLASH_SALE.equals(type)) {
            if (!enabled(tenant == null ? null : tenant.getFlashSaleEnabled())) Asserts.fail("秒杀专区尚未开启");
            rejectUnconfiguredCustom(tenant == null ? null : tenant.getFlashSaleBonusMode(), "秒杀");
        } else if (ShopBusinessType.REPURCHASE.equals(type)) {
            if (!enabled(tenant == null ? null : tenant.getRepurchaseMallEnabled())) Asserts.fail("复购商城尚未开启");
            if (!isRepurchaseEligible(tenant, member)) Asserts.fail(repurchaseHint(tenant, false));
            rejectUnconfiguredCustom(tenant == null ? null : tenant.getRepurchaseBonusMode(), "复购");
        }
        return tenant;
    }

    public boolean usesStandardBonus(DmsTenant tenant, String businessType) {
        String type = normalizeType(businessType);
        if (ShopBusinessType.NORMAL.equals(type)) return true;
        String bonusMode = ShopBusinessType.FLASH_SALE.equals(type)
                ? mode(tenant == null ? null : tenant.getFlashSaleBonusMode(), "NONE")
                : mode(tenant == null ? null : tenant.getRepurchaseBonusMode(), "NONE");
        return "STANDARD".equals(bonusMode);
    }

    public String normalizeType(String value) {
        String normalized = value == null || value.isBlank() ? ShopBusinessType.NORMAL
                : value.trim().toUpperCase(Locale.ROOT);
        if (!ShopBusinessType.ALL.contains(normalized)) Asserts.fail("订单业务类型不正确");
        return normalized;
    }

    private boolean isRepurchaseEligible(DmsTenant tenant, DmsShopMember member) {
        if (!enabled(tenant == null ? null : tenant.getRepurchaseMallEnabled()) || member == null
                || member.getUserId() == null || !Integer.valueOf(1).equals(member.getStatus())) return false;
        String mode = mode(tenant.getRepurchaseEligibilityMode(), "PAID_MEMBER");
        if ("ALL_MEMBER".equals(mode)) return true;
        DmsAgent agent = agentDao.selectByUserId(member.getUserId());
        if (agent == null || !Integer.valueOf(1).equals(agent.getStatus())) return false;
        if ("AGENT".equals(mode)) return agent.getAgentLevel() != null && agent.getAgentLevel() >= 4;
        return true;
    }

    private String repurchaseHint(DmsTenant tenant, boolean eligible) {
        if (eligible) return "您可以进入复购商城";
        if (!enabled(tenant == null ? null : tenant.getRepurchaseMallEnabled())) return "复购商城尚未开启";
        return "AGENT".equals(mode(tenant.getRepurchaseEligibilityMode(), "PAID_MEMBER"))
                ? "复购商城仅向达到代理级别的会员开放" : "按本商城规则开通推广资格后可进入复购商城";
    }

    private void rejectUnconfiguredCustom(String value, String label) {
        if ("CUSTOM".equals(mode(value, "NONE"))) {
            Asserts.fail(label + "客户定制奖金规则尚未配置，当前禁止创建订单");
        }
    }

    private boolean enabled(Integer value) { return Integer.valueOf(1).equals(value); }
    private String mode(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
}
