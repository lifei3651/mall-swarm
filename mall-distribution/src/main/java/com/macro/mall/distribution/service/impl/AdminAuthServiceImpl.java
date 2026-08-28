package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.api.ResultCode;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsAdminSessionDao;
import com.macro.mall.distribution.dao.DmsAdminUserDao;
import com.macro.mall.distribution.dao.DmsMerchantDao;
import com.macro.mall.distribution.dto.AdminLoginDTO;
import com.macro.mall.distribution.entity.DmsAdminSession;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.LoginCaptchaService;
import com.macro.mall.distribution.vo.AdminAuthVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;

@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final String SUPER_PERMISSION = "*";
    private static final int MAX_FAILED_LOGIN_COUNT = 5;
    private static final int LOGIN_LOCK_MINUTES = 15;
    private static final String BCRYPT_MARKER = "BCRYPT";
    private static final String DUMMY_PASSWORD_HASH = BCrypt.hashpw("invalid-admin-login-placeholder");

    private final DmsAdminUserDao adminUserDao;
    private final DmsAdminSessionDao adminSessionDao;
    private final LoginCaptchaService loginCaptchaService;
    @Autowired(required = false)
    private DmsMerchantDao merchantDao;

    /** 管理后台绝对会话默认12小时，避免资金后台长期保持登录。 */
    @Value("${admin.security.session-hours:12}")
    private long sessionHours = 12;

    @Override
    // 不包裹外层事务：密码错误时失败次数必须在抛出异常前独立提交，不能随异常回滚。
    public AdminAuthVO login(AdminLoginDTO dto) {
        if (dto == null || dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getPassword() == null || dto.getPassword().isBlank()) {
            Asserts.fail("账号和密码不能为空");
        }
        loginCaptchaService.verify("admin", dto.getCaptchaId(), dto.getCaptchaCode());
        DmsAdminUser admin = adminUserDao.selectByUsername(dto.getUsername());
        if (admin == null) {
            BCrypt.checkpw(dto.getPassword(), DUMMY_PASSWORD_HASH);
            Asserts.fail("账号或密码错误");
        }
        // 即使账号正处于锁定期，也先完成一次真实密码校验，避免锁定账号形成明显的快速响应侧信道。
        boolean passwordMatches = matchesPassword(dto.getPassword(), admin);
        if (admin.getLockTime() != null) {
            LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(LOGIN_LOCK_MINUTES);
            if (admin.getLockTime().isAfter(expiredBefore)) {
                Asserts.fail("账号或密码错误");
            }
            // 只清除本次读取到的过期锁；并发请求刚形成的新锁不能被旧请求覆盖。
            if (adminUserDao.clearExpiredLoginLock(admin.getId(), expiredBefore) != 1) {
                Asserts.fail("账号或密码错误");
            }
            admin.setLockTime(null);
        }
        if (!passwordMatches) {
            adminUserDao.increaseFailedLogin(admin.getId(), MAX_FAILED_LOGIN_COUNT);
            DmsAdminUser refreshed = adminUserDao.selectById(admin.getId());
            Asserts.fail("账号或密码错误");
        }
        if (!BCRYPT_MARKER.equals(admin.getSalt())) {
            adminUserDao.updatePassword(admin.getId(), BCrypt.hashpw(dto.getPassword()), BCRYPT_MARKER,
                    admin.getMustChangePassword() == null ? 0 : admin.getMustChangePassword());
        }
        if (!Integer.valueOf(1).equals(admin.getStatus())) {
            Asserts.fail("后台账号已禁用");
        }
        requireMerchantAccountEnabled(admin, false);
        requireLoginPortal(admin, dto.getPortal());
        adminUserDao.updateLastLoginTime(admin.getId());
        // 单账号单会话：新登录成功后使该管理员此前的全部会话失效。
        adminSessionDao.disableByAdminId(admin.getId());
        return createSession(admin);
    }

    @Override
    public AdminAuthVO me(String authorization) {
        DmsAdminUser admin = requireAdmin(authorization);
        AdminAuthVO vo = new AdminAuthVO();
        vo.setAdmin(sanitize(admin));
        vo.setPermissions(permissions(admin));
        return vo;
    }

    @Override
    public boolean logout(String authorization) {
        String token = stripToken(authorization);
        if (token == null) return false;
        return adminSessionDao.disableByToken(hashToken(token)) > 0;
    }

    @Override
    public DmsAdminUser resolveAdmin(String authorization) {
        String token = stripToken(authorization);
        if (token == null) {
            return null;
        }
        DmsAdminSession session = adminSessionDao.selectByToken(hashToken(token));
        if (session == null || !Integer.valueOf(1).equals(session.getStatus())
                || session.getExpireTime() == null || session.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        DmsAdminUser admin = adminUserDao.selectById(session.getAdminId());
        if (admin == null || !Integer.valueOf(1).equals(admin.getStatus())) {
            return null;
        }
        if (!merchantAccountEnabled(admin)) return null;
        return admin;
    }

    @Override
    public DmsAdminUser requireAdmin(String authorization) {
        DmsAdminUser admin = resolveAdmin(authorization);
        if (admin == null) {
            throw new ApiException(ResultCode.UNAUTHORIZED, "后台登录已失效，请重新登录");
        }
        return admin;
    }

    @Override
    // 同登录校验，错误次数和锁定状态必须在失败响应前落库。
    public void verifyPassword(DmsAdminUser admin, String password) {
        if (admin == null || admin.getId() == null) Asserts.fail("后台登录已失效，请重新登录");
        if (password == null || password.isBlank()) Asserts.fail("请输入当前管理员登录密码");
        DmsAdminUser current = adminUserDao.selectById(admin.getId());
        if (current == null || !Integer.valueOf(1).equals(current.getStatus())) {
            Asserts.fail("后台账号已禁用");
        }
        if (current.getLockTime() != null) {
            adminSessionDao.disableByAdminId(current.getId());
            Asserts.fail("管理员账号已锁定，请重新登录或联系其他管理员解锁");
        }
        if (!matchesPassword(password, current)) {
            adminUserDao.increaseFailedLogin(current.getId(), MAX_FAILED_LOGIN_COUNT);
            DmsAdminUser refreshed = adminUserDao.selectById(current.getId());
            if (refreshed != null && refreshed.getLockTime() != null) {
                adminSessionDao.disableByAdminId(current.getId());
                Asserts.fail("管理员密码连续错误5次，账号已锁定");
            }
            Asserts.fail("当前管理员登录密码不正确");
        }
        adminUserDao.clearLoginLock(current.getId());
    }

    @Override
    public void requirePermission(DmsAdminUser admin, String permission) {
        if (!hasPermission(admin, permission)) {
            throw new ApiException(ResultCode.FORBIDDEN, "没有操作权限：" + permission);
        }
    }

    @Override
    public boolean hasPermission(DmsAdminUser admin, String permission) {
        if (admin == null || permission == null || permission.isBlank()) {
            return false;
        }
        List<String> permissions = permissions(admin);
        return permissions.contains(SUPER_PERMISSION) || permissions.contains(permission);
    }

    @Override
    public List<String> permissions(DmsAdminUser admin) {
        if (admin == null || admin.getPermissions() == null || admin.getPermissions().isBlank()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> resolved = Arrays.stream(admin.getPermissions().split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        // 兼容已有管理员：旧的总配置权限继续拥有三个细分权限，新账号可以按职责最小授权。
        if (resolved.contains("config:manage")) {
            resolved.add("config:shop");
            resolved.add("config:bonus");
            resolved.add("config:integration");
        }
        return List.copyOf(resolved);
    }

    private AdminAuthVO createSession(DmsAdminUser admin) {
        DmsAdminSession session = new DmsAdminSession();
        session.setAdminId(admin.getId());
        session.setUsername(admin.getUsername());
        String rawToken = IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
        session.setToken(hashToken(rawToken));
        session.setStatus(1);
        session.setExpireTime(LocalDateTime.now().plusHours(Math.max(1, sessionHours)));
        adminSessionDao.insert(session);

        AdminAuthVO vo = new AdminAuthVO();
        vo.setToken(rawToken);
        vo.setExpireTime(session.getExpireTime());
        vo.setAdmin(sanitize(admin));
        vo.setPermissions(permissions(admin));
        return vo;
    }

    private boolean matchesPassword(String password, DmsAdminUser admin) {
        if (BCRYPT_MARKER.equals(admin.getSalt()) || (admin.getPasswordHash() != null
                && admin.getPasswordHash().startsWith("$2"))) {
            return admin.getPasswordHash() != null && BCrypt.checkpw(password, admin.getPasswordHash());
        }
        return SecureUtil.sha256(password + ":" + admin.getSalt()).equals(admin.getPasswordHash());
    }

    private String hashToken(String token) {
        return SecureUtil.sha256(token);
    }

    private String stripToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
    }

    private DmsAdminUser sanitize(DmsAdminUser admin) {
        if (admin == null) {
            return null;
        }
        DmsAdminUser copy = new DmsAdminUser();
        copy.setId(admin.getId());
        copy.setUsername(admin.getUsername());
        copy.setNickname(admin.getNickname());
        copy.setRoleCode(admin.getRoleCode());
        copy.setPermissions(admin.getPermissions());
        copy.setMerchantId(admin.getMerchantId());
        copy.setMerchantName(admin.getMerchantName());
        copy.setStatus(admin.getStatus());
        copy.setLastLoginTime(admin.getLastLoginTime());
        copy.setCreateTime(admin.getCreateTime());
        copy.setUpdateTime(admin.getUpdateTime());
        return copy;
    }

    private void requireMerchantAccountEnabled(DmsAdminUser admin, boolean session) {
        if (merchantAccountEnabled(admin)) return;
        Asserts.fail(session ? "后台登录已失效，请重新登录" : "商户工作台账号已禁用");
    }

    private boolean merchantAccountEnabled(DmsAdminUser admin) {
        if (admin == null || admin.getMerchantId() == null) return true;
        if (merchantDao == null) return true;
        DmsMerchant merchant = merchantDao.selectById(admin.getMerchantId());
        return merchant != null && "ENABLED".equals(merchant.getAccountStatus());
    }

    private void requireLoginPortal(DmsAdminUser admin, String portal) {
        if (portal == null || portal.isBlank()) return;
        if (!"PLATFORM".equals(portal) && !"MERCHANT".equals(portal)) {
            Asserts.fail("后台登录入口不正确");
        }
        boolean merchantAccount = admin.getMerchantId() != null;
        if ("PLATFORM".equals(portal) && merchantAccount) {
            Asserts.fail("该账号属于商家后台，请使用商家登录入口");
        }
        if ("MERCHANT".equals(portal) && !merchantAccount) {
            Asserts.fail("该账号属于平台总后台，请使用平台登录入口");
        }
    }
}
