package com.macro.mall.distribution;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Import;
import com.macro.mall.common.sms.AliyunSmsProperties;
import com.macro.mall.common.sms.AliyunSmsSender;

/**
 * 分销分佣系统启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.macro.mall.distribution.dao")
@EnableScheduling
@Import({AliyunSmsProperties.class, AliyunSmsSender.class})
public class MallDistributionApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallDistributionApplication.class, args);
    }
}
