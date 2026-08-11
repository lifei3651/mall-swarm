package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
public class DmsErpIntegration {
    private Long id;
    private Long tenantId;
    @Size(max = 64, message = "ERP服务商编码不能超过64个字符")
    private String providerCode;
    @Size(max = 128, message = "ERP集成名称不能超过128个字")
    private String integrationName;
    private Integer enabled;
    @Size(max = 32, message = "ERP环境名称不能超过32个字符")
    private String environment;
    @Size(max = 2048, message = "ERP接口地址不能超过2048个字符")
    private String endpoint;
    @Size(max = 256, message = "ERP应用标识不能超过256个字符")
    private String appKey;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @Size(max = 2048, message = "ERP应用密钥内容过长")
    private String appSecret;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @Size(max = 512, message = "ERP回调令牌内容过长")
    private String callbackToken;
    @Size(max = 500, message = "ERP备注不能超过500个字")
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
