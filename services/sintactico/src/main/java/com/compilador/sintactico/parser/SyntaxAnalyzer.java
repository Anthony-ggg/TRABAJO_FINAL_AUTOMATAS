package com.compilador.sintactico.parser;

import com.compilador.shared.model.ASTNode;
import com.compilador.shared.model.CompileError;
import com.compilador.shared.model.Token;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SyntaxAnalyzer {

    private final LLMAstGenerator llmAstGenerator;

    public SyntaxAnalyzer(LLMAstGenerator llmAstGenerator) {
        this.llmAstGenerator = llmAstGenerator;
    }

    public record SyntaxResult(boolean valido,
                               List<CompileError> errores,
                               ASTNode ast,
                               String tipoConsulta,
                               String origenAst,
                               String motivoRespaldo,
                               long tiempoMs,
                               String query,
                               List<Token> tokens) {}

    public SyntaxResult analyze(List<Token> tokens, String consultaOriginal) {
        long inicio = System.nanoTime();

        RecursiveDescentParser.ResultadoParser determinista = RecursiveDescentParser.parse(tokens);

        if (!determinista.valido()) {
            return new SyntaxResult(false, determinista.errores(), determinista.ast(),
                    detectarTipo(tokens), "PARSER_DETERMINISTA",
                    "La consulta no es sintacticamente valida; no se solicito AST al LLM.",
                    transcurrido(inicio), consultaOriginal, tokens);
        }

        ASTNode astLlm = llmAstGenerator.generar(consultaOriginal);

        AstValidator.Resultado verificacion = AstValidator.validar(astLlm, tokens);

        if (verificacion.valido()) {
            System.out.println("[Sintactico] AST generado por el LLM y verificado correctamente.");
            return new SyntaxResult(true, List.of(), astLlm, astLlm.getTipo(),
                    "LLM", null, transcurrido(inicio), consultaOriginal, tokens);
        }

        System.out.println("[Sintactico] AST del LLM descartado (" + verificacion.motivo()
                + "). Se usa el parser determinista.");
        return new SyntaxResult(true, List.of(), determinista.ast(), detectarTipo(tokens),
                "PARSER_DETERMINISTA", verificacion.motivo(), transcurrido(inicio),
                consultaOriginal, tokens);
    }

    private long transcurrido(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000;
    }

    private String detectarTipo(List<Token> tokens) {
        return tokens.isEmpty() ? "DESCONOCIDO" : tokens.get(0).getValor().toUpperCase();
    }
}
