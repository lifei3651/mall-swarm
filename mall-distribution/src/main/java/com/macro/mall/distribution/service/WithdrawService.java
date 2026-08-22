package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.WithdrawApplyDTO;
import com.macro.mall.distribution.dto.WithdrawAuditDTO;
import com.macro.mall.distribution.dto.WithdrawQueryDTO;
import com.macro.mall.distribution.vo.WithdrawRecordVO;
import com.macro.mall.distribution.vo.WithdrawStatsVO;

import java.util.List;

/**
 * 提现服务接口
 */
public interface WithdrawService {

    /**
     * 申请提现
     * @param applyDTO 提现申请信息
     * @return 提现记录
     */
    WithdrawRecordVO applyWithdraw(WithdrawApplyDTO applyDTO);

    /** 在消费短信验证码前预检，并由正式申请事务再次权威校验。 */
    void validateWithdrawalLimits(Long agentId, java.math.BigDecimal amount);

    /**
     * 审核提现
     * @param auditDTO 审核信息
     * @return 是否成功
     */
    boolean auditWithdraw(WithdrawAuditDTO auditDTO);

    /**
     * 确认打款
     * @param id 提现记录ID
     * @param payNo 打款流水号
     * @return 是否成功
     */
    boolean confirmPay(Long id, String payNo);

    /**
     * 查询提现记录
     * @param id 记录ID
     * @return 提现记录
     */
    WithdrawRecordVO getWithdrawById(Long id);

    /**
     * 查询代理的提现记录
     * @param agentId 代理ID
     * @return 提现记录列表
     */
    List<WithdrawRecordVO> getWithdrawsByAgentId(Long agentId);

    /**
     * 查询待审核的提现记录
     * @return 提现记录列表
     */
    List<WithdrawRecordVO> getPendingAuditWithdraws();

    /**
     * 查询所有提现记录
     * @return 提现记录列表
     */
    List<WithdrawRecordVO> getAllWithdraws();

    /**
     * 按条件查询提现记录
     * @param queryDTO 查询条件
     * @return 提现记录列表
     */
    List<WithdrawRecordVO> searchWithdraws(WithdrawQueryDTO queryDTO);

    /**
     * 查询提现统计
     * @param queryDTO 查询条件
     * @return 提现统计
     */
    WithdrawStatsVO getWithdrawStats(WithdrawQueryDTO queryDTO);
}
