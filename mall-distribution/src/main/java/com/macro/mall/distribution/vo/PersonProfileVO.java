package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsAgentAccount;
import com.macro.mall.distribution.entity.DmsCommissionClawback;
import com.macro.mall.distribution.entity.DmsMemberAssetAccount;
import com.macro.mall.distribution.entity.DmsMemberAssetFlow;
import com.macro.mall.distribution.entity.DmsWithdrawRecord;
import com.macro.mall.distribution.entity.DmsShopMember;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 后台人员全景档案
 */
@Data
public class PersonProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private DmsShopMember member;

    private DmsAgent agent;

    private DmsAgentAccount account;

    private BigDecimal pendingDebtAmount;

    private List<OrderAuditVO> orders;

    private List<CommissionRecordVO> commissions;

    private List<DmsCommissionClawback> clawbacks;

    private List<DmsMemberAssetAccount> assetAccounts;

    private List<DmsMemberAssetFlow> assetFlows;

    private List<DmsWithdrawRecord> withdraws;
}
