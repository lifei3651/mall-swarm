package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.config.RealNameVerificationProperties;
import com.macro.mall.distribution.dao.DmsMemberRealNameDao;
import com.macro.mall.distribution.dto.RealNameVerifyDTO;
import com.macro.mall.distribution.entity.DmsMemberRealName;
import com.macro.mall.distribution.entity.DmsMemberRealNameAttempt;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.identity.MainlandIdCard;
import com.macro.mall.distribution.identity.RealNameVerificationProvider;
import com.macro.mall.distribution.identity.RealNameVerificationResult;
import com.macro.mall.distribution.service.impl.RealNameVerificationServiceImpl;
import com.macro.mall.distribution.security.EncryptedStringTypeHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealNameVerificationServiceTest {

    @Test
    void sameIdentityCanVerifyMultipleAccountsWithoutIdentityUniquenessRule() {
        EncryptedStringTypeHandler.configureKey("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        DmsMemberRealNameDao dao = mock(DmsMemberRealNameDao.class);
        RealNameVerificationProvider provider = mock(RealNameVerificationProvider.class);
        when(provider.verify(anyString(), anyString()))
                .thenReturn(new RealNameVerificationResult(true, "0", "request-1"));
        RealNameVerificationServiceImpl service = new RealNameVerificationServiceImpl(dao, provider, readyProperties());
        RealNameVerifyDTO dto = dto("张三", "11010519491231002X");

        assertTrue(service.verify(member(1L, 1001L), dto).getVerified());
        assertTrue(service.verify(member(2L, 1002L), dto).getVerified());

        ArgumentCaptor<DmsMemberRealName> records = ArgumentCaptor.forClass(DmsMemberRealName.class);
        verify(dao, times(2)).insert(records.capture());
        assertEquals(2, records.getAllValues().stream().map(DmsMemberRealName::getMemberId).distinct().count());
        assertEquals(1, records.getAllValues().stream().map(DmsMemberRealName::getIdCard).distinct().count());
    }

    @Test
    void mainlandIdentityChecksumAndAdultRuleAreAppliedLocally() {
        assertTrue(MainlandIdCard.isValid("11010519491231002X"));
        assertTrue(MainlandIdCard.isAdult("11010519491231002X"));
    }

    @Test
    void mismatchPersistsOnlyNonPiiAttemptAudit() {
        EncryptedStringTypeHandler.configureKey("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        DmsMemberRealNameDao dao = mock(DmsMemberRealNameDao.class);
        RealNameVerificationProvider provider = mock(RealNameVerificationProvider.class);
        when(provider.verify(anyString(), anyString()))
                .thenReturn(new RealNameVerificationResult(false, "-1", "request-safe"));
        RealNameVerificationServiceImpl service = new RealNameVerificationServiceImpl(dao, provider, readyProperties());

        ApiException error = assertThrows(ApiException.class,
                () -> service.verify(member(3L, 1003L), dto("李四", "11010519491231002X")));

        assertEquals("姓名与身份证号不一致，请核对后重试", error.getMessage());
        ArgumentCaptor<DmsMemberRealNameAttempt> attempt = ArgumentCaptor.forClass(DmsMemberRealNameAttempt.class);
        verify(dao).insertAttempt(attempt.capture());
        assertEquals("-1", attempt.getValue().getResultCode());
        assertEquals("request-safe", attempt.getValue().getProviderRequestId());
        verify(dao, never()).insert(org.mockito.ArgumentMatchers.any(DmsMemberRealName.class));
    }

    @Test
    void accountDailyLimitStopsProviderBeforeExternalCall() {
        EncryptedStringTypeHandler.configureKey("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        DmsMemberRealNameDao dao = mock(DmsMemberRealNameDao.class);
        RealNameVerificationProvider provider = mock(RealNameVerificationProvider.class);
        when(dao.countAttemptsSince(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.any())).thenReturn(5L);
        RealNameVerificationProperties properties = readyProperties();
        properties.setDailyMaxAttemptsPerAccount(5);
        RealNameVerificationServiceImpl service = new RealNameVerificationServiceImpl(dao, provider, properties);

        ApiException error = assertThrows(ApiException.class,
                () -> service.verify(member(4L, 1004L), dto("王五", "11010519491231002X")));

        assertEquals("今日实名认证次数已达上限，请明日再试", error.getMessage());
        verify(provider, never()).verify(anyString(), anyString());
    }

    private RealNameVerifyDTO dto(String name, String idCard) {
        RealNameVerifyDTO dto = new RealNameVerifyDTO();
        dto.setRealName(name);
        dto.setIdCard(idCard);
        dto.setSensitiveInfoConsent(true);
        return dto;
    }

    private DmsShopMember member(Long id, Long userId) {
        DmsShopMember member = new DmsShopMember();
        member.setId(id);
        member.setUserId(userId);
        member.setStatus(1);
        return member;
    }

    private RealNameVerificationProperties readyProperties() {
        RealNameVerificationProperties properties = new RealNameVerificationProperties();
        properties.setEnabled(true);
        properties.setSecretId("test-id");
        properties.setSecretKey("test-key");
        return properties;
    }
}
