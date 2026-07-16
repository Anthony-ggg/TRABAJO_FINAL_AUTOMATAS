package com.compilador.shared.model;

public class Token {
    private String tipo;
    private String valor;
    private String origen;
    private int posicion;

    public Token() {}

    public Token(String tipo, String valor, String origen, int posicion) {
        this.tipo = tipo;
        this.valor = valor;
        this.origen = origen;
        this.posicion = posicion;
    }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public int getPosicion() { return posicion; }
    public void setPosicion(int posicion) { this.posicion = posicion; }

    @Override
    public String toString() {
        return "Token{tipo='" + tipo + "', valor='" + valor + "', origen='" + origen + "'}";
    }
}
