package com.macro.mall.distribution.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 导入结果VO
 */
@Data
public class ImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 批次编号 */
    private String batchNo;

    /** 批次名称 */
    private String batchName;

    /** 导入类型 */
    private Integer importType;

    /** 导入类型名称 */
    private String importTypeName;

    /** 总记录数 */
    private Integer totalCount;

    /** 成功数 */
    private Integer successCount;

    /** 失败数 */
    private Integer failCount;

    /** 已处理记录数 */
    private Integer processedCount;

    /** 当前进度（0-100） */
    private Integer progressPercent;

    /** 状态 */
    private Integer status;

    /** 状态名称 */
    private String statusName;

    /** 错误信息列表 */
    private List<String> errorMessages;

    /** 错误文件URL */
    private String errorFileUrl;

    /** 操作人 */
    private String operatorName;

    /** 创建时间 */
    private LocalDateTime createTime;
}
