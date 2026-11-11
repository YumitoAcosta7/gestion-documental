#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Cargar la base en el contenedor gd-db desde un respaldo
#
#     bash infra/rocky/cargar-base.sh <respaldo .dump>
#
# Para el primer arranque de un servidor nuevo: Flyway no puede crear la base desde
# cero (no hay V1). Restaura el esquema y los datos de un respaldo de pg_dump -Fc
# (el de producción, o el de db/piloto/crear_base_limpia.sh) en la base vacía que
# creó infra/postgres/initdb, con dueño el rol de la aplicación. Después instala las
# protecciones de las bitácoras y las verifica.
#
# Se niega si la base ya tiene tablas: nunca pisa datos.
# =====================================================================
set -euo pipefail

DUMP="${1:-}"
[ -f "$DUMP" ] || { echo "Uso: bash $0 <respaldo .dump>" >&2; exit 1; }
[ "$(id -u)" = "0" ] || { echo "Ejecútelo como root." >&2; exit 1; }
cd "$(dirname "$(readlink -f "$0")")/../.."
BASE="$(sed -n 's/^BASE=//p' .env)"; BASE="${BASE:-gestion_documental}"
ROL="$(sed -n 's/^ROL_APLICACION=//p' .env)"; ROL="${ROL:-gestion_documental}"
PG=(docker exec -i -u postgres gd-db)

TABLAS="$("${PG[@]}" psql -X -tA -d "$BASE" -c "SELECT count(*) FROM pg_tables WHERE schemaname = 'public'")"
[ "$TABLAS" = "0" ] || { echo "La base $BASE ya tiene $TABLAS tablas: no se restaura encima." >&2; exit 1; }

echo "== Restaurando en $BASE, con dueño $ROL"
# La aplicación detenida mientras tanto: si arrancara, Flyway vería una base a medias
docker stop gd-app > /dev/null 2>&1 || true
# Dentro del contenedor, porque -L necesita leer el respaldo dos veces. Se saltean la creación de
# pg_trgm (la hizo initdb) y los comentarios de las extensiones: el rol de la aplicación no es su
# dueño, y con --exit-on-error esas líneas cortarían la restauración
docker exec -i gd-db sh -c 'cat > /tmp/carga.dump' < "$DUMP"
docker exec -u postgres gd-db sh -c "pg_restore -l /tmp/carga.dump | grep -v -E 'COMMENT - EXTENSION|EXTENSION - pg_trgm' > /tmp/carga.lista"
docker exec -u postgres gd-db pg_restore --exit-on-error --no-owner --no-privileges --role="$ROL" \
    -L /tmp/carga.lista -d "$BASE" /tmp/carga.dump
docker exec gd-db rm -f /tmp/carga.dump /tmp/carga.lista

echo "== Protecciones de las bitácoras"
docker exec -u postgres gd-db bash /opt/scripts-db/seguridad/proteger_bitacoras.sh "$BASE" "$ROL"
docker exec -u postgres gd-db bash /opt/scripts-db/seguridad/reforzar_bitacoras.sh "$BASE"
docker exec -u postgres gd-db bash /opt/scripts-db/seguridad/reforzar_bitacoras.sh "$BASE" --verificar

echo "== Versión de la base"
"${PG[@]}" psql -X -tA -d "$BASE" -c "SELECT max(version::int) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$'"
echo "Listo. Arranque la aplicación con  bash infra/rocky/desplegar.sh <dev|prod>"
