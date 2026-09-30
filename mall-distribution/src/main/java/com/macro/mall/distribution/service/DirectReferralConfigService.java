package com.macro.mall.distribution.service;

import com.macro.mall.distribution.bonus.DirectReferralRuleConfig;
import com.macro.mall.distribution.dto.DirectReferralConfigDTO;
import com.macro.mall.distribution.vo.DirectReferralConfigVO;

public interface DirectReferralConfigService {
    DirectReferralConfigVO current();
    DirectReferralConfigVO save(DirectReferralConfigDTO dto);
    /** 不回退到当前配置；历史版本停用后仍按原快照读取。 */
    DirectReferralRuleConfig frozen(Long tenantId, Long ruleVersionId);
}
