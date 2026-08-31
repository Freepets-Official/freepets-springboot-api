package com.freepets.global.config;

import io.swagger.v3.core.jackson.TypeNameResolver;
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

    static {
        // DTO 이너클래스에 DTO 접미사를 안 붙이는 컨벤션(CLAUDE.md) 때문에 여러 도메인이
        // CreateRequest/UpdateRequest 같은 같은 이름을 쓴다. Swagger는 기본적으로 클래스
        // 단순 이름만으로 컴포넌트 스키마를 등록해서, 서로 다른 도메인의 DTO가 같은 스키마
        // 이름으로 충돌하면 나중에 스캔된 쪽이 먼저 것을 덮어써버린다 — 예: /api/v1/ai/check가
        // PetCheckRequestDTO.CreateRequest 대신 PetRequestDTO.CreateRequest(반려동물 등록)
        // 예시를 보여주던 문제. 전체 경로(FQN)로 스키마 이름을 지으면 충돌 자체가 사라진다.
        TypeNameResolver.std.setUseFqn(true);
    }

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
