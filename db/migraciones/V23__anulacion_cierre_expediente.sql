-- =====================================================================
-- Gestión Documental - Migración V23: pases sin recibir al finalizar
-- (docs/auditoria-logica-negocio.md, hallazgo M2)
--
-- Un expediente finalizado no admite cambios en sus pases. Los que siguen sin
-- recibir al confirmar el acuse final se anulan con este tipo nuevo; si no,
-- quedarían para siempre en los avisos, sin nadie que pueda resolverlos.
-- No borra ni cambia datos. Idempotente.
-- =====================================================================

ALTER TABLE actuaciones_documento DROP CONSTRAINT IF EXISTS chk_actuaciones_anulacion;
ALTER TABLE actuaciones_documento ADD  CONSTRAINT chk_actuaciones_anulacion CHECK (
       (anulada AND tipo_anulacion IN ('RETIRADO', 'RECHAZADO', 'CIERRE_EXCEPCIONAL', 'CIERRE_EXPEDIENTE') AND motivo_anulacion IS NOT NULL)
    OR (NOT anulada AND tipo_anulacion IS NULL));
