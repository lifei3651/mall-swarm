package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.ShopCouponSaveDTO;
import com.macro.mall.distribution.entity.DmsShopCoupon;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.ShopCouponService;
import com.macro.mall.distribution.vo.ShopCouponVO;
import com.macro.mall.distribution.vo.ShopCouponProductVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ShopCouponController {
    private final ShopCouponService coupons;
    private final ShopAuthService auth;

    @GetMapping("/shop/coupons")
    public CommonResult<CommonPage<ShopCouponVO>> catalog(@RequestHeader(value="Authorization",required=false) String token,
            @RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize) {
        return CommonResult.success(coupons.catalog(auth.requireMember(token),pageNum,pageSize));
    }
    @GetMapping("/shop/coupons/mine")
    public CommonResult<CommonPage<ShopCouponVO>> mine(@RequestHeader(value="Authorization",required=false) String token,
            @RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize) {
        return CommonResult.success(coupons.mine(auth.requireMember(token),pageNum,pageSize));
    }
    public record ClaimInput(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,80}") String requestId) {}
    @GetMapping("/shop/coupons/{id}/products")
    public CommonResult<CommonPage<ShopCouponProductVO>> usableProducts(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable Long id,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize) {
        return CommonResult.success(coupons.usableProducts(auth.requireMember(token),id,pageNum,pageSize));
    }
    @PostMapping("/shop/coupons/{id}/claim")
    public CommonResult<ShopCouponVO> claim(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable Long id,@Valid @RequestBody ClaimInput input) {
        // Database request id + member/template locks preserve the successful result across retries.
        return CommonResult.success(coupons.claim(auth.requireMember(token),id,input.requestId()));
    }
    @GetMapping("/shop/admin/coupons")
    public CommonResult<CommonPage<DmsShopCoupon>> list(@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize) {
        return CommonResult.success(coupons.adminList(pageNum,pageSize));
    }
    @GetMapping("/shop/admin/coupons/products")
    public CommonResult<List<ShopCouponProductVO>> products(@RequestParam(required=false) Long merchantId,@RequestParam(required=false) String keyword) {
        return CommonResult.success(coupons.products(merchantId,keyword));
    }
    @GetMapping("/shop/admin/coupons/merchants")
    public CommonResult<CommonPage<Map<String,Object>>> merchants(@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="100") int pageSize) {
        return CommonResult.success(coupons.merchantChoices(pageNum,pageSize));
    }
    @PostMapping("/shop/admin/coupons")
    public CommonResult<DmsShopCoupon> create(@Valid @RequestBody ShopCouponSaveDTO input) { return CommonResult.success(coupons.save(null,input)); }
    @PutMapping("/shop/admin/coupons/{id}")
    public CommonResult<DmsShopCoupon> edit(@PathVariable Long id,@Valid @RequestBody ShopCouponSaveDTO input) { return CommonResult.success(coupons.save(id,input)); }
    public record StatusInput(@NotNull @Min(0) Integer version,@NotNull @Pattern(regexp="PUBLISHED|PAUSED") String status,@NotNull @AssertTrue Boolean impactConfirmed) {}
    @PutMapping("/shop/admin/coupons/{id}/status")
    public CommonResult<DmsShopCoupon> status(@PathVariable Long id,@Valid @RequestBody StatusInput input) {
        return CommonResult.success(coupons.status(id,input.version(),input.status(),input.impactConfirmed()));
    }
}
