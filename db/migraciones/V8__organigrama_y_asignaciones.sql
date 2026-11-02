-- =====================================================================
-- Gestión Documental - Migración V8: fundaciones de la Fase 2 (etapa 2.1)
--
-- docs/diseno-fase2-multiarea.md §2.1 y §3.2:
--  1. dependencias: sigla, ruta materializada (/1/10/40/), mesa de entrada
--     y "recibe externos". La ruta la mantiene un trigger: al crear una
--     dependencia o cambiarle el superior se recalcula todo su subárbol,
--     y se rechaza un cambio de superior que forme un ciclo.
--  2. asignaciones: permiso = rol x ámbito x vigencia. Se cargan desde los
--     roles actuales (§3.3); usuarios.rol se conserva mientras convivan.
-- No borra nada. Idempotente.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Organigrama
-- ---------------------------------------------------------------------
ALTER TABLE dependencias
    ADD COLUMN IF NOT EXISTS sigla            VARCHAR(20),
    ADD COLUMN IF NOT EXISTS ruta             VARCHAR(500),
    ADD COLUMN IF NOT EXISTS es_mesa_entrada  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS recibe_externos  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS resolucion       VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_dependencias_sigla ON dependencias (sigla);
CREATE INDEX IF NOT EXISTS idx_dependencias_ruta ON dependencias (ruta text_pattern_ops);

ALTER TABLE dependencias DROP CONSTRAINT IF EXISTS chk_dependencias_sigla;
ALTER TABLE dependencias ADD  CONSTRAINT chk_dependencias_sigla
    CHECK (sigla IS NULL OR sigla ~ '^[A-Z0-9][A-Z0-9-]{1,19}$');

-- Ruta propia a partir de la del superior; rechaza ciclos
CREATE OR REPLACE FUNCTION fn_dependencias_ruta() RETURNS trigger AS $$
DECLARE
    ruta_padre VARCHAR(500);
BEGIN
    IF NEW.dependencia_padre_id IS NULL THEN
        NEW.ruta := '/' || NEW.id || '/';
    ELSE
        SELECT ruta INTO ruta_padre FROM dependencias WHERE id = NEW.dependencia_padre_id;
        IF ruta_padre IS NULL THEN
            RAISE EXCEPTION 'La dependencia superior % no existe o no tiene ruta', NEW.dependencia_padre_id;
        END IF;
        IF ruta_padre LIKE '%/' || NEW.id || '/%' THEN
            RAISE EXCEPTION 'Ciclo en el organigrama: % no puede depender de % (es su subordinada)',
                NEW.id, NEW.dependencia_padre_id;
        END IF;
        NEW.ruta := ruta_padre || NEW.id || '/';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Al moverse una dependencia, todo su subárbol cambia de prefijo
CREATE OR REPLACE FUNCTION fn_dependencias_ruta_subarbol() RETURNS trigger AS $$
BEGIN
    IF NEW.ruta IS DISTINCT FROM OLD.ruta AND OLD.ruta IS NOT NULL THEN
        UPDATE dependencias
           SET ruta = NEW.ruta || substring(ruta FROM length(OLD.ruta) + 1)
         WHERE ruta LIKE OLD.ruta || '%' AND id <> NEW.id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_dependencias_ruta ON dependencias;
CREATE TRIGGER trg_dependencias_ruta
    BEFORE INSERT OR UPDATE OF dependencia_padre_id ON dependencias
    FOR EACH ROW EXECUTE FUNCTION fn_dependencias_ruta();

DROP TRIGGER IF EXISTS trg_dependencias_ruta_subarbol ON dependencias;
CREATE TRIGGER trg_dependencias_ruta_subarbol
    AFTER UPDATE OF dependencia_padre_id ON dependencias
    FOR EACH ROW EXECUTE FUNCTION fn_dependencias_ruta_subarbol();

