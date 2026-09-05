package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.Asserts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

/**
 * 商品图片的本地存储实现。
 *
 * <p>上传时根据真实文件头识别格式，限制解码尺寸，并对 JPEG/PNG 去除元数据、
 * 缩小超大尺寸。文件名使用内容摘要，便于长期缓存和重复图片去重。后续接入
 * OSS/CDN时，控制器无需再承担图片处理逻辑。</p>
 */
@Service
@Slf4j
public class ShopMediaStorageService {
    static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    public static final long MAX_BRAND_CULTURE_COVER_SIZE = 3L * 1024 * 1024;
    public static final long MAX_BRAND_CULTURE_DETAIL_SIZE = 5L * 1024 * 1024;
    public static final long MAX_BRAND_CULTURE_DETAIL_TOTAL_SIZE = 30L * 1024 * 1024;
    public static final int MAX_BRAND_CULTURE_DETAIL_COUNT = 10;
    static final int MAX_TEMP_PROOFS_PER_MEMBER = 12;
    static final long MAX_TEMP_PROOF_BYTES_PER_MEMBER = 30L * 1024 * 1024;
    private static final String FILE_NAME_PATTERN = "[a-fA-F0-9]{32}\\.(jpg|jpeg|png|webp|gif)";
    private static final AtomicBoolean POSIX_PERMISSION_WARNING_LOGGED = new AtomicBoolean(false);

    private final Path storageDirectory;
    private final Path privateStorageDirectory;
    private final int maxDimension;
    private final long maxPixels;
    private final float jpegQuality;

    @Autowired
    public ShopMediaStorageService(
            @Value("${shop.media.storage-dir:/opt/lingqimall/uploads/products}") String storageDir,
            @Value("${shop.media.private-storage-dir:/opt/lingqimall/uploads/private}") String privateStorageDir,
            @Value("${shop.media.max-dimension:1920}") int maxDimension,
            @Value("${shop.media.max-pixels:25000000}") long maxPixels,
            @Value("${shop.media.jpeg-quality:0.82}") float jpegQuality) {
        this.storageDirectory = Path.of(storageDir).toAbsolutePath().normalize();
        this.privateStorageDirectory = Path.of(privateStorageDir).toAbsolutePath().normalize();
        this.maxDimension = Math.max(640, maxDimension);
        this.maxPixels = Math.max(1_000_000L, maxPixels);
        this.jpegQuality = Math.max(0.65f, Math.min(0.95f, jpegQuality));
    }

    /** 测试与本地工具沿用的兼容构造器；私密文件放在同一临时根目录的 private 子目录。 */
    public ShopMediaStorageService(String storageDir, int maxDimension, long maxPixels, float jpegQuality) {
        this(storageDir, Path.of(storageDir).resolve("private").toString(), maxDimension, maxPixels, jpegQuality);
    }

    public StoredImage store(MultipartFile file) throws IOException {
        ProcessedImage processed = processUpload(file);

        Files.createDirectories(storageDirectory);
        ensureWebReadableDirectory(storageDirectory);
        String filename = contentHash(processed.bytes()).substring(0, 32) + "." + processed.extension();
        Path target = storageDirectory.resolve(filename).normalize();
        if (!target.startsWith(storageDirectory)) Asserts.fail("图片存储路径无效");
        writeOnce(target, processed.bytes());
        return new StoredImage(filename, target, processed.contentType(), processed.bytes().length);
    }

