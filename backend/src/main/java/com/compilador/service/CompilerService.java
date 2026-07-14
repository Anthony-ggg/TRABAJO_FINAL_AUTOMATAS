package com.compilador.service;

import com.compilador.lexer.LexicalAnalyzer;
import com.compilador.model.CompileError;
import com.compilador.model.CompileResult;
import com.compilador.model.Token;
import com.compilador.parser.SyntaxAnalyzer;
import com.compilador.parser.SyntaxAnalyzer.SyntaxResult;
import com.compilador.semantic.SemanticAnalyzer;
import com.compilador.semantic.SemanticAnalyzer.SemanticResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orquestador de las fases del compilador.
 *
 * Cada fase es una barrera: si falla, la compilacion se detiene con errores controlados
 * y no se llega jamas a la base de datos. Ninguna excepcion escapa sin traducirse a un
 * CompileError, de modo que el sistema no colapsa ante ninguna entrada.
 */
@Service
public class CompilerService {

    /** Limite de longitud: evita que una entrada desmesurada sature al LLM o a la BD. */
    private static final int MAX_LONGITUD_CONSULTA = 1000;

    private final LexicalAnalyzer lexicalAnalyzer;
    private final SyntaxAnalyzer syntaxAnalyzer;
    private final SemanticAnalyzer semanticAnalyzer;
    private final QueryExecutorService queryExecutorService;

    public CompilerService(LexicalAnalyzer lexicalAnalyzer,
                           SyntaxAnalyzer syntaxAnalyzer,
                           SemanticAnalyzer semanticAnalyzer,
                           QueryExecutorService queryExecutorService) {
        this.lexicalAnalyzer = lexicalAnalyzer;
        this.syntaxAnalyzer = syntaxAnalyzer;
        this.semanticAnalyzer = semanticAnalyzer;
        this.queryExecutorService = queryExecutorService;
    }

    public CompileResult compile(String query) {
        long inicioTotal = System.nanoTime();
        CompileResult result = new CompileResult();
        result.setFraseOriginal(query);

        try {
            // --- Validacion de la entrada ---
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

            // --- 1. Analisis lexico (LLM extrae, AFD verifica) ---
            LexicalAnalyzer.ResultadoLexico lexico = lexicalAnalyzer.analyze(query);
            List<Token> tokens = lexico.tokens();
            result.setTokens(tokens);
            result.setObservaciones(lexico.observaciones());

            if (!lexico.errores().isEmpty()) {
                result.agregarErrores(lexico.errores());
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

            // --- 2. Analisis sintactico (codigo valida; el LLM genera el AST) ---
            SyntaxResult sintactico = syntaxAnalyzer.analyze(tokens, query);
            result.setAst(sintactico.ast());
            result.getObservaciones().setOrigenAst(sintactico.origenAst());
            result.getObservaciones().setMotivoRespaldoAst(sintactico.motivoRespaldo());
            result.getObservaciones().getTiemposPorFase().put("SINTACTICO", sintactico.tiempoMs());

            if (!sintactico.valido()) {
                result.agregarErrores(sintactico.errores());
                result.setExitoso(false);
                result.setMensaje("Error sintactico");
                return result;
            }

            // --- 3. Analisis semantico (100% codigo, con tabla de simbolos) ---
            SemanticResult semantico = semanticAnalyzer.validate(sintactico.ast(), tokens);
            result.setTablaSimbolos(semantico.simbolos());
            result.getObservaciones().getTiemposPorFase().put("SEMANTICO", semantico.tiempoMs());

            if (!semantico.valido()) {
                result.agregarErrores(semantico.errores());
                result.setExitoso(false);
                result.setMensaje("Error semantico");
                return result;
            }

            // --- 4. Ejecucion ---
            queryExecutorService.execute(sintactico.ast(), result);

            if (!result.getErroresDetallados().isEmpty()) {
                result.setExitoso(false);
                result.setMensaje("Error de ejecucion");
                return result;
            }

            result.setExitoso(true);
            result.setMensaje("Compilacion exitosa");

        } catch (Exception e) {
            // Red de seguridad: ninguna excepcion inesperada llega cruda al usuario.
            System.err.println("[Compilador] Error inesperado: " + e.getMessage());
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
}
