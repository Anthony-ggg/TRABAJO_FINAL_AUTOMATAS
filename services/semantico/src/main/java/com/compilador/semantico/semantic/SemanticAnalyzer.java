package com.compilador.semantico.semantic;

import com.compilador.semantico.model.SymbolTable;
import com.compilador.shared.model.ASTNode;
import com.compilador.shared.model.CompileError;
import com.compilador.shared.model.Token;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.*;

@Component
public class SemanticAnalyzer {

    private final DataSource dataSource;
    private final SymbolTable symbolTable;

    public SemanticAnalyzer(DataSource dataSource) {
        this.dataSource = dataSource;
        this.symbolTable = new SymbolTable();
        loadMetadata();
    }

    public record SemanticResult(boolean valido,
                                 List<CompileError> errores,
                                 Map<String, Object> simbolos,
                                 long tiempoMs) {}

    public SemanticResult validate(ASTNode ast, List<Token> tokens) {
        long inicio = System.nanoTime();
        List<CompileError> errores = new ArrayList<>();
        Map<String, Object> simbolos = new LinkedHashMap<>();

        if (ast == null || "VACIO".equals(ast.getTipo()) || "ERROR".equals(ast.getTipo())) {
            errores.add(new CompileError(CompileError.Fase.SEMANTICO, "AST_INVALIDO",
                    "No se pudo construir un arbol sintactico valido para analizar.",
                    "Revisa la estructura de la consulta."));
            return new SemanticResult(false, errores, simbolos, transcurrido(inicio));
        }

        String tipo = ast.getTipo();
        String tabla = extraerTabla(ast);

        simbolos.put("tipo_consulta", tipo);
        simbolos.put("tabla", tabla != null ? tabla : "N/A");

        if (tabla == null) {
            errores.add(new CompileError(CompileError.Fase.SEMANTICO, "TABLA_NO_ESPECIFICADA",
                    "La consulta no especifica ninguna tabla.",
                    "Indica una tabla existente: " + symbolTable.getTablasDisponibles()));
            return new SemanticResult(false, errores, simbolos, transcurrido(inicio));
        }

        if (!symbolTable.existeTabla(tabla)) {
            errores.add(new CompileError(CompileError.Fase.SEMANTICO, "TABLA_INEXISTENTE",
                    "Error Semantico: la tabla '" + tabla + "' no existe en la base de datos.",
                    tabla, posicionDe(tokens, tabla), tabla.length(),
                    "Las tablas disponibles son: " + symbolTable.getTablasDisponibles()));
            completarSimbolos(simbolos, tabla);
            return new SemanticResult(false, errores, simbolos, transcurrido(inicio));
        }

        if ("SELECCIONAR".equals(tipo)) {
            for (String col : extraerColumnasSeleccion(ast)) {
                if (!"*".equals(col) && !symbolTable.existeColumna(tabla, col)) {
                    errores.add(new CompileError(CompileError.Fase.SEMANTICO, "COLUMNA_INEXISTENTE",
                            "Error Semantico: la columna '" + col + "' no existe en la tabla '" + tabla + "'.",
                            col, posicionDe(tokens, col), col.length(),
                            "Columnas disponibles en '" + tabla + "': " + nombresColumnas(tabla)));
                }
            }
        }

        if ("MODIFICAR".equals(tipo)) {
            for (Asignacion a : extraerAsignaciones(ast)) {
                if (!symbolTable.existeColumna(tabla, a.columna())) {
                    errores.add(new CompileError(CompileError.Fase.SEMANTICO, "COLUMNA_INEXISTENTE",
                            "Error Semantico: la columna '" + a.columna() + "' no existe en la tabla '" + tabla + "'.",
                            a.columna(), posicionDe(tokens, a.columna()), a.columna().length(),
                            "Columnas disponibles en '" + tabla + "': " + nombresColumnas(tabla)));
                } else {
                    validarTipo(tabla, a.columna(), a.valor(), tokens, errores);
                }
            }
        }

        for (Condicion c : extraerCondiciones(ast)) {
            if (!symbolTable.existeColumna(tabla, c.columna())) {
                errores.add(new CompileError(CompileError.Fase.SEMANTICO, "COLUMNA_INEXISTENTE_EN_CUANDO",
                        "Error Semantico: la columna '" + c.columna() + "' de la clausula CUANDO no existe en la tabla '"
                                + tabla + "'.",
                        c.columna(), posicionDe(tokens, c.columna()), c.columna().length(),
                        "Columnas disponibles en '" + tabla + "': " + nombresColumnas(tabla)));
            } else {
                validarTipo(tabla, c.columna(), c.valor(), tokens, errores);
            }
        }

        if ("INSERTAR".equals(tipo)) {
            List<String> valores = extraerValores(ast);
            List<SymbolTable.ColumnaInfo> columnasInsertables = columnasInsertables(tabla);
            simbolos.put("valores_recibidos", valores.size());
            simbolos.put("columnas_esperadas", columnasInsertables.size());

            if (valores.size() != columnasInsertables.size()) {
                errores.add(new CompileError(CompileError.Fase.SEMANTICO, "NUMERO_VALORES_INCORRECTO",
                        "Error Semantico: se proporcionaron " + valores.size() + " valor(es), pero la tabla '"
                                + tabla + "' espera " + columnasInsertables.size() + ".",
                        "Orden esperado: (" + columnasInsertables.stream()
                                .map(SymbolTable.ColumnaInfo::getNombre)
                                .reduce((a, b) -> a + ", " + b).orElse("") + ")"));
            } else {
                for (int i = 0; i < valores.size(); i++) {
                    validarTipo(tabla, columnasInsertables.get(i).getNombre(), valores.get(i), tokens, errores);
                }
            }
        }

        boolean tieneCuando = tieneHijo(ast, "CONDICION");
        if (!tieneCuando && ("ELIMINAR".equals(tipo) || "MODIFICAR".equals(tipo))) {
            String accion = "ELIMINAR".equals(tipo) ? "eliminaria" : "modificaria";
            errores.add(new CompileError(CompileError.Fase.SEMANTICO, "OPERACION_PELIGROSA",
                    "Operacion peligrosa: un " + tipo + " sin clausula CUANDO " + accion
                            + " TODOS los registros de la tabla '" + tabla + "'. La operacion fue bloqueada.",
                    "Anade una clausula CUANDO que delimite los registros afectados, por ejemplo: "
                            + ("ELIMINAR".equals(tipo)
                            ? "ELIMINAR DESDE " + tabla + " CUANDO id = 1"
                            : "MODIFICAR " + tabla + " ESTABLECER ... CUANDO id = 1")));
        }

        completarSimbolos(simbolos, tabla);
        return new SemanticResult(errores.isEmpty(), errores, simbolos, transcurrido(inicio));
    }

