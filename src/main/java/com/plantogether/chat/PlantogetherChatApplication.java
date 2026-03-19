package com.plantogether.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class PlantogetherChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlantogetherChatApplication.class, args);
    }
}
