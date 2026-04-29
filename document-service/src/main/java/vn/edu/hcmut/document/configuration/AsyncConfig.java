package vn.edu.hcmut.document.configuration;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "graphExecutor")
    public Executor graphExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("Neo4jAsync-");
        executor.initialize();
        return executor;
    }


    /**
     * Executor for the deep AI processing background job
     * ({@link vn.edu.hcmut.document.service.DocumentAsyncService#runBackgroundAiProcess}).
     * Tune these values based on:
     *   - Average AI processing time (longer -> larger queue).
     *   - Available CPU / memory on the server.
     *   - Acceptable upload response latency under burst load.
     */
    @Bean("aiDeepProcessExecutor")
    public Executor aiDeepProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-deep-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }


}
