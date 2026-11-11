#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Llevar a producción la imagen probada en desarrollo
#
#     bash infra/rocky/promover.sh <etiqueta> <root@servidor-prod> <puerto SSH>
#     ej.: bash infra/rocky/promover.sh gestion-documental:503d37d root@10.0.0.20 2222
#
# Se ejecuta en el servidor de desarrollo. Copia la imagen tal cual (no se vuelve a
# compilar en producción). Después, en producción: poner la etiqueta en IMAGEN_APP
# de /opt/gestion-documental/.env y ejecutar  bash infra/rocky/desplegar.sh prod
# =====================================================================
set -euo pipefail

IMAGEN="${1:-}"; DESTINO="${2:-}"; PUERTO="${3:-22}"
[ -n "$IMAGEN" ] && [ -n "$DESTINO" ] || { echo "Uso: bash $0 <etiqueta> <root@servidor-prod> [puerto SSH]" >&2; exit 1; }
docker image inspect "$IMAGEN" > /dev/null || { echo "No existe la imagen $IMAGEN en este servidor." >&2; exit 1; }

docker save "$IMAGEN" | gzip | ssh -p "$PUERTO" "$DESTINO" 'gunzip | docker load'
ssh -p "$PUERTO" "$DESTINO" "docker image inspect --format '{{.Id}}' '$IMAGEN'"
echo "Imagen copiada. Su Id debe coincidir con el de aquí: $(docker image inspect --format '{{.Id}}' "$IMAGEN")"
