# Mini-Compilador de Consultas SQL en Espanol

Motor de compilacion que entiende un subconjunto basico de SQL en espanol:
SELECCIONAR, INSERTAR, MODIFICAR y ELIMINAR. Disenado como arquitectura de
microservicios con Spring Boot 3.4, Angular 18 y PostgreSQL.

```
Entrada:  SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18
Salida:   Compilacion exitosa - AST generado - 3 registros encontrados
```

---

## Arquitectura

El principio que rige todo el diseno es **"el LLM genera, el codigo verifica"**.
El LLM participa en las fases que exige la guia (extraccion de tokens y
generacion del AST), pero ninguna salida del modelo llega a ejecutarse sin pasar
antes por un validador determinista escrito en codigo. Si el modelo alucina o no
esta disponible, el compilador sigue funcionando y sigue rechazando correctamente
las consultas invalidas.

### Diagrama de arquitectura

```
                         +-----------------------+
                         |   Frontend Angular    |
                         |      (4200)           |
                         +-----------+-----------+
                                     |
                                     | HTTP
                                     v
                         +-----------+-----------+
                         |     Orchestrator      |
                         |       (8080)          |
                         |  POST /api/compiler/*  |
                         +--+------+------+--+--+
                            |      |      |  |
               +------------+  +---+---+  |  +------------+
               |               |       |  |               |
               v               v       v  v               v
     +---------+------+  +----+----+  +-+-----------+  +--+-----------+
     |  Lexico        |  |Sintactico|  | Semantico  |  |  PostgreSQL  |
     |  (8081)        |  | (8082)   |  |  (8083)    |  |  (5432)      |
     |                 |  |          |  |            |  |              |
     | AFD + LLM      |  | Parser + |  | Tabla de   |  | usuarios     |
     | en paralelo    |  | LLM AST  |  | Simbolos   |  | productos    |
     +----------------+  +----------+  +-----+------+  | pedidos      |
                                                         +--------------+
```

### Flujo de compilacion

```
  Consulta SQL
       |
       v
  +---------+        +----------+
  | Lexico  |------->| Tokens   |  AFD (AutomataStrategy) + LLM en paralelo
  +---------+        +----+-----+  via ThreadPoolExecutor (2 hilos)
                             |
                             v
  +-----------+      +------+-----+
  | Sintactico|----->|    AST     |  RecursiveDescentParser + LLMAstGenerator
  +-----------+      +------+-----+  AstValidator contrasta contra tokens reales
                             |
                             v
  +-----------+      +------+-----+      +------------+
  | Semantico |----->| Valida AST |----->| PostgreSQL |
  +-----------+      +------+-----+      +------------+
                             |
                             v
  +-----------+      +------+-----+
  | Ejecucion |----->| Resultado  |  SELECT / INSERT / UPDATE / DELETE
  +-----------+      +------------+
```

---

## Microservicios

| Servicio | Puerto | Responsabilidad | Dependencias |
|----------|--------|-----------------|--------------|
| `lexico-service` | 8081 | Analisis lexico: AFD + LLM en paralelo | compilador-shared |
| `sintactico-service` | 8082 | Analisis sintactico: parser recursivo + LLM | compilador-shared |
| `semantico-service` | 8083 | Analisis semantico: tabla de simbolos desde DB | compilador-shared, PostgreSQL |
| `orchestrator` | 8080 | Orquesta los 3 servicios, ejecuta la consulta | compilador-shared, PostgreSQL |
| `compilador-db` | 5432 | PostgreSQL con datos de ejemplo | - |

### Compilador-Shared (`services/shared/`)

Modulo Maven compartido que contiene los modelos de datos usados por todos los
servicios:

- `Token` — token clasificado con tipo, valor, origen (AFD/LLM) y posicion
- `ASTNode` — nodo del Arbol Sintactico Abstracto
- `CompileError` — error estructurado con fase, codigo, mensaje y sugerencia

### Lexico Service (8081)

Ejecuta dos estrategias **en paralelo** usando `ThreadPoolExecutor`:

