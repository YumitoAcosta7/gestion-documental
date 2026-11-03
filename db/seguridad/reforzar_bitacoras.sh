#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Huella encadenada de las bitácoras (trazabilidad)
#
# Las bitácoras ya no se pueden modificar ni borrar desde la aplicación
# (V14 y proteger_bitacoras.sh). Este script agrega una prueba de que nadie,
# ni siquiera un superusuario de la base, las alteró después:
#   - Cada fila nueva lleva huella = SHA-256(huella anterior | contenido de la fila).
#     Modificar o borrar una fila rompe la cadena desde ese punto.
#   - auditoria_sistema guarda además la IP del usuario (la de expedientes ya la tenía).
#   - La última huella de cada bitácora queda en bitacora_cadena, del rol de la bitácora.
#   - Las filas existentes se encadenan en orden de id.
#
# Se ejecuta en el servidor 192.168.129.73, como postgres, DESPUÉS de
# proteger_bitacoras.sh:
#
#     sudo -u postgres bash reforzar_bitacoras.sh <base>               # instala (se puede repetir)
#     sudo -u postgres bash reforzar_bitacoras.sh <base> --verificar   # comprueba las dos cadenas
#
# La verificación conviene hacerla periódicamente y guardar fuera del servidor
# la última huella que informa: así también se detectaría que borraron las
# últimas filas.
# =====================================================================
set -euo pipefail

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Uso: sudo -u postgres bash $0 <base> [--verificar]" >&2
    exit 1
fi
BASE="$1"
ROL_BITACORA="gestion_documental_bitacora"

if [ "$(id -un)" != "postgres" ]; then
    echo "Ejecútelo como postgres:  sudo -u postgres bash $0 $*" >&2
    exit 1
fi

if [ "${2:-}" = "--verificar" ]; then
    psql -X -v ON_ERROR_STOP=1 -d "$BASE" <<'SQL'
SELECT tabla, filas, sin_huella, huellas_alteradas, eslabones_perdidos, bifurcaciones,
       CASE WHEN sin_huella + huellas_alteradas + eslabones_perdidos + bifurcaciones = 0 THEN 'ÍNTEGRA' ELSE 'ALTERADA' END AS estado,
       ultima_huella
  FROM fn_verificar_bitacora('auditorias_documento')
UNION ALL
SELECT tabla, filas, sin_huella, huellas_alteradas, eslabones_perdidos, bifurcaciones,
       CASE WHEN sin_huella + huellas_alteradas + eslabones_perdidos + bifurcaciones = 0 THEN 'ÍNTEGRA' ELSE 'ALTERADA' END,
       ultima_huella
  FROM fn_verificar_bitacora('auditoria_sistema');
SQL
    exit 0
fi

# Tienen que pertenecer ya al rol de la bitácora (proteger_bitacoras.sh)
DUENO=$(psql -X -tA -d "$BASE" -c "SELECT pg_get_userbyid(relowner) FROM pg_class WHERE relname = 'auditoria_sistema'")
if [ "$DUENO" != "$ROL_BITACORA" ]; then
    echo "Primero ejecute proteger_bitacoras.sh en $BASE (hoy la bitácora pertenece a '$DUENO')." >&2
    exit 1
fi

psql -X -v ON_ERROR_STOP=1 -q -d "$BASE" -v rol_bitacora="$ROL_BITACORA" <<'SQL'
BEGIN;

ALTER TABLE auditoria_sistema    ADD COLUMN IF NOT EXISTS direccion_ip    VARCHAR(45);
ALTER TABLE auditoria_sistema    ADD COLUMN IF NOT EXISTS huella_anterior CHAR(64);
ALTER TABLE auditoria_sistema    ADD COLUMN IF NOT EXISTS huella          CHAR(64);
ALTER TABLE auditorias_documento ADD COLUMN IF NOT EXISTS huella_anterior CHAR(64);
ALTER TABLE auditorias_documento ADD COLUMN IF NOT EXISTS huella          CHAR(64);

-- Última huella de cada bitácora: el trigger la lee y la actualiza bloqueando la fila,
-- así dos inserciones simultáneas se encadenan una detrás de la otra
CREATE TABLE IF NOT EXISTS bitacora_cadena (
    tabla  VARCHAR(40) PRIMARY KEY,
    huella CHAR(64)    NOT NULL
);
ALTER TABLE bitacora_cadena OWNER TO :"rol_bitacora";

-- Huella de una fila: SHA-256 de la huella anterior y del contenido (sin las columnas de huella)
CREATE OR REPLACE FUNCTION fn_huella_fila(anterior TEXT, fila JSONB) RETURNS CHAR(64) AS $$
    SELECT encode(sha256(convert_to(anterior || '|' || (fila - 'huella' - 'huella_anterior')::text, 'UTF8')), 'hex');
