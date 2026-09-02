package com.macro.mall.distribution.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsAdminSessionDao;
import com.macro.mall.distribution.dao.DmsAdminUserDao;
import com.macro.mall.distribution.dto.AdminPasswordDTO;
import com.macro.mall.distribution.dto.AdminSelfPasswordDTO;
import com.macro.mall.distribution.dto.AdminTemporaryCredentialDTO;
import com.macro.mall.distribution.dto.AdminUserSaveDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.security.TemporaryAdminCredential;
import com.macro.mall.distribution.service.AdminUserService;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.vo.AdminTemporaryCredentialVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final String BCRYPT_MARKER = "BCRYPT";
    private static final String SUPER_PERMISSION = "*";
    private static final List<Map.Entry<String, String>> PERMISSION_DEFINITIONS = List.of(
            Map.entry("*", "超级管理员"), Map.entry("admin:read", "基础查看"),
            Map.entry("admin:write", "基础维护"), Map.entry("system:manage", "系统账号"),
            Map.entry("config:manage", "全部商城配置（兼容权限）"),
            Map.entry("config:shop", "品牌、页面、公告与协议"),
            Map.entry("config:bonus", "奖金、业绩与经营模式规则"),
            Map.entry("config:integration", "ERP与外部系统对接"),
            Map.entry("shop:product", "商品管理"),
            Map.entry("shop:product-review", "商户商品审核"),
            Map.entry("shop:order", "订单发货"), Map.entry("shop:aftersale", "售后处理"),
            Map.entry("shop:member", "会员管理"), Map.entry("finance:read", "财务查看"),
            Map.entry("finance:manage", "财务处理"), Map.entry("distribution:manage", "代理业绩"),
            Map.entry("merchant:staff-manage", "商户子账号管理"),
            Map.entry("line-change:apply", "移线管理（提交后直接生效）"),
            Map.entry("commission:manage", "佣金处理"), Map.entry("import:manage", "批量导入"));
    private static final Set<String> KNOWN_PERMISSIONS = PERMISSION_DEFINITIONS.stream()
            .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> MERCHANT_STAFF_PERMISSIONS = Set.of(
            "admin:read", "shop:product", "shop:order", "shop:aftersale", "finance:read", "finance:manage");
    private static final Set<String> LOGISTICS_COMPANIES = Set.of(
            "顺丰速运", "京东物流", "中通快递", "圆通速递", "申通快递", "韵达快递", "极兔速递",
            "中国邮政", "EMS", "德邦快递", "跨越速运", "安能物流", "壹米滴答", "DHL", "FedEx", "UPS");

    private final DmsAdminUserDao adminUserDao;
    private final DmsAdminSessionDao adminSessionDao;
    private final AdminAuthService adminAuthService;
    private final com.macro.mall.distribution.dao.DmsMerchantDao merchantDao;
    private final OperationLogService operationLogService;

    @Override
    public List<DmsAdminUser> listUsers(String keyword, Integer status) {
        DmsAdminUser actor = requireAccountManager();
        List<DmsAdminUser> users = adminUserDao.list(keyword, status, actor.getMerchantId());
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
        requireAccountManager(actor);
        dto.setPermissions(normalizeRequestedPermissions(dto.getPermissions()));
        if (actor.getMerchantId() != null) {
            dto.setMerchantId(actor.getMerchantId());
            if (dto.getPermissions() != null && dto.getPermissions().contains("merchant:staff-manage")) {
                Asserts.fail("商户子账号不能继续授予账号管理权限");
            }
        }
        validateGrantedPermissions(actor, dto.getPermissions());
        DmsAdminUser user;
        DmsAdminUser before = null;
        String temporaryPassword = null;
        LocalDateTime credentialExpiresAt = null;
        if (dto.getId() == null) {
            if (dto.getUsername() == null || dto.getUsername().isBlank()) {
                Asserts.fail("账号不能为空");
            }
            if (adminUserDao.selectByUsername(dto.getUsername()) != null) {
                Asserts.fail("账号已存在");
            }
            user = buildUser(dto);
            temporaryPassword = TemporaryAdminCredential.generate();
            credentialExpiresAt = TemporaryAdminCredential.expiresAt();
            user.setSalt(BCRYPT_MARKER);
            user.setPasswordHash(BCrypt.hashpw(temporaryPassword));
            user.setMustChangePassword(1);
            user.setCredentialExpiresAt(credentialExpiresAt);
            adminUserDao.insert(user);
        } else {
            user = adminUserDao.selectById(dto.getId());
            if (user == null) {
                Asserts.fail("后台账号不存在");
            }
            before = copyForAudit(user);
            assertCanManage(actor, user, false);
            fillEditable(user, dto, actor);
            adminUserDao.update(user);
            adminSessionDao.disableByAdminId(user.getId());
        }
        DmsAdminUser saved = adminUserDao.selectById(user.getId());
        if (saved != null && temporaryPassword != null) {
            saved.setTemporaryPassword(temporaryPassword);
            saved.setCredentialExpiresAt(credentialExpiresAt);
        }
        operationLogService.log("ADMIN_USER", before == null ? "CREATE" : "UPDATE", "ADMIN_USER",
                String.valueOf(user.getId()), adminSummary(before), adminSummary(saved),
                before == null ? "新增后台账号" : "修改后台账号、角色或权限");
        return sanitize(saved);
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
        requireAccountManager(actor);
        assertCanManage(actor, user, false);
        rejectReusedPassword(user, dto.getPassword());
        LocalDateTime expiresAt = TemporaryAdminCredential.expiresAt();
        boolean updated = adminUserDao.updateTemporaryPassword(id, BCrypt.hashpw(dto.getPassword()), BCRYPT_MARKER, expiresAt) > 0;
        if (updated) {
            adminUserDao.clearLoginLock(id);
            adminSessionDao.disableByAdminId(id);
            operationLogService.log("ADMIN_USER", "PASSWORD_RESET", "ADMIN_USER", String.valueOf(id),
                    "password=unchanged", "password=reset;sessions=revoked", "重置后台账号密码");
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminTemporaryCredentialVO issueTemporaryCredential(Long id, AdminTemporaryCredentialDTO dto) {
        if (id == null || dto == null) Asserts.fail("账号和身份确认信息不能为空");
        DmsAdminUser actor = requireActorAndVerify(dto.getCurrentAdminPassword());
        requireAccountManager(actor);
        DmsAdminUser target = adminUserDao.selectById(id);
        if (target == null) Asserts.fail("后台账号不存在");
        assertCanManage(actor, target, false);
        String temporaryPassword = TemporaryAdminCredential.generate();
        LocalDateTime expiresAt = TemporaryAdminCredential.expiresAt();
        if (adminUserDao.updateTemporaryPassword(id, BCrypt.hashpw(temporaryPassword), BCRYPT_MARKER, expiresAt) <= 0) {
            Asserts.fail("临时登录凭据生成失败，请刷新后重试");
        }
        adminSessionDao.disableByAdminId(id);
        operationLogService.log("ADMIN_USER", "TEMPORARY_CREDENTIAL", "ADMIN_USER", String.valueOf(id),
                "credential=unchanged", "temporaryCredentialIssued=true;sessions=revoked;expiresAt=" + expiresAt,
                "生成一次性后台临时登录凭据");
        return new AdminTemporaryCredentialVO(target.getUsername(), temporaryPassword, expiresAt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeOwnPassword(AdminSelfPasswordDTO dto) {
        if (dto == null) Asserts.fail("密码信息不能为空");
        DmsAdminUser actor = requireActor();
        adminAuthService.verifyPassword(actor, dto.getCurrentPassword());
        validatePassword(dto.getNewPassword());
        DmsAdminUser current = adminUserDao.selectById(actor.getId());
        if (current == null) Asserts.fail("后台账号不存在");
        rejectReusedPassword(current, dto.getNewPassword());
        boolean updated = adminUserDao.updatePassword(current.getId(), BCrypt.hashpw(dto.getNewPassword()), BCRYPT_MARKER, 0) > 0;
        if (updated) {
            operationLogService.log("ADMIN_USER", "SELF_PASSWORD_CHANGE", "ADMIN_USER", String.valueOf(current.getId()),
                    "password=unchanged;mustChangePassword=" + current.getMustChangePassword(),
                    "password=changed;mustChangePassword=0", "管理员自行修改后台密码");
        }
        return updated;
    }

    @Override
    public boolean unlock(Long id) {
        DmsAdminUser target = id == null ? null : adminUserDao.selectById(id);
        if (target == null) Asserts.fail("后台账号不存在");
        DmsAdminUser actor = requireAccountManager();
        assertCanManage(actor, target, false);
        boolean updated = adminUserDao.clearLoginLock(id) > 0;
        if (updated) operationLogService.log("ADMIN_USER", "UNLOCK", "ADMIN_USER", String.valueOf(id),
                "loginLocked=true", "loginLocked=false", "解除后台账号登录锁定");
        return updated;
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        if (id == null || status == null) {
            Asserts.fail("参数不能为空");
        }
        DmsAdminUser current = requireAccountManager();
        DmsAdminUser target = adminUserDao.selectById(id);
        if (target == null) Asserts.fail("后台账号不存在");
        if (id.equals(current.getId()) && Integer.valueOf(0).equals(status)) {
            Asserts.fail("不能禁用当前登录账号");
        }
        assertCanManage(current, target, false);
        Integer previousStatus = target.getStatus();
        boolean updated = adminUserDao.updateStatus(id, status) > 0;
        if (updated && Integer.valueOf(0).equals(status)) adminSessionDao.disableByAdminId(id);
        if (updated) operationLogService.log("ADMIN_USER", "STATUS_UPDATE", "ADMIN_USER", String.valueOf(id),
                "status=" + previousStatus, "status=" + status,
                Integer.valueOf(0).equals(status) ? "禁用后台账号并撤销会话" : "启用后台账号");
        return updated;
    }

    @Override
    public List<Map<String, String>> permissionOptions() {
        DmsAdminUser actor = requireAccountManager();
        Set<String> actorPermissions = permissionSet(actor);
        boolean root = actorPermissions.contains(SUPER_PERMISSION);
        return PERMISSION_DEFINITIONS.stream()
                .filter(entry -> actor.getMerchantId() == null || MERCHANT_STAFF_PERMISSIONS.contains(entry.getKey()))
                .filter(entry -> root || actorPermissions.contains(entry.getKey()))
                .map(entry -> option(entry.getKey(), entry.getValue())).toList();
    }

    @Override
    public List<Map<String, Object>> merchantOptions() {
        DmsAdminUser actor = requireAccountManager();
        return merchantDao.selectList(com.macro.mall.common.tenant.TenantContext.getTenantId(), null, null).stream()
                .filter(item -> actor.getMerchantId() == null || actor.getMerchantId().equals(item.getId()))
                .filter(item -> !"EXITED".equals(item.getExitStatus()))
                .map(item -> {
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
        fillEditable(user, dto, requireActor());
        return user;
    }

    private void fillEditable(DmsAdminUser user, AdminUserSaveDTO dto, DmsAdminUser actor) {
        user.setNickname(blankToDefault(dto.getNickname(), dto.getUsername()));
        user.setRoleCode(blankToDefault(dto.getRoleCode(), "OPERATOR"));
        user.setPermissions(joinPermissions(dto.getPermissions()));
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        user.setMerchantId(dto.getMerchantId());
        if (dto.getMerchantId() != null) {
            com.macro.mall.distribution.entity.DmsMerchant merchant = merchantDao.selectById(dto.getMerchantId());
            if (merchant == null || "EXITED".equals(merchant.getExitStatus())) Asserts.fail("绑定商户不存在或已退出");
            Set<String> allowed = new LinkedHashSet<>(MERCHANT_STAFF_PERMISSIONS);
            if (actor.getMerchantId() == null) allowed.add("merchant:staff-manage");
            if (!allowed.containsAll(permissionSet(user))) Asserts.fail("商户工作台账号只能授予本商户商品、订单、售后和货款权限");
            user.setRoleCode(permissionSet(user).contains("merchant:staff-manage") ? "MERCHANT_OWNER" : "MERCHANT_STAFF");
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

    private List<String> normalizeRequestedPermissions(List<String> permissions) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (permissions != null) permissions.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim).forEach(normalized::add);
        if (normalized.isEmpty()) normalized.add("admin:read");
        if (!normalized.contains(SUPER_PERMISSION)) {
            // 售后必须读取所属订单，财务处理必须读取台账；依赖权限由前后端同时明确补齐。
            if (normalized.contains("shop:aftersale")) normalized.add("shop:order");
            if (normalized.contains("finance:manage")) normalized.add("finance:read");
            if (normalized.contains("line-change:apply")) normalized.add("distribution:manage");
        }
        return List.copyOf(normalized);
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

    private DmsAdminUser requireAccountManager() {
        return requireAccountManager(requireActor());
    }

    private DmsAdminUser requireAccountManager(DmsAdminUser actor) {
        String permission = actor.getMerchantId() == null ? "system:manage" : "merchant:staff-manage";
        adminAuthService.requirePermission(actor, permission);
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
        if (actor.getMerchantId() != null) {
            if (!allowSelf && actor.getId().equals(target.getId())) Asserts.fail("不能通过子账号管理修改当前登录账号");
            if (!actor.getMerchantId().equals(target.getMerchantId())) Asserts.fail("不能管理其他商户的账号");
            if ("MERCHANT_OWNER".equals(target.getRoleCode()) || permissionSet(target).contains("merchant:staff-manage")) {
                Asserts.fail("商户负责人账号只能由平台管理员维护");
            }
            return;
        }
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
        if (password == null || password.length() < 10 || password.length() > 64) Asserts.fail("后台密码需要10至64位");
        int groups = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) groups++;
        if (password.chars().anyMatch(Character::isUpperCase)) groups++;
        if (password.chars().anyMatch(Character::isDigit)) groups++;
        if (password.chars().anyMatch(value -> !Character.isLetterOrDigit(value))) groups++;
        if (groups < 3) Asserts.fail("后台密码必须包含大小写字母、数字、符号中的至少三类");
    }

    private void rejectReusedPassword(DmsAdminUser user, String password) {
        if (user == null || user.getPasswordHash() == null || password == null) return;
        boolean same = BCRYPT_MARKER.equals(user.getSalt()) || user.getPasswordHash().startsWith("$2")
                ? BCrypt.checkpw(password, user.getPasswordHash())
                : cn.hutool.crypto.SecureUtil.sha256(password + ":" + user.getSalt()).equals(user.getPasswordHash());
        if (same) Asserts.fail("新密码不能与当前密码相同");
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

    @Override
    public String currentDefaultLogisticsCompany() {
        DmsAdminUser actor = requireActor();
        DmsAdminUser current = adminUserDao.selectById(actor.getId());
        return current == null ? null : current.getDefaultLogisticsCompany();
    }

    @Override
    public String updateCurrentDefaultLogisticsCompany(String company) {
        DmsAdminUser actor = requireActor();
        String normalized = company == null ? null : company.trim();
        if (normalized == null || !LOGISTICS_COMPANIES.contains(normalized)) Asserts.fail("请选择系统支持的默认物流公司");
        String before = currentDefaultLogisticsCompany();
        if (adminUserDao.updateDefaultLogisticsCompany(actor.getId(), normalized) <= 0) Asserts.fail("默认物流公司保存失败");
        operationLogService.log("ADMIN_PREFERENCE", "LOGISTICS_DEFAULT", "ADMIN_USER", String.valueOf(actor.getId()),
                before, normalized, "设置订单导入导出的默认物流公司");
        return normalized;
    }

    private DmsAdminUser copyForAudit(DmsAdminUser source) {
        if (source == null) return null;
        DmsAdminUser copy = new DmsAdminUser();
        copy.setId(source.getId());
        copy.setUsername(source.getUsername());
        copy.setNickname(source.getNickname());
        copy.setRoleCode(source.getRoleCode());
        copy.setPermissions(source.getPermissions());
        copy.setMerchantId(source.getMerchantId());
        copy.setStatus(source.getStatus());
        copy.setMustChangePassword(source.getMustChangePassword());
        copy.setCredentialExpiresAt(source.getCredentialExpiresAt());
        copy.setDefaultLogisticsCompany(source.getDefaultLogisticsCompany());
        return copy;
    }

    private String adminSummary(DmsAdminUser user) {
        if (user == null) return null;
        return "username=" + user.getUsername() + ";nickname=" + user.getNickname()
                + ";role=" + user.getRoleCode() + ";permissions=" + user.getPermissions()
                + ";merchantId=" + user.getMerchantId() + ";status=" + user.getStatus();
    }
}
