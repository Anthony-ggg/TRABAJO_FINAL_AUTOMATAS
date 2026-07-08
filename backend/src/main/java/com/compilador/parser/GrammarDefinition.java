package com.compilador.parser;

import java.util.*;

public class GrammarDefinition {
    public static final String GRAMMAR = """
        GRAMÁTICA DEL MINI-COMPILADOR SQL EN ESPAÑOL
        
        <query> ::= <select_query> | <insert_query> | <update_query> | <delete_query>
        
        <select_query> ::= SELECCIONAR <columns> DESDE <table> [CUANDO <condition>]
        <insert_query> ::= INSERTAR EN <table> VALORES "(" <values> ")"
        <update_query> ::= MODIFICAR <table> ESTABLECER <assignments> [CUANDO <condition>]
        <delete_query> ::= ELIMINAR DESDE <table> [CUANDO <condition>]
        
        <columns> ::= <column> ("," <column>)*
        <column> ::= IDENTIFIER | "*"
        <table> ::= IDENTIFIER
        <condition> ::= <expression> (("Y" | "O") <expression>)*
        <expression> ::= IDENTIFIER OPERATOR <value>
        <values> ::= <value> ("," <value>)*
        <assignments> ::= <assignment> ("," <assignment>)*
        <assignment> ::= IDENTIFIER "=" <value>
        <value> ::= NUMBER | STRING | IDENTIFIER
        
        TERMINALES:
        - PALABRA_RESERVADA: SELECCIONAR, INSERTAR, MODIFICAR, ELIMINAR, DESDE, CUANDO, EN, VALORES, ESTABLECER, Y, O
        - IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_]*
        - NUMBER: \\d+(\\.\\d+)?
        - STRING: '[^']*'
        - OPERATOR: =, >, <, >=, <=, <>, !=
        - SYMBOL: (, ), ,, *
        """;

    public static final String SYNTAX_RULES = """
        REGLAS DE VALIDACIÓN SINTÁCTICA:
        1. Toda consulta debe comenzar con una palabra reservada (SELECCIONAR, INSERTAR, MODIFICAR, ELIMINAR)
        2. SELECCIONAR debe ir seguido de columnas y luego DESDE <tabla>
        3. INSERTAR debe seguir el patrón: INSERTAR EN <tabla> VALORES (<valores>)
        4. MODIFICAR debe seguir: MODIFICAR <tabla> ESTABLECER <asignaciones>
        5. ELIMINAR debe seguir: ELIMINAR DESDE <tabla>
        6. CUANDO es opcional y debe ir seguido de una condición
        7. Las condiciones usan OPERADOR para comparar IDENTIFIER con VALUE
        8. Y/O conectan múltiples condiciones
        """;

    public static final String SYSTEM_PROMPT = """
        Eres un analizador sintáctico para un compilador SQL en español.
        Revisa si la siguiente secuencia de tokens sigue la gramática definida.
        
        %s
        
        %s
        
        Responde en formato JSON con los siguientes campos:
        {
            "valido": true/false,
            "errores": ["lista de errores sintácticos encontrados"],
            "tipo_consulta": "SELECCIONAR|INSERTAR|MODIFICAR|ELIMINAR|DESCONOCIDO",
            "descripcion": "descripción del análisis"
        }
        
        Solo responde con el JSON, sin explicaciones adicionales.
        """.formatted(GRAMMAR, SYNTAX_RULES);
}
