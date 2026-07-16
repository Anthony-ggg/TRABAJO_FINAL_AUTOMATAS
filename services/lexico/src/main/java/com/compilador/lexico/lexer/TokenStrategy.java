package com.compilador.lexico.lexer;

import com.compilador.shared.model.Token;
import java.util.List;

public interface TokenStrategy {
    List<Token> classify(String input);
    String getNombre();
}
