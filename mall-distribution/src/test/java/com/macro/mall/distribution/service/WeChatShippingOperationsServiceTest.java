package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.api.ResultCode;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsWechatShippingSyncTaskDao;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsWechatShippingSyncTask;
import com.macro.mall.distribution.security.AdminContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WeChatShippingOperationsServiceTest {
    private final DmsWechatShippingSyncTaskDao dao=mock(DmsWechatShippingSyncTaskDao.class);
    private final WeChatShippingInfoService shipping=mock(WeChatShippingInfoService.class);
    private final AdminAuthService auth=mock(AdminAuthService.class);
    private final OperationLogService log=mock(OperationLogService.class);
    private final WeChatShippingOperationsService service=new WeChatShippingOperationsService(dao,shipping,auth,log);
    private DmsAdminUser admin;
    @BeforeEach void setup(){TenantContext.setTenantId(7L);admin=new DmsAdminUser();admin.setId(9L);AdminContext.set(admin);}
    @AfterEach void cleanup(){AdminContext.clear();TenantContext.clear();}

    @Test void requiresPlatformAndExplicitPermissionBeforeAnyQuery(){
        AdminContext.clear();assertThrows(ApiException.class,()->service.list(null,1,20));
        AdminContext.set(admin);admin.setMerchantId(3L);assertThrows(ApiException.class,()->service.retry(1L,1));
        admin.setMerchantId(null);doThrow(new ApiException(ResultCode.FORBIDDEN,"denied")).when(auth).requirePermission(admin,"config:shop");
        assertThrows(ApiException.class,()->service.list(null,1,20));verifyNoInteractions(dao);
    }
    @Test void listIsTenantScopedAndDoesNotExposeFullOrderOrIdentity() throws Exception {
        var task=task();task.setPaymentOrderNo("PAY-PRIVATE-123456");task.setUserId(999L);task.setLeaseOwner("internal-worker");
        when(shipping.ready()).thenReturn(true);when(dao.listScoped(7L,"PERMANENT",0,20)).thenReturn(List.of(task));
        when(dao.countScoped(7L,"PERMANENT")).thenReturn(1L);
        var result=service.list("PERMANENT",1,20);assertEquals(1,result.failedCount());
        assertEquals("***123456",result.tasks().getList().get(0).paymentNoHint());assertTrue(result.tasks().getList().get(0).canRetry());
        String json=new ObjectMapper().writeValueAsString(result);assertFalse(json.contains("PRIVATE"));assertFalse(json.contains("userId"));assertFalse(json.contains("leaseOwner"));
        assertEquals("9007199254740993",result.tasks().getList().get(0).id());
    }
    @Test void cannotRetryAnotherTenantOrWhenGateIsClosed(){
        assertThrows(ApiException.class,()->service.retry(2L,1));verifyNoInteractions(dao);
        when(shipping.ready()).thenReturn(true);assertThrows(ApiException.class,()->service.retry(2L,1));
        verify(dao).selectScoped(7L,2L);verify(dao,never()).retryPermanent(any(),any(),any());verifyNoInteractions(log);
    }
    @Test void retryUsesExpectedRevisionAndWritesAuditWithoutCallingGateway(){
        when(shipping.ready()).thenReturn(true);when(dao.selectScoped(7L,2L)).thenReturn(task());
        when(dao.retryPermanent(7L,2L,3)).thenReturn(1).thenReturn(0);
        service.retry(2L,3);assertThrows(ApiException.class,()->service.retry(2L,3));
        verify(log,times(1)).log(eq("WECHAT_SHIPPING"),eq("REQUEUE"),eq("WECHAT_SHIPPING_TASK"),eq("2"),anyString(),anyString(),anyString());
        verify(shipping,never()).scheduledSync();verify(shipping,never()).enqueue(any());
    }
    private DmsWechatShippingSyncTask task(){var task=new DmsWechatShippingSyncTask();task.setId(9007199254740993L);task.setTenantId(7L);task.setStatus("PERMANENT");task.setRevision(3);return task;}
}
