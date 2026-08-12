package com.macro.mall.distribution.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 佣金查询DTO
 */
@Data
public class CommissionQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 代理ID */
    private Long agentId;

    /** 登录账号或手机号；后台统一查询入口。 */
    private String memberKey;

    /** 订单编号 */
    private String orderNo;

    /** 佣金状态：0-待结算 1-已结算 2-已取消 3-已退款 */
    private Integer status;

    /** 与下单人的关系深度，不限层 */
    private Integer commissionLevel;

    /** DIRECT_REWARD-直推奖，DIRECTOR_SHARE-董事团队分红（同等级仅最近一人） */
    private String bonusType;

    /** 开始时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 页码 */
    private Integer pageNum;

    /** 每页数量 */
    private Integer pageSize;
}
