package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** 导入文件的统一资源边界，避免各导入入口分别放宽格式和容量。 */
final class ImportFilePolicy {

    static final int MAX_IMPORT_ROWS = 5000;
    static final int MAX_IMPORT_COLUMNS = 64;
    static final int MAX_CELL_LENGTH = 2000;
    static final int MAX_TEXT_LINE_LENGTH = 16384;
    static final long MAX_IMPORT_FILE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("csv", "txt", "xls", "xlsx");

    private ImportFilePolicy() {
    }

    static String requireSupportedExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) Asserts.fail("导入文件不能为空");
        if (file.getSize() > MAX_IMPORT_FILE_BYTES) Asserts.fail("导入文件不能超过5MB");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            Asserts.fail("导入文件仅支持 XLSX、XLS、CSV 或 TXT 格式");
        }
        validateContent(file, extension);
        return extension;
    }

    private static void validateContent(MultipartFile file, String extension) {
        try {
            if ("xls".equals(extension)) {
                requireSignature(file, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                        (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}, "XLS");
                return;
            }
            if ("xlsx".equals(extension)) {
                byte[] prefix;
                try (InputStream input = file.getInputStream()) {
                    prefix = input.readNBytes(4);
                }
                boolean zipSignature = prefix.length == 4 && prefix[0] == 'P' && prefix[1] == 'K'
                        && ((prefix[2] == 3 && prefix[3] == 4)
                        || (prefix[2] == 5 && prefix[3] == 6)
                        || (prefix[2] == 7 && prefix[3] == 8));
                if (!zipSignature) Asserts.fail("XLSX文件内容与扩展名不一致");
                return;
            }
            validateUtf8Text(file);
        } catch (IOException e) {
            Asserts.fail("导入文件无法读取，请重新选择文件");
        }
    }

    private static void requireSignature(MultipartFile file, byte[] expected, String format) throws IOException {
        byte[] actual;
        try (InputStream input = file.getInputStream()) {
            actual = input.readNBytes(expected.length);
        }
        if (!java.util.Arrays.equals(actual, expected)) {
            Asserts.fail(format + "文件内容与扩展名不一致");
        }
    }

    private static void validateUtf8Text(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            Asserts.fail("CSV/TXT文件必须使用UTF-8编码");
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (value == '\u0000' || (value < 0x20 && value != '\r' && value != '\n' && value != '\t')) {
                Asserts.fail("CSV/TXT文件包含二进制控制字符");
            }
        }
    }

    static void requireRowCount(int count, int limit) {
        if (count > limit) Asserts.fail("单次导入最多" + limit + "行，请拆分文件后重试");
    }

    static void requireColumnCount(int count) {
        if (count > MAX_IMPORT_COLUMNS) Asserts.fail("导入文件列数过多，最多支持" + MAX_IMPORT_COLUMNS + "列");
    }

    static String requireCellLength(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > MAX_CELL_LENGTH) {
            Asserts.fail("导入文件存在超长单元格，单个字段最多" + MAX_CELL_LENGTH + "个字符");
        }
        return normalized;
    }
}
