# Estándar de Tokens - Mini-Compilador SQL en Español

## Tabla Oficial de Tokens

| Token | Expresión Regular | Ejemplo | Categoría |
|-------|------------------|---------|-----------|
| `SELECCIONAR` | `SELECCIONAR` | `SELECCIONAR nombre` | Palabra Reservada |
| `INSERTAR` | `INSERTAR` | `INSERTAR EN usuarios` | Palabra Reservada |
| `MODIFICAR` | `MODIFICAR` | `MODIFICAR usuarios` | Palabra Reservada |
| `ELIMINAR` | `ELIMINAR` | `ELIMINAR DESDE usuarios` | Palabra Reservada |
| `DESDE` | `DESDE` | `DESDE usuarios` | Palabra Reservada |
| `CUANDO` | `CUANDO` | `CUANDO edad > 18` | Palabra Reservada |
| `EN` | `EN` | `INSERTAR EN usuarios` | Palabra Reservada |
| `VALORES` | `VALORES` | `VALORES (1, 'Ana')` | Palabra Reservada |
| `ESTABLECER` | `ESTABLECER` | `ESTABLECER precio = 100` | Palabra Reservada |
| `Y` | `Y` | `edad > 18 Y ciudad = 'Madrid'` | Palabra Reservada (Operador Lógico) |
| `O` | `O` | `edad < 18 O edad > 65` | Palabra Reservada (Operador Lógico) |
| `IDENTIFICADOR` | `[a-zA-Z_][a-zA-Z0-9_]*` | `nombre`, `edad`, `usuarios` | Identificador |
| `NUMERO` | `\d+(\.\d+)?` | `18`, `3.14`, `100` | Literal |
| `CADENA` | `'[^']*'` | `'Ana'`, `'Madrid'` | Literal |
| `OPERADOR` | `>=` | `<=` | `<>` | `!=` | `=` | `>` | `<` | `=`, `>`, `>=` | Operador de Comparación |
| `SIMBOLO` | `[(),*]` | `(`, `)`, `,`, `*` | Símbolo Especial |
| `ESPACIO` | `\s+` | ` `, `\t`, `\n` | Ignorado |

## Gramática Formal

```
<query> ::= <select_query> | <insert_query> | <update_query> | <delete_query>

<select_query> ::= SELECCIONAR <columns> DESDE <table> [CUANDO <condition>]
<insert_query> ::= INSERTAR EN <table> VALORES "(" <values> ")"
<update_query> ::= MODIFICAR <table> ESTABLECER <assignments> [CUANDO <condition>]
<delete_query> ::= ELIMINAR DESDE <table> [CUANDO <condition>]

<columns> ::= <column> ("," <column>)*
<column> ::= IDENTIFICADOR | "*"
<table> ::= IDENTIFICADOR
<condition> ::= <expression> (("Y" | "O") <expression>)*
<expression> ::= IDENTIFICADOR OPERADOR <value>
<values> ::= <value> ("," <value>)*
<assignments> ::= <assignment> ("," <assignment>)*
<assignment> ::= IDENTIFICADOR "=" <value>
<value> ::= NUMERO | CADENA | IDENTIFICADOR
```

## Arquitectura del Compilador

```
                    +-------------------+
                    |   Entrada SQL     |
                    |  (Texto Original) |
                    +---------+---------+
                              |
                    +---------v---------+
                    |  ANÁLISIS LÉXICO  |
                    |  (Concurrente)    |
                    +---+---+---+---+
                        |   |   |   |
              +---------+   |   |   +---------+
              |             |   |               |
        +-----v-----+ +-----v-----+     +------v------+
        | Hilo-AFD  | | ThreadPool |     | ThreadPool  |
        | (< 1ms)   | | Executor   |     | Executor    |
        | Tokens    | | -0_0       |     | -0_1        |
        | duros     | | (LLM)      |     | (LLM)       |
        +-----------+ +-----+------+     +------+------+
                              |                  |
                              +--------+---------+
                                       |
                              +--------v--------+
                              |  Tokens UNIFICADOS |
                              +--------+----------+
                                       |
                              +--------v--------+
                              | ANÁLISIS        |
                              | SINTÁCTICO      |
                              | (LLM + Grammar)  |
                              +--------+--------+
                                       |
                              +--------v--------+
                              | AST (Árbol       |
                              | Sintáctico       |
                              | Abstracto)       |
                              +--------+--------+
                                       |
                              +--------v--------+
                              | ANÁLISIS        |
                              | SEMÁNTICO       |
                              | (Tabla de        |
                              | Símbolos)        |
                              +--------+--------+
                                       |
                              +--------v--------+
                              | RESULTADO       |
                              | (Éxito/Error)    |
                              +-----------------+
```

