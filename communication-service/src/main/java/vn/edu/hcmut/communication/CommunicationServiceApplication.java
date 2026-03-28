package vn.edu.hcmut.communication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = {
		"vn.edu.hcmut.communication",
		"vn.edu.hcmut.notification",
		"vn.edu.hcmut.event"
})
@EnableMongoRepositories(basePackages = {
		"vn.edu.hcmut.communication.repository",
		"vn.edu.hcmut.notification.repository"
})
@EnableFeignClients
public class CommunicationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CommunicationServiceApplication.class, args);
	}

}
