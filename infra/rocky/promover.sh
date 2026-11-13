#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Llevar a producción la imagen probada en desarrollo
#
#     bash infra/rocky/promover.sh <etiqueta> <usuario@servidor-prod> [puerto SSH]
#     ej.: bash infra/rocky/promover.sh gestion-documental:503d37d admin@caliope.asuncion.gov.py 2222
#
# Se ejecuta en el servidor de desarrollo (caliopedev). Copia la imagen tal cual (no se
# vuelve a compilar en producción). Después, en producción: poner la etiqueta en
# IMAGEN_APP de /opt/gestion-documental/.env y ejecutar  sudo bash infra/rocky/desplegar.sh prod
# =====================================================================
set -euo pipefail

IMAGEN="${1:-}"; DESTINO="${2:-}"; PUERTO="${3:-22}"
[ -n "$IMAGEN" ] && [ -n "$DESTINO" ] || { echo "Uso: bash $0 <etiqueta> <usuario@servidor-prod> [puerto SSH]" >&2; exit 1; }
ARCHIVO="/tmp/imagen-gestion-documental-$$.tar.gz"
# Se ejecuta como el usuario administrador (sin sudo), para usar sus llaves SSH
DOCKER=(docker); [ "$(id -u)" = "0" ] || DOCKER=(sudo docker)
sudo -v 2> /dev/null || true
"${DOCKER[@]}" image inspect "$IMAGEN" > /dev/null || { echo "No existe la imagen $IMAGEN en este servidor." >&2; exit 1; }

# Primero el archivo y después la carga: sudo necesita la terminal para pedir la contraseña
"${DOCKER[@]}" save "$IMAGEN" | gzip | ssh -p "$PUERTO" "$DESTINO" "umask 077 && cat > '$ARCHIVO'"
ssh -t -p "$PUERTO" "$DESTINO" "sudo docker load -i '$ARCHIVO'; rm -f '$ARCHIVO'; sudo docker image inspect --format '{{.Id}}' '$IMAGEN'"
echo "Imagen copiada. Su Id debe coincidir con el de aquí: $("${DOCKER[@]}" image inspect --format '{{.Id}}' "$IMAGEN")"
