package vn.edu.hcmut.communication.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Value("${swagger.host-url:http://localhost:8888/api/v1/communication}")
    private String gateway;

    // the "public" group (excludes internal paths)
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/**")
                .pathsToExclude("/**/internal/**")
                .build();
    }

    // the "internal" group
    @Bean
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("BKUMENT").version("1.0").description("APIs for communication-service"))
                // --- SERVER CONFIGURATION---
                .servers(List.of(
                        new Server().url(gateway).description("API Gateway (Default)"),
                        new Server().url("http://localhost:8083/communication").description("Direct Local (Bypass Gateway)")
                ))

                // --- SECURITY CONFIGURATION---
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(
                                SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Authorization header using the Bearer scheme. "
                                                + "Example: \"Authorization: Bearer {token}\"")));
    }
}
