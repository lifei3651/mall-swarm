package com.macro.mall.distribution.service;

import com.macro.mall.distribution.config.RedisConfig;
import com.macro.mall.distribution.config.ScheduleTask;
import com.macro.mall.distribution.entity.DmsBonusCalculationTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:bonus_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@EnableAutoConfiguration(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        RedisConfig.class,
        ScheduleTask.class
}))
class BonusCalculationConcurrencyTest {

    @Autowired private BonusCalculationTaskService taskService;

    @Test
    void concurrentEnqueueCreatesOnlyOneTaskPerOrder() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<DmsBonusCalculationTask> first = executor.submit(() -> enqueue(ready, start));
            Future<DmsBonusCalculationTask> second = executor.submit(() -> enqueue(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            DmsBonusCalculationTask firstTask = first.get(10, TimeUnit.SECONDS);
            DmsBonusCalculationTask secondTask = second.get(10, TimeUnit.SECONDS);
            assertEquals(firstTask.getId(), secondTask.getId());
            assertEquals(1, taskService.listTasks(null, 990001L).size());
        } finally {
            executor.shutdownNow();
        }
    }

    private DmsBonusCalculationTask enqueue(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return taskService.enqueue(1L, 1L, 990001L, "CONCURRENT-BONUS-990001",
                new BigDecimal("100.00"), 1001L, "并发测试会员");
    }
}
