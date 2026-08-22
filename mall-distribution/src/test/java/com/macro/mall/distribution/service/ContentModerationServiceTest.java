package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentModerationServiceTest {

    @Test
    void emptyCustomerDictionaryKeepsFoundationContentCompatible() {
        ContentModerationService service = new ContentModerationService("");
        assertDoesNotThrow(() -> service.assertAllowed("评价内容", "商品包装完整，物流很快"));
    }

    @Test
    void configuredCustomerDictionaryBlocksMatchingContentIgnoringCase() {
        ContentModerationService service = new ContentModerationService("禁止词,BlockedWord");
        assertThrows(ApiException.class,
                () -> service.assertAllowed("评价内容", "这里包含 BLOCKEDword"));
    }
}
