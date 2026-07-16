package com.compilador.lexico.lexer;

import com.compilador.shared.model.Token;
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
        Eres el analizador lexico de un mini-compilador de SQL en espanol.
        Divide la consulta en tokens y clasifica cada uno.

        Tipos validos: PALABRA_RESERVADA, IDENTIFICADOR, NUMERO, CADENA, OPERADOR, SIMBOLO
        Palabras reservadas: SELECCIONAR, INSERTAR, MODIFICAR, ELIMINAR, DESDE, CUANDO, EN, VALORES, ESTABLECER, Y, O
        NUMERO: digitos, con decimales opcionales
        CADENA: texto entre comillas simples (incluye las comillas en el valor)
        OPERADOR: = > < >= <= <> !=
        SIMBOLO: ( ) , *
        IDENTIFICADOR: cualquier otro nombre (tablas, columnas)

        Reglas estrictas:
        - Copia cada valor EXACTAMENTE como aparece en la consulta.
        - No inventes tokens que no esten en el texto.
        - Responde UNICAMENTE con un array JSON, sin explicaciones.

        Ejemplo:
        Entrada: SELECCIONAR nombre DESDE usuarios CUANDO edad > 18
        Salida: [{"tipo":"PALABRA_RESERVADA","valor":"SELECCIONAR"},{"tipo":"IDENTIFICADOR","valor":"nombre"},{"tipo":"PALABRA_RESERVADA","valor":"DESDE"},{"tipo":"IDENTIFICADOR","valor":"usuarios"},{"tipo":"PALABRA_RESERVADA","valor":"CUANDO"},{"tipo":"IDENTIFICADOR","valor":"edad"},{"tipo":"OPERADOR","valor":">"},{"tipo":"NUMERO","valor":"18"}]
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
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", ollamaModel);
            requestBody.put("prompt", SYSTEM_PROMPT + "\n\nEntrada: " + input + "\n\nSalida:");
            requestBody.put("stream", false);
            requestBody.put("options", Map.of("temperature", 0.0, "num_predict", 512));

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
            return List.of();
        } catch (Exception e) {
            System.err.println("[LLM-Lexico] No disponible: " + e.getMessage());
            return List.of();
        }
    }

    private List<Token> parseResponse(String responseBody, String input) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null || !root.has("response")) return List.of();

            String responseText = root.get("response").asText();
            Matcher matcher = Pattern.compile("\\[.*?\\]", Pattern.DOTALL).matcher(responseText);
            if (!matcher.find()) return List.of();

            JsonNode tokensNode = objectMapper.readTree(matcher.group());
            if (!tokensNode.isArray()) return List.of();

            List<Token> tokens = new ArrayList<>();
            int cursor = 0;

            for (JsonNode node : tokensNode) {
                if (!node.has("tipo") || !node.has("valor")) continue;
                String tipo = node.get("tipo").asText();
                String valor = node.get("valor").asText();
                if (valor.isEmpty()) continue;

                int pos = input.indexOf(valor, cursor);
                if (pos < 0) {
                    pos = input.indexOf(valor);
                }
                if (pos >= 0) {
                    cursor = pos + valor.length();
                }
                tokens.add(new Token(tipo, valor, "LLM", pos));
            }
            return tokens;

        } catch (Exception e) {
            System.err.println("[LLM-Lexico] Error al interpretar la respuesta: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public String getNombre() {
        return "LLM";
    }
}
