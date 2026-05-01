package vn.edu.hcmut.blog.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's @Scheduled annotation processing for the blog-service.
 */

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
