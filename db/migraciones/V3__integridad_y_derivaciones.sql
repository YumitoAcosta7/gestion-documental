-- =====================================================================
-- Gestión Documental - Migración V3: integridad y derivaciones
--
-- Aplicar UNA vez, ANTES de iniciar la versión que la requiere.
-- Ejecutar primero V3__verificacion_previa.sql: todas las consultas
-- marcadas "deben ser 0 filas" tienen que estar vacías.
-- Es idempotente (se puede volver a ejecutar).
--
-- Ver docs/auditoria-validacion-qa.md (hallazgos H1, H5, H7, H8, H11).
-- La restricción de roles (H7) NO se incluye: existen usuarios con roles
-- heredados (JEFA_SECRETARIA, OPERADOR_MESA_ENTRADA) que deben
-- reasignarse primero.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- H1: el historial no se borra nunca junto con el documento
-- ---------------------------------------------------------------------
ALTER TABLE auditorias_documento  DROP CONSTRAINT IF EXISTS fk_auditoria_documento;
ALTER TABLE auditorias_documento  ADD  CONSTRAINT fk_auditoria_documento
    FOREIGN KEY (documento_id) REFERENCES documentos (id) ON DELETE RESTRICT;

ALTER TABLE actuaciones_documento DROP CONSTRAINT IF EXISTS fk_actuacion_documento;
ALTER TABLE actuaciones_documento ADD  CONSTRAINT fk_actuacion_documento
    FOREIGN KEY (documento_id) REFERENCES documentos (id) ON DELETE RESTRICT;

ALTER TABLE adjuntos_documento    DROP CONSTRAINT IF EXISTS fk_adjunto_documento;
ALTER TABLE adjuntos_documento    ADD  CONSTRAINT fk_adjunto_documento
    FOREIGN KEY (documento_id) REFERENCES documentos (id) ON DELETE RESTRICT;

-- ---------------------------------------------------------------------
-- H7: dominios cerrados (estado, prioridad, tipo de actuación, anulación)
-- ---------------------------------------------------------------------
ALTER TABLE documentos DROP CONSTRAINT IF EXISTS chk_documentos_estado;
ALTER TABLE documentos ADD  CONSTRAINT chk_documentos_estado CHECK (estado_actual IN
    ('INGRESADO','EN_REVISION','EN_ESPERA_INFORME','CON_RESPUESTA_TECNICA','PENDIENTE_SALIDA','FINALIZADO','ARCHIVADO'));

ALTER TABLE documentos DROP CONSTRAINT IF EXISTS chk_documentos_prioridad;
ALTER TABLE documentos ADD  CONSTRAINT chk_documentos_prioridad CHECK (prioridad IN ('NORMAL','IMPORTANTE','URGENTE'));

ALTER TABLE actuaciones_documento DROP CONSTRAINT IF EXISTS chk_actuaciones_tipo;
ALTER TABLE actuaciones_documento ADD  CONSTRAINT chk_actuaciones_tipo CHECK (tipo_actuacion IN
    ('PASE_INTERNO','PASE_CONFIRMADO','DEVOLUCION','INFORME_TECNICO','MEMO','ACTA_ENTREGA','PROVIDENCIA','CIERRE_EXCEPCIONAL'));

ALTER TABLE actuaciones_documento DROP CONSTRAINT IF EXISTS chk_actuaciones_anulacion;
ALTER TABLE actuaciones_documento ADD  CONSTRAINT chk_actuaciones_anulacion CHECK (
       (anulada AND tipo_anulacion IN ('RETIRADO','RECHAZADO') AND motivo_anulacion IS NOT NULL)
    OR (NOT anulada AND tipo_anulacion IS NULL));

-- ---------------------------------------------------------------------
-- H8: el pedido de informe es un campo, no un texto en la descripción
-- ---------------------------------------------------------------------
ALTER TABLE actuaciones_documento ADD COLUMN IF NOT EXISTS requiere_informe BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE actuaciones_documento SET requiere_informe = TRUE
 WHERE NOT requiere_informe
   AND tipo_actuacion IN ('PASE_INTERNO','PASE_CONFIRMADO')
   AND descripcion_resumen LIKE '%PARA INFORME%';

-- pase_confirmado sin nulos (la unicidad de pases pendientes depende de él)
UPDATE actuaciones_documento SET pase_confirmado = FALSE WHERE pase_confirmado IS NULL;
ALTER TABLE actuaciones_documento ALTER COLUMN pase_confirmado SET DEFAULT FALSE;
ALTER TABLE actuaciones_documento ALTER COLUMN pase_confirmado SET NOT NULL;

-- ---------------------------------------------------------------------
-- H5: un solo pase pendiente por documento y dependencia receptora
-- (última barrera ante derivaciones duplicadas o simultáneas)
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_pase_pendiente_por_receptora
    ON actuaciones_documento (documento_id, dependencia_receptora_pase_id)
 WHERE tipo_actuacion = 'PASE_INTERNO' AND NOT anulada AND NOT pase_confirmado;

-- ---------------------------------------------------------------------
-- H11: tipos de clave coherentes, fecha de ingreso obligatoria, índices
-- ---------------------------------------------------------------------
ALTER TABLE actuaciones_documento ALTER COLUMN dependencia_emisora_id TYPE INTEGER;
ALTER TABLE actuaciones_documento ALTER COLUMN usuario_id             TYPE INTEGER;
ALTER TABLE documentos            ALTER COLUMN fecha_hora_ingreso     SET NOT NULL;

DROP INDEX IF EXISTS idx_actuaciones_doc;  -- cubierto por idx_actuaciones_documento_doc_fecha
CREATE INDEX IF NOT EXISTS idx_documentos_estado ON documentos (estado_actual);

COMMIT;
