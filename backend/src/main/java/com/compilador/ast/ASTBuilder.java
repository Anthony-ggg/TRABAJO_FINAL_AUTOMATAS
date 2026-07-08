package com.compilador.ast;

import com.compilador.model.ASTNode;
import com.compilador.model.Token;

import java.util.*;

public class ASTBuilder {

    private static final Set<String> KEYWORDS = Set.of(
            "SELECCIONAR", "INSERTAR", "MODIFICAR", "ELIMINAR",
            "DESDE", "CUANDO", "EN", "VALORES", "ESTABLECER", "Y", "O"
    );

    public static ASTNode buildFromTokens(List<Token> tokens) {
        if (tokens.isEmpty()) {
            return new ASTNode("VACIO", "consulta_vacia");
        }

        String firstUpper = tokens.get(0).getValor().toUpperCase();
        return switch (firstUpper) {
            case "SELECCIONAR" -> buildSelect(tokens);
            case "INSERTAR" -> buildInsert(tokens);
            case "MODIFICAR" -> buildUpdate(tokens);
            case "ELIMINAR" -> buildDelete(tokens);
            default -> {
                ASTNode error = new ASTNode("ERROR", "consulta_no_reconocida");
                error.agregarHijo(new ASTNode("PRIMER_TOKEN", tokens.get(0).getValor()));
                yield error;
            }
        };
    }

    private static ASTNode buildSelect(List<Token> tokens) {
        ASTNode root = new ASTNode("SELECCIONAR", "SELECT");
        int i = 1;

        ASTNode columns = new ASTNode("COLUMNAS", "columns");
        while (i < tokens.size()) {
            Token t = tokens.get(i);
            String upper = t.getValor().toUpperCase();

            if (upper.equals("DESDE")) {
                i++;
                break;
            }
            if (upper.equals("CUANDO")) break;

            if (t.getTipo().equals("SIMBOLO") && t.getValor().equals(",")) {
                i++;
                continue;
            }
            columns.agregarHijo(new ASTNode(t.getTipo(), t.getValor()));
            i++;
        }
        root.agregarHijo(columns);

        if (i < tokens.size()) {
            ASTNode table = new ASTNode("TABLA", tokens.get(i).getValor());
            root.agregarHijo(table);
            i++;
        }

        if (i < tokens.size() && tokens.get(i).getValor().toUpperCase().equals("CUANDO")) {
            i++;
            ASTNode condition = new ASTNode("CONDICION", "where");
            while (i < tokens.size()) {
                Token t = tokens.get(i);
                if (t.getValor().toUpperCase().equals("Y") || t.getValor().toUpperCase().equals("O")) {
                    condition.agregarHijo(new ASTNode("OPERADOR_LOGICO", t.getValor()));
                } else {
                    condition.agregarHijo(new ASTNode(t.getTipo(), t.getValor()));
                }
                i++;
            }
            root.agregarHijo(condition);
        }

        return root;
    }

    private static ASTNode buildInsert(List<Token> tokens) {
        ASTNode root = new ASTNode("INSERTAR", "INSERT");
        int i = 1;

        if (i < tokens.size() && tokens.get(i).getValor().toUpperCase().equals("EN")) {
            i++;
        }

        if (i < tokens.size()) {
            root.agregarHijo(new ASTNode("TABLA", tokens.get(i).getValor()));
            i++;
        }

        if (i < tokens.size() && tokens.get(i).getValor().toUpperCase().equals("VALORES")) {
            i++;
        }

        if (i < tokens.size()) {
            ASTNode values = new ASTNode("VALORES", "values");
            while (i < tokens.size()) {
                Token t = tokens.get(i);
                if (t.getTipo().equals("SIMBOLO") && (t.getValor().equals("(") || t.getValor().equals(")") || t.getValor().equals(","))) {
                    values.agregarHijo(new ASTNode("SIMBOLO", t.getValor()));
                } else {
                    values.agregarHijo(new ASTNode(t.getTipo(), t.getValor()));
                }
                i++;
            }
            root.agregarHijo(values);
        }

        return root;
    }

    private static ASTNode buildUpdate(List<Token> tokens) {
        ASTNode root = new ASTNode("MODIFICAR", "UPDATE");
        int i = 1;

        if (i < tokens.size()) {
            root.agregarHijo(new ASTNode("TABLA", tokens.get(i).getValor()));
            i++;
        }

        if (i < tokens.size() && tokens.get(i).getValor().toUpperCase().equals("ESTABLECER")) {
            i++;
        }

        ASTNode assignments = new ASTNode("ASIGNACIONES", "set");
        while (i < tokens.size()) {
            Token t = tokens.get(i);
            if (t.getValor().toUpperCase().equals("CUANDO")) {
                i++;
                break;
            }
            assignments.agregarHijo(new ASTNode(t.getTipo(), t.getValor()));
            i++;
        }
        root.agregarHijo(assignments);

        if (i < tokens.size()) {
            ASTNode condition = new ASTNode("CONDICION", "where");
            while (i < tokens.size()) {
                Token t = tokens.get(i);
                if (t.getValor().toUpperCase().equals("Y") || t.getValor().toUpperCase().equals("O")) {
                    condition.agregarHijo(new ASTNode("OPERADOR_LOGICO", t.getValor()));
                } else {
                    condition.agregarHijo(new ASTNode(t.getTipo(), t.getValor()));
                }
                i++;
            }
            root.agregarHijo(condition);
        }

        return root;
    }

    private static ASTNode buildDelete(List<Token> tokens) {
        ASTNode root = new ASTNode("ELIMINAR", "DELETE");
        int i = 1;

        if (i < tokens.size() && tokens.get(i).getValor().toUpperCase().equals("DESDE")) {
            i++;
        }

        if (i < tokens.size()) {
            ASTNode table = new ASTNode("TABLA", tokens.get(i).getValor());
            root.agregarHijo(table);
            i++;
        }

        if (i < tokens.size() && tokens.get(i).getValor().toUpperCase().equals("CUANDO")) {
            i++;
            ASTNode condition = new ASTNode("CONDICION", "where");
            while (i < tokens.size()) {
                Token t = tokens.get(i);
                if (t.getValor().toUpperCase().equals("Y") || t.getValor().toUpperCase().equals("O")) {
                    condition.agregarHijo(new ASTNode("OPERADOR_LOGICO", t.getValor()));
                } else {
                    condition.agregarHijo(new ASTNode(t.getTipo(), t.getValor()));
                }
                i++;
            }
            root.agregarHijo(condition);
        }

        return root;
    }
}
