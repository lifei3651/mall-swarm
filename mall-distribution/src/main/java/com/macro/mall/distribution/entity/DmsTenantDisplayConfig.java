package com.macro.mall.distribution.entity;

import lombok.Data;

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
    private String layoutTemplate;

    /** 首页是否展示商品分类模块。 */
    private Integer showHomeCategories;

    /** 手机端底部导航是否展示“分类”入口。 */
    private Integer showBottomCategoryNav;

    private String extraConfigJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
