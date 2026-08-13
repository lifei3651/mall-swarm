package com.macro.mall.distribution.config;

import com.macro.mall.common.aspect.IdempotentAspect;
import com.macro.mall.common.idempotency.IdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempotentAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdempotentAutoConfiguration.class))
            .withBean(IdempotencyStore.class, () -> mock(IdempotencyStore.class));

    @Test
    void createsIdempotentAspectAfterPersistentStore() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IdempotencyStore.class);
            assertThat(context).hasSingleBean(IdempotentAspect.class);
        });
    }
}
