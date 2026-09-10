package com.mini.novel.application;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@EnableScheduling
@MapperScan("com.mini.novel.**.mapper")
@SpringBootApplication(scanBasePackages = "com.mini.novel")
public class MiniNovelApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniNovelApplication.class, args);
    }

    /**
     * 显式补齐 Spring Boot 默认的 applicationTaskExecutor。
     *
     * 背景：media 模块的 mediaProcessExecutor（Executor 类型）会让
     * TaskExecutionAutoConfiguration 的 @ConditionalOnMissingBean(Executor.class)
     * 判定“已存在 Executor bean”而跳过默认 applicationTaskExecutor 的创建，
     * 导致 crawler 模块 CrawlerTaskServiceImpl / CrawlerExecutionServiceImpl 通过
     * @Qualifier("applicationTaskExecutor") 注入时装配失败、应用启动崩溃。
     * 这里显式定义同名 bean，保持与 Spring Boot 默认线程池一致的语义。
     */
    @Bean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setThreadNamePrefix("app-task-");
        executor.initialize();
        return executor;
    }
}
