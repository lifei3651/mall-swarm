package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsAgentAccount;
import com.macro.mall.distribution.entity.DmsShopAddress;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsMemberAssetAccount;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.entity.DmsMigrationBaseline;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ShopProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private DmsAgent agent;

    private DmsShopMember member;

    private DmsAgentAccount account;

    private Boolean canViewTeamPerformance;

    private PerformanceOverviewVO performance;

    private List<DmsMemberAssetAccount> assetAccounts;

    private List<DmsShopAddress> addresses;

    private List<ShopOrderVO> orders;

    private DmsTenantDisplayConfig displayConfig;

    private DmsMigrationBaseline migrationBaseline;
}