1. **AutomataStrategy (AFD)** — Automata Finito Determinista real con 15 estados,
   tabla de transiciones y estados de aceptacion. Clasifica tokens en < 1 ms.
2. **LLMStrategy** — Llama 3.2 via Ollama, clasifica tokens mediante PLN.

Los tokens confirmados por ambos se marcan como `LLM+AFD`. Las alucinaciones del
LLM se descartan. Si el LLM no responde, el analisis continua solo con el AFD.

### Sintactico Service (8082)

1. **LLMAstGenerator** — El LLM genera el AST en formato JSON.
2. **AstValidator** — Valida el AST contra los tokens reales.
3. **RecursiveDescentParser** — Parser determinista descendente recursivo sobre
   la gramatica BNF. Actua como respaldo si el LLM falla.

La validez sintactica **siempre la decide el codigo**.

### Semantico Service (8083)

Analisis 100% codigo, sin LLM:

- Tabla de Simbolos cargada desde metadata de PostgreSQL (tablas, columnas, tipos)
- Inicializa la base de datos automaticamente al arrancar (CREATE TABLE + seed)
- Valida: tablas existentes, columnas existentes, tipos compatibles, numero de
  valores en INSERTAR, operaciones peligrosas (DELETE/UPDATE sin WHERE)

### Orchestrator (8080)

Punto de entrada unico. Recibe las consultas del frontend y coordina:

1. Llama a `/api/lexico/analyze` → obtiene tokens
2. Llama a `/api/sintactico/analyze` → obtiene AST
3. Llama a `/api/semantico/analyze` → valida semanticamente
4. Ejecuta la consulta contra PostgreSQL
5. Retorna resultado completo al frontend

---

## Estructura del proyecto

```
TRABAJO_FINAL_AUTOMATAS/
├── pom.xml                          # Parent POM multi-module
├── docker-compose.yml               # 5 servicios (PostgreSQL + 4 microservicios)
├── services/
│   ├── shared/                      # Modelos compartidos (Token, ASTNode, CompileError)
│   │   └── pom.xml
│   ├── lexico/                      # Microservicio lexico (8081)
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/.../lexico/
│   │       ├── controller/LexicoController.java
│   │       └── lexer/
│   │           ├── TokenStrategy.java       # Interfaz Strategy
│   │           ├── AutomataStrategy.java    # AFD real (15 estados)
│   │           ├── LLMStrategy.java         # LLM via Ollama
│   │           ├── LexerContext.java         # Contexto Strategy
│   │           └── LexicalAnalyzer.java     # Concurrencia AFD+LLM
│   ├── sintactico/                  # Microservicio sintactico (8082)
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/.../sintactico/
│   │       ├── controller/SintacticoController.java
│   │       └── parser/
│   │           ├── GrammarDefinition.java         # Gramatica BNF
│   │           ├── RecursiveDescentParser.java    # Parser determinista
│   │           ├── LLMAstGenerator.java           # Generador AST via LLM
│   │           ├── AstValidator.java              # Validador AST
│   │           └── SyntaxAnalyzer.java            # Fachada del analisis
│   └── semantico/                   # Microservicio semantico (8083)
│       ├── Dockerfile
│       ├── pom.xml
│       └── src/.../semantico/
│           ├── controller/SemanticoController.java
│           ├── model/SymbolTable.java             # Tabla de simbolos
│           └── semantic/SemanticAnalyzer.java     # Analizador semantico
├── orchestrator/                    # Orquestador (8080)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/.../orchestrator/
│       ├── controller/CompilerController.java
│       └── service/
│           ├── CompilerService.java               # Orquestacion + CompileResult
│           └── QueryExecutorService.java          # Ejecucion SQL real
├── frontend/                        # Angular 18
│   └── src/app/
│       ├── pages/
│       │   ├── compilador.component.*    # Vista basica
│       │   └── avanzado.component.*      # Vista avanzada
│       └── compiler.service.ts           # Cliente HTTP al orchestrator
└── STANDARD_TOKENS.md               # Documentacion de tokens y gramatica
```

---

## Tecnologias

