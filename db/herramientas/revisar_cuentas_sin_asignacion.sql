-- =====================================================================
-- Gestión Documental - Cuentas sin asignación (auditoría M12)
--
-- Una cuenta sin asignación vigente ya no puede trabajar. Esta consulta, de
-- SOLO LECTURA, se corre ANTES de instalar esa versión, para que nadie se
-- quede afuera sin aviso.
--
--     sudo -u postgres psql -d gestion_documental -f revisar_cuentas_sin_asignacion.sql
-- =====================================================================

\echo '1. Se quedan sin poder trabajar: activas y sin asignación vigente. Si deben seguir, pedirles una ANTES de instalar.'
SELECT u.username, u.nombre_completo, d.sigla AS dependencia
  FROM usuarios u LEFT JOIN dependencias d ON d.id = u.dependencia_id
 WHERE u.activo
   AND NOT EXISTS (SELECT 1 FROM asignaciones a
                    WHERE a.usuario_id = u.id AND a.activa
                      AND a.vigente_desde <= CURRENT_DATE AND (a.vigente_hasta IS NULL OR a.vigente_hasta >= CURRENT_DATE))
 ORDER BY d.sigla, u.username;

\echo '2. Tienen una solicitud esperando aprobación.'
SELECT u.username, a.rol, d.sigla AS ambito
  FROM asignaciones a JOIN usuarios u ON u.id = a.usuario_id JOIN dependencias d ON d.id = a.dependencia_ambito_id
 WHERE a.estado_aprobacion = 'PENDIENTE'
 ORDER BY u.username;
