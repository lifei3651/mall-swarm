package com.macro.mall.distribution.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 导入详情实体类
 * 对应数据库表：dms_import_detail
 */
@Data
@Schema(description = "导入详情信息")
public class DmsImportDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 详情ID */
    @Schema(description = "id")
    private Long id;

    /** 批次ID */
    @Schema(description = "batchId")
    private Long batchId;

    /** 批次编号 */
    @Schema(description = "batchNo")
    private String batchNo;

    /** 行号 */
    @Schema(description = "rowNum")
    private Integer rowNum;

    /** 原始数据JSON */
    @Schema(description = "rawData")
    private String rawData;

    /**
     * 状态
     * 0-待处理 1-成功 2-失败
     */
    @Schema(description = "status")
    private Integer status;

    /** 错误信息 */
    @Schema(description = "errorMsg")
    private String errorMsg;

    /** 生成的目标ID（代理ID/订单ID等） */
    @Schema(description = "targetId")
    private Long targetId;

    /** 创建时间 */
    @Schema(description = "createTime")
    private LocalDateTime createTime;
}
