-- =====================================================================
-- Gestión Documental - Migración V12: primer administrador del sistema
--
-- Etapa 2.3 (docs/diseno-fase2-multiarea.md §3.1): las pantallas de
-- administración (usuarios, asignaciones, suplencias, feriados) son del rol
-- ADMIN_SISTEMA, que hasta ahora nadie tenía.
--
-- Crea a Juan Carlos Acosta Zena (juan.acosta), Jefe de la Unidad de Data
-- Center de la DTIC, con dos asignaciones:
--   - RESPONSABLE de la Unidad de Data Center (su jefatura);
--   - ADMIN_SISTEMA con ámbito en toda la municipalidad (Intendencia).
--
-- El usuario se crea SIN contraseña utilizable: password_hash no es un hash
-- BCrypt, así que nadie puede ingresar hasta que el propio usuario la defina
-- con db/herramientas/DefinirContrasena.java. Idempotente.
-- =====================================================================

INSERT INTO usuarios (nombre_completo, username, password_hash, email, dependencia_id, rol, activo)
SELECT 'Juan Carlos Acosta Zena', 'juan.acosta', '!sin-contrasena', NULL, d.id, 'JEFE_UNIDAD', TRUE
  FROM dependencias d
 WHERE d.sigla = 'DTIC-D04-U01'
   AND NOT EXISTS (SELECT 1 FROM usuarios WHERE username = 'juan.acosta');

INSERT INTO asignaciones (usuario_id, rol, dependencia_ambito_id, incluye_subordinadas, acto_administrativo)
SELECT u.id, 'RESPONSABLE', d.id, TRUE, 'Migración V12: Jefe de la Unidad de Data Center'
  FROM usuarios u, dependencias d
 WHERE u.username = 'juan.acosta' AND d.sigla = 'DTIC-D04-U01'
   AND NOT EXISTS (SELECT 1 FROM asignaciones a WHERE a.usuario_id = u.id AND a.rol = 'RESPONSABLE');

INSERT INTO asignaciones (usuario_id, rol, dependencia_ambito_id, incluye_subordinadas, acto_administrativo)
SELECT u.id, 'ADMIN_SISTEMA', d.id, TRUE, 'Migración V12: administrador del sistema'
  FROM usuarios u, dependencias d
 WHERE u.username = 'juan.acosta' AND d.sigla = 'INT'
   AND NOT EXISTS (SELECT 1 FROM asignaciones a WHERE a.usuario_id = u.id AND a.rol = 'ADMIN_SISTEMA');
