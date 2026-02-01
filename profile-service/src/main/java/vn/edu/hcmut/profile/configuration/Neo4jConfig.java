package vn.edu.hcmut.profile.configuration;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class Neo4jConfig {

    @Bean("neo4jTransactionManager")
    public PlatformTransactionManager neo4jTransactionManager(
            Driver driver, DatabaseSelectionProvider databaseNameProvider) {
        return new Neo4jTransactionManager(driver, databaseNameProvider);
    }
}
