package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.ProductReviewStatusDTO;
import com.macro.mall.distribution.dto.ProductReviewSubmitDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopProductReview;
import com.macro.mall.distribution.service.ProductReviewService;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.vo.ProductReviewPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "ProductReviewController", description = "商品真实购买评价")
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService productReviewService;
    private final ShopAuthService authService;

    @Operation(summary = "前台商品评价列表与评价资格")
    @GetMapping("/products/{productId}/reviews")
    public CommonResult<ProductReviewPageVO> productReviews(
            @PathVariable Long productId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Long orderItemId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        DmsShopMember member = authService.resolveMember(authorization);
        return CommonResult.success(productReviewService.listProductReviews(productId, member, orderItemId, pageNum, pageSize));
    }

    @Operation(summary = "已确认收货的买家发表评价")
    @PostMapping("/products/{productId}/reviews")
    public CommonResult<DmsShopProductReview> submitReview(
            @PathVariable Long productId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ProductReviewSubmitDTO dto) {
        return CommonResult.success(productReviewService.submitReview(productId,
                authService.requireMember(authorization), dto));
    }

    @Operation(summary = "后台评价列表")
    @GetMapping("/admin/reviews")
    public CommonResult<CommonPage<DmsShopProductReview>> adminReviews(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return CommonResult.success(productReviewService.listAdminReviews(keyword, productId, rating, status, pageNum, pageSize));
    }

    @Operation(summary = "后台隐藏或恢复商品评价")
    @PutMapping("/admin/reviews/{id}/status")
    public CommonResult<Boolean> updateReviewStatus(@PathVariable Long id,
                                                    @Valid @RequestBody ProductReviewStatusDTO dto) {
        return CommonResult.success(productReviewService.updateReviewStatus(id, dto));
    }
}
