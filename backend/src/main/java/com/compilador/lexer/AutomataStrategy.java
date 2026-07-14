package com.compilador.lexer;

import com.compilador.model.CompileError;
import com.compilador.model.Token;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Analizador lexico implementado como AFD (Automata Finito Determinista) real.
 *
 * Antes esta clase era una cascada de expresiones regulares. Se reescribio como un
 * automata explicito (estados, alfabeto por clases de caracter, funcion de transicion
 * y estados de aceptacion) por dos razones:
 *
 *   1. Academica: la materia es Teoria de Automatas; la clase debe contener un automata.
 *   2. Arquitectonica: el AFD es el VERIFICADOR DETERMINISTA que blinda al LLM.
 *      La guia exige que el LLM extraiga los tokens, pero un modelo puede alucinar.
 *      El AFD produce la tokenizacion de referencia contra la que se concilian los
 *      tokens del LLM (ver LexicalAnalyzer), de modo que nada que el AFD no reconozca
 *      llega jamas al AST ni a la base de datos.
 *
 * Reconocimiento por maximal munch: se avanza mientras exista transicion y se recuerda
 * el ultimo estado de aceptacion visitado; al atascarse, se emite ese token y se retrocede.
 */
@Component
public class AutomataStrategy implements TokenStrategy {

    // --- Estados del AFD ---
    private static final int S_INICIAL   = 0;
    private static final int S_ID        = 1;  // acepta IDENTIFICADOR / PALABRA_RESERVADA
    private static final int S_NUM_ENT   = 2;  // acepta NUMERO (entero)
    private static final int S_NUM_PUNTO = 3;  // "12." -> aun no acepta
    private static final int S_NUM_DEC   = 4;  // acepta NUMERO (decimal)
    private static final int S_CAD_ABRE  = 5;  // cadena abierta -> no acepta
    private static final int S_CAD_CIERRA= 6;  // acepta CADENA
    private static final int S_OP_IGUAL  = 7;  // acepta OPERADOR  '='
    private static final int S_OP_MAYOR  = 8;  // acepta OPERADOR  '>'
    private static final int S_OP_MENOR  = 9;  // acepta OPERADOR  '<'
    private static final int S_OP_COMP   = 10; // acepta OPERADOR  '>=' '<=' '<>'
    private static final int S_ADMIRACION= 11; // '!' suelto -> no acepta
    private static final int S_OP_DISTINTO=12; // acepta OPERADOR  '!='
    private static final int S_SIMBOLO   = 13; // acepta SIMBOLO
    private static final int S_ESPACIO   = 14; // acepta ESPACIO (se descarta)
    private static final int S_ERROR     = -1;

    private static final int NUM_ESTADOS = 15;

    /** Clases de caracter que forman el alfabeto del automata. */
    private enum Clase {
        LETRA, DIGITO, GUION_BAJO, PUNTO, COMILLA, IGUAL, MAYOR, MENOR,
        ADMIRACION, SIMBOLO, ESPACIO, OTRO
    }

    /** Tipo de token que acepta cada estado final; null si el estado no es de aceptacion. */
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

    /** Palabras reservadas del subconjunto de SQL en espanol. */
    public static final Set<String> PALABRAS_RESERVADAS = Set.of(
            "SELECCIONAR", "INSERTAR", "MODIFICAR", "ELIMINAR",
            "DESDE", "CUANDO", "EN", "VALORES", "ESTABLECER", "Y", "O"
    );

    /** Funcion de transicion delta: transiciones[estado][clase] -> estado destino. */
    private static final int[][] TRANSICIONES = construirTablaTransiciones();

    private static int[][] construirTablaTransiciones() {
        int[][] t = new int[NUM_ESTADOS][Clase.values().length];
        for (int[] fila : t) Arrays.fill(fila, S_ERROR);

        // Estado inicial
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

        // Identificadores: [a-zA-Z_][a-zA-Z0-9_]*
        t[S_ID][Clase.LETRA.ordinal()]      = S_ID;
        t[S_ID][Clase.DIGITO.ordinal()]     = S_ID;
        t[S_ID][Clase.GUION_BAJO.ordinal()] = S_ID;

        // Numeros: \d+(\.\d+)?
        t[S_NUM_ENT][Clase.DIGITO.ordinal()]   = S_NUM_ENT;
        t[S_NUM_ENT][Clase.PUNTO.ordinal()]    = S_NUM_PUNTO;
        t[S_NUM_PUNTO][Clase.DIGITO.ordinal()] = S_NUM_DEC;
        t[S_NUM_DEC][Clase.DIGITO.ordinal()]   = S_NUM_DEC;

        // Cadenas: '...'  (cualquier caracter salvo la comilla de cierre)
        for (Clase c : Clase.values()) {
            t[S_CAD_ABRE][c.ordinal()] = S_CAD_ABRE;
        }
        t[S_CAD_ABRE][Clase.COMILLA.ordinal()] = S_CAD_CIERRA;

        // Operadores compuestos
        t[S_OP_MAYOR][Clase.IGUAL.ordinal()]     = S_OP_COMP;      // >=
        t[S_OP_MENOR][Clase.IGUAL.ordinal()]     = S_OP_COMP;      // <=
        t[S_OP_MENOR][Clase.MAYOR.ordinal()]     = S_OP_COMP;      // <>
        t[S_ADMIRACION][Clase.IGUAL.ordinal()]   = S_OP_DISTINTO;  // !=

        // Espacios en blanco consecutivos
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

    /** Ejecuta el AFD devolviendo tokens y errores lexicos detectados. */
    public ResultadoAFD analizar(String input) {
        return new ResultadoAFD(input);
    }

    @Override
    public String getNombre() {
        return "AFD";
    }

    /** Resultado del recorrido del automata: tokens reconocidos + errores lexicos. */
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

                // Avanzar mientras exista transicion, recordando el ultimo estado de aceptacion.
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

                // Cadena abierta que nunca se cerro: error lexico controlado.
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

                // Ningun estado de aceptacion alcanzado: caracter no perteneciente al alfabeto.
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

                // Emitir el token del ultimo estado de aceptacion (maximal munch).
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

    /**
     * Expone la tabla de transiciones del automata para su visualizacion en la vista
     * Avanzada del frontend, de modo que el AFD sea auditable.
     */
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
        def.put("estados", listarEstados());
        def.put("estadosFinales", finales);
        def.put("transiciones", transiciones);
        def.put("palabrasReservadas", new TreeSet<>(PALABRAS_RESERVADAS));
        return def;
    }

    private List<String> listarEstados() {
        List<String> estados = new ArrayList<>();
        for (int i = 0; i < NUM_ESTADOS; i++) estados.add(nombreEstado(i));
        return estados;
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
