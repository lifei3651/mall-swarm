package com.macro.mall.distribution.identity;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class MainlandIdCard {
    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECKSUMS = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
    private static final DateTimeFormatter BIRTHDAY = DateTimeFormatter.BASIC_ISO_DATE;

    private MainlandIdCard() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String value) {
        String idCard = normalize(value);
        if (!idCard.matches("^[1-9]\\d{16}[0-9X]$")) return false;
        try {
            LocalDate birthDate = LocalDate.parse(idCard.substring(6, 14), BIRTHDAY);
            if (birthDate.isAfter(LocalDate.now()) || birthDate.isBefore(LocalDate.of(1900, 1, 1))) return false;
        } catch (DateTimeException ignored) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) sum += (idCard.charAt(i) - '0') * WEIGHTS[i];
        return idCard.charAt(17) == CHECKSUMS[sum % 11];
    }

    public static boolean isAdult(String value) {
        if (!isValid(value)) return false;
        LocalDate birthDate = LocalDate.parse(normalize(value).substring(6, 14), BIRTHDAY);
        return Period.between(birthDate, LocalDate.now()).getYears() >= 18;
    }
}
