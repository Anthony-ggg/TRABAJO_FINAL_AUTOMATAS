package com.compilador.lexico.controller;

import com.compilador.lexico.lexer.AutomataStrategy;
import com.compilador.lexico.lexer.LexicalAnalyzer;
import com.compilador.shared.model.CompileError;
import com.compilador.shared.model.Token;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/lexico")
@CrossOrigin(origins = "*")
public class LexicoController {

    private final LexicalAnalyzer lexicalAnalyzer;
    private final AutomataStrategy automataStrategy;

    public LexicoController(LexicalAnalyzer lexicalAnalyzer, AutomataStrategy automataStrategy) {
        this.lexicalAnalyzer = lexicalAnalyzer;
        this.automataStrategy = automataStrategy;
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        Map<String, Object> response = new LinkedHashMap<>();

        if (query == null || query.isBlank()) {
            response.put("tokens", List.of());
            response.put("errores", List.of(new CompileError(
                    CompileError.Fase.LEXICO, "CONSULTA_VACIA",
                    "No se proporciono ninguna consulta.",
                    "Escribe una consulta valida.")));
            response.put("observaciones", null);
            return ResponseEntity.ok(response);
        }

        LexicalAnalyzer.ResultadoLexico resultado = lexicalAnalyzer.analyze(query);

        response.put("tokens", resultado.tokens());
        response.put("errores", resultado.errores());
        response.put("observaciones", resultado.observaciones());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/automata")
    public ResponseEntity<Map<String, Object>> getAutomata() {
        return ResponseEntity.ok(automataStrategy.getDefinicionAutomata());
    }
}