-- Rutas de las dependencias existentes
WITH RECURSIVE arbol AS (
    SELECT id, ('/' || id || '/')::VARCHAR(500) AS ruta
      FROM dependencias WHERE dependencia_padre_id IS NULL
    UNION ALL
    SELECT d.id, (a.ruta || d.id || '/')::VARCHAR(500)
      FROM dependencias d JOIN arbol a ON d.dependencia_padre_id = a.id
)
UPDATE dependencias d SET ruta = a.ruta FROM arbol a WHERE a.id = d.id AND d.ruta IS DISTINCT FROM a.ruta;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM dependencias WHERE ruta IS NULL) THEN
        RAISE EXCEPTION 'Hay dependencias fuera del árbol (sin ruta): revisar dependencia_padre_id';
    END IF;
END $$;

ALTER TABLE dependencias ALTER COLUMN ruta SET NOT NULL;

-- Las secretarías administrativas son las mesas de entrada de su dirección
UPDATE dependencias SET es_mesa_entrada = TRUE
 WHERE nombre = 'Unidad de Secretaría Administrativa' AND NOT es_mesa_entrada;

-- ---------------------------------------------------------------------
-- 2. Asignaciones
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asignaciones (
    id                    BIGSERIAL PRIMARY KEY,
    usuario_id            INTEGER NOT NULL REFERENCES usuarios (id),
    rol                   VARCHAR(30) NOT NULL
                          CHECK (rol IN ('ADMIN_SISTEMA','MESA_ENTRADA','RESPONSABLE','OPERADOR','CONSULTA')),
    dependencia_ambito_id INTEGER NOT NULL REFERENCES dependencias (id),
    incluye_subordinadas  BOOLEAN NOT NULL DEFAULT TRUE,
    vigente_desde         DATE NOT NULL DEFAULT CURRENT_DATE,
    vigente_hasta         DATE,
    es_suplencia          BOOLEAN NOT NULL DEFAULT FALSE,
    titular_usuario_id    INTEGER REFERENCES usuarios (id),
    acto_administrativo   VARCHAR(100),
    activa                BOOLEAN NOT NULL DEFAULT TRUE,
    creada_por_id         INTEGER REFERENCES usuarios (id),   -- NULL = carga inicial (V8)
    fecha_hora_creacion   TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_asignaciones_vigencia CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde),
    CONSTRAINT chk_asignaciones_suplencia CHECK (NOT es_suplencia OR (titular_usuario_id IS NOT NULL AND vigente_hasta IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_asignaciones_usuario ON asignaciones (usuario_id) WHERE activa;

-- Roles actuales -> asignaciones (§3.3). DIRECTOR y SECRETARIA_ADMIN toman como ámbito
-- la dirección de referencia de su dependencia (primer ancestro de nivel Dirección o
-- superior): el Director de TIC está cargado en la Coordinación de TIC y su ámbito
-- es la Dirección de TIC completa.
INSERT INTO asignaciones (usuario_id, rol, dependencia_ambito_id, incluye_subordinadas, acto_administrativo)
SELECT u.id,
       CASE u.rol WHEN 'DIRECTOR' THEN 'RESPONSABLE'
                  WHEN 'SECRETARIA_ADMIN' THEN 'MESA_ENTRADA'
                  ELSE 'RESPONSABLE' END,
       CASE WHEN u.rol IN ('DIRECTOR', 'SECRETARIA_ADMIN') THEN (
                SELECT a.id
                  FROM dependencias a JOIN niveles_jerarquicos n ON n.id = a.nivel_jerarquico_id
                 WHERE d.ruta LIKE a.ruta || '%' AND n.orden_nivel <= 4
                 ORDER BY length(a.ruta) DESC LIMIT 1)
            ELSE u.dependencia_id END,
       TRUE,
       'Migración V8 desde el rol ' || u.rol
  FROM usuarios u JOIN dependencias d ON d.id = u.dependencia_id
 WHERE u.rol IN ('DIRECTOR', 'SECRETARIA_ADMIN', 'JEFE_DEPTO', 'JEFE_UNIDAD')
   AND NOT EXISTS (SELECT 1 FROM asignaciones x WHERE x.usuario_id = u.id);

