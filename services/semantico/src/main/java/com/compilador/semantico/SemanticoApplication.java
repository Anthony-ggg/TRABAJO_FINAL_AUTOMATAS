package com.compilador.semantico;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.compilador.semantico")
public class SemanticoApplication {
    public static void main(String[] args) {
        SpringApplication.run(SemanticoApplication.class, args);
    }
}
