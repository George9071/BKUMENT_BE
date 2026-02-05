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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import vn.edu.hcmut.identity.converter.CustomJwtGrantedAuthoritiesConverter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {"/auth/**", "/accounts/registration"};

    private final CustomJwtDecoder customJwtDecoder;

    public SecurityConfig(CustomJwtDecoder customJwtDecoder) {
        this.customJwtDecoder = customJwtDecoder;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(Customizer.withDefaults()) // Uses the corsConfigurationSource bean defined below
                .csrf(AbstractHttpConfigurer::disable)

                // Define authorization rules for incoming HTTP requests
                .authorizeHttpRequests(request -> request
                        // Allow OPTIONS requests for preflight CORS checks
                        .requestMatchers(HttpMethod.OPTIONS, PUBLIC_ENDPOINTS)
                        .permitAll()

                        // Allow POST requests for authentication-related endpoints
                        .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS)
                        .permitAll()

                        // Allow unauthenticated access to certain public routes
                        .requestMatchers("/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/api-docs/**")
                        .permitAll()
                        .requestMatchers("/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/actuator/health")
                        .permitAll()

                        .requestMatchers("/internal/**").permitAll()

                        // All other endpoints require authentication
                        .anyRequest()
                        .authenticated())

                // Configure JWT-based OAuth2 Resource Server authentication
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                                jwt.decoder(customJwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))

                        // Define custom entry point for unauthorized access handling
                        .authenticationEntryPoint(new JwtAuthenticationEntryPoint()));
        return httpSecurity.build();
    }

    // --- NEW CORS CONFIGURATION BEAN ---
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. Add specific allowed origins
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "https://bkument-fe-git-main-khoale2k4s-projects.vercel.app" // Removed trailing slash for exact Origin
                // match
                ));

        // 2. Add allowed methods (GET, POST, etc.)
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));

        // 3. Add allowed headers
        configuration.setAllowedHeaders(List.of("*")); // Or specific headers like "Authorization", "Content-Type"

        // 4. Allow credentials (cookies/auth headers) if needed
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    // -----------------------------------

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
