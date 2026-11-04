-- =====================================================================
-- Gestión Documental - Migración V27: segunda firma para restablecer contraseñas
-- (docs/auditoria-logica-negocio.md, hallazgo A5, caso 3)
--
-- Quien recibe la contraseña temporal puede entrar en nombre de esa cuenta.
-- En las cuentas sensibles, la pide un administrador y la aprueba otro, que no
-- sea el dueño de la cuenta. Tabla nueva: no cambia datos. Idempotente.
-- =====================================================================

CREATE TABLE IF NOT EXISTS solicitudes_contrasena (
    id                BIGSERIAL PRIMARY KEY,
    usuario_id        INTEGER     NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    motivo            TEXT        NOT NULL,
    estado            VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    solicitada_por_id INTEGER     NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    fecha_solicitud   TIMESTAMP   NOT NULL DEFAULT now(),
    resuelta_por_id   INTEGER     REFERENCES usuarios (id) ON DELETE RESTRICT,
    fecha_resolucion  TIMESTAMP,
    motivo_rechazo    TEXT,
    CONSTRAINT chk_contrasena_aprobador_distinto CHECK (estado <> 'APROBADA'
        OR (resuelta_por_id <> solicitada_por_id AND resuelta_por_id <> usuario_id)),
    CONSTRAINT chk_contrasena_resuelta CHECK (estado = 'PENDIENTE' OR (resuelta_por_id IS NOT NULL AND fecha_resolucion IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_contrasena_pendiente ON solicitudes_contrasena (usuario_id) WHERE estado = 'PENDIENTE';
