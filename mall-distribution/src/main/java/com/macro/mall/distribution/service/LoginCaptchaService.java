package com.macro.mall.distribution.service;

import com.macro.mall.distribution.vo.LoginCaptchaVO;

public interface LoginCaptchaService {
    LoginCaptchaVO create(String scene);
    void verify(String scene, String captchaId, String captchaCode);
}
