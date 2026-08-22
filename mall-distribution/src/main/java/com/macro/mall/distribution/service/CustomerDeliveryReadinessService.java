package com.macro.mall.distribution.service;

import com.macro.mall.common.sms.AliyunSmsProperties;
import com.macro.mall.distribution.config.AlipayConfig;
import com.macro.mall.distribution.dao.DmsErpIntegrationDao;
import com.macro.mall.distribution.dao.DmsShopCategoryDao;
import com.macro.mall.distribution.dao.DmsShopNoticeDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dao.DmsShopServiceAddressDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsShopCategory;
import com.macro.mall.distribution.entity.DmsShopNotice;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.vo.CustomerDeliveryReadinessVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomerDeliveryReadinessService {

    private static final List<String> TEST_MARKERS = List.of("测试", "test", "ceshi", "demo", "样例", "示例", "内测");

    private final DmsTenantDao tenantDao;
    private final DmsShopCategoryDao categoryDao;
    private final DmsShopProductDao productDao;
    private final DmsShopNoticeDao noticeDao;
    private final DmsShopServiceAddressDao serviceAddressDao;
    private final DmsErpIntegrationDao erpIntegrationDao;
    private final AlipayConfig alipayConfig;
    private final AliyunSmsProperties smsProperties;
    private final Environment environment;

    public CustomerDeliveryReadinessVO evaluate(Long tenantId) {
        long actualTenantId = tenantId == null ? 1L : tenantId;
        DmsTenant tenant = tenantDao.selectById(actualTenantId);
        List<CustomerDeliveryReadinessVO.Item> items = new ArrayList<>();
        if (tenant == null) {
            items.add(item("TENANT", "基础资料", "商城主体已建立", true, false,
                    "尚未找到客户商城资料", "/tenant/profile"));
            return new CustomerDeliveryReadinessVO(false, 0, 1, items);
        }

        addTenantItems(items, tenant);
        addOperationItems(items, actualTenantId, tenant);
        addChannelItems(items, actualTenantId);

        int totalRequired = (int) items.stream().filter(CustomerDeliveryReadinessVO.Item::isRequired).count();
        int passedRequired = (int) items.stream().filter(CustomerDeliveryReadinessVO.Item::isRequired)
                .filter(CustomerDeliveryReadinessVO.Item::isPassed).count();
        return new CustomerDeliveryReadinessVO(passedRequired == totalRequired, passedRequired, totalRequired, items);
    }

    private void addTenantItems(List<CustomerDeliveryReadinessVO.Item> items, DmsTenant tenant) {
        add(items, "BRAND", "基础资料", "客户品牌与Logo", true,
                present(tenant.getBrandName()) && present(tenant.getLogoUrl()),
                "填写客户商城名称并上传正式Logo", "/tenant/list");
        add(items, "LEGAL_ENTITY", "经营合规", "经营主体信息", true,
                present(tenant.getTenantName()) && present(tenant.getUnifiedSocialCreditCode())
                        && present(tenant.getCompanyAddress()),
                "填写经营主体、18位统一社会信用代码和经营地址", "/tenant/profile");
        add(items, "CUSTOMER_SERVICE", "经营合规", "客服渠道", true,
                present(tenant.getServicePhone()) && present(tenant.getServiceEmail())
                        && present(tenant.getServiceHours()),
                "填写客服电话、客服邮箱和服务时间", "/tenant/profile");
        add(items, "LICENSE", "经营合规", "营业执照公开", true,
                present(tenant.getBusinessLicenseUrl()) && Integer.valueOf(1).equals(tenant.getShowBusinessLicense()),
                "上传正式营业执照并开启展示", "/tenant/profile");
        add(items, "LEGAL_TEXT", "经营合规", "协议与售后规则", true,
                present(tenant.getUserAgreement()) && present(tenant.getPrivacyPolicy())
                        && present(tenant.getAfterSalePolicy()) && tenant.getAfterSaleWindowDays() != null,
                "确认用户协议、隐私政策和交易售后规则均为客户最终版本", "/tenant/legal");
        boolean specialModeSafe = !Integer.valueOf(1).equals(tenant.getFlashSaleEnabled())
                || !"CUSTOM".equalsIgnoreCase(tenant.getFlashSaleBonusMode());
        specialModeSafe = specialModeSafe && (!Integer.valueOf(1).equals(tenant.getRepurchaseMallEnabled())
                || !"CUSTOM".equalsIgnoreCase(tenant.getRepurchaseBonusMode()));
        add(items, "SPECIAL_MODE", "业务规则", "秒杀与复购规则可执行", true, specialModeSafe,
                "CUSTOM模式必须完成客户专属奖金规则开发和验收后才能启用", "/tenant/business-modes");
    }

    private void addOperationItems(List<CustomerDeliveryReadinessVO.Item> items, long tenantId, DmsTenant tenant) {
        boolean shippingAddress = serviceAddressDao.selectDefault(tenantId, 1) != null;
        boolean returnAddress = serviceAddressDao.selectDefault(tenantId, 2) != null;
        add(items, "SHIPPING_ADDRESS", "履约售后", "默认发货地址", true, shippingAddress,
                "配置完整省市区、联系人和联系电话", "/shop/service-addresses");
        add(items, "RETURN_ADDRESS", "履约售后", "默认退货地址", true, returnAddress,
                "配置客户真实售后退货地址", "/shop/service-addresses");

        List<DmsShopCategory> categories = categoryDao.selectList(tenantId, 1);
        List<DmsShopProduct> products = productDao.selectList(tenantId, null, null, 1, null, null);
        add(items, "CATALOG", "商品运营", "正式商品与分类", true,
                !categories.isEmpty() && !products.isEmpty(),
                "至少保留一个启用分类和一个已上架商品", "/shop/products");

        List<DmsShopNotice> notices = noticeDao.selectList(tenantId, 1);
        long suspiciousProducts = products.stream().filter(this::suspiciousProduct).count();
        long suspiciousCategories = categories.stream().filter(category -> hasTestMarker(category.getCategoryName())).count();
        long suspiciousNotices = notices.stream().filter(notice -> hasTestMarker(notice.getTitle())
                || hasTestMarker(notice.getContent())).count();
        long suspiciousTotal = suspiciousProducts + suspiciousCategories + suspiciousNotices;
        add(items, "CLEAN_DATA", "商品运营", "测试内容已清理或复核", true, suspiciousTotal == 0,
                suspiciousTotal == 0 ? "未发现明显测试名称或0.01元上架商品"
                        : "发现" + suspiciousTotal + "项测试标识或0.01元上架商品，交付前需逐项复核",
                "/shop/products");

        boolean filingReady = present(tenant.getIcpNumber());
        add(items, "ICP", "经营合规", "网站备案信息", true, filingReady,
                "填写客户正式域名对应的ICP备案号", "/tenant/profile");
    }

    private void addChannelItems(List<CustomerDeliveryReadinessVO.Item> items, long tenantId) {
        boolean simulationEnabled = Boolean.parseBoolean(environment.getProperty("shop.payment.simulation-enabled", "false"));
        add(items, "PAYMENT", "外部服务", "正式支付通道", true,
                !simulationEnabled && alipayConfig.isConfigured()
                        && secureUrl(alipayConfig.getNotifyUrl()) && secureUrl(alipayConfig.getReturnUrl()),
                "关闭模拟支付，并配置同一支付宝应用的APPID、商户PID、密钥、HTTPS通知与回跳地址", "/tenant/profile");

        boolean smsEnabled = Boolean.parseBoolean(environment.getProperty("sms.provider-enabled", "false"));
        boolean smsConfigured = smsEnabled && present(smsProperties.getAccessKeyId())
                && present(smsProperties.getAccessKeySecret()) && present(smsProperties.getSignName())
                && smsProperties.getTemplates() != null && !smsProperties.getTemplates().isEmpty();
        add(items, "SMS", "外部服务", "正式短信通道", true, smsConfigured,
                "启用短信服务并通过服务器环境变量配置签名、模板和密钥", "/tenant/profile");

        boolean erpEnabled = !erpIntegrationDao.selectEnabled(tenantId).isEmpty();
        add(items, "ERP", "可选集成", "ERP订单对接", false, erpEnabled,
                erpEnabled ? "已启用ERP集成，交付前仍需完成推单和发货回传实测"
                        : "客户未指定ERP时可保持关闭，不影响商城独立发货",
                "/tenant/erp");
        String trackingProvider = environment.getProperty("shop.logistics.tracking-provider", "NONE");
        boolean trackingEnabled = present(trackingProvider) && !"NONE".equalsIgnoreCase(trackingProvider);
        add(items, "LOGISTICS_TRACKING", "可选集成", "真实物流轨迹", false, trackingEnabled,
                trackingEnabled ? "已选择物流轨迹适配器，交付前仍需使用真实运单完成查询实测"
                        : "当前只展示承运商和运单号；客户确定物流查询服务商及授权后再启用真实轨迹",
                "/tenant/erp");
    }

    private CustomerDeliveryReadinessVO.Item item(String code, String group, String title, boolean required,
                                                    boolean passed, String detail, String actionPath) {
        return new CustomerDeliveryReadinessVO.Item(code, group, title, required, passed, detail, actionPath);
    }

    private void add(List<CustomerDeliveryReadinessVO.Item> items, String code, String group, String title,
                     boolean required, boolean passed, String detail, String actionPath) {
        items.add(item(code, group, title, required, passed, detail, actionPath));
    }

    private boolean suspiciousProduct(DmsShopProduct product) {
        return hasTestMarker(product.getProductName()) || hasTestMarker(product.getSubtitle())
                || (product.getSalePrice() != null && product.getSalePrice().compareTo(new BigDecimal("0.01")) <= 0);
    }

    private boolean hasTestMarker(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return TEST_MARKERS.stream().anyMatch(normalized::contains);
    }

    private boolean secureUrl(String value) {
        return present(value) && value.trim().toLowerCase(Locale.ROOT).startsWith("https://");
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
