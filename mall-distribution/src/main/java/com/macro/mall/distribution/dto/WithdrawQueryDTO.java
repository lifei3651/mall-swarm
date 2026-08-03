package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 提现查询条件
 */
@Data
public class WithdrawQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long agentId;

    private String memberKey;

    private Integer status;

    private LocalDate startDate;

    private LocalDate endDate;
}
