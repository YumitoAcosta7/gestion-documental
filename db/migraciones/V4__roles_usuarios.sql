-- =====================================================================
-- Gestión Documental - Migración V4: roles de usuarios
--
-- Reasigna los roles heredados de una versión anterior, que el sistema
-- no reconoce (esos usuarios no podían iniciar sesión), y cierra el
-- dominio de la columna rol (hallazgo H7 pendiente de V3).
-- Decisión: ambos pasan a SECRETARIA_ADMIN (autorizado por la Dirección
-- de TIC el 02/11/2026).
-- Idempotente.
-- =====================================================================

BEGIN;

UPDATE usuarios SET rol = 'SECRETARIA_ADMIN'
 WHERE username IN ('zlobo', 'griveros')
   AND rol IN ('JEFA_SECRETARIA', 'OPERADOR_MESA_ENTRADA');

ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS chk_usuarios_rol;
ALTER TABLE usuarios ADD  CONSTRAINT chk_usuarios_rol
    CHECK (rol IN ('DIRECTOR', 'SECRETARIA_ADMIN', 'JEFE_DEPTO', 'JEFE_UNIDAD'));

COMMIT;
