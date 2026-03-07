package vn.edu.hcmut.lms.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Main security configuration class for the application.
 * Defines authentication, authorization rules, CORS, and JWT decoding mechanisms.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {


    private static final String[] PUBLIC_ENDPOINTS = {
            "/admin/**", // synchronize tasks
    };

    private static final String[] PUBLIC_RESOURCES = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api-docs/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/internal/**", // internal microservice-to-microservice communication
    };

    private final CustomJwtDecoder customJwtDecoder;

    public SecurityConfig(CustomJwtDecoder customJwtDecoder) {
        this.customJwtDecoder = customJwtDecoder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request -> request
                        // Allow OPTIONS requests for preflight CORS checks and specific POST requests
                        .requestMatchers(HttpMethod.OPTIONS, PUBLIC_ENDPOINTS)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS)
                        .permitAll()

                        // Allow swagger documentation, actuator health checks, and internal API calls
                        .requestMatchers(PUBLIC_RESOURCES)
                        .permitAll()

                        // All other endpoints require authentication
                        .anyRequest()
                        .authenticated())

                // Configure OAuth2 Resource Server to use custom JWT decoder and converter
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                                jwt.decoder(customJwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))

                        // Handle unauthenticated access exceptions with custom JSON response
                        .authenticationEntryPoint(new JwtAuthenticationEntryPoint()));
        return httpSecurity.build();
    }

    /**
     * Configures the JWT to GrantedAuthority converter.
     * This maps the "scope" or "role" claims inside the JWT payload into Spring Security Authorities.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

        return jwtAuthenticationConverter;
    }
}
