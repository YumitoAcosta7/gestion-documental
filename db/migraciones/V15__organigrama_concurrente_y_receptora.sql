-- =====================================================================
-- Gestión Documental - Migración V15: hallazgos M3 y M4 de la auditoría
-- (docs/auditoria-arquitectura-seguridad.md)
--
--  1. M3: dos cambios simultáneos de superior en la misma rama podían dejar
--     rutas desactualizadas (cada transacción calculaba con la ruta que veía).
--     Los altas y movimientos del organigrama se serializan con un bloqueo
--     transaccional; las consultas no se ven afectadas.
--  2. M4: todo pase y toda devolución indican su dependencia receptora. La
--     aplicación deja de identificar pases antiguos por el nombre en la
--     descripción (había 55 dependencias llamadas igual).
-- No borra nada. Idempotente.
-- =====================================================================

CREATE OR REPLACE FUNCTION fn_dependencias_ruta() RETURNS trigger AS $$
DECLARE
    ruta_padre VARCHAR(500);
BEGIN
    -- Un solo cambio de estructura a la vez: el siguiente espera y calcula con la ruta ya actualizada
    PERFORM pg_advisory_xact_lock(hashtext('gestion_documental.organigrama'));
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

-- Antes de exigir la receptora, se confirma que no queden pases sin ella
DO $$
DECLARE
    sin_receptora BIGINT;
BEGIN
    SELECT COUNT(*) INTO sin_receptora FROM actuaciones_documento
     WHERE tipo_actuacion IN ('PASE_INTERNO', 'PASE_CONFIRMADO', 'DEVOLUCION') AND dependencia_receptora_pase_id IS NULL;
    IF sin_receptora > 0 THEN
        RAISE EXCEPTION '% pases sin dependencia receptora: complételos antes de aplicar V15', sin_receptora;
    END IF;
END $$;

ALTER TABLE actuaciones_documento DROP CONSTRAINT IF EXISTS chk_actuaciones_pase_con_receptora;
ALTER TABLE actuaciones_documento ADD  CONSTRAINT chk_actuaciones_pase_con_receptora CHECK (
    tipo_actuacion NOT IN ('PASE_INTERNO', 'PASE_CONFIRMADO', 'DEVOLUCION') OR dependencia_receptora_pase_id IS NOT NULL);
