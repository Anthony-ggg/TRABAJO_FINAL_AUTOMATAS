package com.compilador.parser;

import com.compilador.model.ASTNode;
import com.compilador.model.Token;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verificador determinista del AST generado por el LLM.
 *
 * Es la pieza que hace segura la exigencia de la guia de que el LLM genere el AST.
 * El modelo puede inventar una tabla, una columna o un valor que no aparecen en la
 * consulta; si ese AST llegara al ejecutor, se traduciria en SQL contra la base de datos.
 *
 * Regla central (anti-alucinacion): todo literal del AST debe existir literalmente entre
 * los tokens que el AFD verifico. El LLM puede estructurar, pero no puede inventar
 * contenido. Ademas se comprueba la completitud estructural segun el tipo de consulta.
 *
 * Si la validacion falla, SyntaxAnalyzer descarta el AST del modelo y usa el del
 * parser determinista.
 */
public class AstValidator {

    /** Nodos que solo aportan estructura; su valor no debe buscarse entre los tokens. */
    private static final Set<String> NODOS_ESTRUCTURALES =
            Set.of("COLUMNAS", "CONDICION", "VALORES", "ASIGNACIONES");

    public record Resultado(boolean valido, String motivo) {
        static Resultado ok() { return new Resultado(true, null); }
        static Resultado fallo(String motivo) { return new Resultado(false, motivo); }
    }

    /**
     * @param ast    AST propuesto por el LLM
     * @param tokens tokens verificados por el AFD (fuente de verdad del contenido)
     */
    public static Resultado validar(ASTNode ast, List<Token> tokens) {
        if (ast == null) {
            return Resultado.fallo("El LLM no devolvio un AST utilizable.");
        }
        if (tokens.isEmpty()) {
            return Resultado.fallo("No hay tokens con los que contrastar el AST.");
        }

        // 1. El tipo de consulta debe coincidir con la primera palabra reservada real.
        String tipoEsperado = tokens.get(0).getValor().toUpperCase();
        if (!tipoEsperado.equals(ast.getTipo())) {
            return Resultado.fallo("El tipo de consulta del AST ('" + ast.getTipo()
                    + "') no coincide con la consulta real ('" + tipoEsperado + "').");
        }

        // 2. Ningun literal del AST puede ser inventado: debe aparecer entre los tokens.
        Set<String> lexemas = new HashSet<>();
        for (Token t : tokens) {
            lexemas.add(t.getValor().toLowerCase());
        }

        List<String> inventados = new ArrayList<>();
        recolectarInventados(ast, lexemas, inventados);
        if (!inventados.isEmpty()) {
            return Resultado.fallo("El AST del LLM contiene elementos que no existen en la consulta: "
                    + String.join(", ", inventados));
        }

        // 3. Completitud estructural segun el tipo de consulta.
        String faltante = comprobarEstructura(ast);
        if (faltante != null) {
            return Resultado.fallo("El AST del LLM esta incompleto: " + faltante);
        }

        return Resultado.ok();
    }

    private static void recolectarInventados(ASTNode nodo, Set<String> lexemas, List<String> inventados) {
        String tipo = nodo.getTipo();
        String valor = nodo.getValor();

        boolean esLiteral = !NODOS_ESTRUCTURALES.contains(tipo)
                && !tipo.equals("SELECCIONAR") && !tipo.equals("INSERTAR")
                && !tipo.equals("MODIFICAR") && !tipo.equals("ELIMINAR");

        if (esLiteral && valor != null && !valor.isBlank()
                && !lexemas.contains(valor.toLowerCase())) {
            inventados.add("'" + valor + "'");
        }

        for (ASTNode hijo : nodo.getHijos()) {
            recolectarInventados(hijo, lexemas, inventados);
        }
    }

    /** @return descripcion de lo que falta, o null si la estructura esta completa. */
    private static String comprobarEstructura(ASTNode ast) {
        boolean tieneTabla = tieneHijo(ast, "TABLA");

        switch (ast.getTipo()) {
            case "SELECCIONAR" -> {
                if (!tieneHijo(ast, "COLUMNAS") || hijo(ast, "COLUMNAS").getHijos().isEmpty()) {
                    return "no se identificaron columnas.";
                }
                if (!tieneTabla) return "no se identifico la tabla.";
            }
            case "INSERTAR" -> {
                if (!tieneTabla) return "no se identifico la tabla.";
                if (!tieneHijo(ast, "VALORES") || hijo(ast, "VALORES").getHijos().isEmpty()) {
                    return "no se identificaron los valores a insertar.";
                }
            }
            case "MODIFICAR" -> {
                if (!tieneTabla) return "no se identifico la tabla.";
                if (!tieneHijo(ast, "ASIGNACIONES") || hijo(ast, "ASIGNACIONES").getHijos().isEmpty()) {
                    return "no se identificaron las asignaciones de ESTABLECER.";
                }
            }
            case "ELIMINAR" -> {
                if (!tieneTabla) return "no se identifico la tabla.";
            }
            default -> {
                return "tipo de consulta desconocido.";
            }
        }
        return null;
    }

    private static boolean tieneHijo(ASTNode nodo, String tipo) {
        return hijo(nodo, tipo) != null;
    }

    private static ASTNode hijo(ASTNode nodo, String tipo) {
        for (ASTNode h : nodo.getHijos()) {
            if (tipo.equals(h.getTipo())) return h;
        }
        return null;
    }
}
