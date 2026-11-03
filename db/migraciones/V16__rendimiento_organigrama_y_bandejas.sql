-- =====================================================================
-- Gestión Documental - Migración V16: hallazgos B1 y B2 de la auditoría
-- (docs/auditoria-arquitectura-seguridad.md)
--
--  1. B1: la aplicación guarda el organigrama en memoria y solo lo vuelve a
--     leer cuando cambia. Cada cambio en dependencias, niveles o atajos
--     incrementa un número de versión, que la aplicación consulta con una
--     lectura de una sola fila.
--  2. B2: índices para las bandejas, el ámbito y los contadores.
-- No borra nada. Idempotente.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Versión del organigrama
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS organigrama_version (
    id      SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    version BIGINT   NOT NULL DEFAULT 1
);
INSERT INTO organigrama_version (id, version) VALUES (1, 1) ON CONFLICT (id) DO NOTHING;

CREATE OR REPLACE FUNCTION fn_organigrama_version() RETURNS trigger AS $$
BEGIN
    UPDATE organigrama_version SET version = version + 1 WHERE id = 1;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Un disparo por sentencia: una importación masiva cuenta como un solo cambio
DROP TRIGGER IF EXISTS trg_dependencias_version ON dependencias;
CREATE TRIGGER trg_dependencias_version
    AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON dependencias
    FOR EACH STATEMENT EXECUTE FUNCTION fn_organigrama_version();

DROP TRIGGER IF EXISTS trg_niveles_version ON niveles_jerarquicos;
CREATE TRIGGER trg_niveles_version
    AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON niveles_jerarquicos
    FOR EACH STATEMENT EXECUTE FUNCTION fn_organigrama_version();

DROP TRIGGER IF EXISTS trg_reglas_derivacion_version ON reglas_derivacion;
CREATE TRIGGER trg_reglas_derivacion_version
    AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON reglas_derivacion
    FOR EACH STATEMENT EXECUTE FUNCTION fn_organigrama_version();

-- ---------------------------------------------------------------------
-- 2. Índices de bandejas y ámbito
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_actuaciones_emisora ON actuaciones_documento (dependencia_emisora_id);
CREATE INDEX IF NOT EXISTS idx_actuaciones_pendientes ON actuaciones_documento (dependencia_receptora_pase_id, documento_id)
    WHERE tipo_actuacion = 'PASE_INTERNO' AND NOT pase_confirmado AND NOT anulada;
CREATE INDEX IF NOT EXISTS idx_documentos_registro ON documentos (dependencia_registro_id);
CREATE INDEX IF NOT EXISTS idx_documentos_origen ON documentos (dependencia_origen_id);
CREATE INDEX IF NOT EXISTS idx_documentos_carga ON documentos (usuario_carga_ingreso_id);
CREATE INDEX IF NOT EXISTS idx_documentos_ingreso ON documentos (fecha_hora_ingreso DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_usuarios_dependencia ON usuarios (dependencia_id) WHERE activo;
