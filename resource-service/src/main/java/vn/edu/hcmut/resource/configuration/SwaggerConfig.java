package vn.edu.hcmut.resource.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class SwaggerConfig {

    @Value("${swagger.host-url}")
    private String hostUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Resource Service API")
                        .version("1.0")
                        .description("Resource management service for documents and posts"))
                .addServersItem(new Server().url(hostUrl));
    }
}
