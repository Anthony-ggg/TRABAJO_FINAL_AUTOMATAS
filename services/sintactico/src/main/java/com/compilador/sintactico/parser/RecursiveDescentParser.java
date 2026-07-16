package com.compilador.sintactico.parser;

import com.compilador.shared.model.ASTNode;
import com.compilador.shared.model.CompileError;
import com.compilador.shared.model.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RecursiveDescentParser {

    private final List<Token> tokens;
    private final List<CompileError> errores = new ArrayList<>();
    private int i = 0;

    private static final Set<String> INICIOS = Set.of("SELECCIONAR", "INSERTAR", "MODIFICAR", "ELIMINAR");
    private static final Set<String> OPERADORES = Set.of("=", ">", "<", ">=", "<=", "<>", "!=");

    public RecursiveDescentParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public record ResultadoParser(ASTNode ast, List<CompileError> errores) {
        public boolean valido() { return errores.isEmpty(); }
    }

    public static ResultadoParser parse(List<Token> tokens) {
        RecursiveDescentParser p = new RecursiveDescentParser(tokens);
        ASTNode ast = p.parseQuery();
        return new ResultadoParser(ast, p.errores);
    }

    private ASTNode parseQuery() {
        if (tokens.isEmpty()) {
            error("SIN_TOKENS", "La consulta esta vacia.", null,
                    "Escribe una consulta que empiece por SELECCIONAR, INSERTAR, MODIFICAR o ELIMINAR.");
            return new ASTNode("VACIO", "consulta_vacia");
        }

        Token primero = actual();
        String inicio = primero.getValor().toUpperCase();

        if (!INICIOS.contains(inicio)) {
            error("INICIO_INVALIDO",
                    "La consulta debe comenzar con SELECCIONAR, INSERTAR, MODIFICAR o ELIMINAR, "
                            + "pero comienza con '" + primero.getValor() + "'.",
                    primero,
                    "Sustituye '" + primero.getValor() + "' por una de las cuatro palabras reservadas de inicio.");
            return new ASTNode("ERROR", "consulta_no_reconocida");
        }

        ASTNode ast = switch (inicio) {
            case "SELECCIONAR" -> parseSelect();
            case "INSERTAR"    -> parseInsert();
            case "MODIFICAR"   -> parseUpdate();
            case "ELIMINAR"    -> parseDelete();
            default            -> new ASTNode("ERROR", "consulta_no_reconocida");
        };

        if (hayMas()) {
            Token sobra = actual();
            error("TOKENS_SOBRANTES",
                    "Hay contenido inesperado al final de la consulta: '" + sobra.getValor() + "'.",
                    sobra,
                    "Elimina lo que sigue a '" + sobra.getValor() + "' o revisa la estructura de la consulta.");
        }
        return ast;
    }

    private ASTNode parseSelect() {
        ASTNode raiz = new ASTNode("SELECCIONAR", "SELECCIONAR");
        avanzar();
        raiz.agregarHijo(parseColumnas());

        if (!esperarPalabra("DESDE",
                "Falta la palabra reservada DESDE para indicar la tabla.",
                "Estructura esperada: SELECCIONAR <columnas> DESDE <tabla> [CUANDO <condicion>]")) {
            return raiz;
        }

        ASTNode tabla = parseTabla();
        if (tabla != null) raiz.agregarHijo(tabla);

        ASTNode condicion = parseCuandoOpcional();
        if (condicion != null) raiz.agregarHijo(condicion);

        return raiz;
    }

    private ASTNode parseInsert() {
        ASTNode raiz = new ASTNode("INSERTAR", "INSERTAR");
        avanzar();

        if (!esperarPalabra("EN",
                "Falta la palabra reservada EN despues de INSERTAR.",
                "Estructura esperada: INSERTAR EN <tabla> VALORES (<valores>)")) {
            return raiz;
        }

        ASTNode tabla = parseTabla();
        if (tabla != null) raiz.agregarHijo(tabla);

        if (!esperarPalabra("VALORES",
                "Falta la palabra reservada VALORES.",
                "Estructura esperada: INSERTAR EN <tabla> VALORES (<valores>)")) {
            return raiz;
        }

        if (!esperarSimbolo("(",
                "Falta el parentesis de apertura '(' antes de la lista de valores.",
                "Escribe los valores entre parentesis, por ejemplo: VALORES ('Pedro', 28)")) {
            return raiz;
        }

        ASTNode valores = new ASTNode("VALORES", "values");
        if (esSimbolo(")")) {
            error("VALORES_VACIOS", "La lista de valores esta vacia.", actual(),
                    "Indica al menos un valor dentro de los parentesis.");
        } else {
            do {
                ASTNode valor = parseValor();
                if (valor == null) break;
                valores.agregarHijo(valor);
            } while (consumirSimbolo(","));
        }
        raiz.agregarHijo(valores);

        esperarSimbolo(")",
                "Falta el parentesis de cierre ')' al final de la lista de valores.",
                "Cierra los parentesis, por ejemplo: VALORES ('Pedro', 28)");

        return raiz;
    }

    private ASTNode parseUpdate() {
        ASTNode raiz = new ASTNode("MODIFICAR", "MODIFICAR");
        avanzar();

        ASTNode tabla = parseTabla();
        if (tabla != null) raiz.agregarHijo(tabla);

        if (!esperarPalabra("ESTABLECER",
                "Falta la palabra reservada ESTABLECER.",
                "Estructura esperada: MODIFICAR <tabla> ESTABLECER <columna> = <valor> CUANDO <condicion>")) {
            return raiz;
        }

        ASTNode asignaciones = new ASTNode("ASIGNACIONES", "set");
        do {
            Token col = esperarIdentificador("Se esperaba el nombre de una columna en la asignacion.",
                    "Estructura esperada: ESTABLECER <columna> = <valor>");
            if (col == null) break;

            if (!esperarOperadorIgual()) break;

            ASTNode valor = parseValor();
            if (valor == null) break;

            asignaciones.agregarHijo(new ASTNode("IDENTIFICADOR", col.getValor()));
            asignaciones.agregarHijo(new ASTNode("OPERADOR", "="));
            asignaciones.agregarHijo(valor);
        } while (consumirSimbolo(","));

        raiz.agregarHijo(asignaciones);

        ASTNode condicion = parseCuandoOpcional();
        if (condicion != null) raiz.agregarHijo(condicion);

        return raiz;
    }

    private ASTNode parseDelete() {
        ASTNode raiz = new ASTNode("ELIMINAR", "ELIMINAR");
        avanzar();

        if (!esperarPalabra("DESDE",
                "Falta la palabra reservada DESDE despues de ELIMINAR.",
                "Estructura esperada: ELIMINAR DESDE <tabla> CUANDO <condicion>")) {
            return raiz;
        }

        ASTNode tabla = parseTabla();
        if (tabla != null) raiz.agregarHijo(tabla);

        ASTNode condicion = parseCuandoOpcional();
        if (condicion != null) raiz.agregarHijo(condicion);

        return raiz;
    }

    private ASTNode parseColumnas() {
        ASTNode columnas = new ASTNode("COLUMNAS", "columns");
        do {
            if (!hayMas()) {
                error("COLUMNAS_FALTANTES", "Se esperaba al menos una columna despues de SELECCIONAR.",
                        ultimo(), "Indica las columnas o usa * para seleccionarlas todas.");
                return columnas;
            }
            Token t = actual();
            if (esSimbolo("*")) {
                columnas.agregarHijo(new ASTNode("SIMBOLO", "*"));
                avanzar();
            } else if ("IDENTIFICADOR".equals(t.getTipo())) {
                columnas.agregarHijo(new ASTNode("IDENTIFICADOR", t.getValor()));
                avanzar();
            } else {
                error("COLUMNA_INVALIDA",
                        "Se esperaba el nombre de una columna o '*', pero se encontro '" + t.getValor() + "'.",
                        t, "Usa nombres de columna validos separados por comas, o '*' para todas.");
                return columnas;
            }
        } while (consumirSimbolo(","));
        return columnas;
    }

    private ASTNode parseTabla() {
        Token t = esperarIdentificador("Se esperaba el nombre de una tabla.",
                "Indica una tabla existente, por ejemplo: usuarios, productos o pedidos.");
        return t != null ? new ASTNode("TABLA", t.getValor()) : null;
    }

    private ASTNode parseCuandoOpcional() {
        if (!hayMas() || !esPalabra("CUANDO")) return null;
        avanzar();

        ASTNode condicion = new ASTNode("CONDICION", "where");
        boolean primera = true;

        while (true) {
            if (!primera) {
                if (!hayMas() || !(esPalabra("Y") || esPalabra("O"))) break;
                condicion.agregarHijo(new ASTNode("OPERADOR_LOGICO", actual().getValor().toUpperCase()));
                avanzar();
            }
            primera = false;

            Token col = esperarIdentificador("Se esperaba el nombre de una columna en la condicion CUANDO.",
                    "Estructura esperada: CUANDO <columna> <operador> <valor>");
            if (col == null) return condicion;

            if (!hayMas() || !"OPERADOR".equals(actual().getTipo())
                    || !OPERADORES.contains(actual().getValor())) {
                error("OPERADOR_FALTANTE",
                        "Se esperaba un operador de comparacion despues de '" + col.getValor() + "'.",
                        hayMas() ? actual() : ultimo(),
                        "Operadores validos: = > < >= <= <> !=");
                return condicion;
            }
            String operador = actual().getValor();
            avanzar();

            ASTNode valor = parseValor();
            if (valor == null) return condicion;

            condicion.agregarHijo(new ASTNode("IDENTIFICADOR", col.getValor()));
            condicion.agregarHijo(new ASTNode("OPERADOR", operador));
            condicion.agregarHijo(valor);
        }

        if (condicion.getHijos().isEmpty()) {
            error("CONDICION_VACIA", "La clausula CUANDO no tiene ninguna condicion.",
                    ultimo(), "Escribe una condicion, por ejemplo: CUANDO edad > 18");
        }
        return condicion;
    }

    private ASTNode parseValor() {
        if (!hayMas()) {
            error("VALOR_FALTANTE", "Se esperaba un valor.", ultimo(),
                    "Los valores validos son numeros (18), cadenas ('Madrid') o nombres de columna.");
            return null;
        }
        Token t = actual();
        if ("NUMERO".equals(t.getTipo()) || "CADENA".equals(t.getTipo()) || "IDENTIFICADOR".equals(t.getTipo())) {
            avanzar();
            return new ASTNode(t.getTipo(), t.getValor());
        }
        error("VALOR_INVALIDO",
                "Se esperaba un valor, pero se encontro '" + t.getValor() + "'.", t,
                "Los valores validos son numeros (18), cadenas entre comillas simples ('Madrid') o nombres de columna.");
        return null;
    }

    private boolean hayMas() { return i < tokens.size(); }
    private Token actual()   { return tokens.get(i); }
    private Token ultimo()   { return tokens.isEmpty() ? null : tokens.get(tokens.size() - 1); }
    private void avanzar()   { i++; }

    private boolean esPalabra(String palabra) {
        return hayMas() && actual().getValor().equalsIgnoreCase(palabra);
    }

    private boolean esSimbolo(String simbolo) {
        return hayMas() && "SIMBOLO".equals(actual().getTipo()) && actual().getValor().equals(simbolo);
    }

    private boolean consumirSimbolo(String simbolo) {
        if (esSimbolo(simbolo)) { avanzar(); return true; }
        return false;
    }

    private boolean esperarPalabra(String palabra, String mensaje, String sugerencia) {
        if (esPalabra(palabra)) { avanzar(); return true; }
        error("PALABRA_RESERVADA_FALTANTE", mensaje, hayMas() ? actual() : ultimo(), sugerencia);
        return false;
    }

    private boolean esperarSimbolo(String simbolo, String mensaje, String sugerencia) {
        if (esSimbolo(simbolo)) { avanzar(); return true; }
        error("SIMBOLO_FALTANTE", mensaje, hayMas() ? actual() : ultimo(), sugerencia);
        return false;
    }

    private Token esperarIdentificador(String mensaje, String sugerencia) {
        if (hayMas() && "IDENTIFICADOR".equals(actual().getTipo())) {
            Token t = actual();
            avanzar();
            return t;
        }
        error("IDENTIFICADOR_FALTANTE", mensaje, hayMas() ? actual() : ultimo(), sugerencia);
        return null;
    }

    private boolean esperarOperadorIgual() {
        if (hayMas() && "OPERADOR".equals(actual().getTipo()) && "=".equals(actual().getValor())) {
            avanzar();
            return true;
        }
        error("ASIGNACION_INVALIDA",
                "Se esperaba el operador '=' en la asignacion.",
                hayMas() ? actual() : ultimo(),
                "Estructura esperada: ESTABLECER <columna> = <valor>");
        return false;
    }

    private void error(String codigo, String mensaje, Token token, String sugerencia) {
        errores.add(CompileError.enToken(CompileError.Fase.SINTACTICO, codigo, mensaje, token, sugerencia));
    }
}