    /** Owner-only avatar; bounded replacement, never placed in the public product directory. */
    public synchronized StoredImage storeMemberAvatar(Long memberId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 2L * 1024 * 1024) Asserts.fail("请选择不超过2MB的头像");
        byte[] bytes = file.getBytes();
        ImageFormat format = detectFormat(bytes);
        if (format != ImageFormat.JPEG && format != ImageFormat.PNG) Asserts.fail("头像仅支持真实的JPG或PNG图片");
        ProcessedImage processed = processRaster(bytes, 512);
        Path directory = memberAvatarDirectory(memberId);
        Files.createDirectories(directory);
        ensurePrivateDirectory(privateStorageDirectory);
        ensurePrivateDirectory(directory.getParent());
        ensurePrivateDirectory(directory);
        String filename = "avatar." + processed.extension();
        Path target = directory.resolve(filename);
        Path temporary = Files.createTempFile(directory, "avatar-", ".tmp");
        try {
            ensurePrivate(temporary);
            Files.write(temporary, processed.bytes());
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            ensurePrivate(target);
        } finally { Files.deleteIfExists(temporary); }
        // Only two fixed raster filenames can exist for an account; no unbounded historical uploads.
        return new StoredImage(filename, target, processed.contentType(), processed.bytes().length);
    }

    public StoredImage loadMemberAvatar(Long memberId, String filename) throws IOException {
        if (!"avatar.jpg".equals(filename) && !"avatar.png".equals(filename)) return null;
        Path target = memberAvatarDirectory(memberId).resolve(filename);
        if (!Files.isRegularFile(target)) return null;
        return new StoredImage(filename, target, contentType(filename), Files.size(target));
    }

    private Path memberAvatarDirectory(Long memberId) {
        if (memberId == null || memberId <= 0) Asserts.fail("账号信息无效");
        return privateStorageDirectory.resolve("member-avatars").resolve(String.valueOf(memberId));
    }

    /**
     * 品牌文化图片专用存储：只接受声明类型、扩展名和真实内容一致的 JPG/PNG/WEBP，
     * 按客户隔离并使用随机文件名，避免与公开商品图的内容寻址规则混用。
     */
    public StoredImage storeBrandCultureImage(Long tenantId, boolean cover, MultipartFile file) throws IOException {
        if (tenantId == null || tenantId <= 0) Asserts.fail("商城客户信息无效");
        long maxSize = cover ? MAX_BRAND_CULTURE_COVER_SIZE : MAX_BRAND_CULTURE_DETAIL_SIZE;
        if (file == null || file.isEmpty()) Asserts.fail("请选择图片文件");
        if (file.getSize() > maxSize) {
            Asserts.fail(cover ? "页面封面不能超过3MB" : "详情图单张不能超过5MB");
        }
        byte[] original = file.getBytes();
        ImageFormat detected = detectFormat(original);
        if (detected != ImageFormat.JPEG && detected != ImageFormat.PNG && detected != ImageFormat.WEBP) {
            Asserts.fail("仅支持真实的JPG、PNG或WebP图片，不支持SVG、GIF或其他文件");
        }
        validateBrandCultureMetadata(file, detected);
        ProcessedImage processed;
        if (detected == ImageFormat.WEBP) {
            validateWebp(original);
            processed = new ProcessedImage(original, "webp", "image/webp");
        } else {
            processed = processRaster(original);
        }
        if (processed.bytes().length > maxSize) {
            Asserts.fail(cover ? "页面封面处理后仍超过3MB，请压缩后重试" : "详情图处理后仍超过5MB，请压缩后重试");
        }
        Path tenantDirectory = brandCultureDirectory(tenantId);
        Files.createDirectories(tenantDirectory);
        ensureWebReadableDirectory(storageDirectory);
        ensureWebReadableDirectory(storageDirectory.resolve("brand-culture"));
        ensureWebReadableDirectory(tenantDirectory);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + processed.extension();
        Path target = tenantDirectory.resolve(filename).normalize();
        if (!target.startsWith(tenantDirectory)) Asserts.fail("品牌文化图片存储路径无效");
        Files.write(target, processed.bytes(), StandardOpenOption.CREATE_NEW);
        ensureWebReadable(target);
        return new StoredImage(filename, target, processed.contentType(), processed.bytes().length);
    }

    public StoredImage loadBrandCultureImage(Long tenantId, String filename) throws IOException {
        if (tenantId == null || tenantId <= 0 || filename == null || !filename.matches(FILE_NAME_PATTERN)) return null;
        Path directory = brandCultureDirectory(tenantId);
        Path target = directory.resolve(filename.toLowerCase(Locale.ROOT)).normalize();
        if (!target.startsWith(directory) || !Files.isRegularFile(target)) return null;
        return new StoredImage(filename, target, contentType(filename), Files.size(target));
    }

    public StoredImage loadBrandCultureImageByUrl(Long tenantId, String url) throws IOException {
        if (url == null) return null;
        String prefix = "/api/shop/media/brand-culture/" + tenantId + "/";
        if (!url.startsWith(prefix) || url.indexOf('?', prefix.length()) >= 0 || url.indexOf('#', prefix.length()) >= 0) return null;
        return loadBrandCultureImage(tenantId, url.substring(prefix.length()));
    }

    /**
     * 保存会员售后凭证。凭证使用随机文件名并按会员隔离，不进入公开商品图片目录。
     */
    public synchronized StoredImage storeAfterSaleProof(Long memberId, MultipartFile file) throws IOException {
        Path memberDirectory = temporaryAfterSaleProofDirectory(memberId);
        ProcessedImage processed = processUpload(file);
        Files.createDirectories(memberDirectory);
        ensurePrivateDirectory(privateStorageDirectory);
        ensurePrivateDirectory(privateStorageDirectory.resolve("after-sale-proofs"));
        ensurePrivateDirectory(privateStorageDirectory.resolve("after-sale-proofs").resolve("temp"));
        ensurePrivateDirectory(memberDirectory);
        cleanupExpiredTemporaryProofs(memberDirectory);
        try (Stream<Path> files = Files.list(memberDirectory)) {
            List<Path> existing = files.filter(Files::isRegularFile).toList();
            long existingBytes = 0L;
            for (Path path : existing) existingBytes += Files.size(path);
            if (existing.size() >= MAX_TEMP_PROOFS_PER_MEMBER
                    || existingBytes + processed.bytes().length > MAX_TEMP_PROOF_BYTES_PER_MEMBER) {
                Asserts.fail("待提交售后凭证过多，请先提交申请或24小时后重试");
            }
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + processed.extension();
        Path target = memberDirectory.resolve(filename).normalize();
        if (!target.startsWith(memberDirectory)) Asserts.fail("售后凭证存储路径无效");
        Files.write(target, processed.bytes(), StandardOpenOption.CREATE_NEW);
        ensurePrivate(target);
        return new StoredImage(filename, target, processed.contentType(), processed.bytes().length);
    }

    public StoredImage loadAfterSaleProof(Long memberId, String filename) throws IOException {
        if (filename == null || !filename.matches(FILE_NAME_PATTERN)) return null;
        String normalizedFilename = filename.toLowerCase(Locale.ROOT);
        for (Path memberDirectory : List.of(committedAfterSaleProofDirectory(memberId),
                legacyAfterSaleProofDirectory(memberId), temporaryAfterSaleProofDirectory(memberId))) {
            Path target = memberDirectory.resolve(normalizedFilename).normalize();
            if (target.startsWith(memberDirectory) && Files.isRegularFile(target)) {
                return new StoredImage(filename, target, contentType(filename), Files.size(target));
            }
        }
        return null;
    }

    /** 售后单创建成功后，将短期上传凭证转为正式私密凭证。 */
    public synchronized void commitAfterSaleProofs(Long memberId, List<String> filenames) throws IOException {
        if (filenames == null || filenames.isEmpty()) return;
        Path sourceDirectory = temporaryAfterSaleProofDirectory(memberId);
        Path targetDirectory = committedAfterSaleProofDirectory(memberId);
        Files.createDirectories(targetDirectory);
        ensurePrivateDirectory(privateStorageDirectory.resolve("after-sale-proofs").resolve("committed"));
        ensurePrivateDirectory(targetDirectory);
        for (String filename : new java.util.LinkedHashSet<>(filenames)) {
            if (filename == null || !filename.matches(FILE_NAME_PATTERN)) Asserts.fail("售后凭证文件名无效");
            Path source = sourceDirectory.resolve(filename.toLowerCase(Locale.ROOT)).normalize();
            Path target = targetDirectory.resolve(filename.toLowerCase(Locale.ROOT)).normalize();
            if (!source.startsWith(sourceDirectory) || !Files.isRegularFile(source)) {
                if (!Files.isRegularFile(target)) Asserts.fail("售后凭证不存在或已过期");
                continue;
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            ensurePrivate(target);
        }
    }

    private ProcessedImage processUpload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) Asserts.fail("请选择图片文件");
        if (file.getSize() > MAX_IMAGE_SIZE) Asserts.fail("单张图片不能超过5MB");

        byte[] original = file.getBytes();
        ImageFormat detected = detectFormat(original);
        if (detected == null) Asserts.fail("图片内容无效，仅支持JPG、PNG、WEBP、GIF");

        ProcessedImage processed = switch (detected) {
            case JPEG, PNG -> processRaster(original);
            case GIF -> {
                validateDimensions(original);
                yield new ProcessedImage(original, "gif", "image/gif");
            }
            case WEBP -> new ProcessedImage(original, "webp", "image/webp");
        };
        if (processed.bytes().length > MAX_IMAGE_SIZE) {
            Asserts.fail("图片处理后仍超过5MB，请缩小尺寸后重试");
        }
        return processed;
    }

    public StoredImage load(String filename) throws IOException {
        if (filename == null || !filename.matches(FILE_NAME_PATTERN)) return null;
        Path target = storageDirectory.resolve(filename.toLowerCase(Locale.ROOT)).normalize();
        if (!target.startsWith(storageDirectory) || !Files.isRegularFile(target)) return null;
        return new StoredImage(filename, target, contentType(filename), Files.size(target));
    }

    private ProcessedImage processRaster(byte[] source) throws IOException {
        return processRaster(source, maxDimension);
    }

    private ProcessedImage processRaster(byte[] source, int dimension) throws IOException {
        BufferedImage input = readImage(source);
        int sourceWidth = input.getWidth();
        int sourceHeight = input.getHeight();
        double scale = Math.min(1.0d, (double) dimension / Math.max(sourceWidth, sourceHeight));
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        boolean preserveAlpha = input.getColorModel().hasAlpha();

        BufferedImage output = new BufferedImage(targetWidth, targetHeight,
                preserveAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(input, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        if (preserveAlpha) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(output, "png", bytes)) Asserts.fail("PNG图片处理失败");
            return new ProcessedImage(bytes.toByteArray(), "png", "image/png");
        }
        return new ProcessedImage(writeJpeg(output), "jpg", "image/jpeg");
    }

    private BufferedImage readImage(byte[] source) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (stream == null) {
                Asserts.fail("图片内容无法读取");
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                Asserts.fail("图片内容无法解码");
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
                BufferedImage image = reader.read(0);
                if (image == null) Asserts.fail("图片内容无法解码");
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private void validateDimensions(byte[] source) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = stream == null ? null : ImageIO.getImageReaders(stream);
            if (readers == null || !readers.hasNext()) {
                Asserts.fail("图片内容无法解码");
                return;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > maxPixels) {
            Asserts.fail("图片像素过大，请缩小后重试");
        }
    }

    private Path brandCultureDirectory(Long tenantId) {
        Path directory = storageDirectory.resolve("brand-culture").resolve(String.valueOf(tenantId)).normalize();
        if (!directory.startsWith(storageDirectory)) Asserts.fail("品牌文化图片存储路径无效");
        return directory;
    }

    private void validateBrandCultureMetadata(MultipartFile file, ImageFormat detected) {
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String extension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.') + 1) : "";
        String mime = file.getContentType() == null ? "" : file.getContentType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        boolean matches = switch (detected) {
            case JPEG -> ("jpg".equals(extension) || "jpeg".equals(extension)) && "image/jpeg".equals(mime);
            case PNG -> "png".equals(extension) && "image/png".equals(mime);
            case WEBP -> "webp".equals(extension) && "image/webp".equals(mime);
            default -> false;
        };
        if (!matches) Asserts.fail("文件扩展名、类型与图片真实内容不一致，请重新导出后上传");
    }

    /** 解析 WebP 容器和画布尺寸，避免仅凭 RIFF 文件头接受伪装内容。 */
    private void validateWebp(byte[] bytes) {
        if (bytes.length < 30 || readLe32(bytes, 4) + 8L != bytes.length) Asserts.fail("WebP图片内容不完整");
        int offset = 12;
        int canvasWidth = 0;
        int canvasHeight = 0;
        while (offset + 8 <= bytes.length) {
            String chunk = new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long chunkSize = readLe32(bytes, offset + 4);
            int data = offset + 8;
            if (chunkSize < 0 || chunkSize > Integer.MAX_VALUE || data + chunkSize > bytes.length) Asserts.fail("WebP图片内容损坏");
            int width = 0;
            int height = 0;
            if ("VP8X".equals(chunk) && chunkSize >= 10) {
                canvasWidth = 1 + readLe24(bytes, data + 4);
                canvasHeight = 1 + readLe24(bytes, data + 7);
                validateDimensions(canvasWidth, canvasHeight);
            } else if ("VP8L".equals(chunk) && chunkSize >= 5 && (bytes[data] & 0xff) == 0x2f) {
                int b1 = bytes[data + 1] & 0xff, b2 = bytes[data + 2] & 0xff;
                int b3 = bytes[data + 3] & 0xff, b4 = bytes[data + 4] & 0xff;
                width = 1 + b1 + ((b2 & 0x3f) << 8);
                height = 1 + (b2 >> 6) + (b3 << 2) + ((b4 & 0x0f) << 10);
            } else if ("VP8 ".equals(chunk) && chunkSize >= 10
                    && (bytes[data + 3] & 0xff) == 0x9d && (bytes[data + 4] & 0xff) == 0x01
                    && (bytes[data + 5] & 0xff) == 0x2a) {
                width = ((bytes[data + 6] & 0xff) | ((bytes[data + 7] & 0x3f) << 8));
                height = ((bytes[data + 8] & 0xff) | ((bytes[data + 9] & 0x3f) << 8));
            }
            if (width > 0 && height > 0) {
                validateDimensions(width, height);
                if (canvasWidth > 0 && (width > canvasWidth || height > canvasHeight)) {
                    Asserts.fail("WebP图片画布尺寸无效");
                }
                return;
            }
            offset = (int) (data + chunkSize + (chunkSize & 1));
        }
        Asserts.fail("WebP图片内容无法解析");
    }

    private long readLe32(byte[] bytes, int offset) {
        if (offset < 0 || offset + 4 > bytes.length) return -1L;
        return (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16) | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private int readLe24(byte[] bytes, int offset) {
        if (offset < 0 || offset + 3 > bytes.length) return -1;
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8) | ((bytes[offset + 2] & 0xff) << 16);
    }

    private byte[] writeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            Asserts.fail("JPEG图片处理失败");
            return new byte[0];
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(jpegQuality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private void writeOnce(Path target, byte[] bytes) throws IOException {
        if (Files.isRegularFile(target)) {
            ensureWebReadable(target);
            return;
        }
        Path temp = Files.createTempFile(storageDirectory, ".upload-", ".tmp");
        try {
            Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target);
            } catch (FileAlreadyExistsException ignored) {
                // 同一内容并发上传时复用已经落盘的文件。
            }
            ensureWebReadable(target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void ensureWebReadable(Path target) throws IOException {
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r--r--"));
        } catch (UnsupportedOperationException ignored) {
            warnUnsupportedPosix(target);
        }
    }

    private void ensureWebReadableDirectory(Path target) throws IOException {
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException ignored) {
            warnUnsupportedPosix(target);
        }
    }

    private void ensurePrivate(Path target) throws IOException {
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            warnUnsupportedPosix(target);
        }
    }

    private void ensurePrivateDirectory(Path target) throws IOException {
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            warnUnsupportedPosix(target);
        }
    }

    private void warnUnsupportedPosix(Path target) {
        if (POSIX_PERMISSION_WARNING_LOGGED.compareAndSet(false, true)) {
            log.warn("当前文件系统不支持POSIX权限，媒体文件沿用系统默认权限；请核对运行账号和目录ACL: path={}", target);
        }
    }

    private Path temporaryAfterSaleProofDirectory(Long memberId) {
        return afterSaleProofDirectory(memberId, "temp");
    }

    private Path committedAfterSaleProofDirectory(Long memberId) {
        return afterSaleProofDirectory(memberId, "committed");
    }

    private Path legacyAfterSaleProofDirectory(Long memberId) {
        if (memberId == null || memberId <= 0) Asserts.fail("会员信息无效");
        Path directory = privateStorageDirectory.resolve("after-sale-proofs")
                .resolve(String.valueOf(memberId)).normalize();
        if (!directory.startsWith(privateStorageDirectory)) Asserts.fail("售后凭证存储路径无效");
        return directory;
    }

    private Path afterSaleProofDirectory(Long memberId, String state) {
        if (memberId == null || memberId <= 0) Asserts.fail("会员信息无效");
        Path directory = privateStorageDirectory.resolve("after-sale-proofs").resolve(state)
                .resolve(String.valueOf(memberId)).normalize();
        if (!directory.startsWith(privateStorageDirectory)) Asserts.fail("售后凭证存储路径无效");
        return directory;
    }

    private int cleanupExpiredTemporaryProofs(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return 0;
        int deleted = 0;
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        try (Stream<Path> files = Files.list(directory)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff) && Files.deleteIfExists(path)) deleted++;
            }
        }
        return deleted;
    }

    /** 每日清理所有会员超过24小时且尚未提交的售后临时凭证。 */
    @Scheduled(cron = "${shop.media.temp-proof-cleanup-cron:0 15 4 * * ?}")
    public synchronized int cleanupExpiredTemporaryProofs() {
        Path root = privateStorageDirectory.resolve("after-sale-proofs").resolve("temp").normalize();
        if (!root.startsWith(privateStorageDirectory) || !Files.isDirectory(root)) return 0;
        int deleted = 0;
        try (Stream<Path> directories = Files.list(root)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                try {
                    deleted += cleanupExpiredTemporaryProofs(directory);
                    try (Stream<Path> remaining = Files.list(directory)) {
                        if (remaining.findAny().isEmpty()) Files.deleteIfExists(directory);
                    }
                } catch (IOException e) {
                    log.warn("清理会员售后临时凭证失败: directory={}", directory, e);
                }
            }
        } catch (IOException e) {
            log.warn("扫描售后临时凭证目录失败: root={}", root, e);
        }
        if (deleted > 0) log.info("已清理超过24小时的售后临时凭证: count={}", deleted);
        return deleted;
    }

    private static ImageFormat detectFormat(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) return ImageFormat.JPEG;
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) return ImageFormat.PNG;
        if (bytes.length >= 6) {
            String signature = new String(bytes, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
            if ("GIF87a".equals(signature) || "GIF89a".equals(signature)) return ImageFormat.GIF;
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return ImageFormat.WEBP;
        return null;
    }

    private static String contentHash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    private static String contentType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }

    private enum ImageFormat { JPEG, PNG, WEBP, GIF }

    private record ProcessedImage(byte[] bytes, String extension, String contentType) {}

    public record StoredImage(String filename, Path path, String contentType, long size) {}
}
