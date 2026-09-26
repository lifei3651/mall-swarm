package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalRefundRecoveryBatchTest {
    @Test
    void oneProviderFailureDoesNotPreventAnotherRefundFromRecovering() {
        DmsShopAfterSaleDao afterSaleDao = mock(DmsShopAfterSaleDao.class);
        ExternalRefundCoordinator coordinator = mock(ExternalRefundCoordinator.class);
        ShopAfterSaleServiceImpl service = mock(ShopAfterSaleServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "afterSaleDao", afterSaleDao);
        ReflectionTestUtils.setField(service, "externalRefundCoordinator", coordinator);
        ReflectionTestUtils.setField(service, "externalRefundRetryCursors", new ConcurrentHashMap<>());
        when(afterSaleDao.selectProcessingExternalRefunds(any(LocalDateTime.class),
                nullable(LocalDateTime.class), nullable(Long.class), anyInt()))
                .thenReturn(List.of(candidate(1001L, 3), candidate(1002L, 2)));
        doThrow(new IllegalStateException("channel unavailable")).when(coordinator).process(1001L);
        DmsShopAfterSale completed = new DmsShopAfterSale();
        completed.setStatus(1);
        when(afterSaleDao.selectById(1002L)).thenReturn(completed);

        assertEquals(1, service.reconcileProcessingExternalRefunds(1000));
        verify(coordinator).process(1001L);
        verify(coordinator).process(1002L);
    }

    @Test
    void boundedScansAdvancePastPermanentFailuresAndWrapWithoutDuplicateCalls() {
        DmsShopAfterSaleDao afterSaleDao = mock(DmsShopAfterSaleDao.class);
        ExternalRefundCoordinator coordinator = mock(ExternalRefundCoordinator.class);
        ShopAfterSaleServiceImpl service = mock(ShopAfterSaleServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "afterSaleDao", afterSaleDao);
        ReflectionTestUtils.setField(service, "externalRefundCoordinator", coordinator);
        ReflectionTestUtils.setField(service, "externalRefundRetryCursors", new ConcurrentHashMap<>());
        List<DmsShopAfterSale> pending = List.of(candidate(1001L, 8), candidate(1002L, 7),
                candidate(1003L, 6), candidate(1004L, 5), candidate(1005L, 4));
        when(afterSaleDao.selectProcessingExternalRefunds(any(LocalDateTime.class),
                nullable(LocalDateTime.class), nullable(Long.class), anyInt()))
                .thenAnswer(call -> {
                    LocalDateTime cursorTime = call.getArgument(1);
                    Long cursorId = call.getArgument(2);
                    int limit = call.getArgument(3);
                    return pending.stream().filter(sale -> cursorTime == null
                                    || sale.getUpdateTime().isAfter(cursorTime)
                                    || (sale.getUpdateTime().equals(cursorTime) && sale.getId() > cursorId))
                            .sorted(Comparator.comparing(DmsShopAfterSale::getUpdateTime)
                                    .thenComparing(DmsShopAfterSale::getId))
                            .limit(limit).toList();
                });
        List<Long> attempted = new ArrayList<>();
        org.mockito.Mockito.doAnswer(call -> {
            attempted.add(call.getArgument(0));
            throw new IllegalStateException("still pending");
        }).when(coordinator).process(any(Long.class));

        assertEquals(0, service.reconcileProcessingExternalRefunds(2));
        assertEquals(0, service.reconcileProcessingExternalRefunds(2));
        assertEquals(0, service.reconcileProcessingExternalRefunds(2));
        assertEquals(List.of(1001L, 1002L, 1003L, 1004L, 1005L, 1001L), attempted);
    }

    private static DmsShopAfterSale candidate(Long id, int minutesAgo) {
        DmsShopAfterSale sale = new DmsShopAfterSale();
        sale.setId(id);
        sale.setUpdateTime(LocalDateTime.now().minusMinutes(minutesAgo));
        return sale;
    }
}
