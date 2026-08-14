package com.freepets.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("Freepets API")
                .description("Freepets 서버 API 명세")
                .version("v1.0.0");

        return new OpenAPI()
                .info(info);
    }
}
