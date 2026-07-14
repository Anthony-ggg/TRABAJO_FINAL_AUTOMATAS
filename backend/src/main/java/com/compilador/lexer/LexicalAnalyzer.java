package com.compilador.lexer;

import com.compilador.model.CompileError;
import com.compilador.model.CompileResult;
import com.compilador.model.Token;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * Orquestador del analisis lexico.
 *
 * Cumple la guia ("utilizar PLN y un LLM para extraer los tokens") ejecutando el LLM
 * como extractor de tokens, pero SIN dejar que el compilador quede a merced del modelo.
 *
 * Estrategia de conciliacion (LLM propone, AFD verifica):
 *
 *   1. AFD y LLM tokenizan la misma consulta en paralelo (ThreadPoolExecutor).
 *   2. El AFD produce la tokenizacion de referencia, anclada a posiciones reales.
 *   3. Cada token del LLM se concilia contra el AFD por (posicion, lexema):
 *        - Coincide          -> el token se marca "LLM+AFD" (confirmado por ambos).
 *        - El LLM no lo vio  -> se conserva el del AFD (origen "AFD").
 *        - No existe en el texto (posicion -1) o contradice al AFD -> ALUCINACION:
 *          se registra como discrepancia visible en la vista Avanzada y NO entra
 *          al flujo de compilacion.
 *   4. El flujo oficial que alimenta al AST usa unicamente tokens verificados por el AFD.
 *
 * Asi una alucinacion del modelo nunca puede llegar al AST ni a la base de datos,
 * y la contribucion real del LLM sigue siendo medible y demostrable.
 */
@Component
public class LexicalAnalyzer {

    private final AutomataStrategy automataStrategy;
    private final LLMStrategy llmStrategy;

    public LexicalAnalyzer(AutomataStrategy automataStrategy, LLMStrategy llmStrategy) {
        this.automataStrategy = automataStrategy;
        this.llmStrategy = llmStrategy;
    }

    /** Resultado del analisis lexico: tokens verificados, errores y metricas. */
    public record ResultadoLexico(List<Token> tokens,
                                  List<CompileError> errores,
                                  CompileResult.Observaciones observaciones) {}

    public ResultadoLexico analyze(String input) {
        long inicioTotal = System.nanoTime();
        CompileResult.Observaciones obs = new CompileResult.Observaciones();
        Map<String, Long> tiempos = new ConcurrentHashMap<>();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 2, 10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private int contador = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "ThreadPoolExecutor-0_" + contador++);
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
                // El LLM fallo o expiro: el compilador continua con el AFD.
                System.err.println("[Lexico] El LLM no respondio; se continua solo con el AFD: " + e.getMessage());
                tokensLlm = List.of();
            }

        } catch (Exception e) {
            // Fallback total: analisis secuencial con el AFD.
            System.err.println("[Lexico] Fallo la ejecucion concurrente; fallback secuencial: " + e.getMessage());
            long t0 = System.nanoTime();
            resultadoAfd = automataStrategy.analizar(input);
            tiempos.put("AFD-fallback", (System.nanoTime() - t0) / 1_000_000);
            tokensLlm = List.of();
        } finally {
            executor.shutdownNow();
        }

        List<Token> tokensVerificados = conciliar(resultadoAfd.tokens, tokensLlm, obs);

        double totalSeg = (System.nanoTime() - inicioTotal) / 1_000_000_000.0;
        obs.setTokensAFD(resultadoAfd.tokens.stream().map(t -> t.getTipo() + ":" + t.getValor()).toList());
        obs.setTokensLLM(tokensLlm.stream().map(t -> t.getTipo() + ":" + t.getValor()).toList());
        obs.setHilosConcurrentes(2);
        obs.setTiempoTotalLexico(totalSeg);
        obs.setTiemposPorFase(new LinkedHashMap<>(tiempos));
        obs.setLlmDisponible(!tokensLlm.isEmpty());

        return new ResultadoLexico(tokensVerificados, resultadoAfd.errores, obs);
    }

    /**
     * Concilia la tokenizacion del LLM con la del AFD.
     *
     * El AFD manda: la lista devuelta contiene exclusivamente tokens del AFD. Lo que el
     * LLM aporta es la CONFIRMACION (marca de origen "LLM+AFD") y, cuando discrepa, una
     * discrepancia registrada para la vista Avanzada. Ningun token del LLM no verificado
     * llega al parser.
     */
    private List<Token> conciliar(List<Token> tokensAfd, List<Token> tokensLlm,
                                  CompileResult.Observaciones obs) {

        // Indexar los tokens del LLM por posicion real (los alucinados traen posicion -1).
        Map<Integer, Token> llmPorPosicion = new HashMap<>();
        List<String> alucinaciones = new ArrayList<>();

        for (Token t : tokensLlm) {
            if (t.getPosicion() < 0) {
                alucinaciones.add(t.getTipo() + ":" + t.getValor());
            } else {
                llmPorPosicion.putIfAbsent(t.getPosicion(), t);
            }
        }

        List<Token> verificados = new ArrayList<>();
        int confirmados = 0;
        List<String> soloAfd = new ArrayList<>();
        List<String> discrepanciasTipo = new ArrayList<>();

        for (Token afd : tokensAfd) {
            Token llm = llmPorPosicion.remove(afd.getPosicion());

            if (llm != null && llm.getValor().equals(afd.getValor())) {
                // Ambos reconocieron el mismo lexema en la misma posicion.
                afd.setOrigen("LLM+AFD");
                confirmados++;
                if (!tiposEquivalentes(afd.getTipo(), llm.getTipo())) {
                    discrepanciasTipo.add(afd.getValor() + " (AFD=" + afd.getTipo() + ", LLM=" + llm.getTipo() + ")");
                }
            } else {
                // El LLM omitio este token: el AFD lo sostiene.
                soloAfd.add(afd.getTipo() + ":" + afd.getValor());
            }
            verificados.add(afd);
        }

        // Lo que le sobra al LLM y el AFD no reconocio en esa posicion: alucinacion.
        llmPorPosicion.values().forEach(t -> alucinaciones.add(t.getTipo() + ":" + t.getValor()));

        obs.setTokensConfirmados(confirmados);
        obs.setTokensSoloAFD(soloAfd);
        obs.setTokensAlucinados(alucinaciones);
        obs.setDiscrepanciasTipo(discrepanciasTipo);
        obs.setCoincidenciaLexica(tokensAfd.isEmpty() ? 0.0
                : Math.round((confirmados * 1000.0 / tokensAfd.size())) / 10.0);

        if (!alucinaciones.isEmpty()) {
            System.out.println("[Lexico] Se descartaron " + alucinaciones.size()
                    + " token(s) alucinado(s) por el LLM: " + alucinaciones);
        }

        return verificados;
    }

    /** El AFD emite el tipo concreto de la palabra reservada (p. ej. "DESDE"); el LLM emite "PALABRA_RESERVADA". */
    private boolean tiposEquivalentes(String tipoAfd, String tipoLlm) {
        if (tipoAfd.equals(tipoLlm)) return true;
        return "PALABRA_RESERVADA".equals(tipoLlm)
                && AutomataStrategy.PALABRAS_RESERVADAS.contains(tipoAfd);
    }
}
