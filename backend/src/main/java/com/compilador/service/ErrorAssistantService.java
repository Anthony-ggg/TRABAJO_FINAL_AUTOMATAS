package com.compilador.service;

import com.compilador.model.CompileError;
import com.compilador.model.SymbolTable;
import com.compilador.parser.GrammarDefinition;
import com.compilador.semantic.SemanticAnalyzer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agente tutor: explica al usuario los errores que YA detecto el compilador.
 *
 * Papel estrictamente acotado. El analisis ya ocurrio: el LLM no vuelve a analizar la
 * consulta, no decide si es valida y no puede contradecir al compilador. Recibe el
 * diagnostico cerrado (consulta, error, metadata, gramatica) y lo traduce a una
 * explicacion didactica.
 *
 * Si el modelo no esta disponible, se devuelve una explicacion generada a partir del
 * propio CompileError, que ya lleva mensaje y sugerencia. El usuario nunca se queda
 * sin ayuda.
 */
@Service
public class ErrorAssistantService {

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SemanticAnalyzer semanticAnalyzer;

    public ErrorAssistantService(SemanticAnalyzer semanticAnalyzer) {
        this.semanticAnalyzer = semanticAnalyzer;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /** Explicacion didactica de un error, en la forma que consume el frontend. */
    public record Explicacion(String explicacion,
                              String causa,
                              String correccion,
                              String ejemploCorrecto,
                              List<String> pasos,
                              String origen) {}

    public Explicacion explicar(String consulta, List<CompileError> errores) {
        if (errores == null || errores.isEmpty()) {
            return new Explicacion("La consulta no presenta errores.", null, null, null, List.of(), "COMPILADOR");
        }

        Explicacion delLlm = consultarLlm(consulta, errores);
        return delLlm != null ? delLlm : explicacionDeRespaldo(errores);
    }

    private Explicacion consultarLlm(String consulta, List<CompileError> errores) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", ollamaModel);
            body.put("prompt", construirPrompt(consulta, errores));
            body.put("stream", false);
            body.put("format", "json");
            body.put("options", Map.of("temperature", 0.3, "num_predict", 700));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(40))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parsearRespuesta(response.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            System.err.println("[Tutor] LLM no disponible: " + e.getMessage());
            return null;
        }
    }

    /**
     * El prompt entrega el diagnostico ya cerrado. Se le prohibe explicitamente
     * reanalizar la consulta o cuestionar el veredicto del compilador.
     */
    private String construirPrompt(String consulta, List<CompileError> errores) {
        StringBuilder listaErrores = new StringBuilder();
        for (CompileError e : errores) {
            listaErrores.append("- [fase ").append(e.getFase()).append("] ").append(e.getMensaje());
            if (e.getSugerencia() != null && !e.getSugerencia().isBlank()) {
                listaErrores.append(" (pista del compilador: ").append(e.getSugerencia()).append(")");
            }
            listaErrores.append("\n");
        }

        return """
            Eres un tutor que ayuda a estudiantes a entender los errores de un mini-compilador
            de SQL en espanol. Hablas de forma clara, cercana y didactica.

            IMPORTANTE: el analisis YA fue realizado por el compilador. No vuelvas a analizar
            la consulta, no discutas el diagnostico y no inventes errores distintos. Tu unica
            tarea es EXPLICAR el error indicado y guiar al usuario para corregirlo.

            CONSULTA DEL USUARIO:
            %s

            ERRORES DETECTADOS POR EL COMPILADOR:
            %s

            ESQUEMA DE LA BASE DE DATOS:
            %s

            GRAMATICA DEL LENGUAJE:
            %s

            Responde UNICAMENTE con este JSON:
            {
              "explicacion": "que ocurrio, en lenguaje sencillo, sin tecnicismos",
              "causa": "por que se produjo el error",
              "correccion": "que debe cambiar el usuario para arreglarlo",
              "ejemplo_correcto": "una consulta valida y concreta, usando SOLO tablas y columnas del esquema",
              "pasos": ["paso 1", "paso 2", "paso 3"]
            }
            """.formatted(consulta, listaErrores, describirEsquema(), GrammarDefinition.GRAMMAR);
    }

    private String describirEsquema() {
        SymbolTable tabla = semanticAnalyzer.getSymbolTable();
        StringBuilder sb = new StringBuilder();
        for (String nombre : tabla.getTablasDisponibles()) {
            sb.append("- ").append(nombre).append(": ");
            List<SymbolTable.ColumnaInfo> cols = tabla.getColumnas(nombre);
            if (cols != null) {
                sb.append(cols.stream()
                        .map(c -> c.getNombre() + " (" + c.getTipo() + ")")
                        .reduce((a, b) -> a + ", " + b).orElse(""));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private Explicacion parsearRespuesta(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null || !root.has("response")) return null;

            String texto = root.get("response").asText();
            Matcher m = Pattern.compile("\\{.*\\}", Pattern.DOTALL).matcher(texto);
            if (!m.find()) return null;

            JsonNode json = objectMapper.readTree(m.group());

            List<String> pasos = new ArrayList<>();
            if (json.has("pasos") && json.get("pasos").isArray()) {
                json.get("pasos").forEach(p -> pasos.add(p.asText()));
            }

            String explicacion = texto(json, "explicacion");
            if (explicacion == null || explicacion.isBlank()) return null;

            return new Explicacion(
                    explicacion,
                    texto(json, "causa"),
                    texto(json, "correccion"),
                    texto(json, "ejemplo_correcto"),
                    pasos,
                    "LLM");

        } catch (Exception e) {
            System.err.println("[Tutor] Respuesta ilegible: " + e.getMessage());
            return null;
        }
    }

    private String texto(JsonNode json, String campo) {
        return json.has(campo) ? json.get(campo).asText() : null;
    }

    /**
     * Explicacion construida sin el LLM, a partir del propio error del compilador.
     * Garantiza que el usuario siempre reciba ayuda aunque Ollama este caido.
     */
    private Explicacion explicacionDeRespaldo(List<CompileError> errores) {
        CompileError principal = errores.get(0);

        String causa = switch (principal.getFase()) {
            case LEXICO -> "La consulta contiene simbolos que no forman parte del lenguaje.";
            case SINTACTICO -> "La consulta no respeta la estructura definida por la gramatica.";
            case SEMANTICO -> "La consulta esta bien escrita, pero hace referencia a algo que no existe "
                    + "en la base de datos o intenta una operacion no permitida.";
            case EJECUCION -> "La consulta no pudo ejecutarse contra la base de datos.";
        };

        List<String> pasos = new ArrayList<>();
        pasos.add("Revisa el fragmento senalado: " + (principal.getTokenAfectado() != null
                ? "'" + principal.getTokenAfectado() + "'" : "el indicado en el mensaje"));
        if (principal.getSugerencia() != null && !principal.getSugerencia().isBlank()) {
            pasos.add(principal.getSugerencia());
        }
        pasos.add("Vuelve a compilar la consulta corregida.");

        return new Explicacion(
                principal.getMensaje(),
                causa,
                principal.getSugerencia(),
                "SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18",
                pasos,
                "COMPILADOR");
    }
}
