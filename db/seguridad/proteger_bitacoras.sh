#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Bitácoras fuera del alcance de la aplicación (auditoría A5)
#
# Los triggers de la V14 impiden modificar, borrar o vaciar las bitácoras, pero
# el dueño de una tabla puede desactivar sus triggers o borrarla. Hoy el dueño
# es el mismo rol con el que se conecta la aplicación. Este script pasa las dos
# bitácoras a un rol sin inicio de sesión y le deja a la aplicación solo
# SELECT e INSERT.
#
# Se ejecuta UNA vez por base, en el servidor 192.168.129.73, como postgres,
# DESPUÉS de que la aplicación haya aplicado la migración V14 en esa base:
#
#     sudo -u postgres bash proteger_bitacoras.sh gestion_documental gestion_documental
#     sudo -u postgres bash proteger_bitacoras.sh gestion_documental_pruebas gestion_documental_pruebas
#
# Argumentos: <base> <rol de la aplicación>. Se puede repetir sin efectos.
#
# Consecuencia: una migración futura que modifique estas dos tablas tendrá que
# aplicarla un superusuario (el rol de la aplicación ya no es su dueño).
# =====================================================================
set -euo pipefail

if [ $# -ne 2 ]; then
    echo "Uso: sudo -u postgres bash $0 <base> <rol de la aplicación>" >&2
    exit 1
fi
BASE="$1"
ROL_APP="$2"
ROL_BITACORA="gestion_documental_bitacora"

if [ "$(id -un)" != "postgres" ]; then
    echo "Ejecútelo como postgres:  sudo -u postgres bash $0 $BASE $ROL_APP" >&2
    exit 1
fi

# La V14 (triggers contra TRUNCATE) tiene que estar aplicada en esa base
if ! psql -X -tA -d "$BASE" -c "SELECT 1 FROM flyway_schema_history WHERE version = '14' AND success" | grep -q 1; then
    echo "La migración V14 todavía no se aplicó en $BASE: arranque la aplicación sobre esa base primero." >&2
    exit 1
fi

psql -X -v ON_ERROR_STOP=1 -q -d "$BASE" -v rol_app="$ROL_APP" -v rol_bitacora="$ROL_BITACORA" <<'SQL'
SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'rol_bitacora') AS crear_rol \gset
\if :crear_rol
    CREATE ROLE :"rol_bitacora" NOLOGIN;
\endif

ALTER TABLE auditorias_documento OWNER TO :"rol_bitacora";
ALTER TABLE auditoria_sistema    OWNER TO :"rol_bitacora";

-- La aplicación solo lee y agrega (las secuencias pasan con su tabla)
REVOKE ALL ON auditorias_documento, auditoria_sistema FROM :"rol_app";
GRANT SELECT, INSERT ON auditorias_documento, auditoria_sistema TO :"rol_app";
GRANT USAGE, SELECT ON SEQUENCE auditorias_documento_id_seq, auditoria_sistema_id_seq TO :"rol_app";

-- Verificación
SELECT c.relname AS tabla, pg_get_userbyid(c.relowner) AS dueno,
       has_table_privilege(:'rol_app', c.oid, 'INSERT')   AS app_inserta,
       has_table_privilege(:'rol_app', c.oid, 'UPDATE')   AS app_modifica,
       has_table_privilege(:'rol_app', c.oid, 'DELETE')   AS app_borra,
       has_table_privilege(:'rol_app', c.oid, 'TRUNCATE') AS app_vacia
  FROM pg_class c
 WHERE c.relname IN ('auditorias_documento', 'auditoria_sistema')
 ORDER BY 1;
SQL

echo "Listo: en $BASE las bitácoras pertenecen a $ROL_BITACORA y $ROL_APP solo puede leerlas y agregarles filas."
