package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.entity.DmsShopOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class OrderMessageTriggerTest {
    private final MemberMessageService messages=mock(MemberMessageService.class);
    private final OrderRealtimeService realtime=new OrderRealtimeService(new ObjectMapper(),messages);
    @AfterEach void close(){realtime.shutdown();}
    @Test void realOrderAndAfterSaleStatesMapToStablePersonalEvents(){
        DmsShopOrder order=new DmsShopOrder(); order.setId(11L);order.setTenantId(1L);order.setUserId(22L);
        realtime.orderChanged(order,"ORDER_PAID"); realtime.orderChanged(order,"ORDER_SHIPPED");
        realtime.orderChanged(order,"ORDER_RECEIVED"); realtime.orderChanged(order,"ORDER_CANCELLED");
        realtime.orderChanged(order,"AFTER_SALE_APPLIED",33L); realtime.orderChanged(order,"AFTER_SALE_AUDITED",33L);
        realtime.orderChanged(order,"AFTER_SALE_COMPLETED",33L);
        ArgumentCaptor<MemberMessageEvent> events=ArgumentCaptor.forClass(MemberMessageEvent.class);
        verify(messages,times(7)).publish(events.capture());
        assertEquals("ORDER_PAID:11",events.getAllValues().get(0).eventKey());
        assertEquals("AFTER_SALE:33",events.getAllValues().get(4).targetType()+":"+events.getAllValues().get(4).targetId());
        assertEquals("REFUND_RESULT",events.getAllValues().get(6).eventType());
    }
}
