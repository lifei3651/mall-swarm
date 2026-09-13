package com.macro.mall.distribution.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.ShopCouponSaveDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.util.CouponAmounts;
import com.macro.mall.distribution.vo.ShopCouponVO;
import com.macro.mall.distribution.vo.ShopCouponProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import static com.macro.mall.distribution.util.CouponAmounts.money;

@Service
@RequiredArgsConstructor
public class ShopCouponService {
    private final DmsShopCouponDao dao;
    private final DmsShopProductDao products;
    private final DmsMerchantDao merchants;
    private final DmsShopMemberDao members;
    private final AdminAuthService adminAuthService;
    private final OperationLogService logs;
    private final ObjectMapper json;

    public CommonPage<DmsShopCoupon> adminList(int page,int size) { admin(false); page(page,size); return CommonPage.restPage(dao.adminList(tenant())); }
    public List<ShopCouponProductVO> products(Long merchant,String keyword) { admin(false); return dao.products(tenant(),merchant,keyword==null?null:keyword.substring(0,Math.min(100,keyword.length()))); }
    public CommonPage<Map<String,Object>> merchantChoices(int page,int size) { admin(false); page(page,size); return CommonPage.restPage(dao.merchantChoices(tenant())); }

    @Transactional(rollbackFor=Exception.class)
    public DmsShopCoupon save(Long id,ShopCouponSaveDTO dto) {
        admin(true);
        if (dto==null || !Boolean.TRUE.equals(dto.getImpactConfirmed())) Asserts.fail("请确认结算与奖金影响");
        DmsShopCoupon old=id==null?null:dao.lock(tenant(),id);
        if (id!=null && (old==null || !"DRAFT".equals(old.getStatus()) || !Objects.equals(old.getVersion(),dto.getVersion()))) Asserts.fail("配置已变化或已发行，请刷新；已发行规则需新建优惠券");
        DmsShopCoupon c=new DmsShopCoupon();
        c.setId(id); c.setTenantId(tenant()); c.setTitle(dto.getTitle()==null?null:dto.getTitle().trim()); c.setMerchantId(dto.getMerchantId());
        c.setScopeType(dto.getScopeType()); c.setProductIdsJson(write(dto.getProductIds()==null?List.of():dto.getProductIds().stream().distinct().toList()));
        c.setBusinessTypesJson(write(dto.getBusinessTypes())); c.setAmount(dto.getAmount()); c.setMinimumAmount(dto.getMinimumAmount());
        c.setMerchantPercent(dto.getMerchantPercent()); c.setBonusBasis(dto.getBonusBasis()); c.setRefundRule(dto.getRefundRule());
        c.setStartsAt(dto.getStartsAt()); c.setEndsAt(dto.getEndsAt()); c.setTotalCount(dto.getTotalCount()); c.setPerMemberLimit(dto.getPerMemberLimit()); c.setVersion(dto.getVersion());
        validate(c);
        if (id==null) { if (dao.insert(c)!=1) Asserts.fail("优惠券保存失败"); }
        else if (dao.updateDraft(c)!=1) Asserts.fail("配置版本已变化，请重新确认");
        logs.log("SHOP_COUPON","SAVE","SHOP_COUPON",String.valueOf(c.getId()),old==null?null:write(old),write(c),"保存优惠券草稿；结算与奖金影响已确认");
        return dao.get(tenant(),c.getId());
    }

    @Transactional(rollbackFor=Exception.class)
    public DmsShopCoupon status(Long id,int version,String status,boolean confirmed) {
        admin(true); if (!confirmed) Asserts.fail("请确认优惠券影响");
        DmsShopCoupon c=dao.lock(tenant(),id);
        if (c==null || !Objects.equals(c.getVersion(),version) || status==null || !Set.of("PUBLISHED","PAUSED").contains(status)) Asserts.fail("配置版本或状态不正确");
        if ("PAUSED".equals(status) && "DRAFT".equals(c.getStatus())) Asserts.fail("草稿尚未发行");
        if ("PUBLISHED".equals(status)) validate(c);
        if (dao.status(tenant(),id,version,status)!=1) Asserts.fail("优惠券状态已变化");
        logs.log("SHOP_COUPON","STATUS","SHOP_COUPON",String.valueOf(id),c.getStatus(),status,"发行只开放领取；暂停不追溯修改已领取券");
        return dao.get(tenant(),id);
    }

