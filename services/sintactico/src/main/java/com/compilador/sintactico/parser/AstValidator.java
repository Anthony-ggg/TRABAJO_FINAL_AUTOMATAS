package com.compilador.sintactico.parser;

import com.compilador.shared.model.ASTNode;
import com.compilador.shared.model.Token;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AstValidator {

    private static final Set<String> NODOS_ESTRUCTURALES =
            Set.of("COLUMNAS", "CONDICION", "VALORES", "ASIGNACIONES");

    public record Resultado(boolean valido, String motivo) {
        static Resultado ok() { return new Resultado(true, null); }
        static Resultado fallo(String motivo) { return new Resultado(false, motivo); }
    }

    public static Resultado validar(ASTNode ast, List<Token> tokens) {
        if (ast == null) {
            return Resultado.fallo("El LLM no devolvio un AST utilizable.");
        }
        if (tokens.isEmpty()) {
            return Resultado.fallo("No hay tokens con los que contrastar el AST.");
        }

        String tipoEsperado = tokens.get(0).getValor().toUpperCase();
        if (!tipoEsperado.equals(ast.getTipo())) {
            return Resultado.fallo("El tipo de consulta del AST ('" + ast.getTipo()
                    + "') no coincide con la consulta real ('" + tipoEsperado + "').");
        }

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