## Patrón de Diseño: Strategy

El análisis léxico implementa el patrón **Strategy** con dos estrategias concretas:

1. **AutomataStrategy** (AFD): Clasifica tokens "duros" usando expresiones regulares
   - Números, operadores, símbolos, palabras reservadas exactas
   - Ejecución en microsegundos

2. **LLMStrategy** (LLM/NLN): Clasifica tokens "semánticos" usando el modelo Llama 3.2
   - Identificadores contextuales, desambiguación
   - Ejecución simulada de ~1 segundo

**LexerContext**: Contexto que permite intercambiar estrategias sin modificar el código central.

### Beneficios del Patrón Strategy
- **Alta cohesión**: Cada estrategia tiene una responsabilidad única
- **Bajo acoplamiento**: Cambiar de LLM no requiere modificar el AFD
- **Extensibilidad**: Agregar nuevas estrategias sin tocar código existente

## Ejecución Concurrente

El sistema utiliza `ThreadPoolExecutor` para ejecutar AFD y LLM en paralelo:

```
Hilo principal
  ├── Envía tarea AFD → Hilo-AFD (clasifica en < 1ms)
  ├── Envía tarea LLM → ThreadPoolExecutor-0_0 (procesa ~1s)
  └── Envía tarea LLM → ThreadPoolExecutor-0_1 (procesa ~1s)
  
  Tiempo total ≈ max(tiempo_AFD, tiempo_LLM) ≈ 1s
  (vs ~2s secuencial)
```

## Base de Datos

El sistema usa PostgreSQL (vía Docker) con las siguientes tablas:

- **tablas**: Metadatos de tablas disponibles
- **columnas**: Columnas por tabla con tipo de dato
- **registros**: Datos de ejemplo almacenados como JSONB
- **compilaciones**: Historial de compilaciones realizadas

### Tablas Disponibles
| Tabla | Columnas |
|-------|----------|
| usuarios | id, nombre, edad, email, ciudad |
| productos | id, nombre, precio, stock, categoria |
| pedidos | id, usuario_id, producto_id, cantidad, fecha |

## Errores Semánticos

El sistema detecta los siguientes errores semánticos:

1. **Tabla inexistente**: `SELECCIONAR nombre DESDE empleados` → Error si `empleados` no existe
2. **Columna inexistente**: `SELECCIONAR salario DESDE usuarios` → Error si `salario` no existe en `usuarios`
3. **Columna en MODIFICAR**: `MODIFICAR usuarios ESTABLECER salario = 5000` → Error si `salario` no existe

## Ejemplos de Consultas

### Válidas
```
SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18
SELECCIONAR * DESDE productos
INSERTAR EN usuarios VALORES (4, 'Pedro Ruiz', 28, 'pedro@email.com', 'Sevilla')
MODIFICAR usuarios ESTABLECER ciudad = 'Madrid' CUANDO nombre = 'Ana García'
ELIMINAR DESDE usuarios CUANDO edad < 18
```

### Inválidas
```
SELECCIONAR nombre DESDE usuarios CUANDO salario > 50000  (columna no existe)
SELECCIONAR nombre DESDE empleados                        (tabla no existe)
CONSULTAR nombre DESDE usuarios                           (palabra reservada incorrecta)
```
