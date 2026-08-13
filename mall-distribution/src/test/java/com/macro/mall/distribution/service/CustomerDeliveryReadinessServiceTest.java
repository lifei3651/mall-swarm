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
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopServiceAddress;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.vo.CustomerDeliveryReadinessVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerDeliveryReadinessServiceTest {

    @Mock private DmsTenantDao tenantDao;
    @Mock private DmsShopCategoryDao categoryDao;
    @Mock private DmsShopProductDao productDao;
    @Mock private DmsShopNoticeDao noticeDao;
    @Mock private DmsShopServiceAddressDao serviceAddressDao;
    @Mock private DmsErpIntegrationDao erpIntegrationDao;
    @Mock private Environment environment;

    private CustomerDeliveryReadinessService service;
    private DmsShopProduct product;

    @BeforeEach
    void setUp() {
        AlipayConfig alipay = new AlipayConfig();
        alipay.setEnabled(true);
        alipay.setAppId("app");
        alipay.setPrivateKey("private");
        alipay.setAlipayPublicKey("public");
        alipay.setNotifyUrl("https://mall.example/api/pay/alipay/notify");
        alipay.setReturnUrl("https://mall.example/api/pay/alipay/return");
        AliyunSmsProperties sms = new AliyunSmsProperties();
        sms.setAccessKeyId("id");
        sms.setAccessKeySecret("secret");
        sms.setSignName("客户商城");
        sms.setTemplates(Map.of("REGISTER", "SMS_1"));
        service = new CustomerDeliveryReadinessService(tenantDao, categoryDao, productDao, noticeDao,
                serviceAddressDao, erpIntegrationDao, alipay, sms, environment);

        when(tenantDao.selectById(1L)).thenReturn(completeTenant());
        DmsShopCategory category = new DmsShopCategory();
        category.setCategoryName("健康食品");
        when(categoryDao.selectList(1L, 1)).thenReturn(List.of(category));
        product = new DmsShopProduct();
        product.setProductName("营养礼盒");
        product.setSalePrice(new BigDecimal("99.00"));
        when(productDao.selectList(1L, null, null, 1, null)).thenReturn(List.of(product));
        when(noticeDao.selectList(1L, 1)).thenReturn(List.of());
        when(serviceAddressDao.selectDefault(anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new DmsShopServiceAddress());
        when(erpIntegrationDao.selectEnabled(1L)).thenReturn(List.of());
        when(environment.getProperty("shop.payment.simulation-enabled", "false")).thenReturn("false");
        when(environment.getProperty("sms.provider-enabled", "false")).thenReturn("true");
    }

    @Test
    void reportsReadyOnlyWhenEveryRequiredDeliveryItemPasses() {
        CustomerDeliveryReadinessVO result = service.evaluate(1L);
        assertTrue(result.isReady());
        assertTrue(result.getItems().stream().filter(CustomerDeliveryReadinessVO.Item::isRequired)
                .allMatch(CustomerDeliveryReadinessVO.Item::isPassed));
    }

    @Test
    void flagsTestProductsAndOneCentProductsBeforeCustomerDelivery() {
        product.setProductName("0.01元测试商品");
        product.setSalePrice(new BigDecimal("0.01"));

        CustomerDeliveryReadinessVO result = service.evaluate(1L);

        assertFalse(result.isReady());
        assertFalse(result.getItems().stream().filter(item -> "CLEAN_DATA".equals(item.getCode()))
                .findFirst().orElseThrow().isPassed());
    }

    private DmsTenant completeTenant() {
        DmsTenant tenant = new DmsTenant();
        tenant.setId(1L);
        tenant.setTenantName("客户科技有限公司");
        tenant.setBrandName("客户商城");
        tenant.setLogoUrl("/api/shop/media/images/logo.png");
        tenant.setUnifiedSocialCreditCode("91310000MA1234567X");
        tenant.setCompanyAddress("客户正式经营地址");
        tenant.setServicePhone("400-123-4567");
        tenant.setServiceEmail("service@example.com");
        tenant.setServiceHours("9:00-21:00");
        tenant.setBusinessLicenseUrl("/api/shop/media/images/license.png");
        tenant.setShowBusinessLicense(1);
        tenant.setIcpNumber("沪ICP备12345678号");
        tenant.setUserAgreement("用户协议");
        tenant.setPrivacyPolicy("隐私政策");
        tenant.setAfterSalePolicy("售后规则");
        tenant.setAfterSaleWindowDays(7);
        tenant.setFlashSaleEnabled(0);
        tenant.setRepurchaseMallEnabled(0);
        return tenant;
    }
}
