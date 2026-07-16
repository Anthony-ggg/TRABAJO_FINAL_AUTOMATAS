package com.compilador.sintactico.parser;

public class GrammarDefinition {

    public static final String GRAMMAR = """
        <query> ::= <select_query> | <insert_query> | <update_query> | <delete_query>

        <select_query> ::= SELECCIONAR <columns> DESDE <table> [CUANDO <condition>]
        <insert_query> ::= INSERTAR EN <table> VALORES "(" <values> ")"
        <update_query> ::= MODIFICAR <table> ESTABLECER <assignments> [CUANDO <condition>]
        <delete_query> ::= ELIMINAR DESDE <table> [CUANDO <condition>]

        <columns>     ::= <column> ("," <column>)*
        <column>      ::= IDENTIFICADOR | "*"
        <table>       ::= IDENTIFICADOR
        <condition>   ::= <expression> (("Y" | "O") <expression>)*
        <expression>  ::= IDENTIFICADOR OPERADOR <value>
        <values>      ::= <value> ("," <value>)*
        <assignments> ::= <assignment> ("," <assignment>)*
        <assignment>  ::= IDENTIFICADOR "=" <value>
        <value>       ::= NUMERO | CADENA | IDENTIFICADOR

        TERMINALES:
        - PALABRA_RESERVADA: SELECCIONAR, INSERTAR, MODIFICAR, ELIMINAR, DESDE, CUANDO, EN, VALORES, ESTABLECER, Y, O
        - IDENTIFICADOR: [a-zA-Z_][a-zA-Z0-9_]*
        - NUMERO: digitos con decimales opcionales
        - CADENA: texto entre comillas simples
        - OPERADOR: =, >, <, >=, <=, <>, !=
        - SIMBOLO: (, ), ",", *
        """;

    public static final String AST_PROMPT = """
        Eres el analizador sintactico de un mini-compilador de SQL en espanol.
        Tu tarea es GENERAR EL ARBOL SINTACTICO (AST) de la consulta, en formato JSON.

        Gramatica del lenguaje:
        %s

        Devuelve EXACTAMENTE un objeto JSON con esta forma (omite los campos que no apliquen):
        {
          "tipo": "SELECCIONAR" | "INSERTAR" | "MODIFICAR" | "ELIMINAR",
          "tabla": "nombre_de_la_tabla",
          "columnas": ["col1", "col2"],
          "valores": ["'texto'", "18"],
          "asignaciones": [{"columna": "ciudad", "valor": "'Madrid'"}],
          "condiciones": [{"columna": "edad", "operador": ">", "valor": "18"}],
          "conectores": ["Y"]
        }

        Reglas estrictas:
        - "columnas" solo en SELECCIONAR. Usa ["*"] si se seleccionan todas.
        - "valores" solo en INSERTAR, en el mismo orden que aparecen.
        - "asignaciones" solo en MODIFICAR.
        - "condiciones" es la clausula CUANDO. Si no hay CUANDO, omitela.
        - "conectores" son los Y/O que unen las condiciones (hay uno menos que condiciones).
        - Copia los valores EXACTAMENTE como aparecen en la consulta.
        - No inventes tablas, columnas ni valores que no esten en la consulta.
        - Responde SOLO con el JSON, sin explicaciones ni texto adicional.

        Ejemplo:
        Consulta: SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18 Y ciudad = 'Madrid'
        Respuesta: {"tipo":"SELECCIONAR","tabla":"usuarios","columnas":["nombre","edad"],"condiciones":[{"columna":"edad","operador":">","valor":"18"},{"columna":"ciudad","operador":"=","valor":"'Madrid'"}],"conectores":["Y"]}
        """.formatted(GRAMMAR);
}
