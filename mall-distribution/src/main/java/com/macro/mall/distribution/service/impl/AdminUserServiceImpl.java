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
import com.macro.mall.distribution.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final String BCRYPT_MARKER = "BCRYPT";
    private static final String SUPER_PERMISSION = "*";
    private static final List<Map.Entry<String, String>> PERMISSION_DEFINITIONS = List.of(
            Map.entry("*", "超级管理员"), Map.entry("admin:read", "基础查看"),
            Map.entry("admin:write", "基础维护"), Map.entry("system:manage", "系统账号"),
            Map.entry("config:manage", "客户与规则配置"), Map.entry("shop:product", "商品管理"),
            Map.entry("shop:product-review", "商户商品审核"),
            Map.entry("shop:order", "订单发货"), Map.entry("shop:aftersale", "售后处理"),
            Map.entry("shop:member", "会员管理"), Map.entry("finance:read", "财务查看"),
            Map.entry("finance:manage", "财务处理"), Map.entry("distribution:manage", "代理业绩"),
            Map.entry("line-change:apply", "移线管理（提交后直接生效）"),
            Map.entry("commission:manage", "佣金处理"), Map.entry("import:manage", "批量导入"));
    private static final Set<String> KNOWN_PERMISSIONS = PERMISSION_DEFINITIONS.stream()
            .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());

    private final DmsAdminUserDao adminUserDao;
    private final DmsAdminSessionDao adminSessionDao;
    private final AdminAuthService adminAuthService;
    private final com.macro.mall.distribution.dao.DmsMerchantDao merchantDao;

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
        DmsAdminUser actor = requireActorAndVerify(dto.getCurrentAdminPassword());
        validateGrantedPermissions(actor, dto.getPermissions());
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
            assertCanManage(actor, user, false);
            fillEditable(user, dto);
            adminUserDao.update(user);
            adminSessionDao.disableByAdminId(user.getId());
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
        DmsAdminUser actor = requireActorAndVerify(dto.getCurrentAdminPassword());
        assertCanManage(actor, user, false);
        boolean updated = adminUserDao.updatePassword(id, BCrypt.hashpw(dto.getPassword()), BCRYPT_MARKER) > 0;
        if (updated) {
            adminUserDao.clearLoginLock(id);
            adminSessionDao.disableByAdminId(id);
        }
        return updated;
    }

    @Override
    public boolean unlock(Long id) {
        DmsAdminUser target = id == null ? null : adminUserDao.selectById(id);
        if (target == null) Asserts.fail("后台账号不存在");
        assertCanManage(requireActor(), target, false);
        return adminUserDao.clearLoginLock(id) > 0;
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        if (id == null || status == null) {
            Asserts.fail("参数不能为空");
        }
        DmsAdminUser current = requireActor();
        DmsAdminUser target = adminUserDao.selectById(id);
        if (target == null) Asserts.fail("后台账号不存在");
        if (id.equals(current.getId()) && Integer.valueOf(0).equals(status)) {
            Asserts.fail("不能禁用当前登录账号");
        }
        assertCanManage(current, target, false);
        boolean updated = adminUserDao.updateStatus(id, status) > 0;
        if (updated && Integer.valueOf(0).equals(status)) adminSessionDao.disableByAdminId(id);
        return updated;
    }

    @Override
    public List<Map<String, String>> permissionOptions() {
        DmsAdminUser actor = requireActor();
        Set<String> actorPermissions = permissionSet(actor);
        boolean root = actorPermissions.contains(SUPER_PERMISSION);
        return PERMISSION_DEFINITIONS.stream()
                .filter(entry -> root || actorPermissions.contains(entry.getKey()))
                .map(entry -> option(entry.getKey(), entry.getValue())).toList();
    }

    @Override
    public List<Map<String, Object>> merchantOptions() {
        requireActor();
        return merchantDao.selectList(com.macro.mall.common.tenant.TenantContext.getTenantId(), null, 1).stream().map(item -> {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("id", item.getId());
            option.put("merchantName", item.getMerchantName());
            option.put("merchantNo", item.getMerchantNo());
            return option;
        }).toList();
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
        user.setMerchantId(dto.getMerchantId());
        if (dto.getMerchantId() != null) {
            com.macro.mall.distribution.entity.DmsMerchant merchant = merchantDao.selectById(dto.getMerchantId());
            if (merchant == null || !Integer.valueOf(1).equals(merchant.getStatus())) Asserts.fail("绑定商户不存在或已停用");
            Set<String> allowed = Set.of("admin:read", "shop:product", "finance:read", "finance:manage");
            if (!allowed.containsAll(permissionSet(user))) Asserts.fail("商户工作台账号只能授予基础查看、商品管理和本商户货款权限");
            user.setRoleCode("MERCHANT");
        }
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

    private DmsAdminUser requireActorAndVerify(String currentPassword) {
        DmsAdminUser actor = requireActor();
        adminAuthService.verifyPassword(actor, currentPassword);
        return actor;
    }

    private DmsAdminUser requireActor() {
        DmsAdminUser actor = AdminContext.get();
        if (actor == null || actor.getId() == null) Asserts.fail("后台登录已失效，请重新登录");
        return actor;
    }

    private void validateGrantedPermissions(DmsAdminUser actor, List<String> requested) {
        Set<String> normalized = new LinkedHashSet<>();
        if (requested == null || requested.isEmpty()) normalized.add("admin:read");
        else requested.stream().filter(item -> item != null && !item.isBlank())
                .map(String::trim).forEach(normalized::add);
        if (!KNOWN_PERMISSIONS.containsAll(normalized)) Asserts.fail("包含系统不支持的管理员权限");
        Set<String> actorPermissions = permissionSet(actor);
        if (!actorPermissions.contains(SUPER_PERMISSION) && !actorPermissions.containsAll(normalized)) {
            Asserts.fail("不能授予当前管理员自身不具备的权限");
        }
    }

    private void assertCanManage(DmsAdminUser actor, DmsAdminUser target, boolean allowSelf) {
        Set<String> actorPermissions = permissionSet(actor);
        if (actorPermissions.contains(SUPER_PERMISSION)) return;
        if (!allowSelf && actor.getId().equals(target.getId())) Asserts.fail("不能通过账号管理修改当前登录账号");
        Set<String> targetPermissions = permissionSet(target);
        if (targetPermissions.contains(SUPER_PERMISSION) || !actorPermissions.containsAll(targetPermissions)
                || actorPermissions.equals(targetPermissions)) {
            Asserts.fail("不能管理同级或更高权限的管理员账号");
        }
    }

    private Set<String> permissionSet(DmsAdminUser user) {
        return new LinkedHashSet<>(adminAuthService.permissions(user));
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
