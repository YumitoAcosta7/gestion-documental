-- =====================================================================
-- Gestión Documental - Cuentas en mesas de entrada (auditoría A5)
--
-- En una mesa de entrada, pertenecer a la dependencia ya no da acceso: hace
-- falta una asignación aprobada. Esta consulta, de SOLO LECTURA, se corre
-- ANTES de instalar esa versión, para que nadie se quede sin trabajar.
--
--     sudo -u postgres psql -d gestion_documental -f revisar_cuentas_mesa.sql
-- =====================================================================

\echo '1. Se quedan sin acceso: están en una mesa y no tienen asignación vigente. Pedirles una ANTES de instalar.'
SELECT u.username, u.nombre_completo, d.sigla AS mesa
  FROM usuarios u JOIN dependencias d ON d.id = u.dependencia_id
 WHERE u.activo AND d.es_mesa_entrada
   AND NOT EXISTS (SELECT 1 FROM asignaciones a
                    WHERE a.usuario_id = u.id AND a.activa AND a.rol NOT IN ('ADMIN_SISTEMA')
                      AND a.vigente_desde <= CURRENT_DATE AND (a.vigente_hasta IS NULL OR a.vigente_hasta >= CURRENT_DATE))
 ORDER BY d.sigla, u.username;

\echo '2. Para revisar: asignaciones de trámite en una mesa que nadie más aprobó. Siguen vigentes; decidir si corresponden.'
SELECT u.username, a.rol, d.sigla AS mesa, a.vigente_desde, a.vigente_hasta, c.username AS creada_por
  FROM asignaciones a
  JOIN usuarios u ON u.id = a.usuario_id
  JOIN dependencias d ON d.id = a.dependencia_ambito_id
  LEFT JOIN usuarios c ON c.id = a.creada_por_id
 WHERE d.es_mesa_entrada AND a.activa AND a.rol <> 'CONSULTA' AND a.aprobada_por_id IS NULL
   AND (a.vigente_hasta IS NULL OR a.vigente_hasta >= CURRENT_DATE)
 ORDER BY d.sigla, u.username;
