package com.compilador.controller;

import com.compilador.model.CompileResult;
import com.compilador.service.CompilerService;
import com.compilador.service.QueryExecutorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/compiler")
@CrossOrigin(origins = "*")
public class CompilerController {

    private final CompilerService compilerService;
    private final QueryExecutorService queryExecutorService;

    public CompilerController(CompilerService compilerService, QueryExecutorService queryExecutorService) {
        this.compilerService = compilerService;
        this.queryExecutorService = queryExecutorService;
    }

    @PostMapping("/compile")
    public ResponseEntity<CompileResult> compile(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        if (query == null || query.trim().isEmpty()) {
            CompileResult error = new CompileResult();
            error.setExitoso(false);
            error.setMensaje("Query vacía");
            error.setErrores(List.of("No se proporcionó una consulta"));
            return ResponseEntity.badRequest().body(error);
        }
        CompileResult result = compilerService.compile(query);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata() {
        Map<String, Object> meta = queryExecutorService.getLiveDatabaseState();
        
        meta.put("ejemplos", List.of(
                "SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18",
                "SELECCIONAR * DESDE productos",
                "INSERTAR EN usuarios VALORES ('Pedro Ruiz', 28, 'pedro@email.com', 'Sevilla')",
                "MODIFICAR usuarios ESTABLECER ciudad = 'Madrid' CUANDO nombre = 'Ana García'",
                "ELIMINAR DESDE usuarios CUANDO edad < 18",
                "SELECCIONAR nombre, email DESDE usuarios",
                "SELECCIONAR nombre DESDE usuarios CUANDO edad > 25 Y ciudad = 'Madrid'",
                "MODIFICAR productos ESTABLECER precio = 1000 CUANDO nombre = 'Laptop'"
        ));
        meta.put("ejemplos_invalidos", List.of(
                "SELECCIONAR nombre DESDE usuarios CUANDO salario > 50000",
                "SELECCIONAR nombre DESDE empleados",
                "CONSULTAR nombre DESDE usuarios",
                "SELECCIONAR nombre, edad productos CUANDO edad > 18",
                "INSERTAR usuarios VALORES (1, 'test')"
        ));
        return ResponseEntity.ok(meta);
    }
}
