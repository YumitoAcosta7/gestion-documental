#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Crea una base nueva, sin datos de prueba (docs/plan-base-limpia.md)
#
# Copia de producción la ESTRUCTURA y solo los catálogos. No copia expedientes,
# actuaciones, adjuntos, usuarios, asignaciones ni bitácoras. Solo LEE producción.
# Si la base de destino ya existe, se detiene sin tocar nada.
#
# Se ejecuta en el servidor 192.168.129.73, como postgres, con proteger_bitacoras.sh
# y reforzar_bitacoras.sh en la misma carpeta:
#
#     sudo -u postgres bash crear_base_limpia.sh <base nueva> <rol de la aplicación>
#
# Ensayo:  gestion_documental_ensayo y gestion_documental_pruebas
# Corte:   gestion_documental_nueva y gestion_documental
# =====================================================================
set -euo pipefail

if [ $# -ne 2 ]; then
    echo "Uso: sudo -u postgres bash $0 <base nueva> <rol de la aplicación>" >&2
    exit 1
fi
ORIGEN="gestion_documental"
DESTINO="$1"
ROL_APP="$2"
DIR="$(cd "$(dirname "$0")" && pwd)"
CATALOGOS=(niveles_jerarquicos dependencias tipos_documentos reglas_derivacion flyway_schema_history organigrama_version feriados)
VACIAS_PREVISTAS=" usuarios asignaciones documentos actuaciones_documento adjuntos_documento legajos_salida series_cuti auditorias_documento auditoria_sistema solicitudes_reapertura solicitudes_contrasena solicitudes_traslado_custodia biblioratos "

if [ "$(id -un)" != "postgres" ]; then
    echo "Ejecútelo como postgres:  sudo -u postgres bash $0 $*" >&2
    exit 1
fi
if ! [[ "$DESTINO" =~ ^[a-z_][a-z0-9_]*$ && "$ROL_APP" =~ ^[a-z_][a-z0-9_]*$ ]]; then
    echo "La base y el rol solo admiten minúsculas, números y guion bajo." >&2
    exit 1
fi
if [ "$DESTINO" = "$ORIGEN" ]; then
    echo "La base nueva no puede llamarse $ORIGEN: se crea con otro nombre y se renombra en el corte." >&2
    exit 1
fi
for s in proteger_bitacoras.sh reforzar_bitacoras.sh; do
    if [ ! -f "$DIR/$s" ]; then
        echo "Falta $s en $DIR." >&2
        exit 1
    fi
done

psql_ok() { psql -X -v ON_ERROR_STOP=1 -q "$@"; }

if psql_ok -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$DESTINO'" | grep -q 1; then
    echo "La base $DESTINO ya existe: no se modificó nada." >&2
    exit 1
fi
if ! psql_ok -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$ROL_APP'" | grep -q 1; then
    echo "No existe el rol $ROL_APP." >&2
    exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "== 1. Creando $DESTINO, a nombre de $ROL_APP =="
psql_ok -d postgres -c "CREATE DATABASE $DESTINO OWNER $ROL_APP ENCODING 'UTF8' TEMPLATE template0"

echo "== 2. Copiando la estructura de $ORIGEN =="
pg_dump -d "$ORIGEN" --schema-only --no-owner --no-privileges -f "$TMP/esquema.sql"
psql_ok -d "$DESTINO" -f "$TMP/esquema.sql"

echo "== 3. Copiando los catálogos: ${CATALOGOS[*]} =="
ARGS=()
for t in "${CATALOGOS[@]}"; do ARGS+=(-t "public.$t"); done
pg_dump -d "$ORIGEN" --data-only --no-owner --no-privileges --disable-triggers "${ARGS[@]}" -f "$TMP/catalogos.sql"
psql_ok -d "$DESTINO" -f "$TMP/catalogos.sql"

# Los usuarios que cargaron feriados no existen en la base nueva. Las secuencias siguen a los datos copiados
psql_ok -d "$DESTINO" <<'SQL'
UPDATE feriados SET creado_por_id = NULL;
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT c.table_name, c.column_name, pg_get_serial_sequence('public.' || quote_ident(c.table_name), c.column_name) AS secuencia
               FROM information_schema.columns c
              WHERE c.table_schema = 'public' AND pg_get_serial_sequence('public.' || quote_ident(c.table_name), c.column_name) IS NOT NULL LOOP
        EXECUTE format('SELECT setval(%L, COALESCE((SELECT MAX(%I) FROM public.%I), 1), (SELECT MAX(%I) IS NOT NULL FROM public.%I))',
                       r.secuencia, r.column_name, r.table_name, r.column_name, r.table_name);
    END LOOP;
END
$$;
SQL

echo "== 4. Dejando los objetos a nombre de $ROL_APP =="
psql_ok -d "$DESTINO" <<SQL
ALTER SCHEMA public OWNER TO $ROL_APP;
DO \$\$
DECLARE r record;
BEGIN
    FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm')
                AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype = 'e') LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO $ROL_APP', r.relname);
    END LOOP;
    FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE n.nspname = 'public' AND c.relkind = 'S'
                AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype IN ('a', 'i', 'e')) LOOP
        EXECUTE format('ALTER SEQUENCE public.%I OWNER TO $ROL_APP', r.relname);
    END LOOP;
    -- Las funciones de pg_trgm y pgcrypto pertenecen a su extensión: no se les puede cambiar el dueño
    FOR r IN SELECT p.oid::regprocedure AS firma FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
              WHERE n.nspname = 'public'
                AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = p.oid AND d.deptype = 'e') LOOP
        EXECUTE format('ALTER FUNCTION %s OWNER TO $ROL_APP', r.firma);
    END LOOP;
