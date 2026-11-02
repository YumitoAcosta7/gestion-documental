-- =====================================================================
-- Gestión Documental - Migración V14: hallazgos A4 y A5 de la auditoría
-- (docs/auditoria-arquitectura-seguridad.md)
--
--  1. A4: el cierre excepcional de informes anula los pedidos que todavía
--     esperan acuse: nuevo tipo de anulación CIERRE_EXCEPCIONAL.
--  2. A5: las dos bitácoras (auditorias_documento y auditoria_sistema) no se
--     pueden modificar, borrar ni vaciar con TRUNCATE.
--
-- El cambio de dueño de las bitácoras, para que la aplicación no pueda
-- desactivar estos triggers, lo hace un superusuario con
-- db/seguridad/proteger_bitacoras.sh. No borra nada. Idempotente.
-- =====================================================================

ALTER TABLE actuaciones_documento DROP CONSTRAINT IF EXISTS chk_actuaciones_anulacion;
ALTER TABLE actuaciones_documento ADD  CONSTRAINT chk_actuaciones_anulacion CHECK (
       (anulada AND tipo_anulacion IN ('RETIRADO', 'RECHAZADO', 'CIERRE_EXCEPCIONAL') AND motivo_anulacion IS NOT NULL)
    OR (NOT anulada AND tipo_anulacion IS NULL));

CREATE OR REPLACE FUNCTION fn_bitacora_inmutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La bitácora % no se puede modificar, borrar ni vaciar', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

-- Bitácora de los expedientes: hasta ahora no tenía protección
DROP TRIGGER IF EXISTS trg_auditorias_documento_inmutable ON auditorias_documento;
CREATE TRIGGER trg_auditorias_documento_inmutable
    BEFORE UPDATE OR DELETE ON auditorias_documento
    FOR EACH ROW EXECUTE FUNCTION fn_bitacora_inmutable();

-- TRUNCATE no dispara los triggers de fila: hace falta uno de sentencia en cada bitácora
DROP TRIGGER IF EXISTS trg_auditorias_documento_sin_truncate ON auditorias_documento;
CREATE TRIGGER trg_auditorias_documento_sin_truncate
    BEFORE TRUNCATE ON auditorias_documento
    FOR EACH STATEMENT EXECUTE FUNCTION fn_bitacora_inmutable();

DROP TRIGGER IF EXISTS trg_auditoria_sistema_sin_truncate ON auditoria_sistema;
CREATE TRIGGER trg_auditoria_sistema_sin_truncate
    BEFORE TRUNCATE ON auditoria_sistema
    FOR EACH STATEMENT EXECUTE FUNCTION fn_bitacora_inmutable();
