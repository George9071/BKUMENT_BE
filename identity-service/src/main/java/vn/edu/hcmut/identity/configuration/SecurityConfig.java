package vn.edu.hcmut.identity.configuration;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import vn.edu.hcmut.identity.converter.CustomJwtGrantedAuthoritiesConverter;

/**
 * Main security configuration class for the application.
 * Defines authentication, authorization rules, CORS, and JWT decoding mechanisms.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = { "/auth/**", "/accounts/registration" };

    private static final String[] PUBLIC_RESOURCES = {
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/api-docs/**",
        "/swagger-ui.html",
        "/actuator/health",
        /* "/internal/**" */
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
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                // CORS configuration source bean defined below
                .cors(Customizer.withDefaults())

                // Disable CSRF as using stateless JWT authentication
                .csrf(AbstractHttpConfigurer::disable)

                .addFilterBefore(internalApiAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // Define authorization rules
                .authorizeHttpRequests(request -> request
                        // already gated by filter
                        .requestMatchers("/internal/**").permitAll()

                        // Allow OPTIONS requests for preflight CORS checks and specific POST requests
                        .requestMatchers(HttpMethod.OPTIONS, PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()

                        // Allow swagger documentation, actuator health checks, and internal API calls
                        .requestMatchers(PUBLIC_RESOURCES).permitAll()

                        .anyRequest().authenticated())

                // Configure OAuth2 Resource Server to use custom JWT decoder and converter
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                                jwt.decoder(customJwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))

                        // Handle unauthenticated access exceptions with custom JSON response
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint));

        return httpSecurity.build();
    }

    /**
     * Global CORS configuration.
     * Tells the browser which domains, methods, and headers are allowed to interact with this API.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:3000",
                "https://bkument-fe-*-khoale2k4s-projects.vercel.app",
                "https://bkument-fe-git-main-khoale2k4s-projects.vercel.app"));

        // Specify allowed methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));

        // Allow all headers from the client side
        configuration.setAllowedHeaders(List.of("*"));

        configuration.setExposedHeaders(List.of("X-Total-Count", "Authorization"));

        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);

        // Cache the results of the OPTIONS (preflight) request
        configuration.setMaxAge(3600L);

        // Register this configuration for all application paths
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Configures the JWT to GrantedAuthority converter.
     * This maps the "scope" or "role" claims inside the JWT payload into Spring Security Authorities.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new CustomJwtGrantedAuthoritiesConverter());
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
