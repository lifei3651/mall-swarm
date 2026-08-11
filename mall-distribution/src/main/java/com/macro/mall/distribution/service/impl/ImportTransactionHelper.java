package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.dto.ImportAgentDTO;
import com.macro.mall.distribution.dto.ImportOrderDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.enums.AgentSourceTypeEnum;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.CommissionService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.vo.AgentInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 导入事务辅助类（独立 Bean，避免自调用导致 REQUIRES_NEW 不生效）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportTransactionHelper {

    private final AgentService agentService;
    private final DmsAgentDao agentDao;
    private final PerformanceService performanceService;
    private final CommissionService commissionService;

    /**
     * 处理单条代理商导入（独立事务）
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public DmsAgent processAgentImport(ImportAgentDTO agentDTO) {
        AgentRegisterDTO registerDTO = new AgentRegisterDTO();
        registerDTO.setUserId(agentDTO.getUserId());
        registerDTO.setAgentName(agentDTO.getAgentName());
        registerDTO.setRealName(agentDTO.getRealName());
        registerDTO.setPhone(agentDTO.getPhone());
        registerDTO.setIdCard(agentDTO.getIdCard());
        if (agentDTO.getParentAgentCode() != null && !agentDTO.getParentAgentCode().isBlank()) {
            AgentInfoVO parent = agentService.getAgentByAgentCode(agentDTO.getParentAgentCode());
            if (parent == null) {
                throw new IllegalArgumentException("上级代理编号不存在: " + agentDTO.getParentAgentCode());
            }
            registerDTO.setInviteCode(parent.getInviteCode());
        }
        registerDTO.setSourceType(AgentSourceTypeEnum.BATCH_IMPORT.getValue());
        AgentInfoVO created = agentService.register(registerDTO);
        DmsAgent agent = new DmsAgent();
        agent.setId(created.getId());
        agent.setAgentCode(created.getAgentCode());
        agent.setBankName(agentDTO.getBankName());
        agent.setBankAccount(agentDTO.getBankAccount());
        agent.setRemark(agentDTO.getRemark());
        agentService.updateAgent(agent);
        return agent;
    }

    /** 处理单条历史订单导入，确保每一行独立提交并能实时更新批次进度。 */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public Long processOrderImport(ImportOrderDTO orderDTO) {
        DmsAgent ownerAgent = agentDao.selectById(performanceService.resolveAgentId(orderDTO.getOwnerAgentCode()));
        if (ownerAgent == null || ownerAgent.getUserId() == null) {
            throw new IllegalArgumentException("订单归属登录账号不存在: " + orderDTO.getOwnerAgentCode());
        }
        Long orderId = IdUtil.getSnowflakeNextId();
        LocalDateTime orderTime = orderDTO.getOrderTime() != null ? orderDTO.getOrderTime() : LocalDateTime.now();
        performanceService.recordOrderPerformance(orderId, orderDTO.getOrderNo(), orderDTO.getOrderAmount(),
                orderDTO.getQuantity(), ownerAgent.getUserId(), orderTime);
        commissionService.calculateAndRecordCommission(orderId, orderDTO.getOrderNo(), orderDTO.getOrderAmount(),
                ownerAgent.getUserId(), ownerAgent.getAgentName());
        return orderId;
    }
}
