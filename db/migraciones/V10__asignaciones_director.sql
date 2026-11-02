-- =====================================================================
-- Gestión Documental - Migración V10: el Director conserva sus acciones
--
-- Con PoliticaAcceso las acciones salen de las asignaciones. El rol
-- RESPONSABLE (V8) no incluye registrar ingresos, salidas ni archivo,
-- que el rol anterior DIRECTOR sí tenía; para no quitárselos, quien era
-- DIRECTOR recibe además MESA_ENTRADA en el mismo ámbito.
-- No borra nada. Idempotente.
-- =====================================================================

INSERT INTO asignaciones (usuario_id, rol, dependencia_ambito_id, incluye_subordinadas, acto_administrativo)
SELECT a.usuario_id, 'MESA_ENTRADA', a.dependencia_ambito_id, TRUE, 'Migración V10: acciones del rol anterior DIRECTOR'
  FROM asignaciones a JOIN usuarios u ON u.id = a.usuario_id
 WHERE u.rol = 'DIRECTOR' AND a.rol = 'RESPONSABLE' AND a.activa
   AND NOT EXISTS (SELECT 1 FROM asignaciones x
                    WHERE x.usuario_id = a.usuario_id AND x.rol = 'MESA_ENTRADA'
                      AND x.dependencia_ambito_id = a.dependencia_ambito_id);

