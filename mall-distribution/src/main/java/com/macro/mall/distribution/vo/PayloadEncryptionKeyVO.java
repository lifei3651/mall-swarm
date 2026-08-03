package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/** One-time key material used to encrypt sensitive request fields in the browser. */
@Data
@AllArgsConstructor
public class PayloadEncryptionKeyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String challengeId;
    private String publicKey;
    private String algorithm;
    private long expiresAt;
}