    public CommonPage<ShopCouponVO> catalog(DmsShopMember member,int page,int size) {
        member(member); page(page,size); CommonPage<DmsShopCoupon> source=CommonPage.restPage(dao.catalog(tenant()));
        List<ShopCouponVO> rows=source.getList().stream().map(c->{ ShopCouponVO v=view(c,null); int owned=dao.countOwned(tenant(),member.getId(),c.getId());
            v.setUsable(owned<c.getPerMemberLimit()); v.setReason(v.isUsable()?"":"已达到领取上限"); return v; }).toList();
        return pageOf(source,rows);
    }
    public CommonPage<ShopCouponVO> mine(DmsShopMember member,int page,int size) {
        member(member); page(page,size); CommonPage<DmsShopCouponClaim> source=CommonPage.restPage(dao.mine(tenant(),member.getId()));
        return pageOf(source,source.getList().stream().map(c->view(required(c.getCouponId()),c)).toList());
    }
    public CommonPage<ShopCouponProductVO> usableProducts(DmsShopMember member,Long id,int page,int size) {
        member(member); DmsShopCoupon c=required(id);
        if ("DRAFT".equals(c.getStatus())) Asserts.fail("优惠券尚未发行");
        List<Long> ids=productIds(c);
        if ("PRODUCTS".equals(c.getScopeType()) && ids.isEmpty()) Asserts.fail("优惠券商品范围异常");
        page(page,size); return CommonPage.restPage(dao.usableProducts(tenant(),c.getMerchantId(),ids));
    }

    @Transactional(rollbackFor=Exception.class)
    public ShopCouponVO claim(DmsShopMember member,Long id,String requestId) {
        member(member);
        if (requestId==null || !requestId.matches("[A-Za-z0-9_-]{16,80}")) Asserts.fail("领取请求标识不正确");
        DmsShopMember locked=members.selectByIdForUpdate(member.getId());
        if (locked==null || !Objects.equals(locked.getUserId(),member.getUserId()) || !Integer.valueOf(1).equals(locked.getStatus())) Asserts.fail("会员账号不可用");
        DmsShopCouponClaim prior=dao.byRequest(tenant(),member.getId(),requestId);
        if (prior!=null) { if (!id.equals(prior.getCouponId())) Asserts.fail("请勿重复使用领取请求标识"); return view(required(id),prior); }
        DmsShopCoupon c=dao.lock(tenant(),id);
        if (c==null || !"PUBLISHED".equals(c.getStatus()) || !LocalDateTime.now().isBefore(c.getEndsAt())) Asserts.fail("优惠券已停止领取或已过期");
        if (dao.countOwned(tenant(),member.getId(),id)>=c.getPerMemberLimit()) Asserts.fail("已达到该优惠券领取上限");
        if (dao.issue(tenant(),id)!=1) Asserts.fail("优惠券已领完");
        DmsShopCouponClaim claim=new DmsShopCouponClaim(); claim.setTenantId(tenant()); claim.setCouponId(id); claim.setMemberId(member.getId()); claim.setUserId(member.getUserId()); claim.setRequestId(requestId);
        if (dao.insertClaim(claim)!=1) Asserts.fail("领取失败，请重试"); claim.setStatus("AVAILABLE");
        return view(c,claim);
    }

    public Long merchantForClaim(DmsShopMember member,Long claimId) { return required(owned(member,claimId,false).getCouponId()).getMerchantId(); }

    public List<ShopCouponVO> options(DmsShopMember member,List<DmsShopOrderItem> lines,String businessType) {
        if (member==null) return List.of(); member(member);
        List<ShopCouponVO> options=new ArrayList<>();
        for (DmsShopCouponClaim claim:dao.mine(tenant(),member.getId())) {
            DmsShopCoupon c=required(claim.getCouponId()); ShopCouponVO v=view(c,claim);
            v.setReason(reason(c,claim,lines,businessType)); v.setUsable(v.getReason().isEmpty()); options.add(v);
        }
        return options;
    }
    public BigDecimal preview(DmsShopMember member,Long claimId,List<DmsShopOrderItem> lines,String businessType) {
        if (claimId==null) return money(null);
        DmsShopCouponClaim claim=owned(member,claimId,false); DmsShopCoupon c=required(claim.getCouponId());
        String reason=reason(c,claim,lines,businessType); if (!reason.isEmpty()) Asserts.fail(reason); return money(c.getAmount());
    }

