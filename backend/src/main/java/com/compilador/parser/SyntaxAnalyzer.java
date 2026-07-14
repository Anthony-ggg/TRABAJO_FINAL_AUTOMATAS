package com.compilador.parser;

import com.compilador.model.ASTNode;
import com.compilador.model.CompileError;
import com.compilador.model.Token;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Analisis sintactico.
 *
 * Arquitectura "el LLM genera, el codigo verifica", en tres pasos:
 *
 *   1. VALIDEZ (codigo): RecursiveDescentParser decide si la consulta respeta la gramatica
 *      y reporta todos los errores con posicion y sugerencia. Esta decision jamas se delega
 *      al modelo: antes, si Ollama estaba caido, la consulta se daba por valida por defecto.
 *
 *   2. GENERACION DEL AST (LLM): si la consulta es sintacticamente valida, se pide al modelo
 *      que genere el AST, como exige la guia.
 *
 *   3. VERIFICACION DEL AST (codigo): AstValidator contrasta el AST del modelo contra los
 *      tokens reales. Si el LLM alucino, no respondio o produjo un arbol incompleto, se usa
 *      el AST del parser determinista. La compilacion nunca se detiene por culpa del modelo.
 */
@Component
public class SyntaxAnalyzer {

    private final LLMAstGenerator llmAstGenerator;

    public SyntaxAnalyzer(LLMAstGenerator llmAstGenerator) {
        this.llmAstGenerator = llmAstGenerator;
    }

    /**
     * @param origenAst        "LLM" o "PARSER_DETERMINISTA"
     * @param motivoRespaldo   por que se descarto el AST del LLM (null si se uso el del modelo)
     */
    public record SyntaxResult(boolean valido,
                               List<CompileError> errores,
                               ASTNode ast,
                               String tipoConsulta,
                               String origenAst,
                               String motivoRespaldo,
                               long tiempoMs) {}

    public SyntaxResult analyze(List<Token> tokens, String consultaOriginal) {
        long inicio = System.nanoTime();

        // Paso 1: la validez la decide el codigo.
        RecursiveDescentParser.ResultadoParser determinista = RecursiveDescentParser.parse(tokens);

        if (!determinista.valido()) {
            return new SyntaxResult(false, determinista.errores(), determinista.ast(),
                    detectarTipo(tokens), "PARSER_DETERMINISTA",
                    "La consulta no es sintacticamente valida; no se solicito AST al LLM.",
                    transcurrido(inicio));
        }

        // Paso 2: el LLM genera el AST (requisito de la guia).
        ASTNode astLlm = llmAstGenerator.generar(consultaOriginal);

        // Paso 3: el codigo verifica el AST del modelo antes de aceptarlo.
        AstValidator.Resultado verificacion = AstValidator.validar(astLlm, tokens);

        if (verificacion.valido()) {
            System.out.println("[Sintactico] AST generado por el LLM y verificado correctamente.");
            return new SyntaxResult(true, List.of(), astLlm, astLlm.getTipo(),
                    "LLM", null, transcurrido(inicio));
        }

        System.out.println("[Sintactico] AST del LLM descartado (" + verificacion.motivo()
                + "). Se usa el parser determinista.");
        return new SyntaxResult(true, List.of(), determinista.ast(), detectarTipo(tokens),
                "PARSER_DETERMINISTA", verificacion.motivo(), transcurrido(inicio));
    }

    private long transcurrido(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000;
    }

    private String detectarTipo(List<Token> tokens) {
        return tokens.isEmpty() ? "DESCONOCIDO" : tokens.get(0).getValor().toUpperCase();
    }
}
