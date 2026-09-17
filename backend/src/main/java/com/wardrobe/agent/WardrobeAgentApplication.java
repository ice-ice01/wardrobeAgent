package com.wardrobe.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
/**
 * 应用启动入口。EnableAsync 支持 Mock 试穿后台任务；排除默认用户服务是因为项目使用自定义 JWT 身份。
 */
public class WardrobeAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(WardrobeAgentApplication.class, args);
    }
}
