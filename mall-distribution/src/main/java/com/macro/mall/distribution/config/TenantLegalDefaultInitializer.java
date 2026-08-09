package com.macro.mall.distribution.config;

import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.service.TenantLegalTemplateSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 在应用启动时只补齐空白协议和资料默认值，绝不覆盖客户已编辑内容。 */
@Component
@RequiredArgsConstructor
public class TenantLegalDefaultInitializer implements ApplicationRunner {

    private final DmsTenantDao tenantDao;
    private final TenantLegalTemplateSupport templateSupport;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        for (DmsTenant tenant : tenantDao.selectAll()) {
            if (templateSupport.applyDefaults(tenant)) {
                tenantDao.update(tenant);
            }
        }
    }
}
