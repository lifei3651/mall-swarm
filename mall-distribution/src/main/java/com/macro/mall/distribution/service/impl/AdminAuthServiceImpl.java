package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsAdminSessionDao;
import com.macro.mall.distribution.dao.DmsAdminUserDao;
import com.macro.mall.distribution.dto.AdminLoginDTO;
import com.macro.mall.distribution.entity.DmsAdminSession;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.LoginCaptchaService;
import com.macro.mall.distribution.vo.AdminAuthVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
            if (admin.getLockTime().plusMinutes(LOGIN_LOCK_MINUTES).isAfter(LocalDateTime.now())) {
                Asserts.fail("账号或密码错误");
            }
            adminUserDao.clearLoginLock(admin.getId());
            admin.setLockTime(null);
        }
        if (!passwordMatches) {
            adminUserDao.increaseFailedLogin(admin.getId(), MAX_FAILED_LOGIN_COUNT);
            DmsAdminUser refreshed = adminUserDao.selectById(admin.getId());
            Asserts.fail("账号或密码错误");
        }
        if (!BCRYPT_MARKER.equals(admin.getSalt())) {
            adminUserDao.updatePassword(admin.getId(), BCrypt.hashpw(dto.getPassword()), BCRYPT_MARKER);
        }
        if (!Integer.valueOf(1).equals(admin.getStatus())) {
            Asserts.fail("后台账号已禁用");
        }
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
        int updated = adminSessionDao.disableByToken(hashToken(token));
        return updated > 0 || adminSessionDao.disableByToken(token) > 0;
    }

    @Override
    public DmsAdminUser resolveAdmin(String authorization) {
        String token = stripToken(authorization);
        if (token == null) {
            return null;
        }
        DmsAdminSession session = adminSessionDao.selectByToken(hashToken(token));
        if (session == null) session = adminSessionDao.selectByToken(token);
        if (session == null || !Integer.valueOf(1).equals(session.getStatus())
                || session.getExpireTime() == null || session.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        DmsAdminUser admin = adminUserDao.selectById(session.getAdminId());
        if (admin == null || !Integer.valueOf(1).equals(admin.getStatus())) {
            return null;
        }
        return admin;
    }

    @Override
    public DmsAdminUser requireAdmin(String authorization) {
        DmsAdminUser admin = resolveAdmin(authorization);
        if (admin == null) {
            Asserts.fail("后台登录已失效，请重新登录");
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
            Asserts.fail("没有操作权限：" + permission);
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
        return Arrays.stream(admin.getPermissions().split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
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
}
