package vn.edu.hcmut.social.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] SWAGGER_ENDPOINTS = {
        "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-resources/**", "/webjars/**",
    };

    private static final String[] PUBLIC_ENDPOINTS = {
        "/ratings/internal/**", "/internal/**",
    };

    private final InternalApiAuthFilter internalApiAuthFilter;

    public SecurityConfig(InternalApiAuthFilter internalApiAuthFilter) {
        this.internalApiAuthFilter = internalApiAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(internalApiAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.requestMatchers(SWAGGER_ENDPOINTS)
                        .permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
