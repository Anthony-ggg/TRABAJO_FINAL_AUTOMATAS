package com.compilador.parser;

import com.compilador.model.ASTNode;
import com.compilador.model.Token;
import com.compilador.ast.ASTBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SyntaxAnalyzer {

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SyntaxAnalyzer() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public SyntaxResult analyze(List<Token> tokens) {
        long start = System.nanoTime();

        // First, build AST deterministically from tokens
        ASTNode ast = ASTBuilder.buildFromTokens(tokens);

        // Validate syntax using LLM
        SyntaxResult llmResult = validateWithLLM(tokens);

        long elapsed = (System.nanoTime() - start) / 1_000_000;

        if (llmResult != null && !llmResult.valido) {
            return new SyntaxResult(false, llmResult.errores, ast, llmResult.tipoConsulta, elapsed);
        }

        // Also do basic deterministic validation
        List<String> errors = validateBasic(tokens);
        if (!errors.isEmpty()) {
            return new SyntaxResult(false, errors, ast, detectType(tokens), elapsed);
        }

        return new SyntaxResult(true, List.of(), ast,
                llmResult != null ? llmResult.tipoConsulta : detectType(tokens), elapsed);
    }

    private SyntaxResult validateWithLLM(List<Token> tokens) {
        try {
            String tokensJson = objectMapper.writeValueAsString(
                    tokens.stream().map(t -> Map.of("tipo", t.getTipo(), "valor", t.getValor())).toList()
            );

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", ollamaModel);
            requestBody.put("prompt", GrammarDefinition.SYSTEM_PROMPT
                    + "\n\nTokens a analizar:\n" + tokensJson);
            requestBody.put("stream", false);
            requestBody.put("options", Map.of("temperature", 0.1, "num_predict", 512));

            String json = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseLLMResponse(response.body());

        } catch (Exception e) {
            System.err.println("LLM Syntax error: " + e.getMessage());
            return null;
        }
    }

    private SyntaxResult parseLLMResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null || !root.has("response")) {
                return null;
            }
            String responseText = root.get("response").asText();

            Pattern jsonPattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
            Matcher matcher = jsonPattern.matcher(responseText);
            if (matcher.find()) {
                JsonNode result = objectMapper.readTree(matcher.group());
                boolean valido = result.has("valido") && result.get("valido").asBoolean();
                List<String> errores = new ArrayList<>();
                if (result.has("errores")) {
                    for (JsonNode e : result.get("errores")) {
                        errores.add(e.asText());
                    }
                }
                String tipo = result.has("tipo_consulta") ? result.get("tipo_consulta").asText() : "DESCONOCIDO";
                return new SyntaxResult(valido, errores, null, tipo, 0);
            }
        } catch (Exception e) {
            System.err.println("[Syntax] LLM parse error: " + e.getMessage());
        }
        return null;
    }

    private List<String> validateBasic(List<Token> tokens) {
        List<String> errors = new ArrayList<>();
        if (tokens.isEmpty()) {
            errors.add("Consulta vacía");
            return errors;
        }

        String firstUpper = tokens.get(0).getValor().toUpperCase();
        Set<String> validStart = Set.of("SELECCIONAR", "INSERTAR", "MODIFICAR", "ELIMINAR");
        if (!validStart.contains(firstUpper)) {
            errors.add("La consulta debe comenzar con SELECCIONAR, INSERTAR, MODIFICAR o ELIMINAR");
            return errors;
        }

        return errors;
    }

    private String detectType(List<Token> tokens) {
        if (tokens.isEmpty()) return "DESCONOCIDO";
        return tokens.get(0).getValor().toUpperCase();
    }

    public record SyntaxResult(boolean valido, List<String> errores, ASTNode ast, String tipoConsulta, long tiempoMs) {}
}
