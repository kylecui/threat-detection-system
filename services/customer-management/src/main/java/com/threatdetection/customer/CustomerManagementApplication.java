package com.threatdetection.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CustomerManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerManagementApplication.class, args);
    }
}
