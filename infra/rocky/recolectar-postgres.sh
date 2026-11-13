#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Recolecta la configuración de un servidor PostgreSQL
#
#     sudo bash recolectar-postgres.sh
#
# Para atenea (192.168.129.72) y ateneadev (192.168.129.73), antes de poner allí
# las bases de los dos ambientes. Solo lee: no crea, modifica ni reinicia nada.
# Escribe un archivo de texto en la carpeta actual.
#
# No muestra contraseñas: PostgreSQL no las devuelve, no se leen .pgpass ni
# archivos de entorno, y en las tareas programadas se reemplazan por [OCULTO].
# Igual conviene leer el archivo antes de compartirlo.
# =====================================================================
set -uo pipefail

[ "$(id -u)" = "0" ] || { echo "Ejecútelo con sudo o como root." >&2; exit 1; }
SALIDA="$PWD/pg-info-$(hostname -s)-$(date +%Y%m%d_%H%M).txt"

seccion() { printf '\n\n########## %s\n' "$*"; }
ver() { printf '\n$ %s\n' "$*"; bash -c "$*" 2>&1 || true; }
# Consultas como postgres, desde / para no depender de la carpeta actual
sql() { printf '\n> %s\n' "$1"; (cd / && sudo -u postgres psql -X -P pager=off -d postgres -c "$1") 2>&1 || true; }

# Contraseñas que puedan aparecer en tareas programadas o líneas de comando
ocultar() {
    sed -E \
        -e 's/(PGPASSWORD|PASSWORD|PASSWD|PASS|SECRET|TOKEN)([A-Z_]*)=[^ ;&"]+/\1\2=[OCULTO]/Ig' \
        -e 's/(password|passwd)([ =:]+)[^ ;&"]+/\1\2[OCULTO]/Ig' \
        -e 's#(postgres(ql)?://[^:/ ]+:)[^@ ]+@#\1[OCULTO]@#g'
}

{
    echo "Recolectado el $(date -Is) en $(hostname -f 2>/dev/null || hostname)"

    seccion "SISTEMA"
    ver "cat /etc/rocky-release 2> /dev/null || cat /etc/os-release | head -3"
    ver "uname -r"
    ver "lscpu | grep -E '^(Model name|CPU\(s\)|Virtualization)'"
    # Rocky 10 necesita x86-64-v3 (avx2); Rocky 9, x86-64-v2
    ver "grep -m1 -o -w -E 'avx2|sse4_2|popcnt' /proc/cpuinfo | sort -u | tr '\n' ' '"
    ver "free -h"
    ver "df -hT -x tmpfs -x devtmpfs"
    ver "lsblk -o NAME,SIZE,TYPE,FSTYPE,MOUNTPOINT"
    ver "vgs; lvs"
    ver "timedatectl | grep -E 'Time zone|NTP|synchronized'"
    ver "chronyc -n sources"
    ver "getenforce"
    ver "ip -br addr"
    ver "ip route"
    ver "cat /etc/resolv.conf"
    ver "firewall-cmd --get-active-zones"
    ver "firewall-cmd --list-all"
    ver "ss -ltnp"

    seccion "SERVICIOS"
    ver "systemctl list-units --type=service --state=running --no-pager --plain | grep -E 'postgres|nginx|httpd|gestion|docker|pgbouncer|pgbackrest|barman|cron'"
    ver "systemctl list-timers --all --no-pager"
    ver "rpm -qa | grep -iE '^postgresql|^pgdg|pg_|pgbackrest|barman|^docker' | sort"
    ver "for u in root postgres; do echo \"--- crontab de \$u\"; crontab -l -u \$u 2> /dev/null; done"
    ver "ls -la /etc/cron.d/ && grep -vE '^\s*(#|$)' /etc/cron.d/* 2> /dev/null"

    seccion "POSTGRESQL"
    sql "SELECT version()"
    sql "SELECT name, setting, unit FROM pg_settings WHERE name IN ('data_directory','config_file','hba_file','listen_addresses','port','max_connections','shared_buffers','effective_cache_size','work_mem','maintenance_work_mem','ssl','ssl_cert_file','ssl_min_protocol_version','password_encryption','TimeZone','log_timezone','lc_collate','lc_ctype','server_encoding','wal_level','archive_mode','archive_command','max_wal_senders','fsync','synchronous_commit','full_page_writes','log_destination','logging_collector','log_connections','shared_preload_libraries') ORDER BY name"
    sql "SELECT datname, pg_get_userbyid(datdba) AS duenio, pg_encoding_to_char(encoding) AS codificacion, datcollate, pg_size_pretty(pg_database_size(datname)) AS tamanio FROM pg_database ORDER BY pg_database_size(datname) DESC"
    # Solo nombres y atributos: pg_roles nunca devuelve la contraseña
    sql "SELECT rolname, rolsuper, rolcreaterole, rolcreatedb, rolcanlogin, rolconnlimit, rolvaliduntil FROM pg_roles WHERE rolname NOT LIKE 'pg\_%' ORDER BY rolname"
    sql "SELECT name, default_version, installed_version FROM pg_available_extensions WHERE name IN ('pg_trgm','pgcrypto','pg_stat_statements','unaccent') ORDER BY name"
    sql "SELECT line_number, type, database, user_name, address, netmask, auth_method, error FROM pg_hba_file_rules ORDER BY line_number"
    sql "SELECT datname, usename, client_addr, count(*) FROM pg_stat_activity WHERE backend_type = 'client backend' GROUP BY 1,2,3 ORDER BY 4 DESC"
    sql "SELECT client_addr, state, sync_state FROM pg_stat_replication"
    sql "SELECT pg_is_in_recovery() AS es_replica"
    ver "ls -ld \$(sudo -u postgres psql -X -tAc 'SHOW data_directory' 2> /dev/null) && df -h \$(sudo -u postgres psql -X -tAc 'SHOW data_directory' 2> /dev/null)"
} 2>&1 | ocultar > "$SALIDA"

chmod 600 "$SALIDA"
echo "Listo: $SALIDA ($(wc -l < "$SALIDA") líneas). Revíselo antes de compartirlo."
