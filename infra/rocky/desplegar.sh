#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Desplegar o actualizar con Docker
#
#     bash infra/rocky/desplegar.sh dev    # construye la imagen desde el código de /opt/gestion-documental
#     bash infra/rocky/desplegar.sh prod   # usa IMAGEN_APP de .env, traída con promover.sh
#
# Se ejecuta como root, desde /opt/gestion-documental (un clon del repositorio).
# En producción hace un respaldo antes y se detiene si falla: una migración nueva
# no tiene vuelta atrás sin ese respaldo.
# =====================================================================
set -euo pipefail

AMBIENTE="${1:-}"
[[ "$AMBIENTE" =~ ^(dev|prod)$ ]] || { echo "Uso: bash $0 <dev|prod>" >&2; exit 1; }
[ "$(id -u)" = "0" ] || { echo "Ejecútelo como root." >&2; exit 1; }
cd "$(dirname "$(readlink -f "$0")")/../.."

[ -f .env ] || { install -m 640 .env.ejemplo .env; echo "Se creó .env a partir de .env.ejemplo: revíselo y repita." >&2; exit 1; }
COMPOSE=(docker compose -f docker-compose.yml -f "docker-compose.$AMBIENTE.yml")

if [ "$AMBIENTE" = "dev" ]; then
    # La etiqueta es el commit: la misma imagen se puede llevar después a producción
    export IMAGEN_APP="gestion-documental:$(git rev-parse --short HEAD)"
    echo "== Construyendo $IMAGEN_APP"
    "${COMPOSE[@]}" build app
else
    IMAGEN_APP="$(sed -n 's/^IMAGEN_APP=//p' .env)"
    docker image inspect "$IMAGEN_APP" > /dev/null 2>&1 \
        || { echo "No está la imagen $IMAGEN_APP. Tráigala desde desarrollo con promover.sh." >&2; exit 1; }
    if docker ps --format '{{.Names}}' | grep -qx gd-db; then
        echo "== Respaldo previo"
        bash despliegue/respaldar.sh || { echo "El respaldo falló: no se actualiza." >&2; exit 1; }
    fi
fi

# La aplicación no arranca contra una base vacía: Flyway no puede crearla (no hay V1)
"${COMPOSE[@]}" up -d --wait --wait-timeout 180 db
BASE="$(sed -n 's/^BASE=//p' .env)"; BASE="${BASE:-gestion_documental}"
if [ "$(docker exec -u postgres gd-db psql -X -tA -d "$BASE" -c "SELECT to_regclass('flyway_schema_history') IS NOT NULL")" != "t" ]; then
    echo "La base $BASE está vacía. Cárguela primero:  bash infra/rocky/cargar-base.sh <respaldo .dump>" >&2
    exit 1
fi

ANTERIOR="$(docker inspect --format '{{.Config.Image}}' gd-app 2> /dev/null || echo ninguna)"
echo "== Levantando (anterior: $ANTERIOR)"
"${COMPOSE[@]}" up -d --wait --wait-timeout 300

echo "== Estado"
"${COMPOSE[@]}" ps
journalctl CONTAINER_NAME=gd-app --since "10 min ago" --no-pager -o cat \
    | grep -E "Migrating|Successfully applied|Started Gestion|ERROR" || true

echo "$(date -Is) $AMBIENTE $ANTERIOR -> $IMAGEN_APP" >> /var/lib/gestion-documental/despliegues.log
echo "Desplegado $IMAGEN_APP en $AMBIENTE."
