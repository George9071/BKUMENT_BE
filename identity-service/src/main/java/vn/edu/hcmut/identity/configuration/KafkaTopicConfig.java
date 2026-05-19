package vn.edu.hcmut.identity.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic accountDeletedTopic() {
        return TopicBuilder.name("account-deleted-events")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
