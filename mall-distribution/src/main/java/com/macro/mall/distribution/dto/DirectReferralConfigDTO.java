package com.macro.mall.distribution.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 后台直接推荐佣金配置；不包含邀请模式或推广资格的修改字段。 */
@Data
public class DirectReferralConfigDTO {
    private Boolean enabled;
    /** 小数比例，例如 0.10 表示 10%。 */
    private BigDecimal commissionRate;
    private String purchaseScope;
    private Integer settlementDelayDays;
    /** 读取到的当前规则版本；服务端行锁内校验，防止覆盖别人更新。 */
    private Long expectedVersionId;
    /** 明确从客户自定义或历史制度切换，默认不允许静默替换。 */
    private Boolean confirmPolicySwitch;
}
