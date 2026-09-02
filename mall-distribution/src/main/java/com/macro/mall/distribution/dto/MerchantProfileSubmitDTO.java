package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 商户自行填写并提交的平台准入资料。 */
@Data
public class MerchantProfileSubmitDTO {
    @Size(max = 64, message = "联系人不能超过64个字符")
    private String contactName;

    @Size(max = 32, message = "联系电话不能超过32个字符")
    private String contactPhone;

    @NotBlank(message = "请填写经营主体")
    @Size(max = 128, message = "经营主体不能超过128个字符")
    private String legalEntityName;

    @NotBlank(message = "请填写统一社会信用代码")
    @Pattern(regexp = "^[0-9A-HJ-NPQRTUWXY]{18}$", message = "请填写18位统一社会信用代码")
    private String unifiedSocialCreditCode;

    @NotBlank(message = "请填写收款户名")
    @Size(max = 128, message = "收款户名不能超过128个字符")
    private String bankAccountName;

    @NotBlank(message = "请填写开户银行")
    @Size(max = 128, message = "开户银行不能超过128个字符")
    private String bankName;

    @NotBlank(message = "请填写银行账号")
    @Size(max = 64, message = "银行账号不能超过64个字符")
    private String bankAccountNo;

    @NotBlank(message = "请填写发票抬头")
    @Size(max = 128, message = "发票抬头不能超过128个字符")
    private String invoiceTitle;

    @NotBlank(message = "请填写纳税人识别号")
    @Pattern(regexp = "^[0-9A-HJ-NPQRTUWXY]{18}$", message = "请填写18位纳税人识别号")
    private String taxpayerIdentificationNo;
}
