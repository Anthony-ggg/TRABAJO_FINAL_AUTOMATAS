package com.compilador.lexico.lexer;

import com.compilador.shared.model.CompileError;
import com.compilador.shared.model.Token;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Component
public class LexicalAnalyzer {

    private final AutomataStrategy automataStrategy;
    private final LLMStrategy llmStrategy;

    public LexicalAnalyzer(AutomataStrategy automataStrategy, LLMStrategy llmStrategy) {
        this.automataStrategy = automataStrategy;
        this.llmStrategy = llmStrategy;
    }

    public record ResultadoLexico(List<Token> tokens,
                                  List<CompileError> errores,
                                  ObservacionesLexico observaciones) {}

    public record ObservacionesLexico(
            List<String> tokensAFD,
            List<String> tokensLLM,
            int hilosConcurrentes,
            double tiempoTotalLexico,
            Map<String, Long> tiemposPorFase,
            int tokensConfirmados,
            List<String> tokensSoloAFD,
            List<String> tokensAlucinados,
            List<String> discrepanciasTipo,
            double coincidenciaLexica,
            boolean llmDisponible
    ) {}

    public ResultadoLexico analyze(String input) {
        long inicioTotal = System.nanoTime();
        Map<String, Long> tiempos = new ConcurrentHashMap<>();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 2, 10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private int contador = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "LexicoThread-" + contador++);
                        t.setDaemon(true);
                        return t;
                    }
                });

        AutomataStrategy.ResultadoAFD resultadoAfd;
        List<Token> tokensLlm;

        try {
            Future<AutomataStrategy.ResultadoAFD> futuroAfd = executor.submit(() -> {
                long t0 = System.nanoTime();
                AutomataStrategy.ResultadoAFD r = automataStrategy.analizar(input);
                tiempos.put("AFD", (System.nanoTime() - t0) / 1_000_000);
                return r;
            });

            Future<List<Token>> futuroLlm = executor.submit(() -> {
                long t0 = System.nanoTime();
                List<Token> r = llmStrategy.classify(input);
                tiempos.put("LLM", (System.nanoTime() - t0) / 1_000_000);
                return r;
            });

            resultadoAfd = futuroAfd.get(35, TimeUnit.SECONDS);
            try {
                tokensLlm = futuroLlm.get(35, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("[Lexico] LLM no respondio: " + e.getMessage());
                tokensLlm = List.of();
            }

        } catch (Exception e) {
            System.err.println("[Lexico] Fallo concurrente; fallback secuencial: " + e.getMessage());
            long t0 = System.nanoTime();
            resultadoAfd = automataStrategy.analizar(input);
            tiempos.put("AFD-fallback", (System.nanoTime() - t0) / 1_000_000);
            tokensLlm = List.of();
        } finally {
            executor.shutdownNow();
        }

        List<Token> tokensVerificados = conciliar(resultadoAfd.tokens, tokensLlm,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        int confirmados = tokensVerificados.stream()
                .filter(t -> "LLM+AFD".equals(t.getOrigen())).toList().size();
        double coincidencia = resultadoAfd.tokens.isEmpty() ? 0.0
                : Math.round((confirmados * 1000.0 / resultadoAfd.tokens.size())) / 10.0;

        List<String> tokensAfdStr = resultadoAfd.tokens.stream()
                .map(t -> t.getTipo() + ":" + t.getValor()).toList();
        List<String> tokensLlmStr = tokensLlm.stream()
                .map(t -> t.getTipo() + ":" + t.getValor()).toList();

        double totalSeg = (System.nanoTime() - inicioTotal) / 1_000_000_000.0;

        ObservacionesLexico obs = new ObservacionesLexico(
                tokensAfdStr, tokensLlmStr, 2, totalSeg,
                new LinkedHashMap<>(tiempos),
                confirmados, List.of(), List.of(), List.of(),
                coincidencia, !tokensLlm.isEmpty()
        );

        return new ResultadoLexico(tokensVerificados, resultadoAfd.errores, obs);
    }

    private List<Token> conciliar(List<Token> tokensAfd, List<Token> tokensLlm,
                                  List<String> alucinaciones, List<String> soloAfd,
                                  List<String> discrepanciasTipo, List<String> confirmadosList) {

        Map<Integer, Token> llmPorPosicion = new HashMap<>();
        for (Token t : tokensLlm) {
            if (t.getPosicion() < 0) {
                alucinaciones.add(t.getTipo() + ":" + t.getValor());
            } else {
                llmPorPosicion.putIfAbsent(t.getPosicion(), t);
            }
        }

        List<Token> verificados = new ArrayList<>();
        for (Token afd : tokensAfd) {
            Token llm = llmPorPosicion.remove(afd.getPosicion());
            if (llm != null && llm.getValor().equals(afd.getValor())) {
                afd.setOrigen("LLM+AFD");
                confirmadosList.add(afd.getValor());
                if (!tiposEquivalentes(afd.getTipo(), llm.getTipo())) {
                    discrepanciasTipo.add(afd.getValor()
                            + " (AFD=" + afd.getTipo() + ", LLM=" + llm.getTipo() + ")");
                }
            } else {
                soloAfd.add(afd.getTipo() + ":" + afd.getValor());
            }
            verificados.add(afd);
        }

        llmPorPosicion.values().forEach(t -> alucinaciones.add(t.getTipo() + ":" + t.getValor()));
        return verificados;
    }

    private boolean tiposEquivalentes(String tipoAfd, String tipoLlm) {
        if (tipoAfd.equals(tipoLlm)) return true;
        return "PALABRA_RESERVADA".equals(tipoLlm)
                && AutomataStrategy.PALABRAS_RESERVADAS.contains(tipoAfd);
    }
}
