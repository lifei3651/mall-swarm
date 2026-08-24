package com.macro.mall.common.log;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Prevents credentials and customer PII from reaching application logs. */
public final class SensitiveLogSanitizer {
    private static final int MAX_DEPTH = 5;
    private static final int MAX_ITEMS = 50;
    private static final int MAX_TEXT = 2000;
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "password", "pwd", "token", "authorization", "cookie", "session", "secret", "privatekey",
            "publickey", "accesskey", "sign", "smscode", "verifycode", "captcha", "mobile", "phone",
            "idcard", "realname", "bankcard", "creditcode", "address", "receiver", "loginaccount", "username", "email"
    );
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+\\/-]+=*");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1\\d{10}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PRIVATE_KEY = Pattern.compile("(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----");
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
            "(?i)(password|pwd|token|secret|smscode|verifycode|captcha|authorization|cookie)(\\s*[:=]\\s*)(?:Bearer\\s+)?[^,;\\s}\\]]+");

    private SensitiveLogSanitizer() {}

    public static Object sanitize(Object value) {
        return sanitize(value, 0, new IdentityHashMap<>());
    }

    public static String sanitizeText(String value) {
        if (value == null) return null;
        String clean = PRIVATE_KEY.matcher(value).replaceAll("[PRIVATE KEY REDACTED]");
        clean = KEY_VALUE_SECRET.matcher(clean).replaceAll("$1$2***");
        clean = BEARER.matcher(clean).replaceAll("Bearer ***");
        clean = JWT.matcher(clean).replaceAll("[TOKEN REDACTED]");
        clean = PHONE.matcher(clean).replaceAll("1**********");
        clean = EMAIL.matcher(clean).replaceAll("***@***");
        clean = clean.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        return clean.length() > MAX_TEXT ? clean.substring(0, MAX_TEXT) + "…" : clean;
    }

    public static boolean isSensitiveName(String name) {
        if (name == null) return false;
        String normalized = name.replace("_", "").replace("-", "").toLowerCase();
        return SENSITIVE_NAMES.stream().anyMatch(normalized::contains);
    }

    private static Object sanitize(Object value, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (value == null) return null;
        if (value instanceof CharSequence) return sanitizeText(value.toString());
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof TemporalAccessor) return value;
        if (depth >= MAX_DEPTH) return "[MAX_DEPTH]";
        if (visited.put(value, Boolean.TRUE) != null) return "[CIRCULAR]";
        try {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                int count = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (count++ >= MAX_ITEMS) break;
                    String key = String.valueOf(entry.getKey());
                    result.put(key, isSensitiveName(key) ? "***" : sanitize(entry.getValue(), depth + 1, visited));
                }
                return result;
            }
            if (value instanceof Collection<?> collection) {
                List<Object> result = new ArrayList<>();
                int count = 0;
                for (Object item : collection) {
                    if (count++ >= MAX_ITEMS) break;
                    result.add(sanitize(item, depth + 1, visited));
                }
                return result;
            }
            if (value.getClass().isArray()) {
                List<Object> result = new ArrayList<>();
                for (int i = 0; i < Math.min(Array.getLength(value), MAX_ITEMS); i++) {
                    result.add(sanitize(Array.get(value, i), depth + 1, visited));
                }
                return result;
            }
            Package objectPackage = value.getClass().getPackage();
            if (objectPackage != null && objectPackage.getName().startsWith("java.")) return "[" + value.getClass().getSimpleName() + "]";
            Map<String, Object> result = new LinkedHashMap<>();
            Class<?> type = value.getClass();
            while (type != null && type != Object.class) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                    String name = field.getName();
                    if (isSensitiveName(name)) {
                        result.put(name, "***");
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        result.put(name, sanitize(field.get(value), depth + 1, visited));
                    } catch (RuntimeException | IllegalAccessException ignored) {
                        result.put(name, "[UNAVAILABLE]");
                    }
                }
                type = type.getSuperclass();
            }
            return result;
        } finally {
            visited.remove(value);
        }
    }
}
