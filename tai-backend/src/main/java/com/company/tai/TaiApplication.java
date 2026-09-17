package com.company.tai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TaiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaiApplication.class, args);
    }
}
