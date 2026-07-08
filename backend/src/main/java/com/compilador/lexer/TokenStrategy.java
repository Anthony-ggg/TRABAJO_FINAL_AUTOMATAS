package com.compilador.lexer;

import com.compilador.model.Token;
import java.util.List;

public interface TokenStrategy {
    List<Token> classify(String input);
    String getNombre();
}
