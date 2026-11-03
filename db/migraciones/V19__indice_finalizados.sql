-- =====================================================================
-- Gestión Documental - Migración V19: índice del historial de finalizados
--
-- La pantalla muestra los 50 finalizados más recientes que el usuario puede
-- ver. Con este índice la consulta los recorre ya ordenados por fecha de
-- salida y se detiene al juntar 50, en lugar de ordenar todos los finalizados
-- (medido con 200.000 expedientes en la base de pruebas). Idempotente.
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_documentos_finalizados
    ON documentos (fecha_hora_salida DESC NULLS LAST, id DESC)
 WHERE estado_actual = 'FINALIZADO';