$$ LANGUAGE sql IMMUTABLE;

CREATE OR REPLACE FUNCTION fn_bitacora_huella() RETURNS trigger AS $$
DECLARE
    anterior CHAR(64);
BEGIN
    -- IP del usuario que pasa la aplicación en la transacción (auditoria_sistema)
    IF TG_TABLE_NAME = 'auditoria_sistema' AND NEW.direccion_ip IS NULL THEN
        NEW.direccion_ip := NULLIF(current_setting('app.direccion_ip', true), '');
    END IF;
    SELECT huella INTO anterior FROM bitacora_cadena WHERE tabla = TG_TABLE_NAME FOR UPDATE;
    IF anterior IS NULL THEN
        anterior := repeat('0', 64);
        INSERT INTO bitacora_cadena (tabla, huella) VALUES (TG_TABLE_NAME, anterior);
    END IF;
    NEW.huella_anterior := anterior;
    NEW.huella := fn_huella_fila(anterior, to_jsonb(NEW));
    UPDATE bitacora_cadena SET huella = NEW.huella WHERE tabla = TG_TABLE_NAME;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
ALTER FUNCTION fn_bitacora_huella() OWNER TO :"rol_bitacora";

-- Encadenar las filas existentes, en orden de id (una sola vez: las que no tienen huella)
DO $$
DECLARE
    t TEXT;
    r RECORD;
    anterior CHAR(64);
BEGIN
    FOREACH t IN ARRAY ARRAY['auditorias_documento', 'auditoria_sistema'] LOOP
        -- Ya encadenada en una ejecución anterior: desde entonces el trigger agrega cada fila nueva
        CONTINUE WHEN EXISTS (SELECT 1 FROM bitacora_cadena c WHERE c.tabla = t);
        EXECUTE format('ALTER TABLE %I DISABLE TRIGGER USER', t);
        anterior := repeat('0', 64);
        FOR r IN EXECUTE format('SELECT id, to_jsonb(x) AS fila FROM %I x ORDER BY id', t) LOOP
            EXECUTE format('UPDATE %I SET huella_anterior = $1, huella = $2 WHERE id = $3', t)
                USING anterior, fn_huella_fila(anterior, r.fila), r.id;
            anterior := fn_huella_fila(anterior, r.fila);
        END LOOP;
        EXECUTE format('ALTER TABLE %I ENABLE TRIGGER USER', t);
        INSERT INTO bitacora_cadena (tabla, huella) VALUES (t, anterior);
    END LOOP;
END $$;

DROP TRIGGER IF EXISTS trg_auditorias_documento_huella ON auditorias_documento;
CREATE TRIGGER trg_auditorias_documento_huella BEFORE INSERT ON auditorias_documento
    FOR EACH ROW EXECUTE FUNCTION fn_bitacora_huella();
DROP TRIGGER IF EXISTS trg_auditoria_sistema_huella ON auditoria_sistema;
CREATE TRIGGER trg_auditoria_sistema_huella BEFORE INSERT ON auditoria_sistema
    FOR EACH ROW EXECUTE FUNCTION fn_bitacora_huella();

-- Verificación: recalcula cada huella y comprueba que la cadena no tenga huecos ni ramas
CREATE OR REPLACE FUNCTION fn_verificar_bitacora(t TEXT)
RETURNS TABLE (tabla TEXT, filas BIGINT, sin_huella BIGINT, huellas_alteradas BIGINT,
               eslabones_perdidos BIGINT, bifurcaciones BIGINT, ultima_huella TEXT) AS $$
BEGIN
    RETURN QUERY EXECUTE format($q$
        WITH x AS (SELECT id, huella, huella_anterior, to_jsonb(b) AS fila FROM %1$I b)
        SELECT %2$L::text,
               (SELECT count(*) FROM x),
               (SELECT count(*) FROM x WHERE huella IS NULL),
               (SELECT count(*) FROM x WHERE huella IS NOT NULL AND huella <> fn_huella_fila(huella_anterior, fila)),
               (SELECT count(*) FROM x a WHERE a.huella_anterior <> repeat('0', 64)
                   AND NOT EXISTS (SELECT 1 FROM x p WHERE p.huella = a.huella_anterior)),
               (SELECT count(*) FROM (SELECT huella_anterior FROM x WHERE huella_anterior IS NOT NULL
                   GROUP BY huella_anterior HAVING count(*) > 1) d),
               (SELECT huella::text FROM bitacora_cadena c WHERE c.tabla = %2$L)
    $q$, t, t);
END;
$$ LANGUAGE plpgsql STABLE;

COMMIT;
SQL

echo "Huella encadenada instalada en $BASE. Verificación:"
exec bash "$0" "$BASE" --verificar
