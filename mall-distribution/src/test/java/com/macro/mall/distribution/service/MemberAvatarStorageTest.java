package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class MemberAvatarStorageTest {
    @TempDir Path root;
    @Test void storesBoundedSanitizedRasterPrivatelyAndReplacesInsteadOfAccumulating() throws Exception {
        ShopMediaStorageService storage = new ShopMediaStorageService(root.toString(), 1920, 25000000, .82f);
        BufferedImage source = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(source, "png", bytes);
        MockMultipartFile file = new MockMultipartFile("file", "../arbitrary.png", "image/png", bytes.toByteArray());
        ShopMediaStorageService.StoredImage first = storage.storeMemberAvatar(12L, file);
        storage.storeMemberAvatar(12L, file);
        assertTrue(first.path().startsWith(root.resolve("private/member-avatars/12")));
        assertEquals(512, ImageIO.read(first.path().toFile()).getWidth());
        try (var files = Files.list(first.path().getParent())) { assertEquals(1, files.count()); }
        assertNull(storage.loadMemberAvatar(13L, first.filename()));
        assertNull(storage.loadMemberAvatar(12L, "../../avatar.jpg"));
        assertNull(storage.load(first.filename()));
    }
    @Test void rejectsNonRasterOversizedAndInvalidOwnerBeforeWriting() throws Exception {
        ShopMediaStorageService storage = new ShopMediaStorageService(root.toString(), 1920, 25000000, .82f);
        assertThrows(ApiException.class, () -> storage.storeMemberAvatar(1L, new MockMultipartFile("file", "avatar.jpg", "image/jpeg", "<svg onload='x'/>".getBytes())));
        assertThrows(ApiException.class, () -> storage.storeMemberAvatar(1L, new MockMultipartFile("file", new byte[2 * 1024 * 1024 + 1])));
        assertThrows(ApiException.class, () -> storage.loadMemberAvatar(-1L, "avatar.jpg"));
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }
}
