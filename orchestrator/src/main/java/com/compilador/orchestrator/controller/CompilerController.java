package com.compilador.orchestrator.controller;

import com.compilador.orchestrator.service.CompilerService;
import com.compilador.orchestrator.service.QueryExecutorService;
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
    public ResponseEntity<CompilerService.CompileResult> compile(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(compilerService.compile(request.get("query")));
    }

    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata() {
        Map<String, Object> meta = queryExecutorService.getLiveDatabaseState();

        meta.put("ejemplos", List.of(
                "SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18",
                "SELECCIONAR * DESDE productos",
                "INSERTAR EN usuarios VALORES ('Pedro Ruiz', 28, 'pedro@email.com', 'Sevilla')",
                "MODIFICAR usuarios ESTABLECER ciudad = 'Madrid' CUANDO nombre = 'Ana Garcia'",
                "ELIMINAR DESDE usuarios CUANDO edad < 18",
                "SELECCIONAR nombre DESDE usuarios CUANDO edad > 25 Y ciudad = 'Madrid'"
        ));
        meta.put("ejemplos_invalidos", List.of(
                "SELECCIONAR nombre DESDE usuarios CUANDO salario > 50000",
                "SELECCIONAR nombre DESDE empleados",
                "CONSULTAR nombre DESDE usuarios",
                "SELECCIONAR nombre, edad productos CUANDO edad > 18",
                "ELIMINAR DESDE usuarios",
                "INSERTAR usuarios VALORES (1, 'test')"
        ));
        return ResponseEntity.ok(meta);
    }
}
