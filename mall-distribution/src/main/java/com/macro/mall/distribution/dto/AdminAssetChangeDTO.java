package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 后台人工调账命令；敏感的二次认证密码只参与本次请求，不写入流水或日志。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminAssetChangeDTO extends AssetChangeDTO {

    @ToString.Exclude
    private String adminPassword;
}
