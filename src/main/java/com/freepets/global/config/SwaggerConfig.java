package com.freepets.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("Freepets API")
                .description("Freepets 서버 API 명세")
                .version("v1.0.0");

        SecurityScheme bearerAuthScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(info)
                .components(new Components().addSecuritySchemes(BEARER_AUTH_SCHEME_NAME, bearerAuthScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME_NAME));
    }
}
