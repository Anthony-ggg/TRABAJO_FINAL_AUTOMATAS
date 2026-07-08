package com.compilador.lexer;

import com.compilador.model.CompileResult;
import com.compilador.model.Token;
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

    public CompileResult.Observaciones analyze(String input, List<Token> outputTokens) {
        CompileResult.Observaciones obs = new CompileResult.Observaciones();
        Map<String, Long> tiempos = new LinkedHashMap<>();
        long startTotal = System.nanoTime();

        int numCadenas = countStrings(input);
        int threadCount = Math.max(2, numCadenas + 1);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threadCount, threadCount,
                10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private int counter = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "ThreadPoolExecutor-0_" + counter++);
                        t.setDaemon(true);
                        return t;
                    }
                }
        );

        try {
            // AFD task - measures in <1ms
            Future<List<Token>> afdFuture = executor.submit(() -> {
                long start = System.nanoTime();
                List<Token> afdTokens = automataStrategy.classify(input);
                long elapsed = (System.nanoTime() - start);
                tiempos.put("AFD", elapsed / 1_000_000);
                System.out.println("[Hilo-AFD] Clasificó " + afdTokens.size()
                        + " tokens en " + (elapsed / 1_000_000) + "ms - Hilo: "
                        + Thread.currentThread().getName());
                return afdTokens;
            });

            // LLM task - simulates ~1s
            Future<List<Token>> llmFuture = executor.submit(() -> {
                long start = System.nanoTime();
                List<Token> llmTokens = llmStrategy.classify(input);
                long elapsed = (System.nanoTime() - start);
                tiempos.put("LLM", elapsed / 1_000_000);
                System.out.println("[Hilo-LLM] Clasificó " + llmTokens.size()
                        + " tokens en " + (elapsed / 1_000_000) + "ms - Hilo: "
                        + Thread.currentThread().getName());
                return llmTokens;
            });

            // Wait for both with timeout
            List<Token> afdTokens = afdFuture.get(30, TimeUnit.SECONDS);
            List<Token> llmTokens = llmFuture.get(30, TimeUnit.SECONDS);

            // Merge tokens: AFD for hard tokens, LLM for semantic tokens
            outputTokens.addAll(mergeTokens(afdTokens, llmTokens));

            long totalElapsed = (System.nanoTime() - startTotal);
            double totalSec = totalElapsed / 1_000_000_000.0;

            obs.setTokensAFD(afdTokens.stream().map(t -> t.getTipo() + ":" + t.getValor()).toList());
            obs.setTokensLLM(llmTokens.stream().map(t -> t.getTipo() + ":" + t.getValor()).toList());
            obs.setHilosConcurrentes(2);
            obs.setTiempoTotalLexico(totalSec);
            obs.setTiemposPorFase(tiempos);

            System.out.println("=== Lexer Concurrente ===");
            System.out.println("AFD tokens: " + afdTokens.size() + " | LLM tokens: " + llmTokens.size());
            System.out.println("Hilos activos: " + executor.getActiveCount());
            System.out.println("Tiempo total léxico: " + String.format("%.3f", totalSec) + "s");
            System.out.println("=========================");

        } catch (Exception e) {
            System.err.println("Error en análisis léxico concurrente: " + e.getMessage());
            // Fallback to sequential
            long start = System.nanoTime();
            List<Token> afdTokens = automataStrategy.classify(input);
            outputTokens.addAll(afdTokens);
            long elapsed = (System.nanoTime() - start);
            tiempos.put("AFD-fallback", elapsed / 1_000_000);
            obs.setTokensAFD(afdTokens.stream().map(t -> t.getTipo() + ":" + t.getValor()).toList());
            obs.setTokensLLM(List.of());
            obs.setHilosConcurrentes(1);
            obs.setTiempoTotalLexico(elapsed / 1_000_000_000.0);
            obs.setTiemposPorFase(tiempos);
        } finally {
            executor.shutdown();
        }

        return obs;
    }

    private List<Token> mergeTokens(List<Token> afd, List<Token> llm) {
        // Use AFD tokens as primary, enrich with LLM only for uncovered regions
        List<Token> merged = new ArrayList<>(afd);
        
        // Sort AFD tokens by position to check spans
        List<Token> sortedAfd = new ArrayList<>(afd);
        sortedAfd.sort(Comparator.comparingInt(Token::getPosicion));
        
        for (Token t : llm) {
            int start = t.getPosicion();
            int end = start + t.getValor().length();
            
            boolean overlaps = false;
            for (Token afdTok : sortedAfd) {
                int afdStart = afdTok.getPosicion();
                int afdEnd = afdStart + afdTok.getValor().length();
                
                // Check if [start, end) overlaps with [afdStart, afdEnd)
                if (start < afdEnd && end > afdStart) {
                    overlaps = true;
                    break;
                }
            }
            
            if (!overlaps) {
                merged.add(t);
            }
        }
        
        // Keep tokens ordered by their position in the source query
        merged.sort(Comparator.comparingInt(Token::getPosicion));
        return merged;
    }

    private int countStrings(String input) {
        int count = 0;
        boolean inString = false;
        for (char c : input.toCharArray()) {
            if (c == '\'') inString = !inString;
            if (inString && Character.isLetter(c)) {
                // Just counting potential string patterns
            }
        }
        // Return at least 0
        return Math.max(0, count);
    }
}