    private void validarTipo(String tabla, String columna, String valorLiteral,
                             List<Token> tokens, List<CompileError> errores) {
        if (valorLiteral == null) return;
        SymbolTable.ColumnaInfo info = symbolTable.getColumnaInfo(tabla, columna);
        if (info == null) return;

        boolean columnaNumerica = esTipoNumerico(info.getTipo());
        boolean valorEsCadena = valorLiteral.startsWith("'") && valorLiteral.endsWith("'");

        if (columnaNumerica && valorEsCadena) {
            errores.add(new CompileError(CompileError.Fase.SEMANTICO, "TIPO_INCOMPATIBLE",
                    "Error Semantico: la columna '" + columna + "' es de tipo " + info.getTipo()
                            + ", pero se comparo o asigno con el texto " + valorLiteral + ".",
                    valorLiteral, posicionDe(tokens, valorLiteral), valorLiteral.length(),
                    "Usa un valor numerico sin comillas, por ejemplo: " + columna + " = 18"));
        }
    }

    private boolean esTipoNumerico(String tipo) {
        if (tipo == null) return false;
        String t = tipo.toUpperCase();
        return t.contains("INT") || t.contains("DOUBLE") || t.contains("DECIMAL")
                || t.contains("NUMERIC") || t.contains("FLOAT") || t.contains("REAL");
    }

    private record Condicion(String columna, String operador, String valor) {}
    private record Asignacion(String columna, String valor) {}

    private String extraerTabla(ASTNode ast) {
        ASTNode t = hijo(ast, "TABLA");
        return t != null ? t.getValor().toLowerCase() : null;
    }

    private List<String> extraerColumnasSeleccion(ASTNode ast) {
        ASTNode cols = hijo(ast, "COLUMNAS");
        if (cols == null) return List.of();
        return cols.getHijos().stream().map(ASTNode::getValor).filter(v -> !",".equals(v)).toList();
    }

    private List<String> extraerValores(ASTNode ast) {
        ASTNode valores = hijo(ast, "VALORES");
        if (valores == null) return List.of();
        return valores.getHijos().stream()
                .filter(n -> !"SIMBOLO".equals(n.getTipo()))
                .map(ASTNode::getValor)
                .toList();
    }

