package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.ShopMediaStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/shop/media/member-avatar")
@RequiredArgsConstructor
public class ShopMemberAvatarController {
    private final ShopAuthService authService;
    private final ShopMediaStorageService storage;
    private final DmsShopMemberDao memberDao;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<String> upload(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestPart("file") MultipartFile file) throws IOException {
        DmsShopMember member = authService.requireMember(authorization);
        ShopMediaStorageService.StoredImage stored = storage.storeMemberAvatar(member.getId(), file);
        String url = "/api/shop/media/member-avatar/" + member.getId() + "/" + stored.filename();
        if (memberDao.updateAvatarUrl(member.getId(), url) != 1) Asserts.fail("头像保存失败，请重新登录后重试");
        return CommonResult.success(url);
    }

    @GetMapping("/{memberId}/{filename:.+}")
    public ResponseEntity<Resource> read(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @PathVariable Long memberId, @PathVariable String filename) throws IOException {
        DmsShopMember member = authService.requireMember(authorization);
        if (!member.getId().equals(memberId)) return ResponseEntity.notFound().build();
        ShopMediaStorageService.StoredImage stored = storage.loadMemberAvatar(memberId, filename);
        if (stored == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(stored.contentType())).contentLength(stored.size())
                .body(new FileSystemResource(stored.path()));
    }
}
