package vn.edu.hcmut.gateway.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import vn.edu.hcmut.gateway.repository.IdentityClient;

@Configuration
public class WebClientConfiguration {

    @Value("${app.services.identity.url:http://localhost:8080/identity}")
    private String identityServiceUrl;

    @Bean
    WebClient webClient(){
        return WebClient.builder()
                .baseUrl("identityServiceUrl")
                .build();
    }

    @Bean
    IdentityClient identityClient(WebClient webClient){
        WebClientAdapter adapter = WebClientAdapter.create(webClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(IdentityClient.class);
    }
}