    private List<Asignacion> extraerAsignaciones(ASTNode ast) {
        ASTNode nodo = hijo(ast, "ASIGNACIONES");
        if (nodo == null) return List.of();
        List<Asignacion> asignaciones = new ArrayList<>();
        List<ASTNode> h = nodo.getHijos();
        for (int i = 0; i + 2 < h.size(); i += 3) {
            if ("IDENTIFICADOR".equals(h.get(i).getTipo()) && "=".equals(h.get(i + 1).getValor())) {
                asignaciones.add(new Asignacion(h.get(i).getValor(), h.get(i + 2).getValor()));
            }
        }
        return asignaciones;
    }

    private List<Condicion> extraerCondiciones(ASTNode ast) {
        ASTNode nodo = hijo(ast, "CONDICION");
        if (nodo == null) return List.of();
        List<Condicion> condiciones = new ArrayList<>();
        List<ASTNode> h = nodo.getHijos();
        int i = 0;
        while (i < h.size()) {
            if ("OPERADOR_LOGICO".equals(h.get(i).getTipo())) { i++; continue; }
            if (i + 2 < h.size()
                    && "IDENTIFICADOR".equals(h.get(i).getTipo())
                    && "OPERADOR".equals(h.get(i + 1).getTipo())) {
                condiciones.add(new Condicion(h.get(i).getValor(),
                        h.get(i + 1).getValor(), h.get(i + 2).getValor()));
                i += 3;
            } else {
                i++;
            }
        }
        return condiciones;
    }

    private boolean tieneHijo(ASTNode nodo, String tipo) {
        return hijo(nodo, tipo) != null;
    }

    private ASTNode hijo(ASTNode nodo, String tipo) {
        for (ASTNode h : nodo.getHijos()) {
            if (tipo.equals(h.getTipo())) return h;
        }
        return null;
    }

    private int posicionDe(List<Token> tokens, String lexema) {
        if (tokens == null || lexema == null) return -1;
        for (Token t : tokens) {
            if (lexema.equalsIgnoreCase(t.getValor())) return t.getPosicion();
        }
        return -1;
    }

    private void completarSimbolos(Map<String, Object> simbolos, String tabla) {
        simbolos.put("tablas_disponibles", symbolTable.getTablasDisponibles());
        simbolos.put("columnas_disponibles",
                tabla != null && symbolTable.existeTabla(tabla) ? nombresColumnas(tabla) : List.of());
    }

    private List<String> nombresColumnas(String tabla) {
        List<SymbolTable.ColumnaInfo> cols = symbolTable.getColumnas(tabla);
        if (cols == null) return List.of();
        return cols.stream().map(SymbolTable.ColumnaInfo::getNombre).toList();
    }

