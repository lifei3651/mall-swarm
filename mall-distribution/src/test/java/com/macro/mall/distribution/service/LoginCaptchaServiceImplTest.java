package com.macro.mall.distribution.service;

import com.macro.mall.distribution.service.impl.LoginCaptchaServiceImpl;
import com.macro.mall.distribution.vo.LoginCaptchaVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginCaptchaServiceImplTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    private LoginCaptchaServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new LoginCaptchaServiceImpl(redisTemplate);
    }

    @Test
    void createsRasterCaptchaWithoutSvgPlaintext() throws Exception {
        LoginCaptchaVO captcha = service.create("shop");

        assertNotNull(captcha.getCaptchaId());
        assertTrue(captcha.getImage().startsWith("data:image/png;base64,"));
        assertFalse(captcha.getImage().contains("<text"));
        byte[] bytes = Base64.getDecoder().decode(captcha.getImage().substring(captcha.getImage().indexOf(',') + 1));
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image);
        assertEquals(140, image.getWidth());
        assertEquals(48, image.getHeight());

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(anyString(), anyString(), ttl.capture());
        assertEquals(Duration.ofMinutes(2), ttl.getValue());
    }
}
