package com.macro.mall.distribution.entity;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户/客户公司配置
 */
@Data
public class DmsTenant implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @Size(max = 64, message = "客户编码不能超过64个字符")
    private String tenantCode;

    @Size(max = 128, message = "经营主体名称不能超过128个字")
    private String tenantName;

    @Size(max = 64, message = "商城品牌名不能超过64个字")
    private String brandName;

    @Size(max = 2048, message = "品牌LOGO地址不能超过2048个字符")
    private String logoUrl;

    @Size(max = 16, message = "主题色格式不正确")
    private String themeColor;

    @Size(max = 64, message = "页面模板名称不能超过64个字符")
    private String productTemplate;

    /** 实际经营地址及前台客服/合规信息。 */
    @Size(max = 255, message = "经营地址不能超过255个字")
    private String companyAddress;
    @Size(max = 18, message = "统一社会信用代码不能超过18个字符")
    private String unifiedSocialCreditCode;
    @Size(max = 32, message = "客服电话不能超过32个字符")
    private String servicePhone;
    @Email(message = "客服邮箱格式不正确")
    @Size(max = 128, message = "客服邮箱不能超过128个字符")
    private String serviceEmail;
    @Size(max = 128, message = "客服时间不能超过128个字")
    private String serviceHours;
    @Size(max = 2000, message = "第三方服务说明不能超过2000个字")
    private String thirdPartyServices;
    @Size(max = 128, message = "ICP备案号不能超过128个字符")
    private String icpNumber;
    @Size(max = 128, message = "公安备案号不能超过128个字符")
    private String policeRecordNumber;
    @Size(max = 512, message = "公安备案链接不能超过512个字符")
    private String policeRecordUrl;
    @Size(max = 2048, message = "营业执照图片地址不能超过2048个字符")
    private String businessLicenseUrl;

    /** 是否在前台展示营业执照：1-展示，0-隐藏 */
    private Integer showBusinessLicense;

    /** 前台协议正文，按纯文本保存和展示，避免脚本注入。 */
    @Size(max = 30000, message = "用户服务协议不能超过30000个字")
    private String userAgreement;
    @Size(max = 30000, message = "隐私政策不能超过30000个字")
    private String privacyPolicy;
    @Size(max = 30000, message = "交易与售后规则不能超过30000个字")
    private String afterSalePolicy;

    /**
     * 客户售后入口期限起算方式：
     * RECEIVED-签收后起算（推荐，未签收前不关闭入口）；
     * ORDER_CREATED-下单后起算（兼容历史业务规则）。
     */
    @Pattern(regexp = "RECEIVED|ORDER_CREATED", message = "售后期限起算方式不正确")
    private String afterSaleWindowMode;

    @Min(value = 0, message = "售后申请期限不能小于0天")
    @Max(value = 365, message = "售后申请期限不能超过365天")
    private Integer afterSaleWindowDays;

    /** 秒杀模块和复购商城均为客户级可选能力，默认关闭。 */
    private Integer flashSaleEnabled;

    @Pattern(regexp = "NONE|STANDARD|CUSTOM", message = "秒杀奖金模式不正确")
    private String flashSaleBonusMode;

    private Integer repurchaseMallEnabled;

    @Pattern(regexp = "PAID_MEMBER|AGENT|ALL_MEMBER", message = "复购商城准入模式不正确")
    private String repurchaseEligibilityMode;

    @Pattern(regexp = "NONE|STANDARD|CUSTOM", message = "复购奖金模式不正确")
    private String repurchaseBonusMode;

    /** 常见问题FAQ，JSON格式存储 */
    @Size(max = 30000, message = "常见问题内容过长")
    private String faqs;

    private Integer status;

    @Size(max = 500, message = "商城资料备注不能超过500个字")
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