END
\$\$;
SQL

echo "== 5. Protegiendo las bitácoras =="
bash "$DIR/proteger_bitacoras.sh" "$DESTINO" "$ROL_APP"
bash "$DIR/reforzar_bitacoras.sh" "$DESTINO"
bash "$DIR/reforzar_bitacoras.sh" "$DESTINO" --verificar

echo "== 6. Comparación con $ORIGEN =="
contar() {
    local base="$1" t
    for t in $(psql -X -tA -d "$base" -c "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename"); do
        printf '%s|%s\n' "$t" "$(psql -X -tA -d "$base" -c "SELECT count(*) FROM public.\"$t\"")"
    done
}
contar "$ORIGEN" > "$TMP/origen.txt"
contar "$DESTINO" > "$TMP/destino.txt"

SIN_PREVER=0
printf '%-36s %10s %10s  %s\n' "tabla" "origen" "nueva" "tratamiento"
while IFS='|' read -r tabla origen; do
    nueva=$(grep "^$tabla|" "$TMP/destino.txt" | cut -d'|' -f2 || true)
    if [[ " ${CATALOGOS[*]} " == *" $tabla "* ]]; then
        tratamiento="copiada"
        [ "$origen" = "$nueva" ] || { tratamiento="NO COINCIDE"; SIN_PREVER=1; }
    elif [ "$tabla" = "bitacora_cadena" ]; then
        # reforzar_bitacoras.sh deja el inicio de las dos cadenas: una fila por bitácora, con huella en ceros
        tratamiento="iniciada"
        INICIOS=$(psql -X -tA -d "$DESTINO" -c "SELECT count(*) FROM bitacora_cadena WHERE huella = repeat('0', 64)")
        [ "$nueva" = "2" ] && [ "$INICIOS" = "2" ] || { tratamiento="NO QUEDÓ INICIADA"; SIN_PREVER=1; }
    elif [[ "$VACIAS_PREVISTAS" == *" $tabla "* ]]; then
        tratamiento="vacía"
        [ "$nueva" = "0" ] || { tratamiento="NO QUEDÓ VACÍA"; SIN_PREVER=1; }
    else
        tratamiento="SIN PREVER: revisar"
        SIN_PREVER=1
    fi
    printf '%-36s %10s %10s  %s\n' "$tabla" "$origen" "${nueva:-?}" "$tratamiento"
done < "$TMP/origen.txt"

if [ "$SIN_PREVER" -ne 0 ]; then
    echo
    echo "ATENCIÓN: hay tablas para revisar antes de usar esta base." >&2
    exit 2
fi
echo
echo "Listo: $DESTINO se creó y $ORIGEN no se modificó. Falta crear el primer administrador (crear_primer_admin.sh)."
