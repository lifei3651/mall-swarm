package com.macro.mall.distribution.vo;

import lombok.Data;

@Data
public class LoginCaptchaVO {
    private String captchaId;
    /** 可直接赋给 img.src 的 PNG data URL。 */
    private String image;
}
