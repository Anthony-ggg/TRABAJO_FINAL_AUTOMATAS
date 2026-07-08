#!/bin/bash

echo "============================================"
echo " Mini-Compilador SQL en Español - Inicio"
echo "============================================"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Check Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}[ERROR] Docker no está instalado${NC}"
    echo "Usando H2 como base de datos embebida..."
    USE_H2=true
else
    # Start PostgreSQL
    echo -e "${YELLOW}[1/4] Iniciando PostgreSQL...${NC}"
    docker compose up -d postgres 2>/dev/null
    if [ $? -ne 0 ]; then
        docker-compose up -d postgres 2>/dev/null
    fi
    echo -e "${GREEN}[OK] PostgreSQL iniciado${NC}"
    USE_H2=false
fi

# Check Ollama
echo -e "${YELLOW}[2/4] Verificando Ollama...${NC}"
if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo -e "${GREEN}[OK] Ollama disponible${NC}"
else
    echo -e "${YELLOW}[WARN] Ollama no disponible. Usando fallback local.${NC}"
fi

# Start Backend
echo -e "${YELLOW}[3/4] Iniciando Backend (Spring Boot)...${NC}"
cd backend
if [ "$USE_H2" = true ]; then
    mvn spring-boot:run -Dspring-boot.run.profiles=h2 &
else
    mvn spring-boot:run &
fi
BACKEND_PID=$!
cd ..
echo -e "${GREEN}[OK] Backend iniciando (PID: $BACKEND_PID)${NC}"

# Wait for backend
echo "Esperando al backend..."
for i in {1..30}; do
    if curl -s http://localhost:8080/api/compiler/metadata > /dev/null 2>&1; then
        echo -e "${GREEN}[OK] Backend listo en http://localhost:8080${NC}"
        break
    fi
    sleep 2
done

# Start Frontend
echo -e "${YELLOW}[4/4] Iniciando Frontend (Angular)...${NC}"
cd frontend
NG_CLI_ANALYTICS=false npx ng serve --host 0.0.0.0 --port 4200 &
FRONTEND_PID=$!
cd ..
echo -e "${GREEN}[OK] Frontend iniciando (PID: $FRONTEND_PID)${NC}"
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN} Frontend: http://localhost:4200${NC}"
echo -e "${GREEN} Backend:  http://localhost:8080${NC}"
echo -e "${GREEN} H2:       http://localhost:8080/h2-console${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "Presiona Ctrl+C para detener todos los servicios"

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit 0" SIGINT SIGTERM
wait
