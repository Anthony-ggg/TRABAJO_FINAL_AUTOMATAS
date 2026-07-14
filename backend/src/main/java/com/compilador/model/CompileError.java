package com.compilador.model;

/**
 * Error de compilacion estructurado.
 *
 * Reemplaza a las listas de String sueltas: al llevar fase, codigo y posicion,
 * el frontend puede resaltar el fragmento culpable y el agente tutor
 * (ErrorAssistantService) recibe contexto preciso sin volver a analizar la consulta.
 */
public class CompileError {

    /** Fase del compilador que detecto el error. */
    public enum Fase { LEXICO, SINTACTICO, SEMANTICO, EJECUCION }

    /** Los errores bloquean la ejecucion; las advertencias no. */
    public enum Severidad { ERROR, ADVERTENCIA }

    private Fase fase;
    private String codigo;
    private String mensaje;
    private String tokenAfectado;
    private int posicion;
    private int longitud;
    private String sugerencia;
    private Severidad severidad;

    public CompileError() {}

    public CompileError(Fase fase, String codigo, String mensaje, String sugerencia) {
        this(fase, codigo, mensaje, null, -1, 0, sugerencia);
    }

    public CompileError(Fase fase, String codigo, String mensaje, String tokenAfectado,
                        int posicion, int longitud, String sugerencia) {
        this.fase = fase;
        this.codigo = codigo;
        this.mensaje = mensaje;
        this.tokenAfectado = tokenAfectado;
        this.posicion = posicion;
        this.longitud = longitud;
        this.sugerencia = sugerencia;
        this.severidad = Severidad.ERROR;
    }

    /** Crea un error anclado a la posicion real de un token dentro de la consulta. */
    public static CompileError enToken(Fase fase, String codigo, String mensaje, Token token, String sugerencia) {
        int pos = token != null ? token.getPosicion() : -1;
        int len = (token != null && token.getValor() != null) ? token.getValor().length() : 0;
        String valor = token != null ? token.getValor() : null;
        return new CompileError(fase, codigo, mensaje, valor, pos, len, sugerencia);
    }

    public Fase getFase() { return fase; }
    public void setFase(Fase fase) { this.fase = fase; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public String getTokenAfectado() { return tokenAfectado; }
    public void setTokenAfectado(String tokenAfectado) { this.tokenAfectado = tokenAfectado; }
    public int getPosicion() { return posicion; }
    public void setPosicion(int posicion) { this.posicion = posicion; }
    public int getLongitud() { return longitud; }
    public void setLongitud(int longitud) { this.longitud = longitud; }
    public String getSugerencia() { return sugerencia; }
    public void setSugerencia(String sugerencia) { this.sugerencia = sugerencia; }
    public Severidad getSeveridad() { return severidad; }
    public void setSeveridad(Severidad severidad) { this.severidad = severidad; }

    @Override
    public String toString() {
        return "[" + fase + "/" + codigo + "] " + mensaje;
    }
}
