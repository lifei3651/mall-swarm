package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.AdminPasswordDTO;
import com.macro.mall.distribution.dto.AdminUserSaveDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;

import java.util.List;
import java.util.Map;

public interface AdminUserService {

    List<DmsAdminUser> listUsers(String keyword, Integer status);

    DmsAdminUser saveUser(AdminUserSaveDTO dto);

    boolean updatePassword(Long id, AdminPasswordDTO dto);

    boolean unlock(Long id);

    boolean updateStatus(Long id, Integer status);

    List<Map<String, String>> permissionOptions();
}
