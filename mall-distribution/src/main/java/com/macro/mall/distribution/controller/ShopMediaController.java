package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.service.ShopMediaStorageService;
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

    @Operation(summary = "上传商品图片")
    @PostMapping(value = "/admin/media/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<String> upload(@RequestPart("file") MultipartFile file) throws IOException {
        ShopMediaStorageService.StoredImage stored = mediaStorageService.store(file);
        return CommonResult.success("/api/shop/media/images/" + stored.filename());
    }

    @Operation(summary = "读取商品图片")
    @GetMapping("/media/images/{filename:.+}")
    public ResponseEntity<Resource> image(@PathVariable String filename) throws IOException {
        ShopMediaStorageService.StoredImage stored = mediaStorageService.load(filename);
        if (stored == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .contentLength(stored.size())
                .body(new FileSystemResource(stored.path()));
    }
}
