#!/bin/bash
# ============================================================================
# Script para iniciar a stack completa de testes
# ============================================================================
#
# Este script:
# 1. Verifica pré-requisitos (builds)
# 2. Inicia toda a stack Docker
# 3. Aguarda serviços ficarem prontos
# 4. Mostra URLs de acesso
#
# Uso:
#   cd /home/felipph/DEV/java-otel-instrumentation
#   ./scripts/start-stack.sh
#
# ============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTRUMENTATION_DIR="$(dirname "$SCRIPT_DIR")"
SAMPLE_APP_DIR="$(dirname "$INSTRUMENTATION_DIR")/sample-spring-mvc-app"

echo "=========================================="
echo "  Dynamic OTel Instrumentation - Stack"
echo "=========================================="
echo ""

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Função para verificar se um build existe
check_build() {
    local project=$1
    local artifact=$2

    if [ ! -f "$artifact" ]; then
        echo -e "${RED}✗ Build não encontrado: $artifact${NC}"
        echo -e "${YELLOW}  Execute: cd $project && ./scripts/build.sh${NC}"
        return 1
    fi
    echo -e "${GREEN}✓ Build encontrado: $artifact${NC}"
    return 0
}

# Função para aguardar serviço
wait_for_service() {
    local name=$1
    local url=$2
    local max_attempts=${3:-30}

    echo -n "Aguardando $name..."
    for i in $(seq 1 $max_attempts); do
        if curl -s "$url" > /dev/null 2>&1; then
            echo -e " ${GREEN}✓${NC}"
            return 0
        fi
        echo -n "."
        sleep 2
    done
    echo -e " ${RED}✗${NC}"
    return 1
}

# ============================================================================
# VERIFICAR PRÉ-REQUISITOS
# ============================================================================

echo -e "${BLUE}Verificando builds...${NC}"
echo ""

ERRORS=0

# Verificar build do agente
if ! check_build "java-otel-instrumentation" "$INSTRUMENTATION_DIR/target/dynamic-instrumentation-agent-1.1.0.jar"; then
    ERRORS=$((ERRORS + 1))
fi

# Verificar build da sample app
if ! check_build "sample-spring-mvc-app" "$SAMPLE_APP_DIR/ear/target/sample-app.ear"; then
    ERRORS=$((ERRORS + 1))
fi

if [ $ERRORS -gt 0 ]; then
    echo ""
    echo -e "${RED}Erro: $ERRORS build(s) não encontrado(s).${NC}"
    echo ""
    echo "Para fazer o build:"
    echo "  # Agente de instrumentação:"
    echo "  cd $INSTRUMENTATION_DIR && ./scripts/build.sh"
    echo ""
    echo "  # Sample app:"
    echo "  cd $SAMPLE_APP_DIR && ./scripts/build.sh"
    exit 1
fi

echo ""
echo -e "${GREEN}Todos os builds estão prontos!${NC}"
echo ""

# ============================================================================
# INICIAR STACK DOCKER
# ============================================================================

echo -e "${BLUE}Iniciando stack Docker...${NC}"
echo ""

cd "$INSTRUMENTATION_DIR/docker"

# Parar containers existentes
echo "Parando containers existentes..."
docker compose down 2>/dev/null || true

# Iniciar com perfil Jaeger (padrão)
echo ""
echo "Iniciando serviços (modo: Jaeger)..."
docker compose up -d

echo ""
echo -e "${GREEN}Stack iniciada!${NC}"
echo ""

# ============================================================================
# AGUARDAR SERVIÇOS
# ============================================================================

echo -e "${BLUE}Aguardando serviços ficarem prontos...${NC}"
echo ""

# Aguardar PostgreSQL
wait_for_service "PostgreSQL" "http://localhost:5432" 15 || true

# Aguardar OTel Collector
wait_for_service "OTel Collector" "http://localhost:4318" 10 || true

# Aguardar Jaeger
wait_for_service "Jaeger" "http://localhost:16686" 10 || true

# Aguardar JBoss (demora mais)
echo ""
echo "Aguardando JBoss iniciar (pode demorar até 2 minutos)..."
wait_for_service "JBoss" "http://localhost:8080/sample/api/products" 60 || true

echo ""

# ============================================================================
# MOSTRAR INFORMAÇÕES
# ============================================================================

echo ""
echo "=========================================="
echo -e "${GREEN}  Stack pronta!${NC}"
echo "=========================================="
echo ""
echo -e "${BLUE}URLs de Acesso:${NC}"
echo ""
echo "  Aplicação:"
echo "    http://localhost:8080/sample"
echo "    http://localhost:8080/sample/api/products"
echo "    http://localhost:8080/sample/api/customers"
echo "    http://localhost:8080/sample/api/orders"
echo ""
echo "  Observabilidade:"
echo "    Jaeger UI:  http://localhost:16686"
echo "    OTLP gRPC:  localhost:4317"
echo "    OTLP HTTP:  localhost:4318"
echo ""
echo "  Banco de Dados:"
echo "    PostgreSQL: localhost:5432"
echo "    Database:   sampledb"
echo "    User:       sampleuser"
echo "    Password:   samplepass"
echo ""
echo "  JMX (para reload de config):"
echo "    Port: 9990"
echo ""
echo "=========================================="
echo ""
echo -e "${BLUE}Comandos úteis:${NC}"
echo ""
echo "  Ver logs do JBoss:"
echo "    docker logs -f otel-jboss"
echo ""
echo "  Ver todos os logs:"
echo "    cd $INSTRUMENTATION_DIR/docker && docker compose logs -f"
echo ""
echo "  Testar API:"
echo "    curl http://localhost:8080/sample/api/products"
echo ""
echo "  Recarregar configuração do agente:"
echo "    docker exec otel-jboss jcmd 1 JMX.invoke com.otel.dynamic:type=ConfigManager reloadConfiguration"
echo ""
echo "  Parar stack:"
echo "    cd $INSTRUMENTATION_DIR && ./scripts/stop-stack.sh"
echo ""
echo "=========================================="
echo ""

# Mostrar status dos containers
echo -e "${BLUE}Status dos containers:${NC}"
echo ""
docker compose ps
echo ""

# ============================================================================
# OPÇÃO PARA SIGNOZ
# ============================================================================

echo "=========================================="
echo ""
echo -e "${YELLOW}Dica: Para usar SigNoz em vez de Jaeger:${NC}"
echo ""
echo "  docker compose --profile signoz up -d"
echo ""
echo "  SigNoz UI: http://localhost:3301"
echo ""
echo "=========================================="
