package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.vo.BrandCultureImageRefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 品牌文化详情图保存门禁，以磁盘实际文件为准，拒绝跨客户或伪造大小。 */
@Component
@RequiredArgsConstructor
public class BrandCultureImagePolicy {
    private final ShopMediaStorageService mediaStorageService;

    /**
     * 旧客户未修改的封面可继续使用；新上传或替换的封面必须来自当前客户的专用上传区。
     */
    public String validateCover(Long tenantId, String proposedUrl, String existingUrl) {
        if (proposedUrl == null || proposedUrl.isBlank()) return null;
        String url = proposedUrl.trim();
        String current = existingUrl == null ? "" : existingUrl.trim();
        String dedicatedPrefix = "/api/shop/media/brand-culture/";
        String tenantPrefix = dedicatedPrefix + tenantId + "/";
        if (!url.startsWith(tenantPrefix)) {
            if (!url.startsWith(dedicatedPrefix) && url.equals(current)) return url;
            Asserts.fail("请通过品牌文化封面上传区选择当前客户的JPG、PNG或WebP图片");
        }
        try {
            ShopMediaStorageService.StoredImage stored = mediaStorageService.loadBrandCultureImageByUrl(tenantId, url);
            if (stored == null) Asserts.fail("品牌文化封面不存在或不属于当前客户，请重新上传");
            if (stored.size() > ShopMediaStorageService.MAX_BRAND_CULTURE_COVER_SIZE) {
                Asserts.fail("品牌文化封面超过3MB，请压缩后重新上传");
            }
            return url;
        } catch (IOException e) {
            throw new IllegalStateException("校验品牌文化封面失败", e);
        }
    }

    public List<BrandCultureImageRefVO> validate(Long tenantId, List<BrandCultureImageRefVO> images) {
        List<BrandCultureImageRefVO> source = images == null ? List.of() : images;
        if (source.size() > ShopMediaStorageService.MAX_BRAND_CULTURE_DETAIL_COUNT) {
            Asserts.fail("品牌文化详情图最多上传10张");
        }
        List<BrandCultureImageRefVO> result = new ArrayList<>();
        long total = 0L;
        for (int index = 0; index < source.size(); index++) {
            BrandCultureImageRefVO image = source.get(index);
            try {
                ShopMediaStorageService.StoredImage stored = image == null ? null
                        : mediaStorageService.loadBrandCultureImageByUrl(tenantId, image.getUrl());
                if (stored == null) Asserts.fail("第" + (index + 1) + "张详情图不存在或不属于当前客户，请重新上传");
                if (stored.size() > ShopMediaStorageService.MAX_BRAND_CULTURE_DETAIL_SIZE) {
                    Asserts.fail("第" + (index + 1) + "张详情图超过5MB，请压缩后重新上传");
                }
                total += stored.size();
                if (total > ShopMediaStorageService.MAX_BRAND_CULTURE_DETAIL_TOTAL_SIZE) {
                    Asserts.fail("详情图合计超过30MB，请删除或压缩部分图片");
                }
                result.add(new BrandCultureImageRefVO(image.getUrl(), stored.size()));
            } catch (IOException e) {
                throw new IllegalStateException("校验第" + (index + 1) + "张详情图失败", e);
            }
        }
        return result;
    }
}
