#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Prepara un servidor PostgreSQL compartido para una base
#
# En atenea (producción) o ateneadev (desarrollo), como root:
#     sudo bash preparar-servidor.sh <base> <rol de la aplicación> <IP del servidor Docker> [--clave]
#     ej.: sudo bash preparar-servidor.sh gestion_documental gestion_documental 192.168.129.82
#          sudo bash preparar-servidor.sh gestion_documental_pruebas gestion_documental_pruebas 192.168.129.83
#
# Hace, sin tocar las demás bases del servidor:
#   1. SSL: certificado propio del servidor (si no tiene) y ssl = on, con reload (sin cortar).
#   2. Roles: el de la aplicación y <rol>_respaldo, de solo lectura, para el respaldo diario.
#      Pide la contraseña de un rol nuevo (o de los dos, con --clave). Nunca se muestra.
#   3. La base, si no existe: UTF8, la intercalación del servidor y pg_trgm.
#   4. pg_hba: a esta base solo entran esos dos roles, solo desde esa IP y solo con SSL.
#      Cualquier otro origen queda rechazado PARA ESTA BASE. Las reglas de las otras bases
#      siguen iguales. Guarda una copia de pg_hba.conf y la repone si la nueva tiene errores.
#
# Se puede repetir: cambia solo lo que falte. Se detiene si hay conexiones a la base desde
# otra IP (por ejemplo, la producción actual en 127.0.0.1), para no cortarlas.
#
# Con ADMINS="192.168.129.20/32 ..." también se permite entrar (con SSL) desde esas direcciones.
# =====================================================================
set -euo pipefail

