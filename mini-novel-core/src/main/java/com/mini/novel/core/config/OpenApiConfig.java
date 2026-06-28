package com.mini.novel.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI miniNovelOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Mini Novel API")
                .version("0.1.0")
                .description("H5 小说应用、后台管理、爬虫和 VIP 管理接口"));
    }
}
