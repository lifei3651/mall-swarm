package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户/客户公司配置
 */
@Data
public class DmsTenant implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String tenantCode;

    private String tenantName;

    private String brandName;

    private String logoUrl;

    private String themeColor;

    private String productTemplate;

    /** 实际经营地址及前台客服/合规信息。 */
    private String companyAddress;
    private String unifiedSocialCreditCode;
    private String servicePhone;
    private String serviceEmail;
    private String serviceHours;
    private String thirdPartyServices;
    private String icpNumber;
    private String policeRecordNumber;
    private String policeRecordUrl;
    private String businessLicenseUrl;

    /** 是否在前台展示营业执照：1-展示，0-隐藏 */
    private Integer showBusinessLicense;

    /** 前台协议正文，按纯文本保存和展示，避免脚本注入。 */
    private String userAgreement;
    private String privacyPolicy;
    private String afterSalePolicy;

    /** 常见问题FAQ，JSON格式存储 */
    private String faqs;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
