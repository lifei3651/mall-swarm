package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopMediaStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void resizesLargeImageAndUsesDetectedContentInsteadOfClaimedExtension() throws Exception {
        byte[] png = imageBytes("png", 2400, 1200, true);
        ShopMediaStorageService service = new ShopMediaStorageService(tempDir.toString(), 1000, 5_000_000, 0.82f);

        ShopMediaStorageService.StoredImage stored = service.store(
                new MockMultipartFile("file", "fake.jpg", "image/jpeg", png));

        assertTrue(stored.filename().endsWith(".png"));
        BufferedImage result = ImageIO.read(stored.path().toFile());
        assertNotNull(result);
        assertEquals(1000, result.getWidth());
        assertEquals(500, result.getHeight());
    }

    @Test
    void rejectsSpoofedOrNonImageContent() {
        ShopMediaStorageService service = new ShopMediaStorageService(tempDir.toString(), 1920, 25_000_000, 0.82f);
        MockMultipartFile fake = new MockMultipartFile(
                "file", "attack.png", "image/png", "<script>alert(1)</script>".getBytes());

        assertThrows(ApiException.class, () -> service.store(fake));
    }

    @Test
    void sameProcessedContentReusesStableFileName() throws Exception {
        byte[] jpeg = imageBytes("jpg", 800, 600, false);
        ShopMediaStorageService service = new ShopMediaStorageService(tempDir.toString(), 1920, 25_000_000, 0.82f);
        MockMultipartFile upload = new MockMultipartFile("file", "product.jpg", "image/jpeg", jpeg);

        ShopMediaStorageService.StoredImage first = service.store(upload);
        ShopMediaStorageService.StoredImage second = service.store(upload);

        assertEquals(first.filename(), second.filename());
        assertEquals(first.size(), second.size());
    }

    @Test
    void storedImageIsReadableByTheWebServerUser() throws Exception {
        byte[] jpeg = imageBytes("jpg", 320, 240, false);
        ShopMediaStorageService service = new ShopMediaStorageService(tempDir.toString(), 1920, 25_000_000, 0.82f);

        ShopMediaStorageService.StoredImage stored = service.store(
                new MockMultipartFile("file", "product.jpg", "image/jpeg", jpeg));

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(stored.path());
        assertTrue(permissions.contains(PosixFilePermission.GROUP_READ));
        assertTrue(permissions.contains(PosixFilePermission.OTHERS_READ));
        Set<PosixFilePermission> directoryPermissions = Files.getPosixFilePermissions(stored.path().getParent());
        assertTrue(directoryPermissions.contains(PosixFilePermission.GROUP_EXECUTE));
        assertTrue(directoryPermissions.contains(PosixFilePermission.OTHERS_EXECUTE));
    }

    @Test
    void afterSaleProofUsesMemberIsolationAndPrivatePermissions() throws Exception {
        byte[] jpeg = imageBytes("jpg", 320, 240, false);
        ShopMediaStorageService service = new ShopMediaStorageService(tempDir.toString(), 1920, 25_000_000, 0.82f);

        ShopMediaStorageService.StoredImage stored = service.storeAfterSaleProof(101L,
                new MockMultipartFile("file", "proof.jpg", "image/jpeg", jpeg));

        assertNotNull(service.loadAfterSaleProof(101L, stored.filename()));
        assertNull(service.loadAfterSaleProof(102L, stored.filename()));
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(stored.path());
        assertFalse(permissions.contains(PosixFilePermission.GROUP_READ));
        assertFalse(permissions.contains(PosixFilePermission.OTHERS_READ));
        Set<PosixFilePermission> directoryPermissions = Files.getPosixFilePermissions(stored.path().getParent());
        assertFalse(directoryPermissions.contains(PosixFilePermission.GROUP_EXECUTE));
        assertFalse(directoryPermissions.contains(PosixFilePermission.OTHERS_EXECUTE));
    }

    @Test
    void afterSaleTemporaryProofsHaveABoundedPerMemberQuota() throws Exception {
        byte[] jpeg = imageBytes("jpg", 32, 32, false);
        ShopMediaStorageService service = new ShopMediaStorageService(tempDir.toString(), 1920, 25_000_000, 0.82f);

        for (int i = 0; i < 12; i++) {
            service.storeAfterSaleProof(101L,
                    new MockMultipartFile("file", "proof-" + i + ".jpg", "image/jpeg", jpeg));
        }

        assertThrows(ApiException.class, () -> service.storeAfterSaleProof(101L,
                new MockMultipartFile("file", "overflow.jpg", "image/jpeg", jpeg)));
    }

    @Test
    void scheduledCleanupRemovesExpiredTemporaryProofsWithoutTouchingFreshOnes() throws Exception {
        byte[] jpeg = imageBytes("jpg", 32, 32, false);
        ShopMediaStorageService service = new ShopMediaStorageService(tempDir.toString(), 1920, 25_000_000, 0.82f);
        ShopMediaStorageService.StoredImage expired = service.storeAfterSaleProof(101L,
                new MockMultipartFile("file", "old.jpg", "image/jpeg", jpeg));
        ShopMediaStorageService.StoredImage fresh = service.storeAfterSaleProof(102L,
                new MockMultipartFile("file", "fresh.jpg", "image/jpeg", jpeg));
        Files.setLastModifiedTime(expired.path(), FileTime.from(Instant.now().minus(25, ChronoUnit.HOURS)));

        assertEquals(1, service.cleanupExpiredTemporaryProofs());

        assertFalse(Files.exists(expired.path()));
        assertTrue(Files.exists(fresh.path()));
    }

    private byte[] imageBytes(String format, int width, int height, boolean alpha) throws Exception {
        BufferedImage image = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(38, 112, 91));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }
}
