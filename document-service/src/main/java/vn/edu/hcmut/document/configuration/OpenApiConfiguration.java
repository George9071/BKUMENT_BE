package vn.edu.hcmut.document.configuration;

import java.util.List;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfiguration {
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Value("${swagger.gateway-url}")
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
        return GroupedOpenApi.builder().group("internal").pathsToMatch("/**").build();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        System.out.println("Gateway: " + gateway);
        return new OpenAPI()
                .info(new Info().title("BKUMENT").version("1.0").description("APIs for Document-service"))
                // --- SERVER CONFIGURATION---
                .servers(List.of(new Server().url(gateway).description("API Gateway (Default)")))

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
