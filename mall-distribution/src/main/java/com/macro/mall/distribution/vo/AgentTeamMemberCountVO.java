package com.macro.mall.distribution.vo;

import lombok.Data;

/**
 * 代理团队人数批量统计结果。
 */
@Data
public class AgentTeamMemberCountVO {

    private Long agentId;

    private Integer teamMemberCount;
}
