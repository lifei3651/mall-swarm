package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsWithdrawRecordDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.config.WithdrawalLimitProperties;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.WithdrawApplyDTO;
import com.macro.mall.distribution.dto.WithdrawAuditDTO;
import com.macro.mall.distribution.dto.WithdrawQueryDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsWithdrawRecord;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.enums.WithdrawStatusEnum;
import com.macro.mall.distribution.service.AgentAccountService;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.WithdrawService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.MemberMessageService;
import com.macro.mall.distribution.service.MemberMessageEvent;
import com.macro.mall.distribution.service.WithdrawalPayoutService;
import com.macro.mall.distribution.service.WithdrawalRiskPolicyService;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.vo.WithdrawRecordVO;
import com.macro.mall.distribution.vo.WithdrawStatsVO;
import com.macro.mall.distribution.vo.WithdrawalLimitUsageVO;
import com.macro.mall.distribution.util.MemberAccountUtils;
import com.macro.mall.distribution.util.MoneyValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 提现服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawServiceImpl implements WithdrawService {

    private static final BigDecimal MAX_WITHDRAW_AMOUNT = new BigDecimal("99999999.99");

    private final DmsWithdrawRecordDao withdrawDao;
    private final AgentAccountService accountService;
    private final MemberAssetService memberAssetService;
    private final DmsAgentDao agentDao;
    private final DmsShopMemberDao memberDao;
    private final OperationLogService operationLogService;
    private final WithdrawalLimitProperties withdrawalLimits;
    private final MemberMessageService memberMessageService;
    private final WithdrawalPayoutService withdrawalPayoutService;
    private final WithdrawalRiskPolicyService withdrawalRiskPolicyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WithdrawRecordVO applyWithdraw(WithdrawApplyDTO applyDTO) {
        if (applyDTO.getAgentId() == null) {
            Asserts.fail("代理ID不能为空");
        }
        BigDecimal withdrawAmount = MoneyValidationUtils.requirePositiveAmount(
                applyDTO.getWithdrawAmount(), "提现金额", MAX_WITHDRAW_AMOUNT);
        if (applyDTO.getWithdrawType() == null) {
            Asserts.fail("提现方式不能为空");
        }
        if (!List.of(1, 2, 3).contains(applyDTO.getWithdrawType())) {
            Asserts.fail("提现方式不正确");
        }
        DmsAgent agent = agentDao.selectByIdForUpdate(applyDTO.getAgentId());
        if (agent == null) {
            Asserts.fail("代理不存在");
        }
        validateWithdrawalLimitsLocked(agent.getId(), withdrawAmount);
        ReviewDecision review = reviewDecision(agent.getId(), applyDTO, withdrawAmount);

        // 创建提现记录
        DmsWithdrawRecord record = new DmsWithdrawRecord();
        record.setWithdrawNo(generateWithdrawNo());
        record.setAgentId(applyDTO.getAgentId());
        record.setUserId(agent.getUserId());
        record.setWithdrawAmount(withdrawAmount);
        record.setWithdrawType(applyDTO.getWithdrawType());
        record.setBankName(applyDTO.getBankName());
        record.setBankAccount(applyDTO.getBankAccount());
        record.setAccountName(applyDTO.getAccountName());
        record.setStatus(review.manualRequired()
                ? WithdrawStatusEnum.PENDING_AUDIT.getValue()
                : WithdrawStatusEnum.AUDIT_PASSED.getValue());
        record.setAuditTime(review.manualRequired() ? null : LocalDateTime.now());
        record.setAuditRemark(review.manualRequired()
                ? "需人工审核：" + review.reason()
                : "系统风控自动通过：" + review.reason());

        // 支付、转账、提现共用 CASH_BONUS 余额，申请时立即扣减并写入资产流水。
        AssetChangeDTO withdraw = new AssetChangeDTO();
        withdraw.setAgentId(applyDTO.getAgentId());
        withdraw.setAmount(withdrawAmount);
        withdraw.setBizType("WITHDRAW_APPLY");
        withdraw.setBizId(record.getWithdrawNo());
        withdraw.setRequestId("WITHDRAW_APPLY-" + record.getWithdrawNo());
        withdraw.setRemark("申请提现：" + record.getWithdrawNo());
        memberAssetService.withdraw(withdraw);

        withdrawDao.insert(record);
        publishWithdrawal(record, "WITHDRAW_SUBMITTED");
        if (!review.manualRequired()) publishWithdrawal(record, "WITHDRAW_AUDITED");

        log.info("申请提现成功: agentId={}, amount={}, withdrawNo={}, reviewMode={}",
                applyDTO.getAgentId(), withdrawAmount, record.getWithdrawNo(),
                review.manualRequired() ? "MANUAL" : "AUTO");

        return convertToVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean auditWithdraw(WithdrawAuditDTO auditDTO) {
        DmsWithdrawRecord record = withdrawDao.selectByIdForUpdate(auditDTO.getId());
        if (record == null) {
            Asserts.fail("提现记录不存在");
        }
        if (!WithdrawStatusEnum.PENDING_AUDIT.getValue().equals(record.getStatus())) {
            Asserts.fail("提现状态不正确，无法审核");
        }
        if (!WithdrawStatusEnum.AUDIT_PASSED.getValue().equals(auditDTO.getStatus())
                && !WithdrawStatusEnum.AUDIT_REJECTED.getValue().equals(auditDTO.getStatus())) {
            Asserts.fail("审核状态只能为审核通过或审核拒绝");
        }

        // 更新审核信息
        record.setStatus(auditDTO.getStatus());
        record.setAuditUserId(auditDTO.getAuditUserId());
        record.setAuditTime(LocalDateTime.now());
        record.setAuditRemark(mergeAuditRemark(record.getAuditRemark(), auditDTO.getAuditRemark()));

        // 如果审核拒绝，原路退还到同一个余额钱包。
        if (WithdrawStatusEnum.AUDIT_REJECTED.getValue().equals(auditDTO.getStatus())) {
            AssetChangeDTO refund = new AssetChangeDTO();
            refund.setAgentId(record.getAgentId());
            refund.setAmount(record.getWithdrawAmount());
            refund.setBizType("WITHDRAW_REJECT_REFUND");
            refund.setBizId(String.valueOf(record.getId()));
            refund.setRequestId("WITHDRAW_REJECT_REFUND-" + record.getId());
            refund.setRemark("提现审核拒绝退回：" + record.getWithdrawNo());
            memberAssetService.issue(refund);
            log.info("审核拒绝，退还余额: agentId={}, amount={}", record.getAgentId(), record.getWithdrawAmount());
        }

        withdrawDao.update(record);
        publishWithdrawal(record, "WITHDRAW_AUDITED");

        operationLogService.log("WITHDRAW", "AUDIT", "WITHDRAW_RECORD", String.valueOf(record.getId()),
                "status=" + WithdrawStatusEnum.PENDING_AUDIT.getValue(),
                "status=" + record.getStatus() + ";amount=" + record.getWithdrawAmount(),
                "提现审核：" + (record.getAuditRemark() == null ? "未填写备注" : record.getAuditRemark()));

        log.info("提现审核完成: id={}, withdrawNo={}, agentId={}, amount={}, status={}, auditUserId={}",
                record.getId(), record.getWithdrawNo(), record.getAgentId(), record.getWithdrawAmount(),
                record.getStatus(), record.getAuditUserId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void validateWithdrawalLimits(Long agentId, BigDecimal amount) {
        if (agentId == null) Asserts.fail("代理ID不能为空");
        BigDecimal requested = MoneyValidationUtils.requirePositiveAmount(amount, "提现金额", MAX_WITHDRAW_AMOUNT);
        if (agentDao.selectByIdForUpdate(agentId) == null) Asserts.fail("代理不存在");
        validateWithdrawalLimitsLocked(agentId, requested);
    }

    private void validateWithdrawalLimitsLocked(Long agentId, BigDecimal amount) {
        LocalDate today = LocalDate.now();
        WithdrawalLimitUsageVO usage = withdrawDao.selectLimitUsage(
                agentId, today.atStartOfDay(), today.withDayOfMonth(1).atStartOfDay());
        long dailyCount = usage == null || usage.getDailyCount() == null ? 0L : usage.getDailyCount();
        long monthlyCount = usage == null || usage.getMonthlyCount() == null ? 0L : usage.getMonthlyCount();
        if (dailyCount >= withdrawalLimits.getDailyMaxCount()) {
            Asserts.fail("今日提现次数已达上限（" + withdrawalLimits.getDailyMaxCount() + "次）");
        }
        if (monthlyCount >= withdrawalLimits.getMonthlyMaxCount()) {
            Asserts.fail("本月提现次数已达上限（" + withdrawalLimits.getMonthlyMaxCount() + "次）");
        }
    }

    private ReviewDecision reviewDecision(Long agentId, WithdrawApplyDTO applyDTO, BigDecimal amount) {
        List<String> reasons = new ArrayList<>();
        Integer withdrawType = applyDTO.getWithdrawType();
        BigDecimal manualReviewThreshold = withdrawalRiskPolicyService.manualReviewThreshold();
        if (!withdrawalPayoutService.isReady(withdrawType)) {
            reasons.add("官方渠道尚未完成客户签约与安全配置");
        }
        if (manualReviewThreshold.signum() <= 0) {
            reasons.add("自动审核已关闭");
        } else if (amount.compareTo(manualReviewThreshold) > 0) {
            reasons.add("单笔金额超过"
                    + manualReviewThreshold.stripTrailingZeros().toPlainString() + "元");
        }
        DmsWithdrawRecord previous = withdrawDao.selectLatestSuccessfulByAgentAndType(agentId, withdrawType);
        if (previous == null) {
            reasons.add("该渠道首次提现");
        } else if (Integer.valueOf(3).equals(withdrawType)
                && !sameText(previous.getBankAccount(), applyDTO.getBankAccount())) {
            reasons.add("支付宝收款账号与最近一次成功提现不一致");
        }
        if (!reasons.isEmpty()) return new ReviewDecision(true, String.join("；", reasons));
        return new ReviewDecision(false, "单笔金额未超过"
                + manualReviewThreshold.stripTrailingZeros().toPlainString()
                + "元且收款身份已核验");
    }

    private boolean sameText(String left, String right) {
        return left != null && right != null && left.trim().equals(right.trim());
    }

    private String mergeAuditRemark(String riskRemark, String operatorRemark) {
        String operator = operatorRemark == null ? "" : operatorRemark.trim();
        if (operator.isEmpty()) return riskRemark;
        if (riskRemark == null || riskRemark.isBlank()) return limitRemark(operator);
        return limitRemark(riskRemark + "；人工审核：" + operator);
    }

    private String limitRemark(String value) {
        return value == null || value.length() <= 256 ? value : value.substring(0, 256);
    }

    private record ReviewDecision(boolean manualRequired, String reason) {
    }

    @Override
    public boolean confirmPay(Long id, String payNo) {
        Asserts.fail("人工填写流水号确认打款的入口已停用，请使用微信或支付宝官方渠道打款并核对结果");
        return false;
    }

    private void publishWithdrawal(DmsWithdrawRecord record, String eventType) {
        if (record == null || record.getId() == null) return;
        memberMessageService.publish(new MemberMessageEvent(TenantContext.getTenantId(), record.getUserId(),
                eventType + ":" + record.getId() + ("WITHDRAW_AUDITED".equals(eventType) ? ":" + record.getStatus() : ""),
                eventType, "WALLET_FUNDS", "WITHDRAWAL", record.getId(), null, LocalDateTime.now()));
    }

    @Override
    public WithdrawRecordVO getWithdrawById(Long id) {
        DmsWithdrawRecord record = withdrawDao.selectById(id);
        return record != null ? convertToVO(record, true) : null;
    }

    @Override
    public List<WithdrawRecordVO> getWithdrawsByAgentId(Long agentId) {
        List<DmsWithdrawRecord> records = withdrawDao.selectByAgentId(agentId);
        return convertToVOList(records);
    }

    @Override
    public List<WithdrawRecordVO> getPendingAuditWithdraws() {
        List<DmsWithdrawRecord> records = withdrawDao.selectByStatus(WithdrawStatusEnum.PENDING_AUDIT.getValue());
        return convertToVOList(records);
    }

    @Override
    public List<WithdrawRecordVO> getAllWithdraws() {
        List<DmsWithdrawRecord> records = withdrawDao.selectAll();
        return convertToVOList(records);
    }

    @Override
    public List<WithdrawRecordVO> searchWithdraws(WithdrawQueryDTO queryDTO) {
        List<DmsWithdrawRecord> records = searchRecords(queryDTO);
        return convertToVOList(records);
    }

    @Override
    public WithdrawStatsVO getWithdrawStats(WithdrawQueryDTO queryDTO) {
        List<DmsWithdrawRecord> records = searchRecords(queryDTO);
        WithdrawStatsVO stats = new WithdrawStatsVO();
        stats.setTotalCount(records.size());

        for (DmsWithdrawRecord record : records) {
            BigDecimal amount = record.getWithdrawAmount() != null ? record.getWithdrawAmount() : BigDecimal.ZERO;
            stats.setTotalAmount(stats.getTotalAmount().add(amount));
            if (WithdrawStatusEnum.PENDING_AUDIT.getValue().equals(record.getStatus())) {
                stats.setPendingAmount(stats.getPendingAmount().add(amount));
                stats.setPendingCount(stats.getPendingCount() + 1);
            } else if (WithdrawStatusEnum.PAY_SUCCESS.getValue().equals(record.getStatus())) {
                stats.setSuccessAmount(stats.getSuccessAmount().add(amount));
            } else if (WithdrawStatusEnum.AUDIT_REJECTED.getValue().equals(record.getStatus())) {
                stats.setRejectedAmount(stats.getRejectedAmount().add(amount));
            }
        }

        return stats;
    }

    private List<DmsWithdrawRecord> searchRecords(WithdrawQueryDTO queryDTO) {
        WithdrawQueryDTO query = queryDTO != null ? queryDTO : new WithdrawQueryDTO();
        LocalDateTime startTime = toStartTime(query.getStartDate());
        LocalDateTime endTime = toEndTime(query.getEndDate());
        return withdrawDao.search(query.getAgentId(), query.getStatus(), startTime, endTime);
    }

    private LocalDateTime toStartTime(LocalDate date) {
        return date != null ? date.atStartOfDay() : null;
    }

    private LocalDateTime toEndTime(LocalDate date) {
        return date != null ? date.atTime(LocalTime.MAX) : null;
    }

    /**
     * 生成提现单号
     */
    private String generateWithdrawNo() {
        return "WD" + IdUtil.getSnowflakeNextIdStr();
    }

    /**
     * 转换为VO
     */
    private WithdrawRecordVO convertToVO(DmsWithdrawRecord record) {
        return convertToVO(record, false);
    }

    private WithdrawRecordVO convertToVO(DmsWithdrawRecord record, boolean includeSensitiveDetails) {
        WithdrawRecordVO vo = new WithdrawRecordVO();
        BeanUtils.copyProperties(record, vo);

        DmsAgent agent = agentDao.selectById(record.getAgentId());
        DmsShopMember member = memberDao.selectByUserId(record.getUserId());
        if (agent != null) vo.setAgentName(agent.getAgentName());
        if (member != null) {
            vo.setMemberAccount(MemberAccountUtils.display(member));
            vo.setMemberPhone(member.getPhone());
        }

        if (!includeSensitiveDetails) {
            vo.setMemberAccount(MemberAccountUtils.maskAccount(vo.getMemberAccount()));
            vo.setMemberPhone(MemberAccountUtils.maskPhone(vo.getMemberPhone()));
            vo.setBankAccount(MemberAccountUtils.maskBankAccount(vo.getBankAccount()));
            vo.setAccountName(MemberAccountUtils.maskPersonName(vo.getAccountName()));
        }

        // 设置提现方式名称
        vo.setWithdrawTypeName(getWithdrawTypeName(record.getWithdrawType()));

        // 设置状态名称
        WithdrawStatusEnum statusEnum = WithdrawStatusEnum.getByValue(record.getStatus());
        vo.setStatusName(statusEnum != null ? statusEnum.getName() : "未知");

        return vo;
    }

    /**
     * 转换为VO列表
     */
    private List<WithdrawRecordVO> convertToVOList(List<DmsWithdrawRecord> records) {
        List<WithdrawRecordVO> voList = new ArrayList<>();
        for (DmsWithdrawRecord record : records) {
            voList.add(convertToVO(record));
        }
        return voList;
    }

    /**
     * 获取提现方式名称
     */
    private String getWithdrawTypeName(Integer type) {
        if (type == null) {
            return "未知";
        }
        switch (type) {
            case 1:
                return "银行卡";
            case 2:
                return "微信";
            case 3:
                return "支付宝";
            default:
                return "未知";
        }
    }
}
