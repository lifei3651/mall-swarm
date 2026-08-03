package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.CommissionQueryDTO;
import com.macro.mall.distribution.vo.CommissionRecordVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 佣金服务接口
 */
public interface CommissionService {

    /**
     * 计算并记录订单佣金（订单完成后调用）
     * @param orderId 订单ID
     * @param orderNo 订单编号
     * @param orderAmount 订单金额
     * @param orderUserId 下单用户ID
     * @param orderUserName 下单用户名称
     */
    void calculateAndRecordCommission(Long orderId, String orderNo, BigDecimal orderAmount,
                                       Long orderUserId, String orderUserName);

    void calculateAndRecordCommission(Long tenantId, Long orderId, String orderNo, BigDecimal orderAmount,
                                      Long orderUserId, String orderUserName);

    /**
     * 结算佣金
     * @param recordId 佣金记录ID
     * @return 是否成功
     */
    boolean settleCommission(Long recordId);

    /**
     * 批量结算佣金
     * @param recordIds 佣金记录ID列表
     * @return 结算数量
     */
    int settleCommissionBatch(List<Long> recordIds);

    /**
     * 取消佣金
     * @param recordId 佣金记录ID
     * @param cancelReason 取消原因
     * @return 是否成功
     */
    boolean cancelCommission(Long recordId, String cancelReason);

    /**
     * 查询代理的佣金记录
     * @param queryDTO 查询条件
     * @return 佣金记录列表
     */
    List<CommissionRecordVO> getCommissionRecords(CommissionQueryDTO queryDTO);

    /**
     * 查询代理的待结算佣金总额
     * @param agentId 代理ID
     * @return 待结算佣金总额
     */
    BigDecimal getUnsettledAmount(Long agentId);

    /**
     * 查询代理的已结算佣金总额
     * @param agentId 代理ID
     * @return 已结算佣金总额
     */
    BigDecimal getSettledAmount(Long agentId);

    /**
     * 结算代理及其所有下级的待结算佣金（切线时调用）
     * @param agentId 代理ID
     * @return 结算数量
     */
    int settleAgentAndDescendantCommissions(Long agentId);
}
