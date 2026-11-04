-- =====================================================================
-- Gestión Documental - Migración V24: devolución con acuse
-- (docs/auditoria-logica-negocio.md, hallazgo M1 y decisión D2)
--
-- La devolución del original pasa a ser un pase de trámite de vuelta, que se
-- confirma con acuse como cualquier otro. Esta columna lo distingue. Las
-- devoluciones anteriores siguen como están (tipo_actuacion = 'DEVOLUCION').
-- No borra ni cambia datos. Idempotente.
-- =====================================================================

ALTER TABLE actuaciones_documento ADD COLUMN IF NOT EXISTS es_devolucion BOOLEAN NOT NULL DEFAULT FALSE;
