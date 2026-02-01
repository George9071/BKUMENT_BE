package vn.edu.hcmut.lms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class LmsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LmsServiceApplication.class, args);
	}

}
