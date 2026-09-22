package com.tienphat.ticketingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.tienphat")
@EnableJpaRepositories(basePackages = "com.tienphat.infrastructure")
@EntityScan(basePackages = "com.tienphat.infrastructure")
class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
