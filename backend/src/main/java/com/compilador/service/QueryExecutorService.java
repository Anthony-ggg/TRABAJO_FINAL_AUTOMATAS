package com.compilador.service;

import com.compilador.model.ASTNode;
import com.compilador.model.CompileResult;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
public class QueryExecutorService {

    private final DataSource dataSource;

    public QueryExecutorService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void execute(ASTNode ast, CompileResult result) {
        if (ast == null) return;

        try {
            switch (ast.getTipo()) {
                case "SELECCIONAR" -> executeSelect(ast, result);
                case "INSERTAR" -> executeInsert(ast, result);
                case "MODIFICAR" -> executeUpdate(ast, result);
                case "ELIMINAR" -> executeDelete(ast, result);
                default -> result.setMensajeEjecucion("Tipo de consulta no ejecutable: " + ast.getTipo());
            }
        } catch (Exception e) {
            result.setExitoso(false);
            result.getErrores().add("Error de ejecución: " + e.getMessage());
            result.setMensajeEjecucion("Error al ejecutar la consulta: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void executeSelect(ASTNode ast, CompileResult result) throws Exception {
        String tabla = extractTableName(ast);
        if (tabla == null) throw new IllegalArgumentException("No se especificó la tabla");
        if (!tableExists(tabla)) throw new IllegalArgumentException("La tabla '" + tabla + "' no existe");

        List<String> columnas = extractColumnNames(ast);
        boolean selectAll = columnas.contains("*");
        String colsSql = selectAll ? "*" : String.join(", ", columnas);

        List<ASTNode> conditionNodes = findConditionNodes(ast);
        WhereClause where = buildWhereClause(conditionNodes);

        String sql = "SELECT " + colsSql + " FROM " + tabla + where.sql();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < where.params().size(); i++) {
                ps.setObject(i + 1, where.params().get(i));
            }
            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> filas = resultSetToList(rs);
            result.setFilasResultado(filas);
            result.setMensajeEjecucion("Consulta ejecutada exitosamente. Se encontraron " + filas.size() + " registros.");
        }
    }

