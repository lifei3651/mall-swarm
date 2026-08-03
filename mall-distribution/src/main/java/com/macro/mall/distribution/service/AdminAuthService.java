package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.AdminLoginDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.vo.AdminAuthVO;

import java.util.List;

public interface AdminAuthService {

    AdminAuthVO login(AdminLoginDTO dto);

    AdminAuthVO me(String authorization);

    boolean logout(String authorization);

    DmsAdminUser resolveAdmin(String authorization);

    DmsAdminUser requireAdmin(String authorization);

    /** 高风险操作的服务器端二次密码校验。 */
    void verifyPassword(DmsAdminUser admin, String password);

    void requirePermission(DmsAdminUser admin, String permission);

    boolean hasPermission(DmsAdminUser admin, String permission);

    List<String> permissions(DmsAdminUser admin);
}
