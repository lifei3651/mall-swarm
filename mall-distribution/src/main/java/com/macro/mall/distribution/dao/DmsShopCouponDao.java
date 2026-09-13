package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopCoupon;
import com.macro.mall.distribution.entity.DmsShopCouponClaim;
import com.macro.mall.distribution.vo.ShopCouponProductVO;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface DmsShopCouponDao {
    @Select("SELECT id,merchant_name AS merchantName FROM dms_merchant WHERE tenant_id=#{tenant} AND status=1 ORDER BY id DESC")
    List<Map<String,Object>> merchantChoices(@Param("tenant") Long tenant);
    @Select("""
        <script>SELECT p.id,p.product_name AS productName,p.cover_url AS coverUrl,p.sale_price AS salePrice
        FROM dms_shop_product p WHERE p.tenant_id=#{tenant} AND p.status=1
        AND (p.merchant_id IS NULL OR EXISTS(SELECT 1 FROM dms_merchant m WHERE m.id=p.merchant_id AND m.tenant_id=p.tenant_id AND m.status=1))
        <choose><when test="merchant != null">AND p.merchant_id=#{merchant}</when><otherwise>AND p.merchant_id IS NULL</otherwise></choose>
        <if test="ids != null and !ids.isEmpty()">AND p.id IN <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach></if>
        ORDER BY p.id DESC</script>
        """) List<ShopCouponProductVO> usableProducts(@Param("tenant") Long tenant,@Param("merchant") Long merchant,@Param("ids") List<Long> ids);
    @Select("SELECT * FROM dms_shop_coupon WHERE tenant_id=#{tenant} ORDER BY id DESC")
    List<DmsShopCoupon> adminList(@Param("tenant") Long tenant);
    @Select("SELECT * FROM dms_shop_coupon WHERE tenant_id=#{tenant} AND status='PUBLISHED' AND ends_at>NOW() AND issued_count<total_count ORDER BY id DESC")
    List<DmsShopCoupon> catalog(@Param("tenant") Long tenant);
    @Select("SELECT * FROM dms_shop_coupon WHERE tenant_id=#{tenant} AND id=#{id}")
    DmsShopCoupon get(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT * FROM dms_shop_coupon WHERE tenant_id=#{tenant} AND id=#{id} FOR UPDATE")
    DmsShopCoupon lock(@Param("tenant") Long tenant,@Param("id") Long id);
    @Insert("""
        INSERT INTO dms_shop_coupon(tenant_id,title,merchant_id,merchant_name,scope_type,product_ids_json,business_types_json,
        amount,minimum_amount,merchant_percent,bonus_basis,refund_rule,starts_at,ends_at,total_count,per_member_limit,issued_count,status,version)
        VALUES(#{tenantId},#{title},#{merchantId},#{merchantName},#{scopeType},#{productIdsJson},#{businessTypesJson},
        #{amount},#{minimumAmount},#{merchantPercent},#{bonusBasis},#{refundRule},#{startsAt},#{endsAt},#{totalCount},#{perMemberLimit},0,'DRAFT',0)
        """)
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(DmsShopCoupon coupon);
    @Update("""
        UPDATE dms_shop_coupon SET title=#{title},merchant_id=#{merchantId},merchant_name=#{merchantName},scope_type=#{scopeType},
        product_ids_json=#{productIdsJson},business_types_json=#{businessTypesJson},amount=#{amount},minimum_amount=#{minimumAmount},
        merchant_percent=#{merchantPercent},bonus_basis=#{bonusBasis},refund_rule=#{refundRule},starts_at=#{startsAt},ends_at=#{endsAt},
        total_count=#{totalCount},per_member_limit=#{perMemberLimit},version=version+1
        WHERE tenant_id=#{tenantId} AND id=#{id} AND status='DRAFT' AND version=#{version}
        """) int updateDraft(DmsShopCoupon coupon);
    @Update("UPDATE dms_shop_coupon SET status=#{status},version=version+1 WHERE tenant_id=#{tenant} AND id=#{id} AND version=#{version}")
    int status(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") int version,@Param("status") String status);
    @Update("UPDATE dms_shop_coupon SET issued_count=issued_count+1 WHERE tenant_id=#{tenant} AND id=#{id} AND status='PUBLISHED' AND ends_at>NOW() AND issued_count<total_count")
    int issue(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT * FROM dms_shop_coupon_claim WHERE tenant_id=#{tenant} AND member_id=#{member} ORDER BY id DESC")
    List<DmsShopCouponClaim> mine(@Param("tenant") Long tenant,@Param("member") Long member);
    @Select("SELECT COUNT(*) FROM dms_shop_coupon_claim WHERE tenant_id=#{tenant} AND member_id=#{member} AND coupon_id=#{coupon}")
    int countOwned(@Param("tenant") Long tenant,@Param("member") Long member,@Param("coupon") Long coupon);
    @Select("SELECT * FROM dms_shop_coupon_claim WHERE tenant_id=#{tenant} AND member_id=#{member} AND request_id=#{request}")
    DmsShopCouponClaim byRequest(@Param("tenant") Long tenant,@Param("member") Long member,@Param("request") String request);
    @Select("SELECT * FROM dms_shop_coupon_claim WHERE tenant_id=#{tenant} AND member_id=#{member} AND id=#{id}")
    DmsShopCouponClaim owned(@Param("tenant") Long tenant,@Param("member") Long member,@Param("id") Long id);
    @Select("SELECT * FROM dms_shop_coupon_claim WHERE tenant_id=#{tenant} AND member_id=#{member} AND id=#{id} FOR UPDATE")
    DmsShopCouponClaim lockOwned(@Param("tenant") Long tenant,@Param("member") Long member,@Param("id") Long id);
    @Insert("INSERT INTO dms_shop_coupon_claim(tenant_id,coupon_id,member_id,user_id,request_id,status) VALUES(#{tenantId},#{couponId},#{memberId},#{userId},#{requestId},'AVAILABLE')")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertClaim(DmsShopCouponClaim claim);
    @Update("UPDATE dms_shop_coupon_claim SET status='RESERVED',order_id=#{order} WHERE tenant_id=#{tenant} AND member_id=#{member} AND id=#{id} AND status='AVAILABLE'")
    int reserve(@Param("tenant") Long tenant,@Param("member") Long member,@Param("id") Long id,@Param("order") Long order);
    @Update("UPDATE dms_shop_coupon_claim SET status='USED' WHERE tenant_id=#{tenant} AND id=#{id} AND order_id=#{order} AND status='RESERVED'")
    int consume(@Param("tenant") Long tenant,@Param("id") Long id,@Param("order") Long order);
    @Update("UPDATE dms_shop_coupon_claim SET status='AVAILABLE',order_id=NULL WHERE tenant_id=#{tenant} AND id=#{id} AND order_id=#{order} AND status=#{state}")
    int release(@Param("tenant") Long tenant,@Param("id") Long id,@Param("order") Long order,@Param("state") String state);
    @Select("""
        <script>SELECT id,product_name,cover_url,sale_price FROM dms_shop_product WHERE tenant_id=#{tenant}
        <choose><when test="merchant != null">AND merchant_id=#{merchant}</when><otherwise>AND merchant_id IS NULL</otherwise></choose>
        <if test="keyword != null and keyword != ''">AND product_name LIKE CONCAT('%',#{keyword},'%')</if>
        ORDER BY id DESC LIMIT 100</script>
        """) List<ShopCouponProductVO> products(@Param("tenant") Long tenant,@Param("merchant") Long merchant,@Param("keyword") String keyword);
}
