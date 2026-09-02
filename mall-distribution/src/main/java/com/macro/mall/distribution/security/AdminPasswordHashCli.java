package com.macro.mall.distribution.security;

import cn.hutool.crypto.digest.BCrypt;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** 私有部署初始化专用：只从标准输入读取密码，只向标准输出写 BCrypt 哈希。 */
public final class AdminPasswordHashCli {

    private AdminPasswordHashCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("密码只允许通过标准输入提供");
        String password;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            password = reader.readLine();
        }
        validate(password);
        System.out.print(BCrypt.hashpw(password));
    }

    private static void validate(String password) {
        if (password == null || password.length() < 10 || password.length() > 64) {
            throw new IllegalArgumentException("密码长度必须为10-64位");
        }
        int groups = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) groups++;
        if (password.chars().anyMatch(Character::isUpperCase)) groups++;
        if (password.chars().anyMatch(Character::isDigit)) groups++;
        if (password.chars().anyMatch(value -> !Character.isLetterOrDigit(value))) groups++;
        if (groups < 3) throw new IllegalArgumentException("密码复杂度不足");
    }
}
