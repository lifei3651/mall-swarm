package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class BonusSimulationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BigDecimal orderAmount;

    private BigDecimal totalBonus;

    private List<BonusReceiverVO> receivers;

    @Data
    public static class BonusReceiverVO implements Serializable {
        private Long agentId;
        private Long userId;
        private String memberAccount;
        private String agentName;
        private Integer relationLevel;
        private String bonusType;
        private String bonusName;
        private BigDecimal rate;
        private BigDecimal bonusAmount;
    }
}
