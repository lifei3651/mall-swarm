package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportFilePolicyTest {

    @Test
    void acceptsSupportedBusinessFormatsAndRejectsUnknownFiles() {
        assertEquals("xlsx", ImportFilePolicy.requireSupportedExtension(
                new MockMultipartFile("file", "members.xlsx", "application/octet-stream", new byte[]{1})));
        assertThrows(ApiException.class, () -> ImportFilePolicy.requireSupportedExtension(
                new MockMultipartFile("file", "members.bin", "application/octet-stream", new byte[]{1})));
    }

    @Test
    void rejectsOversizedRowsColumnsAndCells() {
        assertThrows(ApiException.class,
                () -> ImportFilePolicy.requireRowCount(ImportFilePolicy.MAX_IMPORT_ROWS + 1,
                        ImportFilePolicy.MAX_IMPORT_ROWS));
        assertThrows(ApiException.class,
                () -> ImportFilePolicy.requireColumnCount(ImportFilePolicy.MAX_IMPORT_COLUMNS + 1));
        assertThrows(ApiException.class,
                () -> ImportFilePolicy.requireCellLength("x".repeat(ImportFilePolicy.MAX_CELL_LENGTH + 1)));
    }
}
