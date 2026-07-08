package com.compilador.config;

import com.compilador.lexer.AutomataStrategy;
import com.compilador.lexer.LLMStrategy;
import com.compilador.lexer.LexerContext;
import com.compilador.lexer.TokenStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaConfig {

    @Bean
    public LexerContext lexerContext() {
        return new LexerContext();
    }
}
