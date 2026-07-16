package com.compilador.sintactico;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.compilador.sintactico")
public class SintacticoApplication {
    public static void main(String[] args) {
        SpringApplication.run(SintacticoApplication.class, args);
    }
}
