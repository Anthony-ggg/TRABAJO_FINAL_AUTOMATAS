package com.compilador.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.compilador.orchestrator")
public class OrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
