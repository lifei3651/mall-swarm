package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.*;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.util.CouponAmounts;
import com.macro.mall.distribution.vo.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against test-profile H2 only; no external payment or production data. */
@SpringBootTest @ActiveProfiles("test") @Transactional
class ShopCouponIntegrationTest {
    @Autowired ShopCouponService coupons;
    @Autowired ShopService shop;
    @Autowired ShopAfterSaleService afterSales;
    @Autowired MerchantService merchants;
    @Autowired DmsShopCouponDao dao;
    @Autowired DmsShopMemberDao members;
    @Autowired JdbcTemplate db;
    @MockitoBean SmsVerificationService sms;
    DmsShopMember member;
    @BeforeEach void setup(){
        TenantContext.setTenantId(1L); admin(null,"*");
        db.update("INSERT INTO dms_shop_member(user_id,phone,password_hash,nickname,status,team_opt_in) VALUES(998801,'13999880001','test-fixture','优惠券验收',1,0)");
        member=members.selectByUserId(998801L);
    }
    @AfterEach void clear(){AdminContext.clear();TenantContext.clear();}
    void admin(Long merchant,String permissions){DmsAdminUser a=new DmsAdminUser();a.setId(1L);a.setNickname("测试管理员");a.setMerchantId(merchant);a.setPermissions(permissions);a.setStatus(1);AdminContext.set(a);}
    ShopCouponSaveDTO input(){ShopCouponSaveDTO d=new ShopCouponSaveDTO();d.setTitle("指定商品优惠");d.setScopeType("PRODUCTS");d.setProductIds(List.of(1L));d.setBusinessTypes(List.of("NORMAL"));d.setAmount(new BigDecimal("10"));d.setMinimumAmount(new BigDecimal("100"));d.setMerchantPercent(0);d.setBonusBasis("NET");d.setRefundRule("FULL_RETURN");d.setStartsAt(LocalDateTime.now().minusHours(1));d.setEndsAt(LocalDateTime.now().plusDays(3));d.setTotalCount(10);d.setPerMemberLimit(1);d.setVersion(0);d.setImpactConfirmed(true);return d;}
    DmsShopCoupon publish(ShopCouponSaveDTO input){DmsShopCoupon c=coupons.save(null,input);return coupons.status(c.getId(),0,"PUBLISHED",true);}
    ShopCouponVO claim(DmsShopCoupon c){return coupons.claim(member,c.getId(),"coupon-test-request-000001");}
    ShopOrderItemDTO item(long product,long sku,int qty){ShopOrderItemDTO i=new ShopOrderItemDTO();i.setProductId(product);i.setSkuId(sku);i.setQuantity(qty);return i;}
    ShopOrderSubmitDTO order(Long claimId,ShopOrderItemDTO...items){ShopOrderSubmitDTO d=new ShopOrderSubmitDTO();d.setCouponClaimId(claimId);d.setBusinessType("NORMAL");d.setPayType("BALANCE");d.setReceiverName("测试收货人");d.setReceiverPhone("13800000000");d.setReceiverProvince("湖南省");d.setReceiverCity("长沙市");d.setReceiverDistrict("岳麓区");d.setReceiverDetailAddress("测试路88号");d.setReceiverAddress("湖南省长沙市岳麓区测试路88号");d.setItems(List.of(items));return d;}
    void money(String expected,BigDecimal actual){assertNotNull(actual);assertEquals(0,new BigDecimal(expected).compareTo(actual));}
    DmsShopAfterSale refund(ShopOrderVO order,DmsShopOrderItem line,int quantity){ShopAfterSaleItemDTO i=new ShopAfterSaleItemDTO();i.setOrderItemId(line.getId());i.setQuantity(quantity);ShopAfterSaleApplyDTO d=new ShopAfterSaleApplyDTO();d.setOrderId(order.getOrder().getId());d.setApplyType(1);d.setReason("测试退款");d.setItems(List.of(i));DmsShopAfterSale sale=afterSales.apply(member,d);ShopAfterSaleAuditDTO audit=new ShopAfterSaleAuditDTO();audit.setStatus(1);audit.setAuditRemark("隔离测试审核");return afterSales.audit(sale.getId(),audit);}

