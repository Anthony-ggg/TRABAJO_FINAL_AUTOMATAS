package com.compilador.lexico;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.compilador.lexico")
public class LexicoApplication {
    public static void main(String[] args) {
        SpringApplication.run(LexicoApplication.class, args);
    }
}
