package vn.edu.hcmut.lms.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Main security configuration class for the application.
 * Defines authentication, authorization rules, CORS, and JWT decoding mechanisms.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/classes/search",
            "/classes/[a-zA-Z0-9-]+",
            "/classes/tutors/[a-zA-Z0-9-]+",
            "/admin/**", // synchronize tasks
    };

    private static final String[] PUBLIC_RESOURCES = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api-docs/**",
            "/swagger-ui.html",
            "/actuator/health"
    };

    private static final String[] INTERNAL_ENDPOINTS = {
            "/classes/internal/**",
            "/subjects/topics/internal/**",
            "/internal/**"
    };

    private final CustomJwtDecoder customJwtDecoder;
    private final InternalApiAuthFilter internalApiAuthFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public SecurityConfig(
            CustomJwtDecoder customJwtDecoder,
            InternalApiAuthFilter internalApiAuthFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.customJwtDecoder = customJwtDecoder;
        this.internalApiAuthFilter = internalApiAuthFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(internalApiAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(request -> request
                        // The internal API filter verifies X-Internal-Api-Key before these reach authorization.
                        .requestMatchers(INTERNAL_ENDPOINTS).permitAll()

                        // Allow OPTIONS requests for preflight CORS checks and specific POST requests
                        .requestMatchers(HttpMethod.OPTIONS, PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/admin/**").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_ENDPOINTS).permitAll()

                        // Allow swagger documentation, actuator health checks, and internal API calls
                        .requestMatchers(PUBLIC_RESOURCES).permitAll()

                        // All other endpoints require authentication
                        .anyRequest().authenticated())

                // Configure OAuth2 Resource Server to use custom JWT decoder and converter
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                                jwt.decoder(customJwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))

                        // Handle unauthenticated access exceptions with custom JSON response
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint));
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
