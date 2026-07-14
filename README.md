# Mini-Compilador de Consultas SQL en Español

## Descripción

Motor de compilación que entiende un subconjunto básico de SQL en español: INSERTAR, MODIFICAR, ELIMINAR y SELECCIONAR.

### Ejemplo
```
Entrada: SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18
Salida:  ✅ Compilación exitosa - AST generado
```

## Arquitectura

El principio que rige todo el diseño es **"el LLM genera, el código verifica"**. El LLM
participa en las fases que exige la guía (extracción de tokens y generación del AST), pero
ninguna salida del modelo llega a ejecutarse sin pasar antes por un validador determinista
escrito en código. Si el modelo alucina o no está disponible, el compilador sigue
funcionando y sigue rechazando correctamente las consultas inválidas.

### Backend (Java 21 + Spring Boot 3.4)

- **Analizador Léxico**: el LLM (Llama 3.2) extrae los tokens mediante PLN y un **AFD real**
  (`AutomataStrategy`: estados, alfabeto, función de transición y estados de aceptación) los
  verifica contra la consulta. Solo los tokens confirmados por el autómata alimentan al
  compilador; las alucinaciones se descartan y se muestran como discrepancia en la vista Avanzada.
  Ambos se ejecutan en paralelo mediante `ThreadPoolExecutor`.

- **Analizador Sintáctico**: el LLM genera el **AST** en formato JSON (`LLMAstGenerator`), que
  se convierte a los objetos `ASTNode` del proyecto. `AstValidator` lo contrasta contra los
  tokens reales antes de aceptarlo. La **validez sintáctica siempre la decide el código**
  (`RecursiveDescentParser`, descendente recursivo sobre la gramática), que además actúa como
  respaldo del AST si el modelo falla.

- **Analizador Semántico**: 100% código, sin LLM. Tabla de Símbolos cargada desde la metadata
  real de la base de datos. Valida tablas, columnas (incluidas las de la cláusula `CUANDO`),
  tipos, número de valores en `INSERTAR` y operaciones peligrosas.

- **Agente tutor** (`ErrorAssistantService`): el LLM explica los errores que el compilador **ya**
  detectó. No reanaliza la consulta ni emite veredictos.

### Frontend (Angular 18)

Dividido en dos secciones mediante router y navbar:

- **Compilador** (`/compilador`): editor, resultado, explicación del error por el agente tutor,
  ejemplos rápidos, ayuda e historial. Sin ruido técnico.
- **Avanzado** (`/avanzado`): tokens, conciliación LLM/AFD, origen del AST, árbol sintáctico,
  tabla de símbolos, metadata, métricas de concurrencia, gramática y tabla de transiciones del AFD.

### Base de Datos (PostgreSQL)
- Tablas: `usuarios`, `productos`, `pedidos`
- Columnas con tipos de datos
- Datos de ejemplo precargados

## Requisitos

- Java 21+
- Maven 3.9+
- Node.js 18+
- Angular CLI 18+
- Docker (opcional, para PostgreSQL)

## Inicio Rápido

```bash
# Opción 1: Script automático
./start.sh

# Opción 2: Manual
# 1. Iniciar PostgreSQL
docker compose up -d

# 2. Iniciar backend
cd backend && mvn spring-boot:run

# 3. Iniciar frontend
cd frontend && npx ng serve
```

- Frontend: http://localhost:4200
- Backend API: http://localhost:8086/api/compiler/compile
- H2 Console: http://localhost:8086/h2-console

## Endpoints API

### POST /api/compiler/compile
Compila una consulta SQL en español.
```json
{
  "query": "SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18"
}
```

### POST /api/compiler/explain-error
Agente tutor: explica un error **ya detectado** por el compilador. Recibe la consulta y los
errores estructurados; devuelve explicación, causa, corrección, ejemplo correcto y pasos.

### GET /api/compiler/metadata
Obtiene metadatos del sistema (tablas, columnas, registros, ejemplos).

### GET /api/compiler/automata
Definición del AFD: estados, alfabeto, estados de aceptación y tabla de transiciones.

### GET /api/compiler/grammar
Gramática BNF del lenguaje.

## Gramática

```
<query> ::= <select_query> | <insert_query> | <update_query> | <delete_query>
<select_query> ::= SELECCIONAR <columns> DESDE <table> [CUANDO <condition>]
<insert_query> ::= INSERTAR EN <table> VALORES "(" <values> ")"
<update_query> ::= MODIFICAR <table> ESTABLECER <assignments> [CUANDO <condition>]
<delete_query> ::= ELIMINAR DESDE <table> [CUANDO <condition>]
```

## Errores Detectados

### Léxicos
- Caracteres fuera del alfabeto del lenguaje (`;`, `@`, `#`, ...)
- Cadenas sin cerrar

### Sintácticos
Se reportan **todos** los errores de la consulta, no solo el primero, con posición y sugerencia:
- Consulta que no empieza por SELECCIONAR / INSERTAR / MODIFICAR / ELIMINAR
- Palabras reservadas faltantes (DESDE, EN, VALORES, ESTABLECER)
- Columnas, tablas, operadores o valores faltantes
- Paréntesis sin cerrar, contenido sobrante al final

### Semánticos
- **Columna inexistente en la cláusula SELECCIONAR** (restricción obligatoria de la práctica)
- Tabla inexistente
- Columna inexistente en la cláusula CUANDO
- Columna inexistente en MODIFICAR ESTABLECER
- Tipos incompatibles (texto asignado o comparado con una columna numérica)
- Número de valores incorrecto en INSERTAR
- **Operaciones peligrosas**: `ELIMINAR` o `MODIFICAR` sin cláusula `CUANDO` se bloquean, ya que
  afectarían a todos los registros de la tabla

## Concurrencia

El analizador léxico ejecuta el AFD y el LLM en paralelo mediante `ThreadPoolExecutor` (2 hilos).
El AFD resuelve la parte formal en menos de 1 ms; el LLM aporta la tokenización por PLN. El tiempo
total del léxico equivale al del hilo más lento, no a la suma de ambos. Si el LLM no responde, el
análisis continúa solo con el AFD.

Ver `STANDARD_TOKENS.md` para documentación detallada de tokens.
