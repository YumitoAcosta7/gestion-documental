#!/usr/bin/env bash
# =====================================================================
# Primer arranque del contenedor gd-db (solo corre con la carpeta de datos vacía).
#
# Crea el rol de la aplicación, que no es superusuario, y su base vacía con pg_trgm.
# El esquema NO sale de aquí ni de Flyway: las migraciones empiezan en V2 (el esquema
# anterior no está en el repositorio). La base se carga después restaurando un
# respaldo, como explica docs/infra-docker-rocky.md (paso "Cargar la base").
# =====================================================================
set -euo pipefail

CLAVE_APP="$(< /run/secretos/app_password)"
if [ -z "$CLAVE_APP" ]; then
    echo "Falta la contraseña de la aplicación en /run/secretos/app_password." >&2
    exit 1
fi

# Los valores viajan como variables de psql, nunca armados dentro del SQL
psql -X -v ON_ERROR_STOP=1 -q --username postgres --dbname postgres \
     -v rol="$ROL_APLICACION" -v base="$BASE_APLICACION" -v clave="$CLAVE_APP" <<'SQL'
CREATE ROLE :"rol" LOGIN PASSWORD :'clave' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE :"base" OWNER :"rol" ENCODING 'UTF8' TEMPLATE template0;
SQL

psql -X -v ON_ERROR_STOP=1 -q --username postgres --dbname "$BASE_APLICACION" -c "CREATE EXTENSION IF NOT EXISTS pg_trgm"
echo "Rol $ROL_APLICACION y base $BASE_APLICACION creados. Falta cargar el esquema restaurando un respaldo."
