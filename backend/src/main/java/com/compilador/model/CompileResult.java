package com.compilador.model;

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
    private List<String> errores;
    private List<Map<String, Object>> filasResultado;
    private String mensajeEjecucion;

    public CompileResult() {}

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
    public List<Map<String, Object>> getFilasResultado() { return filasResultado; }
    public void setFilasResultado(List<Map<String, Object>> filasResultado) { this.filasResultado = filasResultado; }
    public String getMensajeEjecucion() { return mensajeEjecucion; }
    public void setMensajeEjecucion(String mensajeEjecucion) { this.mensajeEjecucion = mensajeEjecucion; }

    public static class Observaciones {
        private List<String> tokensAFD;
        private List<String> tokensLLM;
        private int hilosConcurrentes;
        private double tiempoTotalLexico;
        private double tiempoTotalCompilacion;
        private Map<String, Long> tiemposPorFase;

        public List<String> getTokensAFD() { return tokensAFD; }
        public void setTokensAFD(List<String> tokensAFD) { this.tokensAFD = tokensAFD; }
        public List<String> getTokensLLM() { return tokensLLM; }
        public void setTokensLLM(List<String> tokensLLM) { this.tokensLLM = tokensLLM; }
        public int getHilosConcurrentes() { return hilosConcurrentes; }
        public void setHilosConcurrentes(int hilosConcurrentes) { this.hilosConcurrentes = hilosConcurrentes; }
        public double getTiempoTotalLexico() { return tiempoTotalLexico; }
        public void setTiempoTotalLexico(double tiempoTotalLexico) { this.tiempoTotalLexico = tiempoTotalLexico; }
        public double getTiempoTotalCompilacion() { return tiempoTotalCompilacion; }
        public void setTiempoTotalCompilacion(double tiempoTotalCompilacion) { this.tiempoTotalCompilacion = tiempoTotalCompilacion; }
        public Map<String, Long> getTiemposPorFase() { return tiemposPorFase; }
        public void setTiemposPorFase(Map<String, Long> tiemposPorFase) { this.tiemposPorFase = tiemposPorFase; }
    }
}
