#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Crea la base de PRUEBAS en el servidor PostgreSQL
#
# Se ejecuta UNA vez en el servidor 192.168.129.73, como usuario postgres:
#
#     sudo -u postgres bash crear_base_pruebas.sh
#
# Qué hace (docs/ambiente-pruebas.md):
#   1. Crea el rol gestion_documental_pruebas y la base gestion_documental_pruebas.
#   2. Copia desde producción la ESTRUCTURA completa y solo los catálogos:
#      niveles, organigrama, tipos de documento, atajos del canal y el
#      historial de Flyway (así la aplicación sabe en qué versión está).
#      NO copia expedientes, actuaciones, adjuntos, usuarios ni asignaciones.
#   3. Deja todos los objetos a nombre del rol de pruebas.
#   4. Crea dos usuarios de prueba (opcional) con una contraseña de prueba.
#
# No modifica la base de producción: solo la lee con pg_dump.
# Si la base de pruebas ya existe, se detiene sin tocar nada.
# Las contraseñas se piden por teclado y no quedan guardadas en ningún archivo.
# =====================================================================
set -euo pipefail

BASE_PRODUCCION="gestion_documental"
BASE_PRUEBAS="gestion_documental_pruebas"
ROL_PRUEBAS="gestion_documental_pruebas"
TABLAS_CATALOGO=(niveles_jerarquicos dependencias tipos_documentos reglas_derivacion flyway_schema_history)

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

psql_postgres() { psql -X -v ON_ERROR_STOP=1 -q "$@"; }

echo "== Gestión Documental: creación de la base de pruebas =="

# --- Comprobaciones previas -------------------------------------------
if [ "$(id -un)" != "postgres" ]; then
    echo "Ejecútelo como postgres:  sudo -u postgres bash $0" >&2
    exit 1
fi
if ! psql_postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$BASE_PRODUCCION'" | grep -q 1; then
    echo "No se encontró la base de producción '$BASE_PRODUCCION'." >&2
    exit 1
fi
if psql_postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$BASE_PRUEBAS'" | grep -q 1; then
    echo "La base '$BASE_PRUEBAS' ya existe: no se modificó nada." >&2
    echo "Si quiere recrearla, bórrela antes a mano (DROP DATABASE $BASE_PRUEBAS;) y vuelva a ejecutar." >&2
    exit 1
fi

# --- 1. Rol y base -----------------------------------------------------
if psql_postgres -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$ROL_PRUEBAS'" | grep -q 1; then
    echo "El rol '$ROL_PRUEBAS' ya existe: se usa con su contraseña actual."
else
    read -r -s -p "Contraseña NUEVA para el rol $ROL_PRUEBAS: " CLAVE_ROL; echo
    read -r -s -p "Repita la contraseña: " CLAVE_ROL_2; echo
    if [ -z "$CLAVE_ROL" ] || [ "$CLAVE_ROL" != "$CLAVE_ROL_2" ]; then
        echo "Las contraseñas no coinciden o están vacías." >&2
        exit 1
    fi
    # La contraseña viaja como variable de psql (:'clave'), nunca armada dentro del SQL
    psql_postgres -d postgres -v clave="$CLAVE_ROL" <<'SQL'
SELECT format('CREATE ROLE gestion_documental_pruebas LOGIN PASSWORD %L', :'clave') \gexec
SQL
    unset CLAVE_ROL CLAVE_ROL_2
    echo "Rol $ROL_PRUEBAS creado."
fi

psql_postgres -d postgres -c "CREATE DATABASE $BASE_PRUEBAS OWNER $ROL_PRUEBAS ENCODING 'UTF8' TEMPLATE template0"
echo "Base $BASE_PRUEBAS creada."

# --- 2. Estructura y catálogos desde producción ------------------------
echo "Copiando la estructura de $BASE_PRODUCCION..."
pg_dump -d "$BASE_PRODUCCION" --schema-only --no-owner --no-privileges -f "$TMP/esquema.sql"
psql_postgres -d "$BASE_PRUEBAS" -f "$TMP/esquema.sql"

echo "Copiando catálogos: ${TABLAS_CATALOGO[*]}..."
ARGS_TABLAS=()
for t in "${TABLAS_CATALOGO[@]}"; do ARGS_TABLAS+=(-t "public.$t"); done
# --disable-triggers: carga el organigrama tal cual (con su ruta) sin depender del orden de las filas
pg_dump -d "$BASE_PRODUCCION" --data-only --no-owner --no-privileges --disable-triggers "${ARGS_TABLAS[@]}" -f "$TMP/catalogos.sql"
psql_postgres -d "$BASE_PRUEBAS" -f "$TMP/catalogos.sql"

# --- 3. Todo a nombre del rol de pruebas ------------------------------
psql_postgres -d "$BASE_PRUEBAS" <<SQL
ALTER SCHEMA public OWNER TO $ROL_PRUEBAS;
DO \$\$
DECLARE
    r record;