| Capa | Tecnologia | Version |
|------|-----------|---------|
| Backend | Java | 21 |
| Framework | Spring Boot | 3.4.5 |
| Build | Maven | multi-module |
| Frontend | Angular | 18 |
| Base de datos | PostgreSQL | 16 |
| LLM | Ollama + Llama 3.2 | - |
| Contenedores | Docker + Docker Compose | - |

---

## Requisitos

- Java 21+
- Maven 3.9+
- Node.js 18+ (para frontend)
- Angular CLI 18+ (para frontend)
- Docker + Docker Compose (para microservicios)
- Ollama con Llama 3.2 (opcional, para LLM)

---

## Inicio rapido

### Con Docker Compose (recomendado)

```bash
# Levantar todo (PostgreSQL + 4 microservicios)
docker compose up --build

# En otra terminal, el frontend
cd frontend && npm install && npx ng serve
```

### Desarrollo local (sin Docker)

```bash
# 1. PostgreSQL (debe correr en puerto 5432)
docker compose up -d compilador-db

# 2. Compilar todo
mvn clean package -DskipTests

# 3. Levantar microservicios (en terminales separadas)
java -jar services/lexico/target/lexico-service-1.0.0.jar
java -jar services/sintactico/target/sintactico-service-1.0.0.jar
java -jar services/semantico/target/semantico-service-1.0.0.jar
java -jar orchestrator/target/orchestrator-1.0.0.jar

# 4. Frontend
cd frontend && npm install && npx ng serve
```

### URLs

| Servicio | URL |
|----------|-----|
| Frontend | http://localhost:4200 |
| Orchestrator API | http://localhost:8080/api/compiler |
| Lexico | http://localhost:8081 |
| Sintactico | http://localhost:8082 |
| Semantico | http://localhost:8083 |
| PostgreSQL | localhost:5432 |

---

## API Endpoints

### Orchestrator (8080)

#### `POST /api/compiler/compile`

Compila una consulta SQL en espanol. Coordina lexico → sintactico → semantico → ejecucion.

```json
// Request
{ "query": "SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18" }

// Response
{
  "exitoso": true,
  "mensaje": "Compilacion exitosa",
  "tokens": [ ... ],
  "ast": { "tipo": "SELECCIONAR", "hijos": [ ... ] },
  "tablaSimbolos": { ... },
  "erroresDetallados": [],
  "filasResultado": [ { "nombre": "Ana Garcia", "edad": 25 } ],
  "mensajeEjecucion": "Consulta ejecutada exitosamente. Se encontraron 1 registros."
}
```

#### `GET /api/compiler/metadata`

Metadatos del sistema: tablas, columnas, registros, ejemplos.

#### `GET /api/compiler/automata`

Definicion del AFD: estados, alfabeto, transiciones, estados de aceptacion.

### Lexico Service (8081)

#### `POST /api/lexico/analyze`

```json
// Request
{ "query": "SELECCIONAR nombre DESDE usuarios" }

// Response
{
  "tokens": [ { "tipo": "SELECCIONAR", "valor": "SELECCIONAR", "origen": "AFD", "posicion": 0 } ],
  "errores": [],
  "observaciones": { "tokensAFD": [...], "tokensLLM": [...], "coincidenciaLexica": 100.0 }
}
```

#### `GET /api/lexico/automata`

Definicion completa del AFD con tabla de transiciones.

### Sintactico Service (8082)

#### `POST /api/sintactico/analyze`

```json
// Request
{ "query": "SELECCIONAR nombre DESDE usuarios", "tokens": [...] }

// Response
{ "ast": { "tipo": "SELECCIONAR", "hijos": [...] }, "errores": [] }
```

### Semantico Service (8083)

#### `POST /api/semantico/analyze`

```json
// Request
{ "query": "...", "tokens": [...], "ast": { ... } }

// Response
{ "errores": [], "tablaSimbolos": { "tablas_disponibles": [...] } }
```

---

## Gramatica BNF

