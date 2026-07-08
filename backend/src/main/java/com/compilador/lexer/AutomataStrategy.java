package com.compilador.lexer;

import com.compilador.model.Token;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

@Component
public class AutomataStrategy implements TokenStrategy {

    private static final List<TokenPattern> PATTERNS = List.of(
        new TokenPattern("PALABRA_RESERVADA",
            "\\b(SELECCIONAR|INSERTAR|MODIFICAR|ELIMINAR|DESDE|CUANDO|EN|VALORES|ESTABLECER|Y|O)\\b", false),
        new TokenPattern("NUMERO", "\\b\\d+(\\.\\d+)?\\b", false),
        new TokenPattern("CADENA", "'[^']*'", false),
        new TokenPattern("OPERADOR", ">=|<=|<>|!=|=|>|<", false),
        new TokenPattern("SIMBOLO", "[,()*]", false),
        new TokenPattern("IDENTIFICADOR", "\\b[a-zA-Z_][a-zA-Z0-9_]*\\b", false),
        new TokenPattern("ESPACIO", "\\s+", true)
    );

    private static final Map<String, String> KEYWORDS_MAP = new HashMap<>();
    static {
        KEYWORDS_MAP.put("SELECCIONAR", "SELECCIONAR");
        KEYWORDS_MAP.put("INSERTAR", "INSERTAR");
        KEYWORDS_MAP.put("MODIFICAR", "MODIFICAR");
        KEYWORDS_MAP.put("ELIMINAR", "ELIMINAR");
        KEYWORDS_MAP.put("DESDE", "DESDE");
        KEYWORDS_MAP.put("CUANDO", "CUANDO");
        KEYWORDS_MAP.put("EN", "EN");
        KEYWORDS_MAP.put("VALORES", "VALORES");
        KEYWORDS_MAP.put("ESTABLECER", "ESTABLECER");
        KEYWORDS_MAP.put("Y", "Y");
        KEYWORDS_MAP.put("O", "O");
    }

    @Override
    public List<Token> classify(String input) {
        List<Token> tokens = new ArrayList<>();
        int pos = 0;
        String remaining = input;

        while (!remaining.isEmpty()) {
            boolean matched = false;
            for (TokenPattern tp : PATTERNS) {
                Matcher m = tp.pattern().matcher(remaining);
                if (m.find() && m.start() == 0) {
                    String value = m.group();
                    if (!tp.skip()) {
                        String tipo = tp.type();
                        if ("PALABRA_RESERVADA".equals(tipo)) {
                            tipo = KEYWORDS_MAP.get(value.toUpperCase());
                        }
                        String origen = "AFD";
                        tokens.add(new Token(tipo, value, origen, pos));
                    }
                    pos += value.length();
                    remaining = remaining.substring(value.length());
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                tokens.add(new Token("DESCONOCIDO", String.valueOf(remaining.charAt(0)), "AFD", pos));
                pos++;
                remaining = remaining.substring(1);
            }
        }
        return tokens;
    }

    @Override
    public String getNombre() {
        return "AFD";
    }

    private record TokenPattern(String type, String regex, boolean skip) {
        private Pattern pattern() { return Pattern.compile(regex, Pattern.CASE_INSENSITIVE); }
    }
}
