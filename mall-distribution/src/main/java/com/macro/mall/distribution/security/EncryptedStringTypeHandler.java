package com.macro.mall.distribution.security;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 数据库敏感字符串透明加密。密文带版本前缀，读取时兼容尚未迁移的历史明文。
 */
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    static final String PREFIX = "enc:v1:";
    private static final byte[] AAD = "lingqi-mall-field-v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private static volatile SecretKeySpec configuredKey;
    private static volatile boolean configuredWriteEnabled;

    private final SecretKeySpec key;
    private final boolean writeEnabled;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 由 MyBatis 对明确标注的敏感字段实例化，不能注册成全局 String 处理器。 */
    public EncryptedStringTypeHandler() {
        this.key = configuredKey;
        this.writeEnabled = configuredWriteEnabled;
    }

    /** 仅供独立密码学单元测试使用。 */
    EncryptedStringTypeHandler(String keyHex) {
        this(keyHex, true);
    }

    /** 仅供独立密码学/灰度开关单元测试使用。 */
    EncryptedStringTypeHandler(String keyHex, boolean writeEnabled) {
        this.key = parseKey(keyHex);
        this.writeEnabled = writeEnabled;
    }

    public static void configureKey(String keyHex) {
        configure(keyHex, true);
    }

    public static void configure(String keyHex, boolean writeEnabled) {
        configuredKey = parseConfiguredKey(keyHex);
        configuredWriteEnabled = writeEnabled;
    }

    public static boolean isKeyConfigured() {
        return configuredKey != null;
    }

    public static boolean isWriteEnabled() {
        return configuredWriteEnabled;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, writeEnabled ? encrypt(parameter) : parameter);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plainText) throws SQLException {
        if (plainText == null || plainText.isEmpty() || isEncrypted(plainText)) return plainText;
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(iv, iv.length + cipherText.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new SQLException("敏感字段加密失败", e);
        }
    }

    public String decrypt(String storedValue) throws SQLException {
        if (!isEncrypted(storedValue)) return storedValue;
        requireKey();
        try {
            byte[] payload = Base64.getUrlDecoder().decode(storedValue.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES) throw new GeneralSecurityException("invalid encrypted payload");
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] cipherText = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new SQLException("敏感字段解密失败，请核对部署密钥", e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private void requireKey() throws SQLException {
        if (key == null) throw new SQLException("敏感字段加密密钥未配置");
    }

    private SecretKeySpec parseKey(String keyHex) {
        return parseConfiguredKey(keyHex);
    }

    private static SecretKeySpec parseConfiguredKey(String keyHex) {
        if (keyHex == null || keyHex.isBlank()) return null;
        String normalized = keyHex.trim();
        if (!normalized.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException("敏感字段加密密钥必须是64位十六进制随机值");
        }
        return new SecretKeySpec(HexFormat.of().parseHex(normalized), "AES");
    }
}
