package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.ShopMediaStorageService;
import com.macro.mall.distribution.vo.BrandCultureImageRefVO;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

/** 商品图片上传与读取；存储实现可平滑替换为OSS/CDN。 */
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopMediaController {
    private final ShopMediaStorageService mediaStorageService;
    private final ShopAuthService authService;
    private final com.macro.mall.distribution.service.ShopAfterSaleService afterSaleService;

    @Operation(summary = "上传商品图片")
    @PostMapping(value = "/admin/media/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<String> upload(@RequestPart("file") MultipartFile file) throws IOException {
        ShopMediaStorageService.StoredImage stored = mediaStorageService.store(file);
        return CommonResult.success("/api/shop/media/images/" + stored.filename());
    }

    @Operation(summary = "上传品牌文化横幅、兼容封面或详情图")
    @PostMapping(value = "/admin/media/brand-culture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<BrandCultureImageRefVO> uploadBrandCulture(
            @RequestParam Long tenantId,
            @RequestParam String purpose,
            @RequestPart("file") MultipartFile file) throws IOException {
        if (!"banner".equals(purpose) && !"cover".equals(purpose) && !"detail".equals(purpose)) {
            com.macro.mall.common.exception.Asserts.fail("图片用途无效");
        }
        ShopMediaStorageService.StoredImage stored = mediaStorageService.storeBrandCultureImage(
                tenantId, !"detail".equals(purpose), file);
        String url = "/api/shop/media/brand-culture/" + tenantId + "/" + stored.filename();
        return CommonResult.success(new BrandCultureImageRefVO(url, stored.size()));
    }

    @Operation(summary = "读取商品图片")
    @GetMapping("/media/images/{filename:.+}")
    public ResponseEntity<Resource> image(@PathVariable String filename) throws IOException {
        ShopMediaStorageService.StoredImage stored = mediaStorageService.load(filename);
        if (stored == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic().mustRevalidate())
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .contentLength(stored.size())
                .body(new FileSystemResource(stored.path()));
    }

    @Operation(summary = "读取品牌文化图片")
    @GetMapping("/media/brand-culture/{tenantId}/{filename:.+}")
    public ResponseEntity<Resource> brandCultureImage(@PathVariable Long tenantId,
                                                       @PathVariable String filename) throws IOException {
        ShopMediaStorageService.StoredImage stored = mediaStorageService.loadBrandCultureImage(tenantId, filename);
        if (stored == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic().mustRevalidate())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .contentLength(stored.size())
                .body(new FileSystemResource(stored.path()));
    }

    @Operation(summary = "会员上传售后凭证图片")
    @PostMapping(value = "/media/after-sale-proofs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<String> uploadAfterSaleProof(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam Long orderId,
            @RequestPart("file") MultipartFile file) throws IOException {
        DmsShopMember member = authService.requireMember(authorization);
        afterSaleService.assertCanUploadProof(member, orderId);
        ShopMediaStorageService.StoredImage stored = mediaStorageService.storeAfterSaleProof(member.getId(), file);
        return CommonResult.success(stored.filename());
    }

    @Operation(summary = "会员读取自己的售后凭证图片")
    @GetMapping("/media/after-sale-proofs/{filename:.+}")
    public ResponseEntity<Resource> memberAfterSaleProof(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String filename) throws IOException {
        DmsShopMember member = authService.requireMember(authorization);
        return privateImage(mediaStorageService.loadAfterSaleProof(member.getId(), filename));
    }

    @Operation(summary = "后台读取售后凭证图片")
    @GetMapping("/admin/after-sales/proofs/{memberId}/{filename:.+}")
    public ResponseEntity<Resource> adminAfterSaleProof(@PathVariable Long memberId,
                                                         @PathVariable String filename) throws IOException {
        afterSaleService.assertAdminCanReadProof(memberId, filename);
        return privateImage(mediaStorageService.loadAfterSaleProof(memberId, filename));
    }

    private ResponseEntity<Resource> privateImage(ShopMediaStorageService.StoredImage stored) {
        if (stored == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .contentLength(stored.size())
                .body(new FileSystemResource(stored.path()));
    }
}
