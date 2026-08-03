package com.macro.mall.distribution.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 导入批次实体类
 * 对应数据库表：dms_import_batch
 */
@Data
@Schema(description = "导入批次信息")
public class DmsImportBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 批次ID */
    @Schema(description = "id")
    private Long id;

    /** 批次编号 */
    @Schema(description = "batchNo")
    private String batchNo;

    /** 批次名称 */
    @Schema(description = "batchName")
    private String batchName;

    /**
     * 导入类型
     * 1-代理导入 2-订单导入 3-关系导入
     */
    @Schema(description = "importType")
    private Integer importType;

    /** 导入文件名 */
    @Schema(description = "fileName")
    private String fileName;

    /** 文件存储路径 */
    @Schema(description = "fileUrl")
    private String fileUrl;

    /** 总记录数 */
    @Schema(description = "totalCount")
    private Integer totalCount;

    /** 成功数 */
    @Schema(description = "successCount")
    private Integer successCount;

    /** 失败数 */
    @Schema(description = "failCount")
    private Integer failCount;

    /**
     * 状态
     * 0-待处理 1-处理中 2-处理完成 3-处理失败
     */
    @Schema(description = "status")
    private Integer status;

    /** 错误文件路径 */
    @Schema(description = "errorFileUrl")
    private String errorFileUrl;

    /** 操作人ID */
    @Schema(description = "operatorId")
    private Long operatorId;

    /** 操作人名称 */
    @Schema(description = "operatorName")
    private String operatorName;

    /** 备注 */
    @Schema(description = "remark")
    private String remark;

    /** 创建时间 */
    @Schema(description = "createTime")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Schema(description = "updateTime")
    private LocalDateTime updateTime;
}
