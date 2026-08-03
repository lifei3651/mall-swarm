package com.macro.mall.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {
    String tableAlias() default ""; // 表别名
    String deptColumn() default "dept_id"; // 部门字段
    String userColumn() default "create_by"; // 用户字段
}
