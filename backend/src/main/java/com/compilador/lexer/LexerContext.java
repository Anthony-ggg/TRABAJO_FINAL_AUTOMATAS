package com.compilador.lexer;

import com.compilador.model.Token;
import java.util.List;

public class LexerContext {
    private TokenStrategy strategy;

    public void setStrategy(TokenStrategy strategy) {
        this.strategy = strategy;
    }

    public List<Token> executeStrategy(String input) {
        if (strategy == null) {
            throw new IllegalStateException("No hay estrategia definida");
        }
        return strategy.classify(input);
    }

    public String getStrategyName() {
        return strategy != null ? strategy.getNombre() : "NONE";
    }
}
