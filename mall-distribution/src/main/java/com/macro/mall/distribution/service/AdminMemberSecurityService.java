package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.AdminMemberPasswordResetDTO;
import com.macro.mall.distribution.dto.AdminMemberPhoneUpdateDTO;

public interface AdminMemberSecurityService {

    boolean updatePhone(Long memberId, AdminMemberPhoneUpdateDTO dto);

    boolean resetLoginPassword(Long memberId, AdminMemberPasswordResetDTO dto);
}
