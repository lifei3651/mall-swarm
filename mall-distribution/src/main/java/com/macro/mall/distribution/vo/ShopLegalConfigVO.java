package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsTenant;
import lombok.Data;

import java.io.Serializable;

@Data
public class ShopLegalConfigVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String companyName;
    private String brandName;
    private String companyAddress;
    private String servicePhone;
    private String serviceEmail;
    private String icpNumber;
    private String policeRecordNumber;
    private String policeRecordUrl;
    private String businessLicenseUrl;
    /** 是否在前台展示营业执照：true-展示，false-隐藏 */
    private Boolean showBusinessLicense;
    private String userAgreement;
    private String privacyPolicy;
    private String afterSalePolicy;

    /** 常见问题FAQ，JSON格式 */
    private String faqs;

    public static ShopLegalConfigVO from(DmsTenant tenant) {
        ShopLegalConfigVO vo = new ShopLegalConfigVO();
        if (tenant == null) return vo;
        vo.setCompanyName(tenant.getTenantName());
        vo.setBrandName(tenant.getBrandName());
        vo.setCompanyAddress(tenant.getCompanyAddress());
        vo.setServicePhone(tenant.getServicePhone());
        vo.setServiceEmail(tenant.getServiceEmail());
        vo.setIcpNumber(tenant.getIcpNumber());
        vo.setPoliceRecordNumber(tenant.getPoliceRecordNumber());
        vo.setPoliceRecordUrl(tenant.getPoliceRecordUrl());
        vo.setBusinessLicenseUrl(tenant.getBusinessLicenseUrl());
        vo.setShowBusinessLicense(tenant.getShowBusinessLicense() == null || tenant.getShowBusinessLicense() == 1);
        vo.setUserAgreement(tenant.getUserAgreement());
        vo.setPrivacyPolicy(tenant.getPrivacyPolicy());
        vo.setAfterSalePolicy(tenant.getAfterSalePolicy());
        vo.setFaqs(tenant.getFaqs());
        return vo;
    }
}
