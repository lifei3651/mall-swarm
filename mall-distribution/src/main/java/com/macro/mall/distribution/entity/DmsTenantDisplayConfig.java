package com.macro.mall.distribution.entity;

import lombok.Data;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 前端展示开关配置
 */
@Data
public class DmsTenantDisplayConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    private Integer showPv;

    private Integer showTeamPerformance;

    private Integer showBonusSource;

    private Integer showBonusFlow;

    private Integer showProfit;

    private Integer showRank;

    private Integer showBinaryArea;

    private Integer showRetailModule;

    private Integer showStoreModule;

    private Integer showCompanyShare;

    /**
     * 商城布局模板。该字段通过 extraConfigJson 持久化，避免为界面配置频繁修改表结构。
     */
    @Size(max = 64, message = "布局模板名称不能超过64个字符")
    private String layoutTemplate;

    /** 分类导购子版型：directory-双栏目录、showcase-视觉橱窗、scenario-场景导购。 */
    @Size(max = 32, message = "分类导购版型名称不能超过32个字符")
    private String categoryGuideTemplate;

    /** A 双栏目录导航模块开关。父版型切换时保留原值。 */
    private Integer categoryGuidePrimaryCategoriesEnabled;
    private Integer categoryGuideSubcategoriesEnabled;
    private Integer categoryGuideHotProductsEnabled;

    /** B 视觉品类橱窗模块开关。 */
    private Integer categoryGuideHeroCategoriesEnabled;
    private Integer categoryGuideShelvesEnabled;
    private Integer categoryGuideRecommendedProductsEnabled;

    /** C 需求场景导购模块开关。 */
    private Integer categoryGuideScenariosEnabled;
    private Integer categoryGuideQuickEntriesEnabled;
    private Integer categoryGuidePopularProductsEnabled;

    /**
     * 核心交易与合规能力只能为 1。字段显式暴露给工作台和公开端，服务端保存时拒绝关闭，
     * 不能通过伪造请求把前端的锁定态绕过。
     */
    private Integer productDetailEnabled;
    private Integer cartEnabled;
    private Integer checkoutEnabled;
    private Integer accountSecurityEnabled;
    private Integer legalComplianceEnabled;
    private Integer afterSalesEnabled;
    private Integer customerServiceEnabled;

    /** 首页是否展示商品分类模块。 */
    private Integer showHomeCategories;

    /** 手机端底部导航是否展示“分类”入口。 */
    private Integer showBottomCategoryNav;

    /**
     * 直播广场公开总开关。关闭后首页不返回直播数据，公开列表和详情也不可访问；
     * 直播间配置本身仍保留，方便客户后续重新启用。
     */
    private Integer liveSquareEnabled;

    /**
     * 新品速递公开总开关。关闭后首页和新品列表不公开；商品及其首次上架时间不受影响。
     * 该字段与布局模板一样持久化在 extraConfigJson，避免只为页面开关修改表结构。
     */
    private Integer newArrivalsEnabled;

    /** 自动新品展示周期：0 表示永久，30～365 表示首次上架后的展示天数。 */
    @Min(value = 0, message = "新品展示周期不能小于0天")
    @Max(value = 365, message = "新品展示周期不能超过365天")
    private Integer newArrivalWindowDays;

    @Size(max = 30000, message = "商城页面配置内容过长")
    private String extraConfigJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
