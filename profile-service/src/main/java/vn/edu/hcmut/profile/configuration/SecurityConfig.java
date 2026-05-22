package vn.edu.hcmut.profile.configuration;

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

    private static final String[] PUBLIC_ENDPOINTS = { };

    private static final String[] PUBLIC_INFRASTRUCTURE = {
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/api-docs/**",
        "/swagger-ui.html",
        "/actuator/health",
        "/error",
    };

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/search/**",
            "/universities/**"
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
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers(PUBLIC_INFRASTRUCTURE).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                // Configure OAuth2 Resource Server to use custom JWT decoder and converter
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                                jwt.decoder(customJwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))

                        // // Handle unauthenticated access exceptions with custom JSON response
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
