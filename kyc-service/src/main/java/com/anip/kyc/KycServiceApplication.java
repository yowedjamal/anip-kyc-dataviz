package com.anip.kyc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.ComponentScan;

/**
 * Application entry used for tests and bootstrapping Spring context.
 * Minimal class so @SpringBootTest can discover a @SpringBootConfiguration.
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.anip.kyc.repository")
@EntityScan(basePackages = "com.anip.kyc.models")
@ComponentScan(basePackages = "com.anip.kyc")
public class KycServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(KycServiceApplication.class, args);
	}
}