package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.dto.ImportAgentDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.enums.AgentSourceTypeEnum;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.vo.AgentInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导入事务辅助类（独立 Bean，避免自调用导致 REQUIRES_NEW 不生效）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportTransactionHelper {

    private final AgentService agentService;

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
}
