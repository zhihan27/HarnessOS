package com.harness.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HarnessAgentCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(HarnessAgentCoreApplication.class, args);
    }

}
