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
    private String userAgreement;
    private String privacyPolicy;
    private String afterSalePolicy;

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
        vo.setUserAgreement(tenant.getUserAgreement());
        vo.setPrivacyPolicy(tenant.getPrivacyPolicy());
        vo.setAfterSalePolicy(tenant.getAfterSalePolicy());
        return vo;
    }
}
