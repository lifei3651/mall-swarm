package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.service.LoginCaptchaService;
import com.macro.mall.distribution.vo.LoginCaptchaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/** 一次性、两分钟有效的登录图形验证码。 */
@Service
@RequiredArgsConstructor
public class LoginCaptchaServiceImpl implements LoginCaptchaService {
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final Duration TTL = Duration.ofMinutes(2);
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    @Override
    public LoginCaptchaVO create(String scene) {
        String normalizedScene = normalizeScene(scene);
        String id = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode();
        redisTemplate.opsForValue().set(key(normalizedScene, id), code, TTL);
        LoginCaptchaVO vo = new LoginCaptchaVO();
        vo.setCaptchaId(id);
        vo.setImage("data:image/png;base64," + Base64.getEncoder().encodeToString(png(code)));
        return vo;
    }

    @Override
    public void verify(String scene, String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            Asserts.fail("请输入图形验证码");
        }
        String cacheKey = key(normalizeScene(scene), captchaId);
        String expected = redisTemplate.opsForValue().get(cacheKey);
        // 无论正确与否均作废，避免同一验证码被反复尝试。
        redisTemplate.delete(cacheKey);
        if (expected == null || !expected.equalsIgnoreCase(captchaCode.trim())) {
            Asserts.fail("图形验证码错误或已过期，请刷新后重试");
        }
    }

    private String normalizeScene(String scene) {
        if (!"admin".equals(scene) && !"shop".equals(scene)) Asserts.fail("验证码场景无效");
        return scene;
    }

    private String key(String scene, String id) { return "login-captcha:" + scene + ":" + id; }

    private String randomCode() {
        StringBuilder result = new StringBuilder(4);
        for (int i = 0; i < 4; i++) result.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return result.toString();
    }

    private byte[] png(String code) {
        int width = 140;
        int height = 48;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(248, 250, 252));
            graphics.fillRect(0, 0, width, height);

            for (int i = 0; i < 6; i++) {
                graphics.setColor(new Color(170 + random.nextInt(55), 175 + random.nextInt(50), 180 + random.nextInt(45)));
                graphics.drawLine(random.nextInt(width), random.nextInt(height), random.nextInt(width), random.nextInt(height));
            }
            for (int i = 0; i < 55; i++) {
                graphics.setColor(new Color(150 + random.nextInt(80), 150 + random.nextInt(80), 150 + random.nextInt(80)));
                graphics.fillRect(random.nextInt(width), random.nextInt(height), 1 + random.nextInt(2), 1 + random.nextInt(2));
            }

            Color[] colors = {
                    new Color(15, 118, 110), new Color(29, 78, 216),
                    new Color(154, 52, 18), new Color(76, 29, 149)
            };
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            for (int i = 0; i < code.length(); i++) {
                int x = 15 + i * 29;
                int y = 34 + random.nextInt(7);
                double angle = Math.toRadians(random.nextInt(25) - 12);
                AffineTransform original = graphics.getTransform();
                graphics.rotate(angle, x, y);
                graphics.setColor(colors[random.nextInt(colors.length)]);
                graphics.drawString(String.valueOf(code.charAt(i)), x, y);
                graphics.setTransform(original);
            }
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("图形验证码生成失败", exception);
        }
    }
}
