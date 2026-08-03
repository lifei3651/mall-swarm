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
    private String servicePhone;
    private String serviceEmail;
    private String icpNumber;
    private String policeRecordNumber;
    private String policeRecordUrl;
    private String businessLicenseUrl;

    /** 前台协议正文，按纯文本保存和展示，避免脚本注入。 */
    private String userAgreement;
    private String privacyPolicy;
    private String afterSalePolicy;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
