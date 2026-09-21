package com.macro.mall.distribution.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommerceVocabularyContractTest {

    private static final List<String> FORBIDDEN = List.of(
            "报" + "单",
            "客户" + "定制奖金",
            "客户奖金" + "处理");
    private static final Set<String> TEXT_SUFFIXES = Set.of(
            ".java", ".xml", ".yml", ".yaml", ".properties", ".json");

    @Test
    void productionSourcesUseOnlyOrdinaryMallAndRepurchaseVocabulary() throws IOException {
        Path sourceRoot = Path.of("src/main");
        assertTrue(Files.isDirectory(sourceRoot), "未找到 mall-distribution 生产源码目录");
        List<String> failures = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> TEXT_SUFFIXES.stream().anyMatch(suffix -> path.toString().endsWith(suffix)))
                    .forEach(path -> scan(path, failures));
        }
        assertTrue(failures.isEmpty(), () -> "用户可见业务术语回退:\n" + String.join("\n", failures));
    }

    private void scan(Path path, List<String> failures) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                for (String word : FORBIDDEN) {
                    if (lines.get(index).contains(word)) {
                        failures.add(path + ":" + (index + 1));
                    }
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("无法扫描生产源码: " + path, error);
        }
    }
}
