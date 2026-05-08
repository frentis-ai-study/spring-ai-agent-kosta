package com.kosta.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpringAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiAgentApplication.class, args);
    }
}
