package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsMemberAssetAccountDao;
import com.macro.mall.distribution.dao.DmsMemberAssetFlowDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.service.impl.MemberAssetServiceImpl;
import com.macro.mall.distribution.vo.BalanceFlowVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberAssetMaskingTest {

    @Test
    void balanceFlowListMasksLoginAccountAndPhoneBeforeReturning() {
        DmsMemberAssetFlowDao flowDao = mock(DmsMemberAssetFlowDao.class);
        BalanceFlowVO row = new BalanceFlowVO();
        row.setMemberName("test123");
        row.setMemberUsername("test123");
        row.setMemberPhone("13800138000");
        when(flowDao.selectBalanceFlowList(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));
        MemberAssetServiceImpl service = new MemberAssetServiceImpl(
                mock(DmsMemberAssetAccountDao.class), flowDao, mock(DmsAgentDao.class),
                mock(DmsShopMemberDao.class), mock(OperationLogService.class));

        List<BalanceFlowVO> result = service.searchBalanceFlows(null, null, null, null, null, null);

        assertEquals("te***23", result.get(0).getMemberName());
        assertEquals("te***23", result.get(0).getMemberUsername());
        assertEquals("138****8000", result.get(0).getMemberPhone());
    }
}