    @Transactional(rollbackFor=Exception.class)
    public DmsShopCoupon reserve(DmsShopMember member,Long claimId,Long order,List<DmsShopOrderItem> lines,String businessType) {
        DmsShopCouponClaim claim=owned(member,claimId,true); DmsShopCoupon c=required(claim.getCouponId());
        String reason=reason(c,claim,lines,businessType); if (!reason.isEmpty()) Asserts.fail(reason);
        List<DmsShopOrderItem> eligible=lines.stream().filter(i->matches(c,i)).toList();
        List<BigDecimal> discounts=CouponAmounts.allocate(c.getAmount(),eligible.stream().map(DmsShopOrderItem::getTotalAmount).toList());
        List<BigDecimal> merchantParts=CouponAmounts.merchantParts(c.getAmount(),c.getMerchantPercent(),discounts);
        for (DmsShopOrderItem line:lines) { line.setCouponDiscountAmount(money(null)); line.setCouponMerchantAmount(money(null)); line.setCouponBonusBaseAmount(money(line.getTotalAmount())); }
        for (int i=0;i<eligible.size();i++) {
            DmsShopOrderItem line=eligible.get(i); BigDecimal burden=merchantParts.get(i);
            if (burden.compareTo(money(line.getTotalCost()))>0) Asserts.fail("该商品结算金额不足以承担优惠，请更换优惠券");
            line.setCouponDiscountAmount(discounts.get(i)); line.setCouponMerchantAmount(burden);
            line.setTotalCost(money(line.getTotalCost()).subtract(burden));
            if ("NET".equals(c.getBonusBasis())) line.setCouponBonusBaseAmount(money(line.getTotalAmount()).subtract(discounts.get(i)));
        }
        if (dao.reserve(tenant(),member.getId(),claimId,order)!=1) Asserts.fail("优惠券已被使用，请重新选择");
        return c;
    }
    public void consume(DmsShopOrder order) {
        if (order.getCouponClaimId()!=null && dao.consume(order.getTenantId(),order.getCouponClaimId(),order.getId())!=1) Asserts.fail("优惠券核销状态异常");
    }
    public void releaseCancelled(DmsShopOrder order) {
        if (order.getCouponClaimId()!=null) dao.release(order.getTenantId(),order.getCouponClaimId(),order.getId(),"RESERVED");
    }
    public void releaseFullyRefunded(DmsShopOrder order) {
        if (order.getCouponClaimId()!=null && "FULL_RETURN".equals(order.getCouponRefundRule())) dao.release(order.getTenantId(),order.getCouponClaimId(),order.getId(),"USED");
    }

