package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderRealtimeServiceTest {
    private final OrderRealtimeService service = new OrderRealtimeService(new ObjectMapper());

    @AfterEach
    void cleanup() {
        service.shutdown();
    }

    @Test
    void limitsDuplicateConnectionsForTheSameAuthenticatedPrincipal() {
        ReflectionTestUtils.setField(service, "maxConnections", 10);
        ReflectionTestUtils.setField(service, "maxConnectionsPerPrincipal", 1);
        service.subscribeMember(88L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.subscribeMember(88L));

        assertEquals(429, error.getStatusCode().value());
        service.subscribeMember(89L);
    }

    @Test
    void enforcesTheGlobalConnectionCeiling() {
        ReflectionTestUtils.setField(service, "maxConnections", 1);
        ReflectionTestUtils.setField(service, "maxConnectionsPerPrincipal", 5);
        service.subscribeAdmin(1L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.subscribeMember(99L));

        assertEquals(429, error.getStatusCode().value());
    }
}
