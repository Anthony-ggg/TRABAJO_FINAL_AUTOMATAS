package com.compilador.service;

import com.compilador.lexer.LexicalAnalyzer;
import com.compilador.model.*;
import com.compilador.parser.SyntaxAnalyzer;
import com.compilador.parser.SyntaxAnalyzer.SyntaxResult;
import com.compilador.semantic.SemanticAnalyzer;
import com.compilador.semantic.SemanticAnalyzer.SemanticResult;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CompilerService {

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
        long startTotal = System.nanoTime();
        CompileResult result = new CompileResult();
        result.setFraseOriginal(query);
        result.setErrores(new ArrayList<>());

        List<Token> tokens = new ArrayList<>();

        try {
            // 1. Lexical Analysis (concurrent AFD + LLM)
            System.out.println("\n=== INICIANDO ANÁLISIS LÉXICO ===");
            System.out.println("Query: " + query);
            CompileResult.Observaciones obs = lexicalAnalyzer.analyze(query, tokens);
            result.setTokens(tokens);
            result.setObservaciones(obs);

            System.out.println("Tokens generados: " + tokens.size());
            tokens.forEach(t -> System.out.println("  " + t));

            if (tokens.isEmpty()) {
                result.setExitoso(false);
                result.setMensaje("No se pudieron generar tokens");
                result.getErrores().add("Análisis léxico falló");
                return result;
            }

            // 2. Syntax Analysis (AST construction + LLM validation)
            System.out.println("\n=== INICIANDO ANÁLISIS SINTÁCTICO ===");
            SyntaxResult syntaxResult = syntaxAnalyzer.analyze(tokens);
            result.setAst(syntaxResult.ast());

            System.out.println("AST generado:");
            if (syntaxResult.ast() != null) {
                System.out.println(syntaxResult.ast().toString());
            }

            if (!syntaxResult.valido()) {
                result.setExitoso(false);
                result.setMensaje("Error sintáctico");
                result.getErrores().addAll(syntaxResult.errores());
                result.getObservaciones().getTiemposPorFase().put("SINTACTICO", syntaxResult.tiempoMs());
                return result;
            }

            result.getObservaciones().getTiemposPorFase().put("SINTACTICO", syntaxResult.tiempoMs());

            // 3. Semantic Analysis (symbol table + column validation)
            System.out.println("\n=== INICIANDO ANÁLISIS SEMÁNTICO ===");
            SemanticResult semanticResult = semanticAnalyzer.validate(syntaxResult.ast(), tokens);
            result.setTablaSimbolos(semanticResult.simbolos());

            System.out.println("Análisis semántico: " + (semanticResult.valido() ? "VÁLIDO" : "ERRORES"));
            if (!semanticResult.errores().isEmpty()) {
                semanticResult.errores().forEach(e -> System.out.println("  Error: " + e));
            }

            if (!semanticResult.valido()) {
                result.setExitoso(false);
                result.setMensaje("Error semántico");
                result.getErrores().addAll(semanticResult.errores());
                result.getObservaciones().getTiemposPorFase().put("SEMANTICO", semanticResult.tiempoMs());
                return result;
            }

            result.getObservaciones().getTiemposPorFase().put("SEMANTICO", semanticResult.tiempoMs());

            // 3.5 Execute query
            System.out.println("\n=== EJECUTANDO CONSULTA ===");
            queryExecutorService.execute(syntaxResult.ast(), result);

            // 4. Success
            long totalElapsed = (System.nanoTime() - startTotal);
            result.setExitoso(true);
            result.setMensaje("Compilación exitosa");
            result.getObservaciones().setTiempoTotalCompilacion(totalElapsed / 1_000_000_000.0);

            System.out.println("\n=== COMPILACIÓN EXITOSA ===");
            System.out.println("Tiempo total: " + String.format("%.3f", totalElapsed / 1_000_000_000.0) + "s");

        } catch (Exception e) {
            System.err.println("Error de compilación: " + e.getMessage());
            e.printStackTrace();
            result.setExitoso(false);
            result.setMensaje("Error de compilación: " + e.getMessage());
            result.getErrores().add(e.getMessage());
        }

        return result;
    }
}