    private void executeInsert(ASTNode ast, CompileResult result) throws Exception {
        String tabla = extractTableName(ast);
        if (tabla == null) throw new IllegalArgumentException("No se especificó la tabla");
        if (!tableExists(tabla)) throw new IllegalArgumentException("La tabla '" + tabla + "' no existe");

        List<String> columnas = getInsertColumns(tabla);
        List<String> valoresStr = extractValoresInsertar(ast);

        if (valoresStr.size() != columnas.size()) {
            throw new IllegalArgumentException("El número de valores (" + valoresStr.size()
                    + ") no coincide con el número de columnas de la tabla (" + columnas.size() + ")");
        }

        String colsSql = String.join(", ", columnas);
        String placeholders = String.join(", ", Collections.nCopies(columnas.size(), "?"));
        String sql = "INSERT INTO " + tabla + " (" + colsSql + ") VALUES (" + placeholders + ")";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < valoresStr.size(); i++) {
                ps.setObject(i + 1, parseLiteral(valoresStr.get(i)));
            }
            ps.executeUpdate();
        }

        result.setMensajeEjecucion("Insercion exitosa. 1 registro insertado en la tabla '" + tabla + "'.");
    }

    private void executeUpdate(ASTNode ast, CompileResult result) throws Exception {
        String tabla = extractTableName(ast);
        if (tabla == null) throw new IllegalArgumentException("No se especifico la tabla");
        if (!tableExists(tabla)) throw new IllegalArgumentException("La tabla '" + tabla + "' no existe");

        Map<String, String> asignaciones = extractAsignaciones(ast);
        List<ASTNode> conditionNodes = findConditionNodes(ast);
        WhereClause where = buildWhereClause(conditionNodes);

        List<Object> allParams = new ArrayList<>();
        List<String> setClauses = new ArrayList<>();
        for (Map.Entry<String, String> a : asignaciones.entrySet()) {
            setClauses.add(a.getKey() + " = ?");
            allParams.add(parseLiteral(a.getValue()));
        }
        allParams.addAll(where.params());

        String sql = "UPDATE " + tabla + " SET " + String.join(", ", setClauses) + where.sql();
        int updated;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < allParams.size(); i++) {
                ps.setObject(i + 1, allParams.get(i));
            }
            updated = ps.executeUpdate();
        }

        result.setMensajeEjecucion("Modificacion exitosa. Se actualizaron " + updated + " registros en la tabla '" + tabla + "'.");
    }

    private void executeDelete(ASTNode ast, CompileResult result) throws Exception {
        String tabla = extractTableName(ast);
        if (tabla == null) throw new IllegalArgumentException("No se especifico la tabla");
        if (!tableExists(tabla)) throw new IllegalArgumentException("La tabla '" + tabla + "' no existe");

        List<ASTNode> conditionNodes = findConditionNodes(ast);
        WhereClause where = buildWhereClause(conditionNodes);
        String sql = "DELETE FROM " + tabla + where.sql();
        int deleted;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < where.params().size(); i++) {
                ps.setObject(i + 1, where.params().get(i));
            }
            deleted = ps.executeUpdate();
        }

        result.setMensajeEjecucion("Eliminacion exitosa. Se eliminaron " + deleted + " registros de la tabla '" + tabla + "'.");
    }

    // --- Live database state for frontend metadata ---

    public Map<String, Object> getLiveDatabaseState() {
        Map<String, Object> state = new LinkedHashMap<>();
        List<String> tablas = List.of("usuarios", "productos", "pedidos");
        Map<String, List<String>> columnasMap = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> registrosMap = new LinkedHashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            for (String tabla : tablas) {
                List<String> cols = new ArrayList<>();
                try (ResultSet rs = conn.getMetaData().getColumns(null, null, tabla, null)) {
                    while (rs.next()) cols.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
                columnasMap.put(tabla, cols);

                List<Map<String, Object>> rows = new ArrayList<>();
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM " + tabla)) {
                    rows = resultSetToList(rs);
                }
                registrosMap.put(tabla, rows);
            }

            state.put("tablas", tablas);
            state.put("columnas", columnasMap);
            state.put("registros", registrosMap);
        } catch (Exception e) {
            System.err.println("Error getting live database state: " + e.getMessage());
            state.put("tablas", tablas);
            state.put("columnas", Map.of(
                "usuarios", List.of("id", "nombre", "edad", "email", "ciudad"),
                "productos", List.of("id", "nombre", "precio", "stock", "categoria"),
                "pedidos", List.of("id", "usuario_id", "producto_id", "cantidad", "fecha")
            ));
            state.put("registros", Map.of());
        }
        return state;
    }

    // --- Helpers ---

    private boolean tableExists(String nombre) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.getMetaData().getTables(null, null, nombre, null);
            return rs.next();
        }
    }

    private List<String> getInsertColumns(String tabla) throws Exception {
        List<String> cols = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.getMetaData().getColumns(null, null, tabla, null);
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME").toLowerCase();
                if (!name.equals("id")) cols.add(name);
            }
        }
        return cols;
    }

    private record WhereClause(String sql, List<Object> params) {}

    private WhereClause buildWhereClause(List<ASTNode> children) {
        if (children.isEmpty()) return new WhereClause("", List.of());

        List<String> fragments = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        List<String> logicalOps = new ArrayList<>();

        int i = 0;
        while (i < children.size()) {
            if (i + 2 < children.size()
                    && children.get(i).getTipo().equals("IDENTIFICADOR")
                    && children.get(i + 1).getTipo().equals("OPERADOR")) {

                String col = children.get(i).getValor();
                String op = children.get(i + 1).getValor();
                String valStr = children.get(i + 2).getValor();

                fragments.add(col + " " + op + " ?");
                params.add(parseLiteral(valStr));
                i += 3;
            } else {
                i++;
            }

            if (i < children.size() && children.get(i).getTipo().equals("OPERADOR_LOGICO")) {
                logicalOps.add(children.get(i).getValor().toUpperCase());
                i++;
            }
        }

        if (fragments.isEmpty()) return new WhereClause("", List.of());

        StringBuilder where = new StringBuilder(" WHERE ");
        for (int j = 0; j < fragments.size(); j++) {
            if (j > 0) {
                String op = j - 1 < logicalOps.size() ? logicalOps.get(j - 1) : "Y";
                where.append(" ").append(op.equals("O") ? "OR" : "AND").append(" ");
            }
            where.append(fragments.get(j));
        }

        return new WhereClause(where.toString(), params);
    }

    private Object parseLiteral(String val) {
        if (val.startsWith("'") && val.endsWith("'")) {
            return val.substring(1, val.length() - 1);
        }
        try {
            if (val.contains(".")) return Double.parseDouble(val);
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return val;
        }
    }

    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                String name = meta.getColumnLabel(i).toLowerCase();
                Object val = rs.getObject(i);
                row.put(name, val);
            }
            list.add(row);
        }
        return list;
    }

    private String extractTableName(ASTNode node) {
        for (ASTNode child : node.getHijos()) {
            if ("TABLA".equals(child.getTipo())) return child.getValor().toLowerCase();
        }
        return null;
    }

    private List<String> extractColumnNames(ASTNode node) {
        for (ASTNode child : node.getHijos()) {
            if ("COLUMNAS".equals(child.getTipo())) {
                return child.getHijos().stream()
                        .map(ASTNode::getValor)
                        .filter(v -> !v.equals(","))
                        .toList();
            }
        }
        return List.of();
    }

    private List<ASTNode> findConditionNodes(ASTNode node) {
        for (ASTNode child : node.getHijos()) {
            if ("CONDICION".equals(child.getTipo())) return child.getHijos();
        }
        return List.of();
    }

    private List<String> extractValoresInsertar(ASTNode node) {
        List<String> vals = new ArrayList<>();
        for (ASTNode child : node.getHijos()) {
            if ("VALORES".equals(child.getTipo())) {
                for (ASTNode vNode : child.getHijos()) {
                    if (!vNode.getTipo().equals("SIMBOLO")) vals.add(vNode.getValor());
                }
            }
        }
        return vals;
    }

    private Map<String, String> extractAsignaciones(ASTNode node) {
        Map<String, String> assigns = new LinkedHashMap<>();
        for (ASTNode child : node.getHijos()) {
            if ("ASIGNACIONES".equals(child.getTipo())) {
                List<ASTNode> list = child.getHijos();
                int idx = 0;
                while (idx + 2 < list.size()) {
                    if (list.get(idx).getTipo().equals("IDENTIFICADOR")
                            && list.get(idx + 1).getValor().equals("=")) {
                        assigns.put(list.get(idx).getValor(), list.get(idx + 2).getValor());
                        idx += 3;
                    } else {
                        idx++;
                    }
                }
            }
        }
        return assigns;
    }
}
