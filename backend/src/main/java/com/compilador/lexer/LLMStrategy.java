package com.compilador.lexer;

import com.compilador.model.Token;
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
public class LLMStrategy implements TokenStrategy {

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        Clasifica estos tokens para SQL en español.
        Tipos: PALABRA_RESERVADA, IDENTIFICADOR, NUMERO, CADENA, OPERADOR, SIMBOLO
        Palabras reservadas: SELECCIONAR, INSERTAR, MODIFICAR, ELIMINAR, DESDE, CUANDO, EN, VALORES, ESTABLECER, Y, O
        Numeros: digitos
        Cadenas: entre comillas simples
        Operadores: = > < >= <= <> !=
        Simbolos: ( ) , *
        Responde SOLO con JSON array.
        Ejemplo: [{"tipo":"PALABRA_RESERVADA","valor":"SELECCIONAR"},{"tipo":"IDENTIFICADOR","valor":"nombre"}]
        """;

    public LLMStrategy() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<Token> classify(String input) {
        try {
            // Simulate LLM processing time ~1s to demonstrate concurrency
            Thread.sleep(800);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", ollamaModel);
            requestBody.put("prompt", SYSTEM_PROMPT + "\n\nEntrada: " + input + "\n\nJSON:");
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
            return parseResponse(response.body(), input);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallbackTokenize(input);
        } catch (Exception e) {
            System.err.println("[LLM] Error: " + e.getMessage());
            return fallbackTokenize(input);
        }
    }

    private List<Token> parseResponse(String responseBody, String input) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null || !root.has("response")) {
                return fallbackTokenize(input);
            }
            String responseText = root.get("response").asText();

            Pattern jsonPattern = Pattern.compile("\\[.*?\\]", Pattern.DOTALL);
            Matcher matcher = jsonPattern.matcher(responseText);
            if (matcher.find()) {
                String jsonArray = matcher.group();
                JsonNode tokensNode = objectMapper.readTree(jsonArray);
                if (!tokensNode.isArray() || tokensNode.isEmpty()) {
                    return fallbackTokenize(input);
                }
                List<Token> tokens = new ArrayList<>();
                int pos = 0;
                for (JsonNode node : tokensNode) {
                    if (!node.has("tipo") || !node.has("valor")) continue;
                    String tipo = node.get("tipo").asText();
                    String valor = node.get("valor").asText();
                    tokens.add(new Token(tipo, valor, "LLM", pos));
                    pos += valor.length() + 1;
                }
                if (!tokens.isEmpty()) {
                    return tokens;
                }
            }
        } catch (Exception e) {
            System.err.println("[LLM] Parse error: " + e.getMessage());
            System.err.println("[LLM] Response: " + (responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody));
        }
        return fallbackTokenize(input);
    }

    private List<Token> fallbackTokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        String[] parts = input.split("\\s+");
        int pos = 0;
        for (String part : parts) {
            String tipo = "IDENTIFICADOR";
            String clean = part.replaceAll("[(),;]", "");
            String upper = clean.toUpperCase();
            if (List.of("SELECCIONAR","INSERTAR","MODIFICAR","ELIMINAR","DESDE","CUANDO","EN","VALORES","ESTABLECER","Y","O").contains(upper)) {
                tipo = "PALABRA_RESERVADA";
            } else if (clean.matches("\\d+(\\.\\d+)?")) {
                tipo = "NUMERO";
            }
            tokens.add(new Token(tipo, clean, "LLM-fallback", pos));
            pos += part.length() + 1;
        }
        return tokens;
    }

    @Override
    public String getNombre() {
        return "LLM";
    }
}
