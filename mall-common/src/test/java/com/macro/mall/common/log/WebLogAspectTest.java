package com.macro.mall.common.log;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebLogAspectTest {

    @Test
    void pathVariablesUseTheSameSanitizerAsRequestParameters() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("member", String.class, Long.class);

        Object result = new WebLogAspect().getParameter(method, new Object[]{"13812345678", 9L});

        assertEquals(List.of(Map.of("memberKey", "1**********"), Map.of("id", 9L)), result);
    }

    static class SampleController {
        void member(@PathVariable("memberKey") String memberKey, @PathVariable("id") Long id) {
        }
    }
}
