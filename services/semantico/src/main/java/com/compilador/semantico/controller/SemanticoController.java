package com.compilador.semantico.controller;

import com.compilador.semantico.semantic.SemanticAnalyzer;
import com.compilador.shared.model.ASTNode;
import com.compilador.shared.model.CompileError;
import com.compilador.shared.model.Token;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/semantico")
@CrossOrigin(origins = "*")
public class SemanticoController {

    private final SemanticAnalyzer semanticAnalyzer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SemanticoController(SemanticAnalyzer semanticAnalyzer) {
        this.semanticAnalyzer = semanticAnalyzer;
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, Object> request) {
        String query = String.valueOf(request.getOrDefault("query", ""));
        List<Token> tokens = new ArrayList<>();
        ASTNode ast = null;

        Object tokensRaw = request.get("tokens");
        if (tokensRaw instanceof List<?> lista) {
            for (Object item : lista) {
                try {
                    tokens.add(objectMapper.convertValue(item, Token.class));
                } catch (Exception e) {
                    // token malformado
                }
            }
        }

        Object astRaw = request.get("ast");
        if (astRaw != null) {
            try {
                ast = objectMapper.convertValue(astRaw, ASTNode.class);
            } catch (Exception e) {
                // ast malformado
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();

        SemanticAnalyzer.SemanticResult result = semanticAnalyzer.validate(ast, tokens);

        response.put("query", query);
        response.put("tokens", tokens);
        response.put("ast", ast);
        response.put("errores", result.errores());
        response.put("tablaSimbolos", result.simbolos());
        response.put("tiempoMs", result.tiempoMs());
        response.put("valido", result.valido());
        return ResponseEntity.ok(response);
    }
}
