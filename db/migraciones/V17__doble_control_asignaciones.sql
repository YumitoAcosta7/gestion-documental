-- =====================================================================
-- Gestión Documental - Migración V17: doble control de asignaciones (C2)
-- (docs/auditoria-arquitectura-seguridad.md)
--
-- Las asignaciones sensibles (ADMIN_SISTEMA, y MESA_ENTRADA o RESPONSABLE
-- sobre una Dirección o superior) quedan PENDIENTES al crearse: inactivas,
-- sin dar permisos, hasta que las apruebe otro administrador, distinto de
-- quien la pidió y del beneficiario. La base lo controla con restricciones,
-- además de la aplicación.
--
-- Las asignaciones existentes quedan APROBADAS. No borra nada. Idempotente.
-- =====================================================================

ALTER TABLE asignaciones ADD COLUMN IF NOT EXISTS estado_aprobacion VARCHAR(20) NOT NULL DEFAULT 'APROBADA';
ALTER TABLE asignaciones ADD COLUMN IF NOT EXISTS aprobada_por_id INTEGER REFERENCES usuarios (id) ON DELETE RESTRICT;
ALTER TABLE asignaciones ADD COLUMN IF NOT EXISTS fecha_hora_aprobacion TIMESTAMP;

ALTER TABLE asignaciones DROP CONSTRAINT IF EXISTS chk_asignaciones_estado_aprobacion;
ALTER TABLE asignaciones ADD CONSTRAINT chk_asignaciones_estado_aprobacion
    CHECK (estado_aprobacion IN ('PENDIENTE', 'APROBADA', 'RECHAZADA'));

-- Solo una asignación aprobada puede dar permisos
ALTER TABLE asignaciones DROP CONSTRAINT IF EXISTS chk_asignaciones_activa_aprobada;
ALTER TABLE asignaciones ADD CONSTRAINT chk_asignaciones_activa_aprobada
    CHECK (NOT activa OR estado_aprobacion = 'APROBADA');

-- Nadie aprueba lo que pidió ni lo que lo beneficia (aprobada_por_id NULL: aprobación del DBA o datos previos)
ALTER TABLE asignaciones DROP CONSTRAINT IF EXISTS chk_asignaciones_aprobador_distinto;
ALTER TABLE asignaciones ADD CONSTRAINT chk_asignaciones_aprobador_distinto
    CHECK (aprobada_por_id IS NULL
           OR (aprobada_por_id <> usuario_id AND (creada_por_id IS NULL OR aprobada_por_id <> creada_por_id)));

CREATE INDEX IF NOT EXISTS idx_asignaciones_pendientes ON asignaciones (fecha_hora_creacion)
    WHERE estado_aprobacion = 'PENDIENTE';
