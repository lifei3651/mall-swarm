package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.entity.DmsShopServiceAddress;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.security.AdminContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MerchantAddressIsolationTest {
    @Autowired private MerchantService merchantService;
    @Autowired private ShopServiceAddressService addressService;
    @Autowired private ShopService shopService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearAdmin() {
        AdminContext.clear();
    }

    @Test
    void merchantOnlySeesOwnAndExplicitlySharedAddressesAndCannotModifySharedAddress() {
        DmsMerchant first = merchant("M-ADDR-FIRST", "地址商户一");
        DmsMerchant second = merchant("M-ADDR-SECOND", "地址商户二");

        DmsShopServiceAddress platformPrivate = address("平台私有仓", 1);
        platformPrivate.setSharedToMerchants(0);
        platformPrivate = addressService.save(platformPrivate);
        Long privateId = platformPrivate.getId();
        DmsShopServiceAddress platformShared = address("平台共享仓", 1);
        platformShared.setSharedToMerchants(1);
        platformShared = addressService.save(platformShared);
        Long sharedId = platformShared.getId();

        AdminContext.set(merchantAdmin(50001L, first));
        DmsShopServiceAddress forged = address("商户一仓", 1);
        forged.setMerchantId(second.getId());
        DmsShopServiceAddress firstOwned = addressService.save(forged);
        assertEquals(first.getId(), firstOwned.getMerchantId());
        assertEquals(0, firstOwned.getSharedToMerchants());

        AdminContext.set(merchantAdmin(50002L, second));
        DmsShopServiceAddress secondOwned = addressService.save(address("商户二仓", 1));
        assertEquals(second.getId(), secondOwned.getMerchantId());
        Long secondOwnedId = secondOwned.getId();

        AdminContext.set(merchantAdmin(50001L, first));
        List<DmsShopServiceAddress> visible = addressService.list(1L, 1, 1);
        assertTrue(visible.stream().anyMatch(item -> item.getId().equals(firstOwned.getId())));
        assertTrue(visible.stream().anyMatch(item -> item.getId().equals(sharedId)));
        assertFalse(visible.stream().anyMatch(item -> item.getId().equals(privateId)));
        assertFalse(visible.stream().anyMatch(item -> item.getId().equals(secondOwnedId)));

        DmsShopServiceAddress illegalEdit = address("篡改共享仓", 1);
        illegalEdit.setId(sharedId);
        assertThrows(RuntimeException.class, () -> addressService.save(illegalEdit));
        assertThrows(RuntimeException.class, () -> addressService.updateStatus(sharedId, 1L, 0));

        jdbcTemplate.update("UPDATE dms_shop_product SET merchant_id=?,merchant_name=?,status=0,merchant_review_status='DRAFT',team_bonus_mode='NONE' WHERE id=1",
                first.getId(), first.getMerchantName());
        DmsShopProduct illegalProduct = new DmsShopProduct();
        BeanUtils.copyProperties(shopService.getProduct(1L), illegalProduct);
        illegalProduct.setShippingAddressId(secondOwnedId);
        RuntimeException addressBypass = assertThrows(RuntimeException.class,
                () -> shopService.updateProduct(1L, illegalProduct));
        assertTrue(addressBypass.getMessage().contains("不属于当前商户"));

        AdminContext.clear();
        List<DmsShopServiceAddress> platformVisible = addressService.list(1L, 1, 1);
        assertTrue(platformVisible.stream().anyMatch(item -> item.getId().equals(privateId)));
        assertTrue(platformVisible.stream().anyMatch(item -> item.getId().equals(secondOwnedId)));
    }

    private DmsMerchant merchant(String no, String name) {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo(no);
        merchant.setMerchantName(name);
        return merchantService.saveMerchant(merchant);
    }

    private DmsAdminUser merchantAdmin(Long id, DmsMerchant merchant) {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(id);
        admin.setUsername("merchant-" + id);
        admin.setMerchantId(merchant.getId());
        admin.setPermissions("admin:read,shop:product,finance:read,finance:manage");
        return admin;
    }

    private DmsShopServiceAddress address(String label, int type) {
        DmsShopServiceAddress address = new DmsShopServiceAddress();
        address.setTenantId(1L);
        address.setAddressType(type);
        address.setAddressLabel(label);
        address.setContactName("测试联系人");
        address.setContactPhone("13800000000");
        address.setProvince("湖南省");
        address.setCity("长沙市");
        address.setDistrict("岳麓区");
        address.setDetailAddress("测试路1号");
        address.setIsDefault(0);
        return address;
    }
}