```
<query>         ::= <select_query> | <insert_query> | <update_query> | <delete_query>

<select_query>  ::= SELECCIONAR <columns> DESDE <table> [CUANDO <condition>]
<insert_query>  ::= INSERTAR EN <table> VALORES "(" <values> ")"
<update_query>  ::= MODIFICAR <table> ESTABLECER <assignments> [CUANDO <condition>]
<delete_query>  ::= ELIMINAR DESDE <table> [CUANDO <condition>]

<columns>       ::= <column> ("," <column>)*
<column>        ::= IDENTIFICADOR | "*"
<table>         ::= IDENTIFICADOR
<condition>     ::= <expression> (("Y" | "O") <expression>)*
<expression>    ::= IDENTIFICADOR OPERADOR <value>
<values>        ::= <value> ("," <value>)*
<assignments>   ::= <assignment> ("," <assignment>)*
<assignment>    ::= IDENTIFICADOR "=" <value>
<value>         ::= NUMERO | CADENA | IDENTIFICADOR
```

### Ejemplos validos

```sql
SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18
SELECCIONAR * DESDE productos
INSERTAR EN usuarios VALORES ('Pedro Ruiz', 28, 'pedro@email.com', 'Sevilla')
MODIFICAR usuarios ESTABLECER ciudad = 'Madrid' CUANDO nombre = 'Ana Garcia'
ELIMINAR DESDE usuarios CUANDO edad < 18
SELECCIONAR nombre DESDE usuarios CUANDO edad > 25 Y ciudad = 'Madrid'
```

### Ejemplos invalidos

```sql
SELECCIONAR nombre DESDE usuarios CUANDO salario > 50000   -- columna inexistente
SELECCIONAR nombre DESDE empleados                          -- tabla inexistente
CONSULTAR nombre DESDE usuarios                             -- palabra reservada incorrecta
ELIMINAR DESDE usuarios                                     -- sin CUANDO (bloqueado)
```

---

## Errores detectados

### Lexicos

- Caracteres fuera del alfabeto del lenguaje (`;`, `@`, `#`, ...)
- Cadenas sin cerrar

### Sintacticos

Se reportan **todos** los errores de la consulta, no solo el primero:

- Consulta que no empieza por SELECCIONAR / INSERTAR / MODIFICAR / ELIMINAR
- Palabras reservadas faltantes (DESDE, EN, VALORES, ESTABLECER)
- Columnas, tablas, operadores o valores faltantes
- Parentesis sin cerrar, contenido sobrante al final

### Semanticos

- Columna inexistente en SELECCIONAR / CUANDO / MODIFICAR ESTABLECER
- Tabla inexistente
- Tipos incompatibles (texto comparado con columna numerica)
- Numero de valores incorrecto en INSERTAR
- Operaciones peligrosas: ELIMINAR o MODIFICAR sin clausula CUANDO

---

## Patrones de diseno

### Strategy (Analisis Lexico)

```
        TokenStrategy (interfaz)
               /          \
    AutomataStrategy    LLMStrategy
         (AFD)          (Llama 3.2)
```

`LexerContext` permite intercambiar estrategias sin modificar el codigo central.
Ambas estrategias se ejecutan en paralelo via `ThreadPoolExecutor`.

### Concurrencia

El analisis lexico ejecuta el AFD y el LLM en paralelo (2 hilos). El tiempo
total equivale al del hilo mas lento, no a la suma de ambos. Si el LLM no
responde en 35 segundos, el analisis continua solo con el AFD.

### Orquestacion REST

El orchestrator coordina los 3 servicios via HTTP. Cada servicio es un
microservicio independiente con su propio `@SpringBootApplication`, su propio
puerto y su propio Dockerfile. Se comunican mediante REST API usando
`HttpClient` de Java.

---

## Base de datos

PostgreSQL con las siguientes tablas de negocio:

| Tabla | Columnas |
|-------|----------|
| `usuarios` | id, nombre, edad, email, ciudad |
| `productos` | id, nombre, precio, stock, categoria |
| `pedidos` | id, usuario_id, producto_id, cantidad, fecha |

El servicio semantico crea automaticamente las tablas de metadata (`tablas`,
`columnas`) y siembra datos de ejemplo al arrancar.

Ver `STANDARD_TOKENS.md` para documentacion detallada de tokens y gramatica.
