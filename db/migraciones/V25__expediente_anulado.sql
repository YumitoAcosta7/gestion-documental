-- =====================================================================
-- Gestión Documental - Migración V25: estado ANULADO
-- (docs/auditoria-logica-negocio.md, hallazgo A6)
--
-- Un expediente registrado por error, sin movimientos, se anula con motivo.
-- No se borra y su CUTI no se reutiliza. No cambia datos. Idempotente.
-- =====================================================================

ALTER TABLE documentos DROP CONSTRAINT IF EXISTS chk_documentos_estado;
ALTER TABLE documentos ADD  CONSTRAINT chk_documentos_estado CHECK (estado_actual IN
    ('INGRESADO','EN_REVISION','EN_ESPERA_INFORME','CON_RESPUESTA_TECNICA','PENDIENTE_SALIDA','FINALIZADO','ARCHIVADO','ANULADO'));
