package com.compilador.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompileResult {
    private boolean exitoso;
    private String mensaje;
    private List<Token> tokens;
    private ASTNode ast;
    private Map<String, Object> tablaSimbolos;
    private String fraseOriginal;
    private Observaciones observaciones;

    /** Errores en texto plano. Se conserva por compatibilidad con el frontend anterior. */
    private List<String> errores = new ArrayList<>();

    /** Errores estructurados (fase, codigo, posicion, sugerencia). Fuente para el agente tutor. */
    private List<CompileError> erroresDetallados = new ArrayList<>();

    private List<Map<String, Object>> filasResultado;
    private String mensajeEjecucion;

    public CompileResult() {}

    /** Registra un error manteniendo sincronizadas ambas listas. */
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

    /** Metricas de las fases del compilador, expuestas en la vista Avanzada. */
    public static class Observaciones {
        private List<String> tokensAFD = List.of();
        private List<String> tokensLLM = List.of();
        private int hilosConcurrentes;
        private double tiempoTotalLexico;
        private double tiempoTotalCompilacion;
        private Map<String, Long> tiemposPorFase;

        // --- Conciliacion lexica LLM vs AFD ---
        /** Tokens que LLM y AFD reconocieron identicos en la misma posicion. */
        private int tokensConfirmados;
        /** Tokens que solo reconocio el AFD (el LLM los omitio). */
        private List<String> tokensSoloAFD = List.of();
        /** Tokens inventados por el LLM que no existen en la consulta: descartados. */
        private List<String> tokensAlucinados = List.of();
        /** Tokens confirmados pero con tipo discrepante entre LLM y AFD. */
        private List<String> discrepanciasTipo = List.of();
        /** Porcentaje de tokens del AFD que el LLM confirmo. */
        private double coincidenciaLexica;
        private boolean llmDisponible;

        // --- Generacion del AST ---
        /** "LLM" si el AST provino del modelo y supero la validacion; "PARSER_DETERMINISTA" si actuo el respaldo. */
        private String origenAst;
        /** Motivo por el que se descarto el AST del LLM, si aplica. */
        private String motivoRespaldoAst;

        public List<String> getTokensAFD() { return tokensAFD; }
        public void setTokensAFD(List<String> tokensAFD) { this.tokensAFD = tokensAFD; }
        public List<String> getTokensLLM() { return tokensLLM; }
        public void setTokensLLM(List<String> tokensLLM) { this.tokensLLM = tokensLLM; }
        public int getHilosConcurrentes() { return hilosConcurrentes; }
        public void setHilosConcurrentes(int hilosConcurrentes) { this.hilosConcurrentes = hilosConcurrentes; }
        public double getTiempoTotalLexico() { return tiempoTotalLexico; }
        public void setTiempoTotalLexico(double t) { this.tiempoTotalLexico = t; }
        public double getTiempoTotalCompilacion() { return tiempoTotalCompilacion; }
        public void setTiempoTotalCompilacion(double t) { this.tiempoTotalCompilacion = t; }
        public Map<String, Long> getTiemposPorFase() { return tiemposPorFase; }
        public void setTiemposPorFase(Map<String, Long> tiemposPorFase) { this.tiemposPorFase = tiemposPorFase; }
        public int getTokensConfirmados() { return tokensConfirmados; }
        public void setTokensConfirmados(int tokensConfirmados) { this.tokensConfirmados = tokensConfirmados; }
        public List<String> getTokensSoloAFD() { return tokensSoloAFD; }
        public void setTokensSoloAFD(List<String> tokensSoloAFD) { this.tokensSoloAFD = tokensSoloAFD; }
        public List<String> getTokensAlucinados() { return tokensAlucinados; }
        public void setTokensAlucinados(List<String> tokensAlucinados) { this.tokensAlucinados = tokensAlucinados; }
        public List<String> getDiscrepanciasTipo() { return discrepanciasTipo; }
        public void setDiscrepanciasTipo(List<String> discrepanciasTipo) { this.discrepanciasTipo = discrepanciasTipo; }
        public double getCoincidenciaLexica() { return coincidenciaLexica; }
        public void setCoincidenciaLexica(double c) { this.coincidenciaLexica = c; }
        public boolean isLlmDisponible() { return llmDisponible; }
        public void setLlmDisponible(boolean llmDisponible) { this.llmDisponible = llmDisponible; }
        public String getOrigenAst() { return origenAst; }
        public void setOrigenAst(String origenAst) { this.origenAst = origenAst; }
        public String getMotivoRespaldoAst() { return motivoRespaldoAst; }
        public void setMotivoRespaldoAst(String m) { this.motivoRespaldoAst = m; }
    }
}
