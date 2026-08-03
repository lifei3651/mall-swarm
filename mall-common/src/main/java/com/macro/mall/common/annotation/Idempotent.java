package com.macro.mall.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    long timeout() default 5; // 防重复提交时间窗口（秒）
    String message() default "请勿重复提交";
}
