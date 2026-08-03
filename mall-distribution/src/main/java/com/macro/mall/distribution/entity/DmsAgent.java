package com.macro.mall.distribution.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 代理实体类
 * 对应数据库表：dms_agent
 */
@Data
@Schema(description = "代理信息")
public class DmsAgent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "代理ID")
    private Long id;

    @Schema(description = "关联用户ID（ums_user表）")
    private Long userId;

    @Schema(description = "代理编号（唯一标识）")
    private String agentCode;

    @Schema(description = "代理名称")
    private String agentName;

    @Schema(description = "新零售等级：1-会员 2-VIP会员 3-店铺 4-代理 5-一星董事 6-二星董事 7-三星董事 8-合伙人")
    private Integer agentLevel;

    @Schema(description = "直属上级代理ID")
    private Long parentId;

    @Schema(description = "所有上级ID路径，如：1,5,12")
    private String ancestorIds;

    @Schema(description = "层级深度（1表示顶级代理）")
    private Integer levelDepth;

    @Schema(description = "邀请码（用于扫码绑定）")
    private String inviteCode;

    @Schema(description = "推广二维码URL")
    private String qrCodeUrl;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "身份证号")
    private String idCard;

    @Schema(description = "开户行")
    private String bankName;

    @Schema(description = "银行账号")
    private String bankAccount;

    @Schema(description = "状态：0-禁用 1-正常 2-冻结")
    private Integer status;

    @Schema(description = "来源：1-自主注册 2-扫码邀请 3-后台添加 4-批量导入")
    private Integer sourceType;

    @Schema(description = "导入批次ID（批量导入时记录）")
    private String importBatchId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
