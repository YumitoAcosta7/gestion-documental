-- =====================================================================
-- Gestión Documental - Migración V2: mejoras operativas
--
-- Aplicar UNA vez sobre la base gestion_documental, ANTES de iniciar la
-- versión de la aplicación que incluye estas mejoras (ddl-auto=validate).
-- El script es idempotente: puede ejecutarse de nuevo sin efectos.
--
-- Contenido:
--   1. Anulación lógica de actuaciones (retirar / rechazar pases, cierre forzado).
--   2. Versionado de adjuntos PDF (sin sobrescritura).
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. ANULACIÓN LÓGICA DE ACTUACIONES
--    Una actuación anulada no se borra: queda en el historial con el
--    motivo, quién la anuló y cuándo. Deja de contar para bandejas,
--    informes pendientes y devoluciones.
-- ---------------------------------------------------------------------
ALTER TABLE actuaciones_documento
    ADD COLUMN IF NOT EXISTS anulada              BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS tipo_anulacion       VARCHAR(30),
    ADD COLUMN IF NOT EXISTS motivo_anulacion     TEXT,
    ADD COLUMN IF NOT EXISTS fecha_hora_anulacion TIMESTAMP,
    ADD COLUMN IF NOT EXISTS usuario_anulacion_id INTEGER REFERENCES usuarios (id);

COMMENT ON COLUMN actuaciones_documento.tipo_anulacion IS 'RETIRADO (lo retira el emisor) | RECHAZADO (lo rechaza el receptor)';

CREATE INDEX IF NOT EXISTS idx_actuaciones_documento_doc_fecha
    ON actuaciones_documento (documento_id, fecha_hora_actuacion);

CREATE INDEX IF NOT EXISTS idx_actuaciones_documento_receptora
    ON actuaciones_documento (dependencia_receptora_pase_id);

-- ---------------------------------------------------------------------
-- 2. VERSIONADO DE ADJUNTOS
--    Cada PDF subido es una fila. Al corregir un archivo se agrega una
--    nueva versión vigente y la anterior queda marcada como reemplazada.
-- ---------------------------------------------------------------------
ALTER TABLE adjuntos_documento
    ADD COLUMN IF NOT EXISTS actuacion_id       BIGINT REFERENCES actuaciones_documento (id),
    ADD COLUMN IF NOT EXISTS vigente            BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS reemplazado_por_id BIGINT REFERENCES adjuntos_documento (id),
    ADD COLUMN IF NOT EXISTS motivo_reemplazo   TEXT;

CREATE INDEX IF NOT EXISTS idx_adjuntos_documento_doc
    ON adjuntos_documento (documento_id);

CREATE INDEX IF NOT EXISTS idx_adjuntos_documento_actuacion
    ON adjuntos_documento (actuacion_id);

COMMIT;
