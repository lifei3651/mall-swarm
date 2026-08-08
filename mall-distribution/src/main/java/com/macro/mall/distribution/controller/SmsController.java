package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.sms.AliyunSmsSender;
import com.macro.mall.common.sms.SmsBusinessType;
import com.macro.mall.distribution.dto.SmsCodeRequestDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.SmsVerificationService;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Tag(name = "SmsController", description = "短信验证码")
@RestController
@RequestMapping("/sms")
@RequiredArgsConstructor
public class SmsController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SMS_CODE_KEY_PREFIX = "sms:";
    private static final int SMS_CODE_EXPIRE_MINUTES = 5;
    private static final Set<Integer> SUPPORTED_BIZ_TYPES = SmsBusinessType.SUPPORTED;
    /** 提现、支付及密码类验证码只能发送到当前登录会员绑定的手机号。 */
    private static final Set<Integer> ACCOUNT_BOUND_BIZ_TYPES = Set.of(
            SmsBusinessType.WITHDRAW,
            SmsBusinessType.PAYMENT,
            SmsBusinessType.SET_PAYMENT_PASSWORD,
            SmsBusinessType.RESET_LOGIN_PASSWORD,
            SmsBusinessType.CHANGE_PHONE_CURRENT);

    private final StringRedisTemplate redisTemplate;
    private final AliyunSmsSender aliyunSmsSender;
    private final SmsVerificationService smsVerificationService;
    private final ShopAuthService shopAuthService;

    @Value("${sms.expose-code:false}")
    private boolean exposeCode;

    @Value("${sms.provider-enabled:false}")
    private boolean providerEnabled;

    /** 仅开发/测试环境配置；生产配置必须为空。 */
    @Value("${sms.test-code:}")
    private String testCode;

    /** 同一手机号每日可发送短信验证码的次数上限，防止短信资费被恶意刷取。 */
    @Value("${sms.daily-limit-per-phone:10}")
    private int dailyLimitPerPhone = 10;

    @Operation(summary = "发送验证码")
    @PostMapping("/send")
    public CommonResult<String> sendCode(@RequestBody SmsCodeRequestDTO dto,
                                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        Integer bizType = dto == null || dto.getBizType() == null ? 1 : dto.getBizType();
        if (!SUPPORTED_BIZ_TYPES.contains(bizType)) {
            return CommonResult.failed("短信业务类型不支持");
        }
        String phone = resolvePhone(dto == null ? null : dto.getPhone(), bizType, authorization);
        phone = PhoneNumberUtils.normalize(phone);
        if (!PhoneNumberUtils.isValidMainlandMobile(phone)) {
            return CommonResult.failed("请输入正确的手机号");
        }
        // 检查发送频率（60秒内最多1次）
        String rateLimitKey = SMS_CODE_KEY_PREFIX + "rate:" + phone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateLimitKey))) {
            return CommonResult.failed("发送过于频繁，请稍后再试");
        }

        // 未接短信平台时，开发环境可使用固定测试码；生产环境不会回退为万能验证码。
        boolean useTestCode = !providerEnabled && testCode != null && testCode.matches("\\d{6}");
        String code = useTestCode ? testCode : String.format("%06d", RANDOM.nextInt(1000000));

        String codeKey = SMS_CODE_KEY_PREFIX + bizType + ":" + phone;
        if (providerEnabled) {
            String dailyKey = SMS_CODE_KEY_PREFIX + "daily:" + phone + ":" + LocalDate.now();
            Long sentToday = redisTemplate.opsForValue().increment(dailyKey);
            if (sentToday != null && sentToday == 1L) {
                redisTemplate.expire(dailyKey, 1, TimeUnit.DAYS);
            }
            if (sentToday != null && sentToday > dailyLimitPerPhone) {
                return CommonResult.failed("该手机号今日短信发送次数已达上限，请明天再试");
            }
            aliyunSmsSender.sendVerificationCode(phone, bizType, code);
        }
        // 短信平台接受请求后再保存验证码，发送失败不会留下可用验证码。
        redisTemplate.opsForValue().set(codeKey, code, SMS_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 设置发送频率限制（60秒）
        redisTemplate.opsForValue().set(rateLimitKey, "1", 60, TimeUnit.SECONDS);
        // 新验证码生成后重置该号码此业务类型的错误次数
        smsVerificationService.resetAttempts(phone, bizType);

        if (providerEnabled) {
            return CommonResult.success(null, "发送成功");
        }
        if (exposeCode) {
            return CommonResult.success(code, useTestCode ? "发送成功（测试验证码）" : "发送成功（仅开发环境）");
        }
        redisTemplate.delete(codeKey);
        redisTemplate.delete(rateLimitKey);
        return CommonResult.failed("短信服务未配置");
    }

    /**
     * 支付密码验证码专用入口。业务类型由服务端固定，避免旧页面或不同版本前端传错数字类型。
     */
    @Operation(summary = "发送支付密码验证码")
    @PostMapping("/send/payment-password")
    public CommonResult<String> sendPaymentPasswordCode(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        DmsShopMember member = shopAuthService.requireMember(authorization);
        SmsCodeRequestDTO dto = new SmsCodeRequestDTO();
        dto.setPhone(member.getPhone());
        dto.setBizType(SmsBusinessType.SET_PAYMENT_PASSWORD);
        return sendCode(dto, authorization);
    }

    @Operation(summary = "验证验证码")
    @PostMapping("/verify")
    public CommonResult<Boolean> verifyCode(@RequestBody SmsCodeRequestDTO dto,
                                            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Integer bizType = dto == null || dto.getBizType() == null ? 1 : dto.getBizType();
        if (!SUPPORTED_BIZ_TYPES.contains(bizType)) {
            return CommonResult.failed("短信业务类型不支持");
        }
        String phone = resolvePhone(dto == null ? null : dto.getPhone(), bizType, authorization);
        smsVerificationService.verifyAndConsume(phone, dto == null ? null : dto.getCode(), bizType);
        return CommonResult.success(true, "验证成功");
    }

    private String resolvePhone(String requestedPhone, Integer bizType, String authorization) {
        if (!ACCOUNT_BOUND_BIZ_TYPES.contains(bizType)) return requestedPhone;
        DmsShopMember member = shopAuthService.requireMember(authorization);
        return member.getPhone();
    }
}
