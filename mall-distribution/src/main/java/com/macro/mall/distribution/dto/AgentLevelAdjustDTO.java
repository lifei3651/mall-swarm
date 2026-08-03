package com.macro.mall.distribution.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class AgentLevelAdjustDTO implements Serializable {
    private Integer level;
    private String reason;
}