    private String reason(DmsShopCoupon c,DmsShopCouponClaim claim,List<DmsShopOrderItem> lines,String type) {
        if (!"AVAILABLE".equals(claim.getStatus())) return "RESERVED".equals(claim.getStatus())?"已被待支付订单占用":"已使用";
        LocalDateTime now=LocalDateTime.now(); if (now.isBefore(c.getStartsAt())) return "尚未到使用时间"; if (!now.isBefore(c.getEndsAt())) return "已过期";
        if (!businessTypes(c).contains(type)) return "不适用于当前业务，优惠券不与秒杀叠加";
        List<DmsShopOrderItem> applicable=lines.stream().filter(i->matches(c,i)).toList();
        BigDecimal gross=applicable.stream().map(DmsShopOrderItem::getTotalAmount).map(CouponAmounts::money).reduce(money(null),BigDecimal::add);
        if (applicable.isEmpty()) return "当前商品不在适用范围";
        if (gross.compareTo(c.getMinimumAmount())<0) return "适用商品未满¥"+c.getMinimumAmount().toPlainString();
        if (gross.compareTo(c.getAmount())<=0) return "适用商品金额须高于券面额";
        List<BigDecimal> discounts=CouponAmounts.allocate(c.getAmount(),applicable.stream().map(DmsShopOrderItem::getTotalAmount).toList());
        List<BigDecimal> parts=CouponAmounts.merchantParts(c.getAmount(),c.getMerchantPercent(),discounts);
        for (int i=0;i<applicable.size();i++) if (parts.get(i).compareTo(money(applicable.get(i).getTotalCost()))>0) return "商品结算金额不足以承担优惠";
        return "";
    }
    private boolean matches(DmsShopCoupon c,DmsShopOrderItem item) { return Objects.equals(c.getMerchantId(),item.getMerchantId()) && ("ALL".equals(c.getScopeType()) || productIds(c).contains(item.getProductId())); }
    private DmsShopCouponClaim owned(DmsShopMember member,Long id,boolean lock) {
        member(member); DmsShopCouponClaim c=lock?dao.lockOwned(tenant(),member.getId(),id):dao.owned(tenant(),member.getId(),id);
        if (c==null || !Objects.equals(c.getUserId(),member.getUserId())) Asserts.fail("优惠券不存在或不属于当前账号"); return c;
    }
    private DmsShopCoupon required(Long id) { DmsShopCoupon c=dao.get(tenant(),id); if (c==null) Asserts.fail("优惠券不存在"); return c; }
    private void validate(DmsShopCoupon c) {
        if (c.getTitle()==null || c.getTitle().isBlank() || c.getTitle().length()>60 || c.getAmount()==null || c.getAmount().signum()<=0 || c.getMinimumAmount()==null || c.getMinimumAmount().signum()<0
                || c.getMerchantPercent()==null || c.getMerchantPercent()<0 || c.getMerchantPercent()>100 || c.getBonusBasis()==null || !Set.of("GROSS","NET").contains(c.getBonusBasis()) || c.getRefundRule()==null || !Set.of("FULL_RETURN","NEVER").contains(c.getRefundRule())
                || c.getTotalCount()==null || c.getTotalCount()<1 || c.getPerMemberLimit()==null || c.getPerMemberLimit()<1 || c.getPerMemberLimit()>10 || c.getStartsAt()==null || c.getEndsAt()==null || !c.getEndsAt().isAfter(c.getStartsAt()) || !c.getEndsAt().isAfter(LocalDateTime.now())) Asserts.fail("请完整配置优惠金额、范围、承担方、奖金、退款规则及有效期");
        if (c.getMerchantId()==null) { c.setMerchantName("平台自营"); if (c.getMerchantPercent()!=0) Asserts.fail("自营商品优惠须由平台承担"); }
        else { DmsMerchant m=merchants.selectById(c.getMerchantId()); if (m==null || !tenant().equals(m.getTenantId())) Asserts.fail("商家不属于当前商城"); c.setMerchantName(m.getMerchantName()); }
        if (c.getScopeType()==null || !Set.of("ALL","PRODUCTS").contains(c.getScopeType()) || ("PRODUCTS".equals(c.getScopeType()) && productIds(c).isEmpty())) Asserts.fail("请选择适用商品，空列表不能表示全部");
        if ("ALL".equals(c.getScopeType()) && !productIds(c).isEmpty()) Asserts.fail("全部商品范围不能残留指定商品");
        for (Long id:productIds(c)) { DmsShopProduct p=products.selectById(id); if (p==null || !tenant().equals(p.getTenantId()) || !Objects.equals(c.getMerchantId(),p.getMerchantId())) Asserts.fail("指定商品不属于所选商城或商家"); }
        if (businessTypes(c).isEmpty() || businessTypes(c).stream().anyMatch(t->!Set.of("NORMAL","REPURCHASE").contains(t))) Asserts.fail("请选择普通商城或复购业务");
    }
    private ShopCouponVO view(DmsShopCoupon c,DmsShopCouponClaim claim) {
        ShopCouponVO v=new ShopCouponVO(); v.setId(c.getId()); v.setTitle(c.getTitle()); v.setMerchantId(c.getMerchantId()); v.setProductIds(productIds(c)); v.setBusinessTypes(businessTypes(c));
        v.setScopeLabel(c.getMerchantName()+("ALL".equals(c.getScopeType())?"全部商品可用":"指定商品可用")); v.setAmount(c.getAmount()); v.setMinimumAmount(c.getMinimumAmount()); v.setStartsAt(c.getStartsAt()); v.setEndsAt(c.getEndsAt()); v.setRefundRule(c.getRefundRule());
        v.setClaimId(claim==null?null:claim.getId()); v.setStatus(claim==null?c.getStatus():claim.getStatus());
        if (claim!=null && "AVAILABLE".equals(claim.getStatus()) && !LocalDateTime.now().isBefore(c.getEndsAt())) v.setStatus("EXPIRED");
        v.setUsable("AVAILABLE".equals(v.getStatus())); v.setReason(""); return v;
    }
    private List<Long> productIds(DmsShopCoupon c) { try { return json.readValue(c.getProductIdsJson(),new TypeReference<List<Long>>(){}); } catch(Exception e) { throw new IllegalStateException("优惠券商品配置损坏",e); } }
    private List<String> businessTypes(DmsShopCoupon c) { try { return json.readValue(c.getBusinessTypesJson(),new TypeReference<List<String>>(){}); } catch(Exception e) { throw new IllegalStateException("优惠券业务配置损坏",e); } }
    private String write(Object v) { try { return json.writeValueAsString(v); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Long tenant() { return TenantContext.getTenantId(); }
    private void member(DmsShopMember m) { if (m==null || m.getId()==null || m.getUserId()==null || !Integer.valueOf(1).equals(m.getStatus())) Asserts.unauthorized("请先登录"); }
    private void admin(boolean write) { DmsAdminUser a=AdminContext.get(); if (a==null || a.getMerchantId()!=null) Asserts.fail("仅平台可配置优惠券"); adminAuthService.requirePermission(a,"config:shop"); if(write){adminAuthService.requirePermission(a,"finance:manage");adminAuthService.requirePermission(a,"config:bonus");} }
    private void page(int p,int s) { PageHelper.startPage(Math.max(1,p),Math.max(1,Math.min(100,s))); }
    private <T> CommonPage<ShopCouponVO> pageOf(CommonPage<T> source,List<ShopCouponVO> rows) { CommonPage<ShopCouponVO> p=new CommonPage<>(); p.setList(rows); p.setPageNum(source.getPageNum()); p.setPageSize(source.getPageSize()); p.setTotal(source.getTotal()); p.setTotalPage(source.getTotalPage()); return p; }
}