BASE="${1:-}"; ROL="${2:-}"; IP_APP="${3:-}"; OPCION="${4:-}"
uso() { echo "Uso: sudo bash $0 <base> <rol de la aplicación> <IP del servidor Docker> [--clave]" >&2; exit 1; }
falla() { echo "ERROR: $*" >&2; exit 1; }
[[ "$BASE" =~ ^[a-z_][a-z0-9_]*$ && "$ROL" =~ ^[a-z_][a-z0-9_]*$ ]] || uso
[[ "$IP_APP" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || uso
[ -z "$OPCION" ] || [ "$OPCION" = "--clave" ] || uso
[ "$(id -u)" = "0" ] || falla "ejecútelo como root"
ROL_RESPALDO="${ROL}_respaldo"
ADMINS="${ADMINS:-}"

# psql como postgres, desde / para no depender de los permisos de la carpeta actual
pg() { (cd / && sudo -u postgres psql -X -v ON_ERROR_STOP=1 -q "$@"); }
valor() { pg -tA -d postgres -c "$1"; }
# Ruta del certificado: ssl_cert_file puede ser relativo a la carpeta de datos
ruta_cert() { local c; c="$(valor "SHOW ssl_cert_file")"; [[ "$c" == /* ]] && echo "$c" || echo "$DATA/$c"; }

DATA="$(valor 'SHOW data_directory')"
HBA="$(valor 'SHOW hba_file')"
echo "Servidor $(hostname -f): $(valor 'SHOW server_version'), datos en $DATA"

# ---- Antes de cambiar nada: conexiones que pg_hba dejaría afuera
PERMITIDAS=" $IP_APP "
for a in $ADMINS; do PERMITIDAS+="${a%/32} "; done
AJENAS=""
while read -r ip; do
    [ -n "$ip" ] && [[ "$PERMITIDAS" != *" $ip "* ]] && AJENAS+=" $ip"
done < <(valor "SELECT DISTINCT host(client_addr) FROM pg_stat_activity WHERE datname = '$BASE' AND client_addr IS NOT NULL")
if [ -n "$AJENAS" ]; then
    falla "hay conexiones abiertas a $BASE desde:$AJENAS. Con las reglas nuevas quedarían rechazadas.
       Si es la producción actual, ejecute este script recién después del corte."
fi

echo
echo "== 1. SSL"
if [ "$(valor 'SHOW ssl')" != "on" ]; then
    if [ ! -f "$DATA/server.crt" ]; then
        SAN="DNS:$(hostname -f),DNS:$(hostname -s)"
        for ip in $(hostname -I); do [[ "$ip" == *.* ]] && SAN+=",IP:$ip"; done
        sudo -u postgres openssl req -x509 -newkey rsa:3072 -nodes -days 1825 \
            -subj "/CN=$(hostname -f)" -addext "subjectAltName=$SAN" \
            -keyout "$DATA/server.key" -out "$DATA/server.crt" 2> /dev/null
        chmod 600 "$DATA/server.key"
        echo "Certificado nuevo, válido 5 años, para $SAN"
    fi
    pg -d postgres -c "ALTER SYSTEM SET ssl = on" -c "SELECT pg_reload_conf()" > /dev/null
    sleep 2
    if [ "$(valor 'SHOW ssl')" != "on" ]; then
        pg -d postgres -c "ALTER SYSTEM RESET ssl" -c "SELECT pg_reload_conf()" > /dev/null
        falla "PostgreSQL no aceptó el certificado; se dejó ssl como estaba. Vea el registro en $DATA/log"
    fi
    echo "ssl = on (las otras bases no están obligadas a usarlo)."
else
    echo "Ya estaba activo."
fi
openssl x509 -in "$(ruta_cert)" -noout -subject -enddate -ext subjectAltName 2> /dev/null || true

echo
echo "== 2. Roles"
for r in "$ROL" "$ROL_RESPALDO"; do
    if [ "$(valor "SELECT count(*) FROM pg_roles WHERE rolname = '$r'")" = "0" ]; then
        pg -d postgres -v r="$r" <<'SQL'
CREATE ROLE :"r" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
SQL
        NUEVO=1
        echo "Rol $r creado."
    else
        NUEVO=0
        echo "Rol $r: ya existía."
    fi
    if [ "$NUEVO" = "1" ] || [ "$OPCION" = "--clave" ]; then
        if [ "$r" = "$ROL" ]; then
            echo "Contraseña de $r: la de /etc/gestion-documental/secretos/db/DB_PASSWORD del servidor Docker."
        else
            echo "Contraseña de $r: el último campo de /etc/gestion-documental/secretos/respaldo.pgpass del servidor Docker."
        fi
        # \password la cifra en este equipo antes de enviarla: no queda en el registro ni en el historial
        pg -d postgres -c "\\password $r"
    fi
done
pg -d postgres -v r="$ROL_RESPALDO" <<'SQL'
-- El respaldo nunca escribe, aunque alguien use su contraseña para otra cosa
ALTER ROLE :"r" SET default_transaction_read_only = on;
SQL

echo
echo "== 3. Base"
if [ "$(valor "SELECT count(*) FROM pg_database WHERE datname = '$BASE'")" = "0" ]; then
    pg -d postgres -v base="$BASE" -v rol="$ROL" <<'SQL'
CREATE DATABASE :"base" OWNER :"rol" ENCODING 'UTF8' TEMPLATE template0;
SQL
    echo "Base $BASE creada, vacía. Cárguela con restaurar-base.sh."
else
    echo "Base $BASE: ya existía."
fi
pg -d "$BASE" -v base="$BASE" -v rol="$ROL" -v resp="$ROL_RESPALDO" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- Solo sus roles se conectan; el resto de los usuarios del servidor, no
REVOKE CONNECT ON DATABASE :"base" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"base" TO :"rol", :"resp";
-- Lectura para el respaldo: lo que existe y lo que creen las migraciones futuras
GRANT USAGE ON SCHEMA public TO :"resp";
GRANT SELECT ON ALL TABLES IN SCHEMA public TO :"resp";
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO :"resp";
ALTER DEFAULT PRIVILEGES FOR ROLE :"rol" IN SCHEMA public GRANT SELECT ON TABLES TO :"resp";
ALTER DEFAULT PRIVILEGES FOR ROLE :"rol" IN SCHEMA public GRANT SELECT ON SEQUENCES TO :"resp";
SQL
echo "pg_trgm, conexión y lectura para $ROL_RESPALDO: listos."

echo
echo "== 4. pg_hba: $HBA"
INICIO="# >>> gestion-documental: $BASE"
FIN="# <<< gestion-documental: $BASE"
BLOQUE="$INICIO (preparar-servidor.sh, $(date +%F))
# A $BASE entran solo la aplicación y el respaldo, desde el servidor Docker y con SSL
hostssl $BASE $ROL,$ROL_RESPALDO $IP_APP/32 scram-sha-256"
for a in $ADMINS; do
    [[ "$a" == */* ]] || a="$a/32"
    BLOQUE+="
hostssl $BASE all $a scram-sha-256"
done
BLOQUE+="
# Cualquier otro origen, o sin SSL, queda rechazado para esta base (las demás no cambian)
host    $BASE all 0.0.0.0/0 reject
host    $BASE all ::/0      reject
$FIN"

COPIA="$HBA.antes-$(date +%Y%m%d_%H%M%S)"
cp -p "$HBA" "$COPIA"
NUEVO_HBA="$(mktemp)"
# Quita el bloque anterior de esta base (si lo hay) y pone el nuevo antes de la primera regla:
# pg_hba usa la primera línea que coincide, así que tiene que ir arriba de las generales
BLOQUE="$BLOQUE" INICIO="$INICIO" FIN="$FIN" awk '
    # Exactos: el bloque de "gestion_documental" no tiene que tocar el de "gestion_documental_pruebas"
    $0 == ENVIRON["INICIO"] || index($0, ENVIRON["INICIO"] " (") == 1 { saltar = 1; next }
    saltar && $0 == ENVIRON["FIN"] { saltar = 0; next }
    saltar { next }
    # Antes de la primera regla o del bloque de otra base, nunca dentro de él
    !puesto && ($0 !~ /^[[:space:]]*(#|$)/ || index($0, "# >>> gestion-documental: ") == 1) { print ENVIRON["BLOQUE"]; puesto = 1 }
    { print }
    END { if (!puesto) print ENVIRON["BLOQUE"] }
' "$HBA" > "$NUEVO_HBA"
# cat > conserva dueño, permisos y etiqueta de SELinux del archivo original
cat "$NUEVO_HBA" > "$HBA"
rm -f "$NUEVO_HBA"

ERRORES="$(valor "SELECT count(*) FROM pg_hba_file_rules WHERE error IS NOT NULL")"
if [ "$ERRORES" != "0" ]; then
    cat "$COPIA" > "$HBA"
    valor "SELECT line_number, error FROM pg_hba_file_rules WHERE error IS NOT NULL" >&2 || true
    falla "pg_hba.conf nuevo con errores: se repuso el anterior y no se recargó nada"
fi
pg -d postgres -c "SELECT pg_reload_conf()" > /dev/null
echo "Copia del anterior: $COPIA"
pg -d postgres -P pager=off -c "SELECT line_number AS linea, type, database, user_name, address, auth_method FROM pg_hba_file_rules WHERE '$BASE' = ANY(database) ORDER BY line_number"

echo
echo "== Cortafuegos (solo se muestra; no se cambia)"
firewall-cmd --list-rich-rules 2> /dev/null | grep -E 'port="5432"' || echo "No hay reglas para 5432: el servidor Docker ($IP_APP) no llegará a la base."

CERT="$(ruta_cert)"
cat <<FIN_TEXTO

Listo. Falta en el servidor Docker ($IP_APP) el certificado público de esta base, para que la
aplicación y el respaldo comprueben que hablan con este servidor (sslmode=verify-full):
    scp $CERT <usuario>@$IP_APP:/tmp/servidor-pg.crt
    (allá)  sudo install -m 644 /tmp/servidor-pg.crt /etc/gestion-documental/pg/servidor.crt
FIN_TEXTO
