package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.vo.AdminStepUpTokenVO;

public interface AdminStepUpService {
    AdminStepUpTokenVO issue(DmsAdminUser admin, String method, String path);

    void consume(DmsAdminUser admin, String method, String path, String token);
}
