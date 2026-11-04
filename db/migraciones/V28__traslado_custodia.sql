-- =====================================================================
-- Gestión Documental - Migración V28: traslado de custodia
-- (docs/auditoria-logica-negocio.md, hallazgo M7)
--
-- Cuando una dependencia queda sin nadie que pueda actuar por ella, sus originales
-- se trasladan con motivo, acto administrativo y la aprobación de un segundo
-- administrador. La base garantiza que nadie apruebe su propia solicitud y que haya
-- una sola pendiente por expediente. Tabla nueva: no cambia datos. Idempotente.
-- =====================================================================

CREATE TABLE IF NOT EXISTS solicitudes_traslado_custodia (
    id                     BIGSERIAL PRIMARY KEY,
    documento_id           BIGINT       NOT NULL REFERENCES documentos (id) ON DELETE RESTRICT,
    dependencia_origen_id  INTEGER      NOT NULL REFERENCES dependencias (id) ON DELETE RESTRICT,
    dependencia_destino_id INTEGER      NOT NULL REFERENCES dependencias (id) ON DELETE RESTRICT,
    motivo                 TEXT         NOT NULL,
    acto_administrativo    VARCHAR(150) NOT NULL,
    estado                 VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    solicitada_por_id      INTEGER      NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    fecha_solicitud        TIMESTAMP    NOT NULL DEFAULT now(),
    resuelta_por_id        INTEGER      REFERENCES usuarios (id) ON DELETE RESTRICT,
    fecha_resolucion       TIMESTAMP,
    motivo_rechazo         TEXT,
    CONSTRAINT chk_traslado_destino_distinto CHECK (dependencia_destino_id <> dependencia_origen_id),
    CONSTRAINT chk_traslado_aprobador_distinto CHECK (estado <> 'APROBADA' OR resuelta_por_id <> solicitada_por_id),
    CONSTRAINT chk_traslado_resuelta CHECK (estado = 'PENDIENTE' OR (resuelta_por_id IS NOT NULL AND fecha_resolucion IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_traslado_pendiente ON solicitudes_traslado_custodia (documento_id) WHERE estado = 'PENDIENTE';
