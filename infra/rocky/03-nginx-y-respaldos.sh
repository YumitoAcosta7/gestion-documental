#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Paso 3: nginx, conexión del respaldo y respaldo diario
#
#     sudo bash 03-nginx-y-respaldos.sh <nombre público> <servidor PostgreSQL> [usuario@equipo:/carpeta]
#     ej.: sudo bash 03-nginx-y-respaldos.sh mesa.asuncion.gov.py 192.168.129.72 respaldo@192.168.129.71:/srv/respaldos/gestion_documental
#          sudo bash 03-nginx-y-respaldos.sh mesadev.asuncion.gov.py 192.168.129.73 respaldo@192.168.129.71:/srv/respaldos/gestion_documental_pruebas
#
# Desde /opt/gestion-documental, con .env ya completo (BASE y ROL_APLICACION).
# nginx termina el HTTPS que le reenvía el proxy interno y pasa a la aplicación en
# 127.0.0.1:8080. El respaldo corre en este servidor con un temporizador de systemd y
# llega a la base por la red, con SSL y el rol de solo lectura <rol>_respaldo.
# Se puede repetir: no pisa la configuración ni la contraseña del respaldo ya creadas.
# =====================================================================
set -euo pipefail

NOMBRE="${1:-}"; SERVIDOR_PG="${2:-}"; DESTINO="${3:-}"
[[ "$NOMBRE" =~ ^[A-Za-z0-9.-]+$ && "$SERVIDOR_PG" =~ ^[A-Za-z0-9.-]+$ ]] \
    || { echo "Uso: sudo bash $0 <nombre público> <servidor PostgreSQL> [usuario@equipo:/carpeta]" >&2; exit 1; }
[ "$(id -u)" = "0" ] || { echo "Ejecútelo como root." >&2; exit 1; }
REPO="$(cd "$(dirname "$(readlink -f "$0")")/../.." && pwd)"
[ -f "$REPO/.env" ] || { echo "Falta $REPO/.env: cópielo de .env.ejemplo y complételo." >&2; exit 1; }
BASE="$(sed -n 's/^BASE=//p' "$REPO/.env")"; BASE="${BASE:-gestion_documental}"
ROL="$(sed -n 's/^ROL_APLICACION=//p' "$REPO/.env")"; ROL="${ROL:-gestion_documental}"
EQUIPO="$(hostname -f)"
IP="$(hostname -I | awk '{print $1}')"

echo "== Certificado entre el proxy y este servidor (interno; el usuario ve el *.asuncion.gov.py del proxy)"
if [ ! -f /etc/gestion-documental/tls/certificado.crt ]; then
    openssl req -x509 -newkey rsa:3072 -nodes -days 1825 -subj "/CN=$EQUIPO" \
        -addext "subjectAltName=DNS:$EQUIPO,DNS:$NOMBRE,IP:$IP" \
        -keyout /etc/gestion-documental/tls/certificado.key -out /etc/gestion-documental/tls/certificado.crt 2> /dev/null
    chmod 600 /etc/gestion-documental/tls/certificado.key
fi
openssl x509 -in /etc/gestion-documental/tls/certificado.crt -noout -enddate -ext subjectAltName

echo "== nginx"
# Responde por el nombre público (el que conserva el proxy) y por el del equipo (pruebas directas)
sed "s/NOMBRE_SERVIDOR/$NOMBRE $EQUIPO/g" "$REPO/despliegue/nginx-gestion-documental.conf" > /etc/nginx/conf.d/gestion-documental.conf
# Sin la versión en las respuestas
grep -q '^\s*server_tokens off' /etc/nginx/nginx.conf || sed -i 's/^\(\s*\)http {/\1http {\n\1    server_tokens off;/' /etc/nginx/nginx.conf
nginx -t
systemctl enable --now nginx
systemctl reload nginx

echo "== Conexión del respaldo a $BASE en $SERVIDOR_PG (rol ${ROL}_respaldo, solo lectura)"
PGPASS=/etc/gestion-documental/secretos/respaldo.pgpass
if [ ! -f "$PGPASS" ]; then
    # La misma contraseña se carga en el servidor PostgreSQL con preparar-servidor.sh (la pide allá)
    printf '%s:5432:%s:%s:%s\n' "$SERVIDOR_PG" "$BASE" "${ROL}_respaldo" "$(openssl rand -base64 33 | tr -d '\n/+=:')" > "$PGPASS"
    chmod 600 "$PGPASS"
    echo "Contraseña nueva en $PGPASS (último campo). Cárguela en $SERVIDOR_PG al ejecutar preparar-servidor.sh."
fi
if [ ! -f /etc/gestion-documental/respaldo ]; then
    {
        echo "# Lo leen despliegue/respaldar.sh, revisar_pdf.sh e infra/rocky/desplegar.sh"
        echo "# La base está en otro servidor: pg_dump y psql se conectan por la red, con SSL verificado"
        echo 'COMO_POSTGRES="env"'
        echo "BASE=$BASE"
        echo 'export PATH="/usr/pgsql-17/bin:$PATH"'
        echo "export PGHOST=$SERVIDOR_PG PGPORT=5432 PGUSER=${ROL}_respaldo"
        echo "export PGSSLMODE=verify-full PGSSLROOTCERT=/etc/gestion-documental/pg/servidor.crt"
        echo "export PGPASSFILE=$PGPASS PGCONNECT_TIMEOUT=15"
        [ -n "$DESTINO" ] && echo "RESPALDO_DESTINO=$DESTINO"
    } > /etc/gestion-documental/respaldo
    chmod 640 /etc/gestion-documental/respaldo
else
    echo "Ya existía /etc/gestion-documental/respaldo: no se cambia."
fi

echo "== Respaldo diario (22:00)"
install -m 644 "$REPO/infra/rocky/archivos/gestion-documental-respaldo.service" \
               "$REPO/infra/rocky/archivos/gestion-documental-respaldo.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now gestion-documental-respaldo.timer
systemctl list-timers gestion-documental-respaldo.timer --no-pager

[ -z "$DESTINO" ] && echo "AVISO: sin destino de respaldo, las copias quedan solo en este servidor." >&2
[ -s /etc/gestion-documental/pg/servidor.crt ] \
    || echo "FALTA: /etc/gestion-documental/pg/servidor.crt, el certificado de $SERVIDOR_PG (lo indica preparar-servidor.sh)." >&2
cat <<FIN
Listo el paso 3. Certificado que el proxy interno debe aceptar para este servidor:
    /etc/gestion-documental/tls/certificado.crt
FIN
