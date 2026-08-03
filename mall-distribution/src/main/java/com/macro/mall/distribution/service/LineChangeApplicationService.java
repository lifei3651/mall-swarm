package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.dto.LineChangeAuditDTO;
import com.macro.mall.distribution.entity.DmsLineChangeApplication;
import java.util.List;

public interface LineChangeApplicationService {
    DmsLineChangeApplication submit(AgentSwitchLineDTO dto);
    DmsLineChangeApplication audit(Long id, LineChangeAuditDTO dto);
    /**
     * 查询全局移线记录。调用方只要通过移线管理权限校验，即可查看所有管理员产生的记录，
     * 不按申请人或处理人过滤。
     */
    List<DmsLineChangeApplication> list(Integer status);
    boolean executeApproved(Long id);
    int executeDue();
}
