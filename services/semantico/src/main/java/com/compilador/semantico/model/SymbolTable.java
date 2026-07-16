package com.compilador.semantico.model;

import java.util.*;

public class SymbolTable {
    private final Map<String, List<ColumnaInfo>> tablas;

    public SymbolTable() {
        this.tablas = new HashMap<>();
    }

    public void agregarTabla(String nombre, List<ColumnaInfo> columnas) {
        tablas.put(nombre.toLowerCase(), columnas);
    }

    public boolean existeTabla(String nombre) {
        return tablas.containsKey(nombre.toLowerCase());
    }

    public boolean existeColumna(String tabla, String columna) {
        List<ColumnaInfo> cols = tablas.get(tabla.toLowerCase());
        if (cols == null) return false;
        if (columna.equals("*")) return true;
        return cols.stream().anyMatch(c -> c.getNombre().equalsIgnoreCase(columna));
    }

    public List<ColumnaInfo> getColumnas(String tabla) {
        return tablas.get(tabla.toLowerCase());
    }

    public ColumnaInfo getColumnaInfo(String tabla, String columna) {
        List<ColumnaInfo> cols = tablas.get(tabla.toLowerCase());
        if (cols == null) return null;
        return cols.stream()
                .filter(c -> c.getNombre().equalsIgnoreCase(columna))
                .findFirst()
                .orElse(null);
    }

    public List<String> getTablasDisponibles() {
        return new ArrayList<>(tablas.keySet());
    }

    public static class ColumnaInfo {
        private final String nombre;
        private final String tipo;

        public ColumnaInfo(String nombre, String tipo) {
            this.nombre = nombre;
            this.tipo = tipo;
        }

        public String getNombre() { return nombre; }
        public String getTipo() { return tipo; }
    }
}
