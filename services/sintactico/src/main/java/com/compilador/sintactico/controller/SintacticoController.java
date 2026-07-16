package com.compilador.sintactico.controller;

import com.compilador.sintactico.parser.GrammarDefinition;
import com.compilador.sintactico.parser.SyntaxAnalyzer;
import com.compilador.shared.model.CompileError;
import com.compilador.shared.model.Token;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/sintactico")
@CrossOrigin(origins = "*")
public class SintacticoController {

    private final SyntaxAnalyzer syntaxAnalyzer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SintacticoController(SyntaxAnalyzer syntaxAnalyzer) {
        this.syntaxAnalyzer = syntaxAnalyzer;
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, Object> request) {
        String query = String.valueOf(request.getOrDefault("query", ""));
        List<Token> tokens = new ArrayList<>();

        Object tokensRaw = request.get("tokens");
        if (tokensRaw instanceof List<?> lista) {
            for (Object item : lista) {
                try {
                    tokens.add(objectMapper.convertValue(item, Token.class));
                } catch (Exception e) {
                    // token malformado, se ignora
                }
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();

        if (tokens.isEmpty()) {
            response.put("ast", null);
            response.put("errores", List.of(new CompileError(
                    CompileError.Fase.SINTACTICO, "SIN_TOKENS",
                    "No hay tokens para analizar sintacticamente.",
                    "Ejecuta primero el analisis lexico.")));
            response.put("query", query);
            response.put("tokens", tokens);
            return ResponseEntity.ok(response);
        }

        SyntaxAnalyzer.SyntaxResult result = syntaxAnalyzer.analyze(tokens, query);

        response.put("ast", result.ast());
        response.put("errores", result.errores());
        response.put("query", result.query());
        response.put("tokens", result.tokens());
        response.put("origenAst", result.origenAst());
        response.put("motivoRespaldo", result.motivoRespaldo());
        response.put("tiempoMs", result.tiempoMs());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/grammar")
    public ResponseEntity<Map<String, String>> getGrammar() {
        return ResponseEntity.ok(Map.of("gramatica", GrammarDefinition.GRAMMAR));
    }
}
