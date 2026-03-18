package vn.edu.hcmut.identity.configuration;

import java.util.HashSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.identity.constant.UserRole;
import vn.edu.hcmut.identity.entity.Account;
import vn.edu.hcmut.identity.repository.AccountRepository;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitConfig {

    PasswordEncoder passwordEncoder;

    @NonFinal
    @Value("${app.init.admin.username}")
    String ADMIN_USERNAME;

    @NonFinal
    @Value("${app.init.admin.password}")
    String ADMIN_PASSWORD;

    @Bean
    @ConditionalOnProperty(
            prefix = "spring",
            value = "datasource.driverClassName",
            havingValue = "org.postgresql.Driver")
    ApplicationRunner applicationRunner(AccountRepository accountRepository) {
        return args -> {
            if (accountRepository.findByUsername(ADMIN_USERNAME).isEmpty()) {

                var roles = new HashSet<UserRole>();
                roles.add(UserRole.ADMIN);

                Account account = Account.builder()
                        .username(ADMIN_USERNAME)
                        .password(passwordEncoder.encode(ADMIN_PASSWORD))
                        .roles(roles)
                        .build();

                accountRepository.save(account);
                log.warn("admin account has been created with default password: admin, please change it");
            }
        };
    }
}