    @Test void impactConfirmationPermissionsVersionsAndIssuedRulesAreEnforced(){
        ShopCouponSaveDTO d=input();d.setImpactConfirmed(false);assertThrows(ApiException.class,()->coupons.save(null,d));
        d.setImpactConfirmed(true);admin(null,"config:shop");assertThrows(ApiException.class,()->coupons.save(null,d));
        admin(88L,"*");assertThrows(ApiException.class,()->coupons.save(null,d));admin(null,"*");
        DmsShopCoupon c=publish(d);d.setVersion(c.getVersion());d.setAmount(new BigDecimal("50"));
        assertThrows(ApiException.class,()->coupons.save(c.getId(),d));
        assertThrows(ApiException.class,()->coupons.status(c.getId(),0,"PAUSED",true));
        money("10",dao.get(1L,c.getId()).getAmount());
    }
    @Test void claimRetriesReturnSameCouponAndCannotExceedPerMemberCap(){
        DmsShopCoupon c=publish(input());ShopCouponVO first=claim(c);assertEquals(first.getClaimId(),claim(c).getClaimId());
        assertEquals(1,dao.get(1L,c.getId()).getIssuedCount());
        assertThrows(ApiException.class,()->coupons.claim(member,c.getId(),"coupon-test-request-000002"));
    }
    @Test void tenantOwnerExpiryBusinessAndThresholdAreEnforced(){
        DmsShopCoupon c=publish(input());ShopCouponVO owned=claim(c);
        DmsShopMember other=new DmsShopMember();other.setId(member.getId()+1);other.setUserId(77L);other.setStatus(1);
        assertThrows(ApiException.class,()->shop.quoteFreight(order(owned.getClaimId(),item(1,1,1)),other));
        TenantContext.setTenantId(2L);assertNull(dao.owned(2L,member.getId(),owned.getClaimId()));TenantContext.setTenantId(1L);
        assertThrows(ApiException.class,()->shop.quoteFreight(order(owned.getClaimId(),item(2,3,1)),member));
        ShopOrderSubmitDTO flash=order(owned.getClaimId(),item(1,1,1));flash.setBusinessType("FLASH_SALE");
        assertThrows(ApiException.class,()->shop.submitReservedFlashSaleOrder(flash,member));
        db.update("UPDATE dms_shop_coupon SET ends_at=? WHERE id=?",LocalDateTime.now().minusSeconds(1),c.getId());
        assertThrows(ApiException.class,()->shop.quoteFreight(order(owned.getClaimId(),item(1,1,1)),member));
    }
    @Test void specifiedProductDiscountDoesNotTouchOtherProductsAndCancellationReleases(){
        DmsShopCoupon c=publish(input());ShopCouponVO owned=claim(c);
        ShopOrderSubmitDTO d=order(owned.getClaimId(),item(1,1,1),item(2,3,1));
        FreightQuoteVO q=shop.quoteFreight(d,member);money("10",q.getDiscountAmount());money("487",q.getPayAmount());
        ShopOrderVO result=shop.submitOrder(d,member);money("487",result.getOrder().getPayAmount());
        money("10",result.getItems().get(0).getCouponDiscountAmount());money("0",result.getItems().get(1).getCouponDiscountAmount());
        assertEquals("RESERVED",dao.owned(1L,member.getId(),owned.getClaimId()).getStatus());
        assertThrows(ApiException.class,()->shop.submitOrder(d,member));
        shop.cancelOrder(result.getOrder().getId(),member);shop.cancelOrder(result.getOrder().getId(),member);
        assertEquals("AVAILABLE",dao.owned(1L,member.getId(),owned.getClaimId()).getStatus());
    }
    @Test void partialRefundReturnsOnlyItsNetLineAndFullRefundReturnsCouponOnce(){
        DmsShopCoupon c=publish(input());ShopCouponVO owned=claim(c);
        ShopOrderVO result=shop.submitOrder(order(owned.getClaimId(),item(1,1,1),item(2,3,1)),member);
        shop.markOrderPaid(result.getOrder().getId(),"BALANCE");
        assertEquals("USED",dao.owned(1L,member.getId(),owned.getClaimId()).getStatus());
        DmsShopAfterSale first=refund(result,result.getItems().get(1),1);money("198",first.getProductRefundAmount());
        assertEquals("USED",dao.owned(1L,member.getId(),owned.getClaimId()).getStatus());
        DmsShopAfterSale second=refund(result,result.getItems().get(0),1);money("289",second.getProductRefundAmount());
        assertEquals("AVAILABLE",dao.owned(1L,member.getId(),owned.getClaimId()).getStatus());
        assertNull(dao.owned(1L,member.getId(),owned.getClaimId()).getOrderId());
    }
    @Test void grossBonusBasisAndRepeatedQuantityRefundsCloseToTheCent(){
        ShopCouponSaveDTO d=input();d.setAmount(new BigDecimal("0.01"));d.setBonusBasis("GROSS");d.setRefundRule("NEVER");
        ShopCouponVO owned=claim(publish(d));ShopOrderVO result=shop.submitOrder(order(owned.getClaimId(),item(1,1,3)),member);
        money("897",result.getItems().get(0).getCouponBonusBaseAmount());shop.markOrderPaid(result.getOrder().getId(),"BALANCE");
        money("298.99",refund(result,result.getItems().get(0),1).getProductRefundAmount());
        money("299",refund(result,result.getItems().get(0),1).getProductRefundAmount());
        money("299",refund(result,result.getItems().get(0),1).getProductRefundAmount());
        assertEquals("USED",dao.owned(1L,member.getId(),owned.getClaimId()).getStatus());
    }
    @Test void mixedCheckoutAssignsCouponOnlyToMatchingMerchantAndCostSnapshot(){
        DmsMerchant merchant=new DmsMerchant();merchant.setMerchantNo("COUPON-TEST-MERCHANT");merchant.setMerchantName("优惠券测试商户");merchant=merchants.saveMerchant(merchant);
        db.update("UPDATE dms_shop_product SET merchant_id=?,merchant_name=? WHERE id=2",merchant.getId(),merchant.getMerchantName());
        ShopCouponSaveDTO d=input();d.setMerchantId(merchant.getId());d.setProductIds(List.of(2L));d.setMerchantPercent(50);
        ShopCouponVO owned=claim(publish(d));ShopOrderVO result=shop.submitOrder(order(owned.getClaimId(),item(1,1,1),item(2,3,1)),member);
        assertTrue(result.getGroupedCheckout());assertEquals(2,result.getChildOrders().size());
        ShopOrderVO platform=result.getChildOrders().get(0), seller=result.getChildOrders().get(1);
        assertNull(platform.getOrder().getCouponClaimId());money("299",platform.getOrder().getPayAmount());
        assertEquals(owned.getClaimId(),seller.getOrder().getCouponClaimId());money("188",seller.getOrder().getPayAmount());money("67",seller.getOrder().getTotalCost());
        money("5",seller.getItems().get(0).getCouponMerchantAmount());
        shop.cancelCheckout(result.getCheckoutId(),member);assertEquals("AVAILABLE",dao.owned(1L,member.getId(),owned.getClaimId()).getStatus());
    }
    @Test void pauseDoesNotInvalidateAlreadyClaimedRules(){
        DmsShopCoupon c=publish(input());ShopCouponVO owned=claim(c);coupons.status(c.getId(),c.getVersion(),"PAUSED",true);
        assertTrue(coupons.catalog(member,1,20).getList().isEmpty());money("10",shop.quoteFreight(order(owned.getClaimId(),item(1,1,1)),member).getDiscountAmount());
        assertEquals(1,coupons.usableProducts(member,c.getId(),1,20).getList().size());
        assertEquals("轻奢焕活礼盒",coupons.usableProducts(member,c.getId(),1,20).getList().get(0).getProductName());
    }
    @Test void merchantFundingAndThreeRefundsReverseExactlyTheNetSettlement(){
        DmsMerchant m=new DmsMerchant();m.setMerchantNo("COUPON-REFUND-SELLER");m.setMerchantName("优惠分担测试商家");
        m.setLegalEntityName(m.getMerchantName());m.setUnifiedSocialCreditCode("91430100TEST000001");m.setBankAccountName(m.getMerchantName());m.setBankName("测试银行长沙支行");m.setBankAccountNo("6222000000000000001");m.setInvoiceTitle(m.getMerchantName());m.setTaxpayerIdentificationNo("91430100TEST000001");m.setContractStatus("SIGNED");m=merchants.saveMerchant(m);
        db.update("UPDATE dms_shop_product SET merchant_id=?,merchant_name=? WHERE id=2",m.getId(),m.getMerchantName());
        ShopCouponSaveDTO d=input();d.setMerchantId(m.getId());d.setProductIds(List.of(2L));d.setMerchantPercent(50);
        ShopCouponVO owned=claim(publish(d));ShopOrderVO o=shop.submitOrder(order(owned.getClaimId(),item(2,3,3)),member);
        shop.markOrderPaid(o.getOrder().getId(),"BALANCE");
        Long itemId=o.getItems().get(0).getId();
        money("211",db.queryForObject("SELECT settlement_amount FROM dms_merchant_settlement WHERE order_item_id=?",BigDecimal.class,itemId));
        refund(o,o.getItems().get(0),1);money("70.33",db.queryForObject("SELECT reversed_amount FROM dms_merchant_settlement WHERE order_item_id=?",BigDecimal.class,itemId));
        refund(o,o.getItems().get(0),1);money("140.66",db.queryForObject("SELECT reversed_amount FROM dms_merchant_settlement WHERE order_item_id=?",BigDecimal.class,itemId));
        refund(o,o.getItems().get(0),1);money("211",db.queryForObject("SELECT reversed_amount FROM dms_merchant_settlement WHERE order_item_id=?",BigDecimal.class,itemId));
        assertEquals("REVERSED",db.queryForObject("SELECT status FROM dms_merchant_settlement WHERE order_item_id=?",String.class,itemId));
        assertEquals("AVAILABLE",dao.owned(1L,member.getId(),owned.getClaimId()).getStatus());
    }
    @Test void pennyAllocationNeverExceedsIndividualLinesAndFundingConserves(){
        var parts=CouponAmounts.allocate(new BigDecimal("0.02"),List.of(new BigDecimal("0.01"),new BigDecimal("0.02")));
        money("0.02",parts.stream().reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(List.of(new BigDecimal("0.01")),CouponAmounts.merchantParts(new BigDecimal("0.01"),99,List.of(new BigDecimal("0.01"))));
        assertThrows(ApiException.class,()->CouponAmounts.allocate(new BigDecimal("1"),List.of(new BigDecimal("1"))));
    }
}
