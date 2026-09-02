package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsAdminSessionDao;
import com.macro.mall.distribution.dao.DmsAdminUserDao;
import com.macro.mall.distribution.entity.DmsAdminSession;
import com.macro.mall.distribution.entity.DmsAdminUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminLoginTransactionServiceTest {
    @Mock private DmsAdminUserDao userDao;
    @Mock private DmsAdminSessionDao sessionDao;

    @Test
    void consumesTemporaryCredentialBeforeIssuingTheOnlySession() {
        DmsAdminUser admin = temporaryAdmin();
        when(userDao.consumeTemporaryCredential(eq(7L), any(LocalDateTime.class))).thenReturn(1);

        new AdminLoginTransactionService(userDao, sessionDao).issue(admin, 168, 3);

        InOrder ordered = inOrder(userDao, sessionDao);
        ordered.verify(userDao).consumeTemporaryCredential(eq(7L), any(LocalDateTime.class));
        ordered.verify(userDao).updateLastLoginTime(7L);
        ordered.verify(sessionDao).insert(any(DmsAdminSession.class));
    }

    @Test
    void reusedTemporaryCredentialCannotCreateAnotherSession() {
        DmsAdminUser admin = temporaryAdmin();
        when(userDao.consumeTemporaryCredential(eq(7L), any(LocalDateTime.class))).thenReturn(0);

        assertThrows(ApiException.class,
                () -> new AdminLoginTransactionService(userDao, sessionDao).issue(admin, 168, 3));

        verify(userDao, never()).updateLastLoginTime(7L);
        verify(sessionDao, never()).insert(any(DmsAdminSession.class));
    }

    private DmsAdminUser temporaryAdmin() {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(7L);
        admin.setUsername("operator");
        admin.setMustChangePassword(1);
        admin.setCredentialExpiresAt(LocalDateTime.now().plusHours(1));
        return admin;
    }
}
