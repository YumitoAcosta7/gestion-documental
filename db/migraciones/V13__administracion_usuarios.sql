-- =====================================================================
-- Gestión Documental - Migración V13: administración de usuarios
--
-- Etapa 2.3 (docs/diseno-fase2-multiarea.md §6.1 y §6.3):
--  1. usuarios.debe_cambiar_contrasena: tras un alta o un reseteo, el
--     usuario entra con una contraseña temporal y debe cambiarla.
--  2. usuarios.rol deja de ser obligatorio: los permisos salen de las
--     asignaciones (V8, PoliticaAcceso); los usuarios nuevos no lo llevan.
--  3. auditoria_sistema: quién creó, modificó o dio de baja usuarios y
--     asignaciones, y cuándo. Solo se inserta: nunca se modifica ni borra.
-- No borra nada. Idempotente.
-- =====================================================================

ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS debe_cambiar_contrasena BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE usuarios ALTER COLUMN rol DROP NOT NULL;
ALTER TABLE usuarios ALTER COLUMN rol DROP DEFAULT;

CREATE TABLE IF NOT EXISTS auditoria_sistema (
    id              BIGSERIAL PRIMARY KEY,
    fecha_hora      TIMESTAMP NOT NULL DEFAULT now(),
    usuario_id      INTEGER NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT, -- quién lo hizo
    accion          VARCHAR(40) NOT NULL,
    entidad         VARCHAR(30) NOT NULL,   -- USUARIO, ASIGNACION
    entidad_id      BIGINT,
    detalle         TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auditoria_sistema_fecha ON auditoria_sistema (fecha_hora DESC);

-- La bitácora no se corrige ni se borra
CREATE OR REPLACE FUNCTION fn_auditoria_sistema_inmutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La auditoría del sistema no se puede modificar ni borrar';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_auditoria_sistema_inmutable ON auditoria_sistema;
CREATE TRIGGER trg_auditoria_sistema_inmutable
    BEFORE UPDATE OR DELETE ON auditoria_sistema
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_sistema_inmutable();
