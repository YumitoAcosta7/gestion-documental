-- =====================================================================
-- Gestión Documental - Expedientes en PENDIENTE_SALIDA con informes sin responder
--
-- Antes de la corrección del conteo de informes (auditoría A4, y la del
-- 03/11/2026 que cose cada respuesta a su pedido), un expediente podía pasar
-- a PENDIENTE_SALIDA aunque faltara alguna respuesta. Esta consulta, de SOLO
-- LECTURA, lista los que tienen pedidos de informe vigentes sin una
-- respuesta del área receptora posterior al pedido, para revisarlos a mano.
--
--     sudo -u postgres psql -d gestion_documental -f revisar_pendientes_salida.sql
--
-- Para cada fila, la dependencia con el original decide: pedir de nuevo el
-- informe (el expediente vuelve a esperarlo) o dejar constancia con un
-- cierre excepcional justificado.
-- =====================================================================

WITH pedidos AS (
    SELECT a.id, a.documento_id, a.fecha_hora_actuacion, a.dependencia_emisora_id,
           -- El área que responde: la receptora, o su superior si la receptora es su mesa de entrada
           CASE WHEN r.es_mesa_entrada AND r.dependencia_padre_id IS NOT NULL THEN r.dependencia_padre_id ELSE r.id END AS area_id
      FROM actuaciones_documento a
      JOIN dependencias r ON r.id = a.dependencia_receptora_pase_id
     WHERE a.tipo_actuacion IN ('PASE_INTERNO', 'PASE_CONFIRMADO') AND a.requiere_informe AND NOT a.anulada
)
SELECT d.codigo_cuti, d.asunto, emi.nombre AS pidio, area.nombre AS a_quien, p.fecha_hora_actuacion AS pedido_el,
       cus.nombre AS tiene_el_original
  FROM documentos d
  JOIN pedidos p ON p.documento_id = d.id
  JOIN dependencias area ON area.id = p.area_id
  JOIN dependencias emi ON emi.id = p.dependencia_emisora_id
  LEFT JOIN dependencias cus ON cus.id = d.dependencia_custodia_id
 WHERE d.estado_actual = 'PENDIENTE_SALIDA'
   AND NOT EXISTS (
        SELECT 1 FROM actuaciones_documento resp
          JOIN dependencias e ON e.id = resp.dependencia_emisora_id
         WHERE resp.documento_id = d.id AND NOT resp.anulada
           AND resp.tipo_actuacion IN ('INFORME_TECNICO', 'MEMO')
           AND resp.fecha_hora_actuacion >= p.fecha_hora_actuacion
           AND (resp.actuacion_padre_id = p.id
                OR (resp.actuacion_padre_id IS NULL AND e.ruta LIKE area.ruta || '%')))
 ORDER BY d.codigo_cuti, p.fecha_hora_actuacion;
