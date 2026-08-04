package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.AssetTransferDTO;
import com.macro.mall.distribution.entity.DmsMemberAssetAccount;
import com.macro.mall.distribution.entity.DmsMemberAssetFlow;
import com.macro.mall.distribution.vo.BalanceFlowVO;
import com.macro.mall.distribution.vo.BalanceFlowSummaryVO;

import java.util.List;
import java.time.LocalDateTime;

public interface MemberAssetService {
    List<DmsMemberAssetAccount> listAccounts(Long agentId, Long userId);

    List<DmsMemberAssetFlow> listFlows(Long agentId, Long userId);

    List<BalanceFlowVO> searchBalanceFlows(String keyword, String relatedNo, String direction, String sourceType,
                                           LocalDateTime startTime, LocalDateTime endTime);

    BalanceFlowSummaryVO summarizeBalanceFlows(String keyword, String relatedNo, String direction, String sourceType,
                                               LocalDateTime startTime, LocalDateTime endTime);

    DmsMemberAssetFlow issue(AssetChangeDTO dto);

    DmsMemberAssetFlow consume(AssetChangeDTO dto);

    DmsMemberAssetFlow deduct(AssetChangeDTO dto);

    DmsMemberAssetFlow withdraw(AssetChangeDTO dto);

    /** 系统自动入账；requestId必须稳定，重复执行只返回原流水，不重复加钱。 */
    DmsMemberAssetFlow issueSystem(AssetChangeDTO dto);

    /** 系统退款冲回；允许余额变为负数，负数即欠款，后续入账会自动抵扣。 */
    DmsMemberAssetFlow deductSystemAllowNegative(AssetChangeDTO dto);

    boolean transfer(AssetTransferDTO dto);
}
