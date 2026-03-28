package vn.edu.hcmut.identity.configuration;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CorsConfig {

    //    @Bean
    //    public CorsFilter corsFilter() {
    //        CorsConfiguration config = new CorsConfiguration();
    //
    //        config.setAllowedOrigins(List.of(
    //                "http://localhost:3000",
    //                "https://bkument-fe-git-main-khoale2k4s-projects.vercel.app"
    //        ));
    //
    //        // Allows of credentials (cookies, authorization headers, etc.).
    //        config.setAllowCredentials(true);
    //
    //        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
    //
    //        // Allows all headers from the client side (Content-Type, Authorization, etc.)
    //        config.setAllowedHeaders(List.of("*"));
    //
    //        // Read custom headers from the response
    //        config.setExposedHeaders(List.of("X-Total-Count", "Authorization"));
    //
    //        // client cache the results of the OPTIONS (preflight) request for 1 hour.
    //        config.setMaxAge(3600L);
    //
    //        // Register configuration for all endpoints (/**)
    //        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    //        source.registerCorsConfiguration("/**", config);
    //
    //        return new CorsFilter(source);
    //    }
}
