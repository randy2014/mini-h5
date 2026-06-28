package com.mini.novel.application;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@MapperScan("com.mini.novel.**.mapper")
@SpringBootApplication(scanBasePackages = "com.mini.novel")
public class MiniNovelApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniNovelApplication.class, args);
    }
}
