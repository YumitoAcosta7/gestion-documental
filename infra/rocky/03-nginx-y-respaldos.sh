#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Paso 3: nginx en el servidor y respaldo diario
#
#     bash 03-nginx-y-respaldos.sh <nombre o IP con que entran los usuarios> [usuario@equipo:/carpeta de respaldo]
#
# nginx queda fuera de Docker: termina el HTTPS, reemplaza X-Forwarded-For (la IP de
# las bitácoras no se puede falsificar) y reenvía a la aplicación en 127.0.0.1:8080.
# El respaldo corre en el servidor con un temporizador de systemd y llega a la base
# del contenedor con docker exec. Se puede repetir.
# =====================================================================
set -euo pipefail

NOMBRE="${1:-}"; DESTINO="${2:-}"
[[ "$NOMBRE" =~ ^[A-Za-z0-9.-]+$ ]] || { echo "Uso: bash $0 <nombre o IP> [usuario@equipo:/carpeta]" >&2; exit 1; }
[ "$(id -u)" = "0" ] || { echo "Ejecútelo como root." >&2; exit 1; }
REPO="$(cd "$(dirname "$(readlink -f "$0")")/../.." && pwd)"

echo "== Certificado (interno si todavía no está el institucional)"
if [ ! -f /etc/gestion-documental/tls/certificado.crt ]; then
    if [[ "$NOMBRE" =~ ^[0-9.]+$ ]]; then SAN="IP:$NOMBRE"; else SAN="DNS:$NOMBRE"; fi
    openssl req -x509 -newkey rsa:2048 -nodes -days 730 -subj "/CN=$NOMBRE" -addext "subjectAltName=$SAN" \
        -keyout /etc/gestion-documental/tls/certificado.key -out /etc/gestion-documental/tls/certificado.crt 2> /dev/null
    chmod 600 /etc/gestion-documental/tls/certificado.key
fi

echo "== nginx"
sed "s/NOMBRE_SERVIDOR/$NOMBRE/g" "$REPO/despliegue/nginx-gestion-documental.conf" > /etc/nginx/conf.d/gestion-documental.conf
# Sin la versión en las respuestas
grep -q '^\s*server_tokens off' /etc/nginx/nginx.conf || sed -i 's/^\(\s*\)http {/\1http {\n\1    server_tokens off;/' /etc/nginx/nginx.conf
nginx -t
systemctl enable --now nginx
systemctl reload nginx

echo "== Respaldo diario (22:00 de Asunción)"
if [ ! -f /etc/gestion-documental/respaldo ]; then
    {
        echo "# Lo lee despliegue/respaldar.sh"
        echo 'COMO_POSTGRES="docker exec -i -u postgres gd-db"'
        [ -n "$DESTINO" ] && echo "RESPALDO_DESTINO=$DESTINO"
    } > /etc/gestion-documental/respaldo
    chmod 640 /etc/gestion-documental/respaldo
fi
install -m 644 "$REPO/infra/rocky/archivos/gestion-documental-respaldo.service" \
               "$REPO/infra/rocky/archivos/gestion-documental-respaldo.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now gestion-documental-respaldo.timer
systemctl list-timers gestion-documental-respaldo.timer --no-pager

[ -z "$DESTINO" ] && echo "AVISO: sin destino de respaldo, las copias quedan solo en este servidor." >&2
echo "Listo el paso 3. Entrada: https://$NOMBRE"
