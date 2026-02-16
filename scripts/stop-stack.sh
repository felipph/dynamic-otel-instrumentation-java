#!/bin/bash
# ============================================================================
# Script para parar a stack completa
# ============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTRUMENTATION_DIR="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "  Parando Dynamic OTel Stack"
echo "=========================================="
echo ""

cd "$INSTRUMENTATION_DIR/docker"

echo "Parando containers..."
docker compose down

echo ""
echo "Containers parados."

# Perguntar se deseja remover volumes
read -p "Deseja remover os volumes (dados do banco)? [y/N] " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Removendo volumes..."
    docker compose down -v
    echo "Volumes removidos."
fi

echo ""
echo "=========================================="
echo "  Stack parada com sucesso!"
echo "=========================================="