BEGIN
    FOR r IN SELECT c.relname, c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm') LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO $ROL_PRUEBAS', r.relname);
    END LOOP;
    -- Las secuencias de las columnas serial cambian con su tabla; las sueltas, aparte
    FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE n.nspname = 'public' AND c.relkind = 'S'
                AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype IN ('a', 'i')) LOOP
        EXECUTE format('ALTER SEQUENCE public.%I OWNER TO $ROL_PRUEBAS', r.relname);
    END LOOP;
    FOR r IN SELECT p.oid::regprocedure AS firma FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
              WHERE n.nspname = 'public' LOOP
        EXECUTE format('ALTER FUNCTION %s OWNER TO $ROL_PRUEBAS', r.firma);
    END LOOP;
END
\$\$;
SQL
echo "Objetos asignados a $ROL_PRUEBAS."

# --- 4. Usuarios de prueba (opcional) ----------------------------------
echo
read -r -p "¿Crear los usuarios de prueba prueba_mesa_tic y prueba_resp_hacienda? [s/N] " RESPUESTA
if [[ "$RESPUESTA" =~ ^[sS]$ ]]; then
    read -r -s -p "Contraseña de PRUEBA para ambos usuarios (no use una contraseña real): " CLAVE_USUARIOS; echo
    if [ ${#CLAVE_USUARIOS} -lt 8 ]; then
        echo "La contraseña debe tener al menos 8 caracteres. No se crearon usuarios." >&2
        echo "Puede crearlos después, sin rehacer la base, con:  sudo -u postgres bash crear_usuarios_prueba.sh" >&2
    else
        # BCrypt con pgcrypto: Spring Security acepta los hashes con prefijo 2a
        psql_postgres -d "$BASE_PRUEBAS" -v clave="$CLAVE_USUARIOS" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO usuarios (nombre_completo, username, password_hash, email, dependencia_id, rol, activo)
SELECT 'Prueba Mesa de Entrada TIC', 'prueba_mesa_tic', crypt(:'clave', gen_salt('bf', 10)), NULL, d.id, 'SECRETARIA_ADMIN', TRUE
  FROM dependencias d WHERE d.sigla = 'DTIC-SA';
INSERT INTO usuarios (nombre_completo, username, password_hash, email, dependencia_id, rol, activo)
SELECT 'Prueba Responsable Hacienda', 'prueba_resp_hacienda', crypt(:'clave', gen_salt('bf', 10)), NULL, d.id, 'JEFE_DEPTO', TRUE
  FROM dependencias d WHERE d.sigla = 'DHAC';

INSERT INTO asignaciones (usuario_id, rol, dependencia_ambito_id, incluye_subordinadas, acto_administrativo)
SELECT u.id, 'MESA_ENTRADA', d.id, TRUE, 'Usuario de prueba'
  FROM usuarios u, dependencias d WHERE u.username = 'prueba_mesa_tic' AND d.sigla = 'DTIC';
INSERT INTO asignaciones (usuario_id, rol, dependencia_ambito_id, incluye_subordinadas, acto_administrativo)
SELECT u.id, 'RESPONSABLE', d.id, TRUE, 'Usuario de prueba'
  FROM usuarios u, dependencias d WHERE u.username = 'prueba_resp_hacienda' AND d.sigla = 'DHAC';
SQL
        echo "Usuarios de prueba creados: prueba_mesa_tic (mesa de la DTIC) y prueba_resp_hacienda (responsable de Hacienda)."
    fi
    unset CLAVE_USUARIOS
fi

# --- Verificación -------------------------------------------------------
echo
echo "== Resultado =="
psql_postgres -d "$BASE_PRUEBAS" -c "
SELECT (SELECT COUNT(*) FROM dependencias)        AS dependencias,
       (SELECT COUNT(*) FROM tipos_documentos)    AS tipos_documento,
       (SELECT COUNT(*) FROM documentos)          AS expedientes,
       (SELECT COUNT(*) FROM usuarios)            AS usuarios,
       (SELECT MAX(version::int) FROM flyway_schema_history WHERE success) AS version_flyway;"

cat <<FIN

Listo. Pasos que quedan:
  1. Si la aplicación se conecta desde otra máquina, habilite el rol en pg_hba.conf
     igual que el de producción, por ejemplo:
         host  $BASE_PRUEBAS  $ROL_PRUEBAS  <red de la aplicación>/24  scram-sha-256
     y recargue la configuración:  psql -c "SELECT pg_reload_conf();"
  2. En la máquina de la aplicación (docs/ambiente-pruebas.md, paso 4):
         \$env:DB_PASSWORD_PRUEBAS = '<contraseña del rol>'
         .\\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=pruebas"
FIN
