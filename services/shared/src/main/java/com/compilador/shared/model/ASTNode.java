package com.compilador.shared.model;

import java.util.ArrayList;
import java.util.List;

public class ASTNode {
    private String tipo;
    private String valor;
    private List<ASTNode> hijos;

    public ASTNode() {
        this.hijos = new ArrayList<>();
    }

    public ASTNode(String tipo, String valor) {
        this.tipo = tipo;
        this.valor = valor;
        this.hijos = new ArrayList<>();
    }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }
    public List<ASTNode> getHijos() { return hijos; }
    public void setHijos(List<ASTNode> hijos) { this.hijos = hijos; }
    public void agregarHijo(ASTNode hijo) { this.hijos.add(hijo); }

    @Override
    public String toString() {
        return toString(0);
    }

    private String toString(int nivel) {
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(nivel);
        sb.append(indent).append(tipo).append(": ").append(valor);
        for (ASTNode h : hijos) {
            sb.append("\n").append(h.toString(nivel + 1));
        }
        return sb.toString();
    }
}