    private List<SymbolTable.ColumnaInfo> columnasInsertables(String tabla) {
        List<SymbolTable.ColumnaInfo> cols = symbolTable.getColumnas(tabla);
        if (cols == null) return List.of();
        return cols.stream().filter(c -> !"id".equalsIgnoreCase(c.getNombre())).toList();
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    private long transcurrido(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000;
    }

    private void loadMetadata() {
        initDatabase();
        try (Connection conn = dataSource.getConnection()) {
            var stmt = conn.prepareStatement("""
                SELECT t.nombre AS tabla, c.nombre AS columna, c.tipo
                FROM tablas t
                JOIN columnas c ON t.id = c.tabla_id
                ORDER BY t.nombre, c.id
            """);
            ResultSet rs = stmt.executeQuery();

            Map<String, List<SymbolTable.ColumnaInfo>> map = new LinkedHashMap<>();
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("tabla").toLowerCase(), k -> new ArrayList<>())
                        .add(new SymbolTable.ColumnaInfo(rs.getString("columna"), rs.getString("tipo")));
            }

            map.forEach((tabla, cols) -> {
                symbolTable.agregarTabla(tabla, cols);
                System.out.println("[Semantico] Cargada tabla '" + tabla + "' con " + cols.size() + " columnas");
            });
            System.out.println("[Semantico] Metadata cargada: " + map.size() + " tablas");

        } catch (Exception e) {
            System.err.println("[Semantico] Error cargando metadata: " + e.getMessage());
        }
    }

    private void initDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            var stmt = conn.createStatement();

            stmt.execute("CREATE TABLE IF NOT EXISTS usuarios ("
                    + " id INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                    + " nombre VARCHAR(100) NOT NULL,"
                    + " edad INT NOT NULL,"
                    + " email VARCHAR(100),"
                    + " ciudad VARCHAR(100))");
            stmt.execute("CREATE TABLE IF NOT EXISTS productos ("
                    + " id INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                    + " nombre VARCHAR(100) NOT NULL,"
                    + " precio DOUBLE PRECISION NOT NULL,"
                    + " stock INT NOT NULL,"
                    + " categoria VARCHAR(100))");
            stmt.execute("CREATE TABLE IF NOT EXISTS pedidos ("
                    + " id INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                    + " usuario_id INT NOT NULL,"
                    + " producto_id INT NOT NULL,"
                    + " cantidad INT NOT NULL,"
                    + " fecha VARCHAR(20))");

            stmt.execute("CREATE TABLE IF NOT EXISTS tablas ("
                    + " id INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                    + " nombre VARCHAR(100) UNIQUE NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS columnas ("
                    + " id INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                    + " tabla_id INT REFERENCES tablas(id) ON DELETE CASCADE,"
                    + " nombre VARCHAR(100) NOT NULL,"
                    + " tipo VARCHAR(50) DEFAULT 'VARCHAR',"
                    + " UNIQUE(tabla_id, nombre))");

            var rs = stmt.executeQuery("SELECT COUNT(*) FROM tablas");
            rs.next();
            if (rs.getInt(1) == 0) {
                seedMetadata(conn);
            }

            var rs2 = stmt.executeQuery("SELECT COUNT(*) FROM usuarios");
            rs2.next();
            if (rs2.getInt(1) == 0) {
                seedDatosDemo(conn);
            }
        } catch (Exception e) {
            System.err.println("[Semantico] DB init error: " + e.getMessage());
        }
    }

    private void seedMetadata(Connection conn) throws Exception {
        var stmt = conn.createStatement();
        stmt.execute("INSERT INTO tablas (nombre) VALUES ('usuarios')");
        stmt.execute("INSERT INTO tablas (nombre) VALUES ('productos')");
        stmt.execute("INSERT INTO tablas (nombre) VALUES ('pedidos')");

        stmt.execute("INSERT INTO columnas (tabla_id, nombre, tipo) SELECT t.id, c.n, c.t FROM tablas t, "
                + "(VALUES ('id','INTEGER'),('nombre','VARCHAR'),('edad','INTEGER'),('email','VARCHAR'),('ciudad','VARCHAR'))"
                + " AS c(n,t) WHERE t.nombre='usuarios' AND NOT EXISTS (SELECT 1 FROM columnas WHERE tabla_id=t.id)");
        stmt.execute("INSERT INTO columnas (tabla_id, nombre, tipo) SELECT t.id, c.n, c.t FROM tablas t, "
                + "(VALUES ('id','INTEGER'),('nombre','VARCHAR'),('precio','DOUBLE'),('stock','INTEGER'),('categoria','VARCHAR'))"
                + " AS c(n,t) WHERE t.nombre='productos' AND NOT EXISTS (SELECT 1 FROM columnas WHERE tabla_id=t.id)");
        stmt.execute("INSERT INTO columnas (tabla_id, nombre, tipo) SELECT t.id, c.n, c.t FROM tablas t, "
                + "(VALUES ('id','INTEGER'),('usuario_id','INTEGER'),('producto_id','INTEGER'),('cantidad','INTEGER'),('fecha','VARCHAR'))"
                + " AS c(n,t) WHERE t.nombre='pedidos' AND NOT EXISTS (SELECT 1 FROM columnas WHERE tabla_id=t.id)");

        System.out.println("[Semantico] Metadata sembrada");
    }

    private void seedDatosDemo(Connection conn) throws Exception {
        var stmt = conn.createStatement();
        stmt.execute("INSERT INTO usuarios (nombre, edad, email, ciudad) VALUES "
                + "('Ana Garcia', 25, 'ana@email.com', 'Madrid'),"
                + "('Carlos Lopez', 32, 'carlos@email.com', 'Barcelona'),"
                + "('Maria Rodriguez', 17, 'maria@email.com', 'Valencia')");
        stmt.execute("INSERT INTO productos (nombre, precio, stock, categoria) VALUES "
                + "('Laptop', 1200.50, 15, 'Electronica'),"
                + "('Mouse', 25.99, 100, 'Accesorios')");
        stmt.execute("INSERT INTO pedidos (usuario_id, producto_id, cantidad, fecha) VALUES "
                + "(1, 1, 2, '2024-01-15'),"
                + "(2, 2, 5, '2024-01-20')");
        System.out.println("[Semantico] Datos de ejemplo insertados");
    }
}
