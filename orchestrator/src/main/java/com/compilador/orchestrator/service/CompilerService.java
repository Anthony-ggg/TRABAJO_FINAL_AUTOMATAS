package com.compilador.orchestrator.service;

import com.compilador.shared.model.ASTNode;
import com.compilador.shared.model.CompileError;
import com.compilador.shared.model.Token;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class CompilerService {

    private static final int MAX_LONGITUD_CONSULTA = 1000;

    @Value("${lexico.url:http://localhost:8081}")
    private String lexicoUrl;

    @Value("${sintactico.url:http://localhost:8082}")
    private String sintacticoUrl;

    @Value("${semantico.url:http://localhost:8083}")
    private String semanticoUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final QueryExecutorService queryExecutorService;

    public CompilerService(QueryExecutorService queryExecutorService) {
        this.queryExecutorService = queryExecutorService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public CompileResult compile(String query) {
        long inicioTotal = System.nanoTime();
        CompileResult result = new CompileResult();
        result.setFraseOriginal(query);
        result.setErrores(new ArrayList<>());
        result.setErroresDetallados(new ArrayList<>());

        try {
            // Validacion de entrada
            if (query == null || query.isBlank()) {
                result.setExitoso(false);
                result.setMensaje("Consulta vacia");
                result.agregarError(new CompileError(CompileError.Fase.LEXICO, "CONSULTA_VACIA",
                        "No se proporciono ninguna consulta.",
                        "Escribe una consulta, por ejemplo: SELECCIONAR * DESDE usuarios"));
                return result;
            }

            if (query.length() > MAX_LONGITUD_CONSULTA) {
                result.setExitoso(false);
                result.setMensaje("Consulta demasiado larga");
                result.agregarError(new CompileError(CompileError.Fase.LEXICO, "CONSULTA_DEMASIADO_LARGA",
                        "La consulta supera el limite de " + MAX_LONGITUD_CONSULTA + " caracteres.",
                        "Acorta la consulta."));
                return result;
            }

            // 1. Analisis lexico
            System.out.println("[Orchestrator] Llamando a microservicio lexico...");
            Map<String, Object> lexicoResponse = callService(lexicoUrl + "/api/lexico/analyze",
                    Map.of("query", query));

            List<Token> tokens = parseTokens(lexicoResponse.get("tokens"));
            List<CompileError> lexicoErrores = parseErrores(lexicoResponse.get("errores"));

            result.setTokens(tokens);
            result.setObservaciones(parseObservaciones(lexicoResponse.get("observaciones")));

            if (!lexicoErrores.isEmpty()) {
                result.agregarErrores(lexicoErrores);
                result.setExitoso(false);
                result.setMensaje("Error lexico");
                return result;
            }

            if (tokens.isEmpty()) {
                result.setExitoso(false);
                result.setMensaje("Error lexico");
                result.agregarError(new CompileError(CompileError.Fase.LEXICO, "SIN_TOKENS",
                        "No se reconocio ningun token en la consulta.",
                        "Escribe una consulta valida, por ejemplo: SELECCIONAR * DESDE usuarios"));
                return result;
            }

            // 2. Analisis sintactico
            System.out.println("[Orchestrator] Llamando a microservicio sintactico...");
            Map<String, Object> sintacticoRequest = new LinkedHashMap<>();
            sintacticoRequest.put("query", query);
            sintacticoRequest.put("tokens", tokens);
            Map<String, Object> sintacticoResponse = callService(sintacticoUrl + "/api/sintactico/analyze",
                    sintacticoRequest);

            ASTNode ast = parseAST(sintacticoResponse.get("ast"));
            List<CompileError> sintacticoErrores = parseErrores(sintacticoResponse.get("errores"));

            result.setAst(ast);

            if (!sintacticoErrores.isEmpty()) {
                result.agregarErrores(sintacticoErrores);
                result.setExitoso(false);
                result.setMensaje("Error sintactico");
                return result;
            }

            // 3. Analisis semantico
            System.out.println("[Orchestrator] Llamando a microservicio semantico...");
            Map<String, Object> semanticoRequest = new LinkedHashMap<>();
            semanticoRequest.put("query", query);
            semanticoRequest.put("tokens", tokens);
            semanticoRequest.put("ast", ast);
            Map<String, Object> semanticoResponse = callService(semanticoUrl + "/api/semantico/analyze",
                    semanticoRequest);

            List<CompileError> semanticoErrores = parseErrores(semanticoResponse.get("errores"));
            result.setTablaSimbolos(parseMap(semanticoResponse.get("tablaSimbolos")));

            if (!semanticoErrores.isEmpty()) {
                result.agregarErrores(semanticoErrores);
                result.setExitoso(false);
                result.setMensaje("Error semantico");
                return result;
            }

            // 4. Ejecucion
            System.out.println("[Orchestrator] Ejecutando consulta...");
            queryExecutorService.execute(ast, result);

            if (!result.getErroresDetallados().isEmpty()) {
                result.setExitoso(false);
                result.setMensaje("Error de ejecucion");
                return result;
            }

            result.setExitoso(true);
            result.setMensaje("Compilacion exitosa");

        } catch (Exception e) {
            System.err.println("[Orchestrator] Error inesperado: " + e.getMessage());
            result.setExitoso(false);
            result.setMensaje("Error interno del compilador");
            result.agregarError(new CompileError(CompileError.Fase.EJECUCION, "ERROR_INTERNO",
                    "El compilador encontro un error inesperado al procesar la consulta.",
                    "Revisa la sintaxis de la consulta e intentalo de nuevo."));
        } finally {
            if (result.getObservaciones() != null) {
                result.getObservaciones().setTiempoTotalCompilacion(
                        (System.nanoTime() - inicioTotal) / 1_000_000_000.0);
            }
        }

        return result;
    }

    private Map<String, Object> callService(String url, Map<String, Object> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    private List<Token> parseTokens(Object raw) {
        if (raw == null) return new ArrayList<>();
        return objectMapper.convertValue(raw, new TypeReference<List<Token>>() {});
    }

    private List<CompileError> parseErrores(Object raw) {
        if (raw == null) return new ArrayList<>();
        return objectMapper.convertValue(raw, new TypeReference<List<CompileError>>() {});
    }

    private ASTNode parseAST(Object raw) {
        if (raw == null) return null;
        return objectMapper.convertValue(raw, ASTNode.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(Object raw) {
        if (raw == null) return new LinkedHashMap<>();
        return objectMapper.convertValue(raw, new TypeReference<>() {});
    }

    private CompileResult.Observaciones parseObservaciones(Object raw) {
        if (raw == null) {
            CompileResult.Observaciones obs = new CompileResult.Observaciones();
            obs.setTiemposPorFase(new LinkedHashMap<>());
            return obs;
        }
        return objectMapper.convertValue(raw, CompileResult.Observaciones.class);
    }

    public static class CompileResult {
        private boolean exitoso;
        private String mensaje;
        private List<Token> tokens;
        private ASTNode ast;
        private Map<String, Object> tablaSimbolos;
        private String fraseOriginal;
        private Observaciones observaciones;
        private List<String> errores = new ArrayList<>();
        private List<CompileError> erroresDetallados = new ArrayList<>();
        private List<Map<String, Object>> filasResultado;
        private String mensajeEjecucion;

        public void agregarError(CompileError error) {
            this.erroresDetallados.add(error);
            this.errores.add(error.getMensaje());
        }

        public void agregarErrores(List<CompileError> nuevos) {
            nuevos.forEach(this::agregarError);
        }

        public boolean isExitoso() { return exitoso; }
        public void setExitoso(boolean exitoso) { this.exitoso = exitoso; }
        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }
        public List<Token> getTokens() { return tokens; }
        public void setTokens(List<Token> tokens) { this.tokens = tokens; }
        public ASTNode getAst() { return ast; }
        public void setAst(ASTNode ast) { this.ast = ast; }
        public Map<String, Object> getTablaSimbolos() { return tablaSimbolos; }
        public void setTablaSimbolos(Map<String, Object> tablaSimbolos) { this.tablaSimbolos = tablaSimbolos; }
        public String getFraseOriginal() { return fraseOriginal; }
        public void setFraseOriginal(String fraseOriginal) { this.fraseOriginal = fraseOriginal; }
        public Observaciones getObservaciones() { return observaciones; }
        public void setObservaciones(Observaciones observaciones) { this.observaciones = observaciones; }
        public List<String> getErrores() { return errores; }
        public void setErrores(List<String> errores) { this.errores = errores; }
        public List<CompileError> getErroresDetallados() { return erroresDetallados; }
        public void setErroresDetallados(List<CompileError> e) { this.erroresDetallados = e; }
        public List<Map<String, Object>> getFilasResultado() { return filasResultado; }
        public void setFilasResultado(List<Map<String, Object>> filasResultado) { this.filasResultado = filasResultado; }
        public String getMensajeEjecucion() { return mensajeEjecucion; }
        public void setMensajeEjecucion(String mensajeEjecucion) { this.mensajeEjecucion = mensajeEjecucion; }

        public static class Observaciones {
            private List<String> tokensAFD = List.of();
            private List<String> tokensLLM = List.of();
            private int hilosConcurrentes;
            private double tiempoTotalLexico;
            private double tiempoTotalCompilacion;
            private Map<String, Long> tiemposPorFase;
            private int tokensConfirmados;
            private List<String> tokensSoloAFD = List.of();
            private List<String> tokensAlucinados = List.of();
            private List<String> discrepanciasTipo = List.of();
            private double coincidenciaLexica;
            private boolean llmDisponible;
            private String origenAst;
            private String motivoRespaldoAst;

            public List<String> getTokensAFD() { return tokensAFD; }
            public void setTokensAFD(List<String> tokensAFD) { this.tokensAFD = tokensAFD; }
            public List<String> getTokensLLM() { return tokensLLM; }
            public void setTokensLLM(List<String> tokensLLM) { this.tokensLLM = tokensLLM; }
            public int getHilosConcurrentes() { return hilosConcurrentes; }
            public void setHilosConcurrentes(int h) { this.hilosConcurrentes = h; }
            public double getTiempoTotalLexico() { return tiempoTotalLexico; }
            public void setTiempoTotalLexico(double t) { this.tiempoTotalLexico = t; }
            public double getTiempoTotalCompilacion() { return tiempoTotalCompilacion; }
            public void setTiempoTotalCompilacion(double t) { this.tiempoTotalCompilacion = t; }
            public Map<String, Long> getTiemposPorFase() { return tiemposPorFase; }
            public void setTiemposPorFase(Map<String, Long> t) { this.tiemposPorFase = t; }
            public int getTokensConfirmados() { return tokensConfirmados; }
            public void setTokensConfirmados(int t) { this.tokensConfirmados = t; }
            public List<String> getTokensSoloAFD() { return tokensSoloAFD; }
            public void setTokensSoloAFD(List<String> t) { this.tokensSoloAFD = t; }
            public List<String> getTokensAlucinados() { return tokensAlucinados; }
            public void setTokensAlucinados(List<String> t) { this.tokensAlucinados = t; }
            public List<String> getDiscrepanciasTipo() { return discrepanciasTipo; }
            public void setDiscrepanciasTipo(List<String> t) { this.discrepanciasTipo = t; }
            public double getCoincidenciaLexica() { return coincidenciaLexica; }
            public void setCoincidenciaLexica(double c) { this.coincidenciaLexica = c; }
            public boolean isLlmDisponible() { return llmDisponible; }
            public void setLlmDisponible(boolean l) { this.llmDisponible = l; }
            public String getOrigenAst() { return origenAst; }
            public void setOrigenAst(String o) { this.origenAst = o; }
            public String getMotivoRespaldoAst() { return motivoRespaldoAst; }
            public void setMotivoRespaldoAst(String m) { this.motivoRespaldoAst = m; }
        }
    }
}
