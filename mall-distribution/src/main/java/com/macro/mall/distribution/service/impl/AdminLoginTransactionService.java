package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsAdminSessionDao;
import com.macro.mall.distribution.dao.DmsAdminUserDao;
import com.macro.mall.distribution.entity.DmsAdminSession;
import com.macro.mall.distribution.entity.DmsAdminUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 把一次性凭据消费和受限会话签发放在同一事务中，避免半完成状态。 */
@Service
@RequiredArgsConstructor
public class AdminLoginTransactionService {

    private final DmsAdminUserDao adminUserDao;
    private final DmsAdminSessionDao adminSessionDao;

    @Transactional(rollbackFor = Exception.class)
    public IssuedSession issue(DmsAdminUser admin, long sessionHours, int maxActiveSessions) {
        LocalDateTime now = LocalDateTime.now();
        if (Integer.valueOf(1).equals(admin.getMustChangePassword())) {
            if (adminUserDao.consumeTemporaryCredential(admin.getId(), now) != 1) {
                Asserts.fail("一次性临时密码已使用或已过期，请联系平台或商户负责人重新生成");
            }
            admin.setCredentialConsumedAt(now);
        }
        adminUserDao.updateLastLoginTime(admin.getId());

        DmsAdminSession session = new DmsAdminSession();
        session.setAdminId(admin.getId());
        session.setUsername(admin.getUsername());
        String rawToken = IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
        session.setToken(SecureUtil.sha256(rawToken));
        session.setStatus(1);
        LocalDateTime expireTime = now.plusHours(Math.max(1, sessionHours));
        if (Integer.valueOf(1).equals(admin.getMustChangePassword())
                && admin.getCredentialExpiresAt().isBefore(expireTime)) {
            expireTime = admin.getCredentialExpiresAt();
        }
        session.setExpireTime(expireTime);
        adminSessionDao.insert(session);
        retainRecentSessions(admin.getId(), maxActiveSessions);
        return new IssuedSession(rawToken, expireTime);
    }

    private void retainRecentSessions(Long adminId, int maxActiveSessions) {
        List<DmsAdminSession> sessions = adminSessionDao.selectActiveByAdminId(adminId);
        if (sessions == null) return;
        int keepCount = Math.max(1, maxActiveSessions);
        for (int index = keepCount; index < sessions.size(); index++) {
            String tokenHash = sessions.get(index).getToken();
            if (tokenHash != null && !tokenHash.isBlank()) adminSessionDao.disableByToken(tokenHash);
        }
    }

    public record IssuedSession(String token, LocalDateTime expireTime) {
    }
}
