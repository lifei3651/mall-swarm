package com.macro.mall.distribution.vo;

import java.io.Serializable;

public record TenantLegalTemplatesVO(String userAgreement, String privacyPolicy, String afterSalePolicy)
        implements Serializable {
}
