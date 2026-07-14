package com.compilador.controller;

import com.compilador.lexer.AutomataStrategy;
import com.compilador.model.CompileError;
import com.compilador.model.CompileResult;
import com.compilador.parser.GrammarDefinition;
import com.compilador.service.CompilerService;
import com.compilador.service.ErrorAssistantService;
import com.compilador.service.QueryExecutorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/compiler")
@CrossOrigin(origins = "*")
public class CompilerController {

    private final CompilerService compilerService;
    private final QueryExecutorService queryExecutorService;
    private final ErrorAssistantService errorAssistantService;
    private final AutomataStrategy automataStrategy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompilerController(CompilerService compilerService,
                              QueryExecutorService queryExecutorService,
                              ErrorAssistantService errorAssistantService,
                              AutomataStrategy automataStrategy) {
        this.compilerService = compilerService;
        this.queryExecutorService = queryExecutorService;
        this.errorAssistantService = errorAssistantService;
        this.automataStrategy = automataStrategy;
    }

    @PostMapping("/compile")
    public ResponseEntity<CompileResult> compile(@RequestBody Map<String, String> request) {
        // CompilerService ya valida la entrada y nunca lanza: siempre devuelve un resultado.
        return ResponseEntity.ok(compilerService.compile(request.get("query")));
    }

    /**
     * Agente tutor. Recibe el diagnostico que YA produjo el compilador y devuelve una
     * explicacion didactica. El LLM no reanaliza la consulta ni emite veredictos.
     */
    @PostMapping("/explain-error")
    public ResponseEntity<ErrorAssistantService.Explicacion> explainError(@RequestBody Map<String, Object> request) {
        String consulta = String.valueOf(request.getOrDefault("query", ""));

        List<CompileError> errores = new ArrayList<>();
        Object crudos = request.get("errores");
        if (crudos instanceof List<?> lista) {
            for (Object item : lista) {
                try {
                    errores.add(objectMapper.convertValue(item, CompileError.class));
                } catch (IllegalArgumentException e) {
                    // Entrada malformada: se ignora ese error en lugar de romper la peticion.
                }
            }
        }

        return ResponseEntity.ok(errorAssistantService.explicar(consulta, errores));
    }

    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata() {
        Map<String, Object> meta = queryExecutorService.getLiveDatabaseState();

        meta.put("ejemplos", List.of(
                "SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18",
                "SELECCIONAR * DESDE productos",
                "INSERTAR EN usuarios VALORES ('Pedro Ruiz', 28, 'pedro@email.com', 'Sevilla')",
                "MODIFICAR usuarios ESTABLECER ciudad = 'Madrid' CUANDO nombre = 'Ana Garcia'",
                "ELIMINAR DESDE usuarios CUANDO edad < 18",
                "SELECCIONAR nombre DESDE usuarios CUANDO edad > 25 Y ciudad = 'Madrid'"
        ));
        meta.put("ejemplos_invalidos", List.of(
                "SELECCIONAR nombre DESDE usuarios CUANDO salario > 50000",
                "SELECCIONAR nombre DESDE empleados",
                "CONSULTAR nombre DESDE usuarios",
                "SELECCIONAR nombre, edad productos CUANDO edad > 18",
                "ELIMINAR DESDE usuarios",
                "INSERTAR usuarios VALORES (1, 'test')"
        ));
        return ResponseEntity.ok(meta);
    }

    /** Definicion del AFD (estados, alfabeto y tabla de transiciones) para la vista Avanzada. */
    @GetMapping("/automata")
    public ResponseEntity<Map<String, Object>> getAutomata() {
        return ResponseEntity.ok(automataStrategy.getDefinicionAutomata());
    }

    /** Gramatica del lenguaje, para la vista Avanzada. */
    @GetMapping("/grammar")
    public ResponseEntity<Map<String, String>> getGrammar() {
        return ResponseEntity.ok(Map.of("gramatica", GrammarDefinition.GRAMMAR));
    }
}
