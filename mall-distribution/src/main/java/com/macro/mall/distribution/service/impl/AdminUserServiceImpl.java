package com.macro.mall.distribution.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsAdminSessionDao;
import com.macro.mall.distribution.dao.DmsAdminUserDao;
import com.macro.mall.distribution.dto.AdminPasswordDTO;
import com.macro.mall.distribution.dto.AdminUserSaveDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final String BCRYPT_MARKER = "BCRYPT";

    private final DmsAdminUserDao adminUserDao;
    private final DmsAdminSessionDao adminSessionDao;

    @Override
    public List<DmsAdminUser> listUsers(String keyword, Integer status) {
        List<DmsAdminUser> users = adminUserDao.list(keyword, status);
        users.forEach(this::sanitize);
        return users;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsAdminUser saveUser(AdminUserSaveDTO dto) {
        if (dto == null) {
            Asserts.fail("账号信息不能为空");
        }
        DmsAdminUser user;
        if (dto.getId() == null) {
            if (dto.getUsername() == null || dto.getUsername().isBlank()) {
                Asserts.fail("账号不能为空");
            }
            if (dto.getPassword() == null || dto.getPassword().isBlank()) {
                Asserts.fail("初始密码不能为空");
            }
            if (adminUserDao.selectByUsername(dto.getUsername()) != null) {
                Asserts.fail("账号已存在");
            }
            user = buildUser(dto);
            validatePassword(dto.getPassword());
            user.setSalt(BCRYPT_MARKER);
            user.setPasswordHash(BCrypt.hashpw(dto.getPassword()));
            adminUserDao.insert(user);
        } else {
            user = adminUserDao.selectById(dto.getId());
            if (user == null) {
                Asserts.fail("后台账号不存在");
            }
            fillEditable(user, dto);
            adminUserDao.update(user);
        }
        return sanitize(adminUserDao.selectById(user.getId()));
    }

    @Override
    public boolean updatePassword(Long id, AdminPasswordDTO dto) {
        if (id == null || dto == null || dto.getPassword() == null || dto.getPassword().isBlank()) {
            Asserts.fail("密码不能为空");
        }
        validatePassword(dto.getPassword());
        DmsAdminUser user = adminUserDao.selectById(id);
        if (user == null) {
            Asserts.fail("后台账号不存在");
        }
        boolean updated = adminUserDao.updatePassword(id, BCrypt.hashpw(dto.getPassword()), BCRYPT_MARKER) > 0;
        if (updated) {
            adminUserDao.clearLoginLock(id);
            adminSessionDao.disableByAdminId(id);
        }
        return updated;
    }

    @Override
    public boolean unlock(Long id) {
        if (id == null || adminUserDao.selectById(id) == null) Asserts.fail("后台账号不存在");
        return adminUserDao.clearLoginLock(id) > 0;
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        if (id == null || status == null) {
            Asserts.fail("参数不能为空");
        }
        DmsAdminUser current = AdminContext.get();
        if (current != null && id.equals(current.getId()) && Integer.valueOf(0).equals(status)) {
            Asserts.fail("不能禁用当前登录账号");
        }
        boolean updated = adminUserDao.updateStatus(id, status) > 0;
        if (updated && Integer.valueOf(0).equals(status)) adminSessionDao.disableByAdminId(id);
        return updated;
    }

    @Override
    public List<Map<String, String>> permissionOptions() {
        return List.of(
                option("*", "超级管理员"),
                option("admin:read", "基础查看"),
                option("admin:write", "基础维护"),
                option("system:manage", "系统账号"),
                option("config:manage", "客户与规则配置"),
                option("shop:product", "商品管理"),
                option("shop:order", "订单发货"),
                option("shop:aftersale", "售后处理"),
                option("shop:member", "会员管理"),
                option("finance:read", "财务查看"),
                option("finance:manage", "财务处理"),
                option("distribution:manage", "代理业绩"),
                option("line-change:apply", "移线管理（提交后直接生效）"),
                option("commission:manage", "佣金处理"),
                option("import:manage", "批量导入")
        );
    }

    private DmsAdminUser buildUser(AdminUserSaveDTO dto) {
        DmsAdminUser user = new DmsAdminUser();
        user.setUsername(dto.getUsername().trim());
        fillEditable(user, dto);
        return user;
    }

    private void fillEditable(DmsAdminUser user, AdminUserSaveDTO dto) {
        user.setNickname(blankToDefault(dto.getNickname(), dto.getUsername()));
        user.setRoleCode(blankToDefault(dto.getRoleCode(), "OPERATOR"));
        user.setPermissions(joinPermissions(dto.getPermissions()));
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }

    private String joinPermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "admin:read";
        }
        List<String> normalized = new java.util.ArrayList<>(permissions);
        // 旧版 line-change:audit 不再授予移线能力；只有明确勾选移线管理权限才能操作。
        normalized.remove("line-change:audit");
        if (normalized.contains("line-change:apply")
                && !normalized.contains("distribution:manage")) {
            normalized.add("distribution:manage");
        }
        return normalized.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 64) {
            Asserts.fail("后台密码需要8至64位");
        }
    }

    private Map<String, String> option(String value, String label) {
        Map<String, String> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", label);
        return option;
    }

    private DmsAdminUser sanitize(DmsAdminUser user) {
        if (user != null) {
            user.setPasswordHash(null);
            user.setSalt(null);
        }
        return user;
    }
}
