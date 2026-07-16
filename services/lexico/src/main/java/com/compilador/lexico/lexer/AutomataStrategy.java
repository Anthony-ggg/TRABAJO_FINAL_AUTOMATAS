package com.compilador.lexico.lexer;

import com.compilador.shared.model.CompileError;
import com.compilador.shared.model.Token;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AutomataStrategy implements TokenStrategy {

    private static final int S_INICIAL    = 0;
    private static final int S_ID         = 1;
    private static final int S_NUM_ENT    = 2;
    private static final int S_NUM_PUNTO  = 3;
    private static final int S_NUM_DEC    = 4;
    private static final int S_CAD_ABRE   = 5;
    private static final int S_CAD_CIERRA = 6;
    private static final int S_OP_IGUAL   = 7;
    private static final int S_OP_MAYOR   = 8;
    private static final int S_OP_MENOR   = 9;
    private static final int S_OP_COMP    = 10;
    private static final int S_ADMIRACION = 11;
    private static final int S_OP_DISTINTO= 12;
    private static final int S_SIMBOLO    = 13;
    private static final int S_ESPACIO    = 14;
    private static final int S_ERROR      = -1;
    private static final int NUM_ESTADOS  = 15;

    private enum Clase {
        LETRA, DIGITO, GUION_BAJO, PUNTO, COMILLA, IGUAL, MAYOR, MENOR,
        ADMIRACION, SIMBOLO, ESPACIO, OTRO
    }

    private static final Map<Integer, String> ESTADOS_ACEPTACION = Map.of(
            S_ID,          "IDENTIFICADOR",
            S_NUM_ENT,     "NUMERO",
            S_NUM_DEC,     "NUMERO",
            S_CAD_CIERRA,  "CADENA",
            S_OP_IGUAL,    "OPERADOR",
            S_OP_MAYOR,    "OPERADOR",
            S_OP_MENOR,    "OPERADOR",
            S_OP_COMP,     "OPERADOR",
            S_OP_DISTINTO, "OPERADOR",
            S_SIMBOLO,     "SIMBOLO"
    );

    public static final Set<String> PALABRAS_RESERVADAS = Set.of(
            "SELECCIONAR", "INSERTAR", "MODIFICAR", "ELIMINAR",
            "DESDE", "CUANDO", "EN", "VALORES", "ESTABLECER", "Y", "O"
    );

    private static final int[][] TRANSICIONES = construirTablaTransiciones();

    private static int[][] construirTablaTransiciones() {
        int[][] t = new int[NUM_ESTADOS][Clase.values().length];
        for (int[] fila : t) Arrays.fill(fila, S_ERROR);

        t[S_INICIAL][Clase.LETRA.ordinal()]       = S_ID;
        t[S_INICIAL][Clase.GUION_BAJO.ordinal()]  = S_ID;
        t[S_INICIAL][Clase.DIGITO.ordinal()]      = S_NUM_ENT;
        t[S_INICIAL][Clase.COMILLA.ordinal()]     = S_CAD_ABRE;
        t[S_INICIAL][Clase.IGUAL.ordinal()]       = S_OP_IGUAL;
        t[S_INICIAL][Clase.MAYOR.ordinal()]       = S_OP_MAYOR;
        t[S_INICIAL][Clase.MENOR.ordinal()]       = S_OP_MENOR;
        t[S_INICIAL][Clase.ADMIRACION.ordinal()]  = S_ADMIRACION;
        t[S_INICIAL][Clase.SIMBOLO.ordinal()]     = S_SIMBOLO;
        t[S_INICIAL][Clase.ESPACIO.ordinal()]     = S_ESPACIO;

        t[S_ID][Clase.LETRA.ordinal()]      = S_ID;
        t[S_ID][Clase.DIGITO.ordinal()]     = S_ID;
        t[S_ID][Clase.GUION_BAJO.ordinal()] = S_ID;

        t[S_NUM_ENT][Clase.DIGITO.ordinal()]   = S_NUM_ENT;
        t[S_NUM_ENT][Clase.PUNTO.ordinal()]    = S_NUM_PUNTO;
        t[S_NUM_PUNTO][Clase.DIGITO.ordinal()] = S_NUM_DEC;
        t[S_NUM_DEC][Clase.DIGITO.ordinal()]   = S_NUM_DEC;

        for (Clase c : Clase.values()) {
            t[S_CAD_ABRE][c.ordinal()] = S_CAD_ABRE;
        }
        t[S_CAD_ABRE][Clase.COMILLA.ordinal()] = S_CAD_CIERRA;

        t[S_OP_MAYOR][Clase.IGUAL.ordinal()]   = S_OP_COMP;
        t[S_OP_MENOR][Clase.IGUAL.ordinal()]   = S_OP_COMP;
        t[S_OP_MENOR][Clase.MAYOR.ordinal()]   = S_OP_COMP;
        t[S_ADMIRACION][Clase.IGUAL.ordinal()] = S_OP_DISTINTO;

        t[S_ESPACIO][Clase.ESPACIO.ordinal()] = S_ESPACIO;

        return t;
    }

    private static Clase clasificar(char c) {
        if (Character.isLetter(c)) return Clase.LETRA;
        if (Character.isDigit(c))  return Clase.DIGITO;
        if (c == '_')  return Clase.GUION_BAJO;
        if (c == '.')  return Clase.PUNTO;
        if (c == '\'') return Clase.COMILLA;
        if (c == '=')  return Clase.IGUAL;
        if (c == '>')  return Clase.MAYOR;
        if (c == '<')  return Clase.MENOR;
        if (c == '!')  return Clase.ADMIRACION;
        if (c == ',' || c == '(' || c == ')' || c == '*') return Clase.SIMBOLO;
        if (Character.isWhitespace(c)) return Clase.ESPACIO;
        return Clase.OTRO;
    }

    @Override
    public List<Token> classify(String input) {
        return new ResultadoAFD(input).tokens;
    }

    public ResultadoAFD analizar(String input) {
        return new ResultadoAFD(input);
    }

    @Override
    public String getNombre() {
        return "AFD";
    }

    public static class ResultadoAFD {
        public final List<Token> tokens = new ArrayList<>();
        public final List<CompileError> errores = new ArrayList<>();

        private ResultadoAFD(String input) {
            if (input == null) return;
            int pos = 0;
            final int n = input.length();

            while (pos < n) {
                int estado = S_INICIAL;
                int ultimoAceptado = S_ERROR;
                int finUltimoAceptado = -1;
                int i = pos;

                while (i < n) {
                    Clase clase = clasificar(input.charAt(i));
                    int siguiente = TRANSICIONES[estado][clase.ordinal()];
                    if (siguiente == S_ERROR) break;
                    estado = siguiente;
                    i++;
                    if (ESTADOS_ACEPTACION.containsKey(estado)) {
                        ultimoAceptado = estado;
                        finUltimoAceptado = i;
                    } else if (estado == S_ESPACIO) {
                        ultimoAceptado = S_ESPACIO;
                        finUltimoAceptado = i;
                    }
                }

                if (estado == S_CAD_ABRE && i == n && ultimoAceptado == S_ERROR) {
                    String lexema = input.substring(pos);
                    errores.add(new CompileError(
                            CompileError.Fase.LEXICO, "LEX_CADENA_SIN_CERRAR",
                            "La cadena iniciada en la posicion " + pos + " nunca se cierra con una comilla simple.",
                            lexema, pos, lexema.length(),
                            "Cierra la cadena con una comilla simple, por ejemplo: 'Madrid'"));
                    tokens.add(new Token("DESCONOCIDO", lexema, "AFD", pos));
                    break;
                }

                if (ultimoAceptado == S_ERROR) {
                    String lexema = String.valueOf(input.charAt(pos));
                    errores.add(new CompileError(
                            CompileError.Fase.LEXICO, "LEX_CARACTER_INVALIDO",
                            "El caracter '" + lexema + "' no pertenece al alfabeto del lenguaje.",
                            lexema, pos, 1,
                            "Elimina el caracter '" + lexema + "'. Solo se permiten letras, numeros, "
                                    + "cadenas entre comillas simples, operadores (= > < >= <= <> !=) y los simbolos , ( ) *"));
                    tokens.add(new Token("DESCONOCIDO", lexema, "AFD", pos));
                    pos++;
                    continue;
                }

                String lexema = input.substring(pos, finUltimoAceptado);
                if (ultimoAceptado != S_ESPACIO) {
                    String tipo = ESTADOS_ACEPTACION.get(ultimoAceptado);
                    if ("IDENTIFICADOR".equals(tipo) && PALABRAS_RESERVADAS.contains(lexema.toUpperCase())) {
                        tipo = lexema.toUpperCase();
                    }
                    tokens.add(new Token(tipo, lexema, "AFD", pos));
                }
                pos = finUltimoAceptado;
            }
        }
    }

    public Map<String, Object> getDefinicionAutomata() {
        List<Map<String, Object>> transiciones = new ArrayList<>();
        for (int estado = 0; estado < NUM_ESTADOS; estado++) {
            for (Clase clase : Clase.values()) {
                int destino = TRANSICIONES[estado][clase.ordinal()];
                if (destino != S_ERROR) {
                    transiciones.add(Map.of(
                            "origen", nombreEstado(estado),
                            "simbolo", clase.name(),
                            "destino", nombreEstado(destino)
                    ));
                }
            }
        }

        List<Map<String, String>> finales = new ArrayList<>();
        ESTADOS_ACEPTACION.forEach((estado, tipo) ->
                finales.add(Map.of("estado", nombreEstado(estado), "token", tipo)));
        finales.sort(Comparator.comparing(m -> m.get("estado")));

        Map<String, Object> def = new LinkedHashMap<>();
        def.put("estadoInicial", nombreEstado(S_INICIAL));
        def.put("alfabeto", Arrays.stream(Clase.values()).map(Enum::name).toList());
        def.put("estadosFinales", finales);
        def.put("transiciones", transiciones);
        def.put("palabrasReservadas", new TreeSet<>(PALABRAS_RESERVADAS));
        return def;
    }

    private static String nombreEstado(int estado) {
        return switch (estado) {
            case S_INICIAL    -> "q0_INICIAL";
            case S_ID         -> "q1_IDENTIFICADOR";
            case S_NUM_ENT    -> "q2_NUMERO_ENTERO";
            case S_NUM_PUNTO  -> "q3_NUMERO_PUNTO";
            case S_NUM_DEC    -> "q4_NUMERO_DECIMAL";
            case S_CAD_ABRE   -> "q5_CADENA_ABIERTA";
            case S_CAD_CIERRA -> "q6_CADENA_CERRADA";
            case S_OP_IGUAL   -> "q7_OP_IGUAL";
            case S_OP_MAYOR   -> "q8_OP_MAYOR";
            case S_OP_MENOR   -> "q9_OP_MENOR";
            case S_OP_COMP    -> "q10_OP_COMPUESTO";
            case S_ADMIRACION -> "q11_ADMIRACION";
            case S_OP_DISTINTO-> "q12_OP_DISTINTO";
            case S_SIMBOLO    -> "q13_SIMBOLO";
            case S_ESPACIO    -> "q14_ESPACIO";
            default           -> "qERROR";
        };
    }
}
