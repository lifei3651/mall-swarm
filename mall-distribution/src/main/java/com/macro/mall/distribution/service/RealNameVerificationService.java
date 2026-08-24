package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.RealNameVerifyDTO;
import com.macro.mall.distribution.entity.DmsMemberRealName;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.RealNameStatusVO;

public interface RealNameVerificationService {
    RealNameStatusVO getStatus(DmsShopMember member);
    RealNameStatusVO verify(DmsShopMember member, RealNameVerifyDTO dto);
    DmsMemberRealName requireEligible(DmsShopMember member, String actionName);
    boolean isVerified(DmsShopMember member);
}
