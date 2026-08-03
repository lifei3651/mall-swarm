package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.dto.LineChangeAuditDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.LineChangeApplicationService;
import com.macro.mall.distribution.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LineChangeApplicationServiceImpl implements LineChangeApplicationService {
    private final DmsLineChangeApplicationDao applicationDao;
    private final DmsAgentDao agentDao;
    private final DmsAgentRelationDao relationDao;
    private final AgentService agentService;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsLineChangeApplication submit(AgentSwitchLineDTO dto) {
        if (dto == null || dto.getAgentId() == null || dto.getNewParentAgentId() == null) Asserts.fail("移线会员和新直属上级不能为空");
        if (dto.getAgentId().equals(dto.getNewParentAgentId())) Asserts.fail("不能移动到自己名下");
        if (dto.getReason() == null || dto.getReason().isBlank()) Asserts.fail("移线原因不能为空");
        DmsAgent agent = agentDao.selectById(dto.getAgentId());
        if (agent == null) Asserts.fail("移线会员不存在");
        if (agentDao.selectById(dto.getNewParentAgentId()) == null) Asserts.fail("新直属上级会员不存在");
        if (applicationDao.selectPendingByAgentId(dto.getAgentId()) != null) {
            Asserts.fail("该会员有待移线处理申请，暂不可再进行移线操作");
        }
        DmsAdminUser admin = requireAdmin();
        DmsLineChangeApplication application = new DmsLineChangeApplication();
        application.setApplyNo("LINE" + IdUtil.getSnowflakeNextIdStr());
        application.setAgentId(agent.getId()); application.setOldParentAgentId(agent.getParentId());
        application.setNewParentAgentId(dto.getNewParentAgentId()); application.setReason(dto.getReason());
        application.setStatus(0); application.setApplicantId(admin.getId()); application.setApplicantName(admin.getUsername());
        // 后台移线使用单一专用权限，提交后在同一事务内立即生效，不再等待第二人审批。
        application.setEffectiveTime(LocalDateTime.now());
        application.setBeforeSnapshot(snapshot(agent.getId()));
        applicationDao.insert(application);

        if (!agentService.switchLine(dto)) Asserts.fail("移线执行失败");
        String after = snapshot(application.getAgentId());
        LocalDateTime executeTime = LocalDateTime.now();
        if (applicationDao.markDirectExecuted(application.getId(), admin.getId(), admin.getUsername(),
                "拥有移线管理权限，提交后直接生效", after, executeTime) != 1) {
            Asserts.fail("移线记录状态已变化，请刷新后重试");
        }
        operationLogService.log("AGENT", "LINE_CHANGE_EXECUTE", "LINE_CHANGE", String.valueOf(application.getId()),
                application.getBeforeSnapshot(), after, "后台管理员直接移线：" + dto.getReason());
        return applicationDao.selectById(application.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsLineChangeApplication audit(Long id, LineChangeAuditDTO dto) {
        if (dto == null || dto.getStatus() == null || (dto.getStatus() != 1 && dto.getStatus() != 2)) Asserts.fail("审批结果无效");
        if (dto.getRemark() == null || dto.getRemark().isBlank()) Asserts.fail("审批意见不能为空");
        DmsLineChangeApplication application = applicationDao.selectById(id);
        if (application == null || !Integer.valueOf(0).equals(application.getStatus())) Asserts.fail("申请不存在或已处理");
        DmsAdminUser auditor = requireAdmin();
        // 仅用于兼容升级前遗留的待审批记录；具备移线管理权限的操作人可以直接处理。
        if (applicationDao.audit(id, dto.getStatus(), auditor.getId(), auditor.getUsername(), dto.getRemark(), LocalDateTime.now()) != 1) {
            Asserts.fail("审批状态已变化，请刷新后重试");
        }
        operationLogService.log("AGENT", dto.getStatus() == 1 ? "LINE_CHANGE_APPROVE" : "LINE_CHANGE_REJECT",
                "LINE_CHANGE", String.valueOf(id), application.getBeforeSnapshot(), null, dto.getRemark());
        if (dto.getStatus() == 1 && !application.getEffectiveTime().isAfter(LocalDateTime.now())) executeApproved(id);
        return applicationDao.selectById(id);
    }

    @Override
    public List<DmsLineChangeApplication> list(Integer status) {
        // 移线记录属于全局业务审计记录。权限在接口拦截器统一校验，此处不得再按申请人或处理人缩小范围。
        return applicationDao.selectList(status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean executeApproved(Long id) {
        DmsLineChangeApplication application = applicationDao.selectById(id);
        if (application == null || !Integer.valueOf(1).equals(application.getStatus()) || application.getEffectiveTime().isAfter(LocalDateTime.now())) return false;
        AgentSwitchLineDTO command = new AgentSwitchLineDTO();
        command.setAgentId(application.getAgentId()); command.setNewParentAgentId(application.getNewParentAgentId()); command.setReason(application.getReason());
        if (!agentService.switchLine(command)) Asserts.fail("移线执行失败");
        String after = snapshot(application.getAgentId());
        if (applicationDao.markExecuted(id, after, LocalDateTime.now()) != 1) Asserts.fail("移线申请状态已变化");
        operationLogService.log("AGENT", "LINE_CHANGE_EXECUTE", "LINE_CHANGE", String.valueOf(id), application.getBeforeSnapshot(), after, application.getReason());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int executeDue() { int count=0; for (DmsLineChangeApplication item: applicationDao.selectDueApproved(LocalDateTime.now())) if (executeApproved(item.getId())) count++; return count; }

    private String snapshot(Long rootAgentId) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("root", agentDao.selectById(rootAgentId));
        data.put("relations", relationDao.selectAllDescendants(rootAgentId));
        try { return objectMapper.writeValueAsString(data); } catch (JsonProcessingException e) { throw new IllegalStateException("关系快照序列化失败", e); }
    }
    private DmsAdminUser requireAdmin() { DmsAdminUser admin=AdminContext.get(); if(admin==null) Asserts.fail("未获取到后台操作人"); return admin; }
}
