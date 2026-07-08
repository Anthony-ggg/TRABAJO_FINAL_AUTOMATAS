# Mini-Compilador de Consultas SQL en Español

## Descripción

Motor de compilación que entiende un subconjunto básico de SQL en español: INSERTAR, MODIFICAR, ELIMINAR y SELECCIONAR.

### Ejemplo
```
Entrada: SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18
Salida:  ✅ Compilación exitosa - AST generado
```

## Arquitectura

### Backend (Java 21 + Spring Boot 3.4)
- **Analizador Léxico**: Strategy Pattern con AutomataStrategy (AFD) + LLMStrategy (Llama 3.2)
- **Analizador Sintáctico**: Construcción de AST con validación vía LLM
- **Analizador Semántico**: Tabla de Símbolos con validación contra base de datos
- **Concurrencia**: ThreadPoolExecutor para ejecución paralela AFD/LLM

### Frontend (Angular 18)
- Interfaz con ejemplos rápidos (válidos e inválidos)
- Visualización de tokens con colores distintivos
- Tabla de observaciones con métricas de concurrencia
- Visualización del AST

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
- Backend API: http://localhost:8080/api/compiler/compile
- H2 Console: http://localhost:8080/h2-console

## Endpoints API

### POST /api/compiler/compile
Compila una consulta SQL en español.
```json
{
  "query": "SELECCIONAR nombre, edad DESDE usuarios CUANDO edad > 18"
}
```

### GET /api/compiler/metadata
Obtiene metadatos del sistema (tablas, columnas, ejemplos).

## Gramática

```
<query> ::= <select_query> | <insert_query> | <update_query> | <delete_query>
<select_query> ::= SELECCIONAR <columns> DESDE <table> [CUANDO <condition>]
<insert_query> ::= INSERTAR EN <table> VALORES "(" <values> ")"
<update_query> ::= MODIFICAR <table> ESTABLECER <assignments> [CUANDO <condition>]
<delete_query> ::= ELIMINAR DESDE <table> [CUANDO <condition>]
```

## Errores Semánticos Detectados
- Columnas inexistentes en la cláusula SELECCIONAR
- Tablas inexistentes en la cláusula DESDE
- Columnas inválidas en MODIFICAR ESTABLECER

## Concurrencia

El analizador léxico ejecuta AFD y LLM en paralelo usando ThreadPoolExecutor:
- Hilo-AFD: clasifica tokens sintácticos en < 1ms
- ThreadPoolExecutor-0_0/0_1: procesan tokens semánticos con LLM (~1s)
- Tiempo total: ~1s (vs ~2s secuencial)

Ver `STANDARD_TOKENS.md` para documentación detallada de tokens.
