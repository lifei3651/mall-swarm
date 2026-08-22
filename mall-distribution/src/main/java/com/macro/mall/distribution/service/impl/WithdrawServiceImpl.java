package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsWithdrawRecordDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
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
import com.macro.mall.distribution.vo.WithdrawRecordVO;
import com.macro.mall.distribution.vo.WithdrawStatsVO;
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
        DmsAgent agent = agentDao.selectById(applyDTO.getAgentId());
        if (agent == null) {
            Asserts.fail("代理不存在");
        }

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
        record.setStatus(WithdrawStatusEnum.PENDING_AUDIT.getValue());

        // 支付、转账、提现共用 CASH_BONUS 余额，申请时立即扣减并写入资产流水。
        AssetChangeDTO withdraw = new AssetChangeDTO();
        withdraw.setAgentId(applyDTO.getAgentId());
        withdraw.setAmount(withdrawAmount);
        withdraw.setBizType("WITHDRAW_APPLY");
        withdraw.setBizId(record.getWithdrawNo());
        withdraw.setRemark("申请提现：" + record.getWithdrawNo());
        memberAssetService.withdraw(withdraw);

        withdrawDao.insert(record);

        log.info("申请提现成功: agentId={}, amount={}, withdrawNo={}",
                applyDTO.getAgentId(), withdrawAmount, record.getWithdrawNo());

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
        record.setAuditRemark(auditDTO.getAuditRemark());

        // 如果审核拒绝，原路退还到同一个余额钱包。
        if (WithdrawStatusEnum.AUDIT_REJECTED.getValue().equals(auditDTO.getStatus())) {
            AssetChangeDTO refund = new AssetChangeDTO();
            refund.setAgentId(record.getAgentId());
            refund.setAmount(record.getWithdrawAmount());
            refund.setBizType("WITHDRAW_REJECT_REFUND");
            refund.setBizId(String.valueOf(record.getId()));
            refund.setRemark("提现审核拒绝退回：" + record.getWithdrawNo());
            memberAssetService.issue(refund);
            log.info("审核拒绝，退还余额: agentId={}, amount={}", record.getAgentId(), record.getWithdrawAmount());
        }

        withdrawDao.update(record);

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
    public boolean confirmPay(Long id, String payNo) {
        DmsWithdrawRecord record = withdrawDao.selectByIdForUpdate(id);
        if (record == null) {
            Asserts.fail("提现记录不存在");
        }
        if (!WithdrawStatusEnum.AUDIT_PASSED.getValue().equals(record.getStatus())) {
            Asserts.fail("提现状态不正确，无法确认打款");
        }

        // 更新打款信息
        record.setStatus(WithdrawStatusEnum.PAY_SUCCESS.getValue());
        record.setPayTime(LocalDateTime.now());
        record.setPayNo(payNo);

        accountService.addWithdrawnAmount(record.getAgentId(), record.getWithdrawAmount());
        withdrawDao.update(record);

        operationLogService.log("WITHDRAW", "PAY_CONFIRMED", "WITHDRAW_RECORD", String.valueOf(record.getId()),
                "status=" + WithdrawStatusEnum.AUDIT_PASSED.getValue(),
                "status=" + WithdrawStatusEnum.PAY_SUCCESS.getValue() + ";payNo=" + payNo,
                "财务确认提现打款");

        log.info("确认打款成功: id={}, withdrawNo={}, agentId={}, amount={}, payNo={}",
                id, record.getWithdrawNo(), record.getAgentId(), record.getWithdrawAmount(), payNo);
        return true;
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
