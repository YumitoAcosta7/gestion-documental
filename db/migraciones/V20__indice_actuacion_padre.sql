-- =====================================================================
-- Gestión Documental - Migración V20: índice de actuacion_padre_id
--
-- La respuesta a un pedido de informe apunta a su pase (actuacion_padre_id).
-- Sin índice, cada borrado o actualización de una actuación obliga a
-- PostgreSQL a recorrer toda la tabla para comprobar esa referencia: borrar
-- el millón de actuaciones de la prueba de volumen no terminó en 13 minutos.
-- Idempotente.
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_actuaciones_padre ON actuaciones_documento (actuacion_padre_id)
    WHERE actuacion_padre_id IS NOT NULL;
