#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Carga una base vacía desde un respaldo (pg_dump -Fc)
#
# En el servidor PostgreSQL (atenea para producción), como root, desde una copia
# del repositorio (necesita db/seguridad), DESPUÉS de preparar-servidor.sh:
#     sudo bash infra/postgres/restaurar-base.sh <base> <rol de la aplicación> <respaldo .dump>
#     ej.: sudo bash infra/postgres/restaurar-base.sh gestion_documental gestion_documental /tmp/base_20261120_1800.dump
#
# Restaura con dueño el rol de la aplicación, instala las protecciones de las bitácoras,
# verifica la cadena de huellas y vuelve a dar lectura al rol del respaldo.
# Flyway no puede crear la base desde cero (no hay V1): por eso se parte de un respaldo.
#
# Se niega si la base ya tiene tablas: nunca pisa datos.
# =====================================================================
set -euo pipefail

BASE="${1:-}"; ROL="${2:-}"; DUMP="${3:-}"
[[ "$BASE" =~ ^[a-z_][a-z0-9_]*$ && "$ROL" =~ ^[a-z_][a-z0-9_]*$ && -f "$DUMP" ]] \
    || { echo "Uso: sudo bash $0 <base> <rol de la aplicación> <respaldo .dump>" >&2; exit 1; }
[ "$(id -u)" = "0" ] || { echo "Ejecútelo como root." >&2; exit 1; }
REPO="$(cd "$(dirname "$(readlink -f "$0")")/../.." && pwd)"
for s in proteger_bitacoras.sh reforzar_bitacoras.sh; do
    [ -f "$REPO/db/seguridad/$s" ] || { echo "Falta $REPO/db/seguridad/$s: copie la carpeta db junto con infra." >&2; exit 1; }
done
pg() { (cd / && sudo -u postgres psql -X -v ON_ERROR_STOP=1 -q "$@"); }

[ "$(pg -tA -d postgres -c "SELECT count(*) FROM pg_database WHERE datname = '$BASE'")" = "1" ] \
    || { echo "No existe la base $BASE: ejecute antes preparar-servidor.sh." >&2; exit 1; }
TABLAS="$(pg -tA -d "$BASE" -c "SELECT count(*) FROM pg_tables WHERE schemaname = 'public'")"
[ "$TABLAS" = "0" ] || { echo "La base $BASE ya tiene $TABLAS tablas: no se restaura encima." >&2; exit 1; }

# Una carpeta que postgres pueda leer (el respaldo y los scripts suelen estar en /root o /tmp de otro usuario)
TRABAJO="$(mktemp -d)"
trap 'rm -rf "$TRABAJO"' EXIT
cp "$DUMP" "$TRABAJO/carga.dump"
cp "$REPO"/db/seguridad/{proteger_bitacoras.sh,reforzar_bitacoras.sh} "$TRABAJO/"
chown -R postgres:postgres "$TRABAJO"
chmod 700 "$TRABAJO"
como_postgres() { (cd "$TRABAJO" && sudo -u postgres "$@"); }

echo "== Restaurando $(basename "$DUMP") en $BASE, con dueño $ROL"
# Se saltean la creación de pg_trgm (la hizo preparar-servidor.sh como postgres) y los comentarios
# de las extensiones: el rol de la aplicación no es su dueño, y con --exit-on-error cortarían la carga
como_postgres sh -c "pg_restore -l carga.dump | grep -v -E 'COMMENT - EXTENSION|EXTENSION - pg_trgm' > carga.lista"
como_postgres pg_restore --exit-on-error --no-owner --no-privileges --role="$ROL" \
    -L carga.lista -d "$BASE" carga.dump

echo "== Protecciones de las bitácoras"
como_postgres bash proteger_bitacoras.sh "$BASE" "$ROL"
como_postgres bash reforzar_bitacoras.sh "$BASE"
como_postgres bash reforzar_bitacoras.sh "$BASE" --verificar

echo "== Lectura para el respaldo (incluye las tablas que pasaron al rol de las bitácoras)"
pg -d "$BASE" -v resp="${ROL}_respaldo" <<'SQL'
SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'resp') AS hay_respaldo \gset
\if :hay_respaldo
    GRANT SELECT ON ALL TABLES IN SCHEMA public TO :"resp";
    GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO :"resp";
\else
    \echo 'AVISO: no existe el rol del respaldo; ejecute preparar-servidor.sh.'
\endif
SQL

echo "== Versión de la base"
pg -tA -d "$BASE" -c "SELECT 'Migración ' || max(version::int) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$'"
echo "Listo. En el servidor Docker:  bash infra/rocky/desplegar.sh <dev|prod>"
