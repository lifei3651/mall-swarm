package com.macro.mall.distribution.service;

import com.macro.mall.distribution.vo.PayloadEncryptionKeyVO;

public interface PayloadEncryptionService {

    PayloadEncryptionKeyVO issueChallenge();

    boolean hasSensitiveValue(Object body);

    void decryptSensitiveValues(String challengeId, String encryptedKey, Object body);
}
