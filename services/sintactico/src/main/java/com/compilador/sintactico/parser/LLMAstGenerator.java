package com.compilador.sintactico.parser;

import com.compilador.shared.model.ASTNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LLMAstGenerator {

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LLMAstGenerator() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public ASTNode generar(String consulta) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", ollamaModel);
            body.put("prompt", GrammarDefinition.AST_PROMPT + "\n\nConsulta: " + consulta + "\n\nRespuesta:");
            body.put("stream", false);
            body.put("format", "json");
            body.put("options", Map.of("temperature", 0.0, "num_predict", 512));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return convertirAAst(extraerJson(response.body()));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            System.err.println("[LLM-AST] No disponible: " + e.getMessage());
            return null;
        }
    }

    private JsonNode extraerJson(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        if (root == null || !root.has("response")) return null;
        String texto = root.get("response").asText();
        Matcher m = Pattern.compile("\\{.*\\}", Pattern.DOTALL).matcher(texto);
        if (!m.find()) return null;
        return objectMapper.readTree(m.group());
    }

    private ASTNode convertirAAst(JsonNode json) {
        if (json == null || !json.has("tipo")) return null;

        String tipo = json.get("tipo").asText().toUpperCase();
        if (!tipo.equals("SELECCIONAR") && !tipo.equals("INSERTAR")
                && !tipo.equals("MODIFICAR") && !tipo.equals("ELIMINAR")) {
            return null;
        }

        ASTNode raiz = new ASTNode(tipo, tipo);

        if ("SELECCIONAR".equals(tipo)) {
            ASTNode columnas = new ASTNode("COLUMNAS", "columns");
            if (json.has("columnas")) {
                for (JsonNode c : json.get("columnas")) {
                    String valor = c.asText();
                    String tipoCol = "*".equals(valor) ? "SIMBOLO" : "IDENTIFICADOR";
                    columnas.agregarHijo(new ASTNode(tipoCol, valor));
                }
            }
            raiz.agregarHijo(columnas);
        }

        if (json.has("tabla")) {
            raiz.agregarHijo(new ASTNode("TABLA", json.get("tabla").asText()));
        }

        if ("INSERTAR".equals(tipo) && json.has("valores")) {
            ASTNode valores = new ASTNode("VALORES", "values");
            for (JsonNode v : json.get("valores")) {
                String valor = v.asText();
                valores.agregarHijo(new ASTNode(tipoDeLiteral(valor), valor));
            }
            raiz.agregarHijo(valores);
        }

        if ("MODIFICAR".equals(tipo) && json.has("asignaciones")) {
            ASTNode asignaciones = new ASTNode("ASIGNACIONES", "set");
            for (JsonNode a : json.get("asignaciones")) {
                if (!a.has("columna") || !a.has("valor")) continue;
                String valor = a.get("valor").asText();
                asignaciones.agregarHijo(new ASTNode("IDENTIFICADOR", a.get("columna").asText()));
                asignaciones.agregarHijo(new ASTNode("OPERADOR", "="));
                asignaciones.agregarHijo(new ASTNode(tipoDeLiteral(valor), valor));
            }
            raiz.agregarHijo(asignaciones);
        }

        if (json.has("condiciones") && json.get("condiciones").isArray()
                && !json.get("condiciones").isEmpty()) {
            ASTNode condicion = new ASTNode("CONDICION", "where");
            JsonNode condiciones = json.get("condiciones");
            JsonNode conectores = json.has("conectores") ? json.get("conectores") : null;

            for (int i = 0; i < condiciones.size(); i++) {
                JsonNode c = condiciones.get(i);
                if (!c.has("columna") || !c.has("operador") || !c.has("valor")) continue;

                if (i > 0) {
                    String conector = (conectores != null && conectores.size() >= i)
                            ? conectores.get(i - 1).asText() : "Y";
                    condicion.agregarHijo(new ASTNode("OPERADOR_LOGICO", conector));
                }
                String valor = c.get("valor").asText();
                condicion.agregarHijo(new ASTNode("IDENTIFICADOR", c.get("columna").asText()));
                condicion.agregarHijo(new ASTNode("OPERADOR", c.get("operador").asText()));
                condicion.agregarHijo(new ASTNode(tipoDeLiteral(valor), valor));
            }
            raiz.agregarHijo(condicion);
        }

        return raiz;
    }

    private String tipoDeLiteral(String valor) {
        if (valor.startsWith("'") && valor.endsWith("'")) return "CADENA";
        if (valor.matches("\\d+(\\.\\d+)?")) return "NUMERO";
        return "IDENTIFICADOR";
    }
}
