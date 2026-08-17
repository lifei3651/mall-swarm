package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DmsShopProduct implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    /** 空表示平台自营；有值表示租户内商户。 */
    private Long merchantId;

    private String merchantName;

    @Size(max = 64, message = "商品编码不能超过64个字符")
    private String productNo;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 60, message = "商品名称不能超过60个字")
    private String productName;

    @Size(max = 80, message = "商品卖点不能超过80个字")
    private String subtitle;

    @Size(max = 64, message = "商品分类名称不能超过64个字")
    private String categoryName;

    @Size(max = 2048, message = "商品主图地址不能超过2048个字符")
    private String coverUrl;

    @Size(max = 10000, message = "商品轮播图内容过长")
    private String galleryUrls;

    private BigDecimal salePrice;

    private BigDecimal marketPrice;

    private BigDecimal costAmount;

    /** 仅用于后台提交商户结算价变更原因，不写入商品表。 */
    private String settlementCostChangeReason;

    private BigDecimal pvValue;

    private BigDecimal bvValue;

    private Integer stock;

    /** 商品级安全库存；单规格直接生效，多规格用于商品汇总预警。 */
    private Integer safetyStock;

    /** 每位会员累计限购数量，0 表示不限购。 */
    private Integer purchaseLimit;

    /** 普通商城和复购商城使用独立商品池；同一商品可同时进入两个商品池。 */
    private Integer normalSaleEnabled;

    private Integer repurchaseSaleEnabled;

    @PositiveOrZero(message = "复购价不能小于0")
    private BigDecimal repurchasePrice;

    @PositiveOrZero(message = "复购PV不能小于0")
    private BigDecimal repurchasePv;

    @PositiveOrZero(message = "复购限购数量不能小于0")
    private Integer repurchasePurchaseLimit;

    /** 预留的报单区渠道，客户制度确认前不开放前台下单。 */
    private Integer enrollmentSaleEnabled;

    /** INHERIT兼容历史、NONE不参与、STANDARD标准奖金、CUSTOM客户定制。 */
    private String teamBonusMode;

    private Integer salesCount;

    private Integer sort;

    private Integer status;

    /** 商户商品审核状态：DRAFT/PENDING/APPROVED/REJECTED；平台自营为空。 */
    private String merchantReviewStatus;

    private Integer merchantReviewVersion;

    private String merchantReviewRemark;

    private LocalDateTime merchantReviewSubmittedAt;

    private LocalDateTime merchantReviewedAt;

    private Long merchantReviewerId;

    private String merchantReviewerName;

    @Size(max = 30000, message = "商品详情不能超过30000个字")
    private String detail;

    @Size(max = 30000, message = "商品详情图片内容过长")
    private String detailImages;

    @Size(max = 512, message = "发货地址不能超过512个字")
    private String deliveryAddress;

    @NotBlank(message = "请选择发货省份")
    @Size(max = 64, message = "发货省份不能超过64个字")
    private String deliveryProvince;

    @NotBlank(message = "请选择发货城市")
    @Size(max = 64, message = "发货城市不能超过64个字")
    private String deliveryCity;

    @NotBlank(message = "请选择发货区/县")
    @Size(max = 64, message = "发货区/县不能超过64个字")
    private String deliveryDistrict;

    /** 商品实际使用的商城发货地址。 */
    private Long shippingAddressId;

    /** 售后审核通过后使用的商城退货地址。 */
    private Long returnAddressId;

    private Integer freightType;

    private BigDecimal freightAmount;

    private BigDecimal freeShippingAmount;

    @Size(max = 128, message = "运费模板名称不能超过128个字")
    private String freightTemplateName;

    private Long freightTemplateId;

    @Size(max = 128, message = "发货时效说明不能超过128个字")
    private String deliveryTime;

    @Size(max = 1000, message = "售后说明不能超过1000个字")
    private String afterSalePolicy;

    @Size(max = 10000, message = "服务保障内容过长")
    private String serviceTags;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
