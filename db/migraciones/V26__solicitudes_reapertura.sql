-- =====================================================================
-- Gestión Documental - Migración V26: reapertura de expedientes finalizados
-- (docs/auditoria-logica-negocio.md, hallazgo A6 y decisión D3)
--
-- Un expediente finalizado se reabre solo con la aprobación de otra persona.
-- La base garantiza que nadie apruebe su propia solicitud y que haya una sola
-- pendiente por expediente. Tabla nueva: no cambia datos. Idempotente.
-- =====================================================================

CREATE TABLE IF NOT EXISTS solicitudes_reapertura (
    id                BIGSERIAL PRIMARY KEY,
    documento_id      BIGINT      NOT NULL REFERENCES documentos (id) ON DELETE RESTRICT,
    motivo            TEXT        NOT NULL,
    estado            VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    solicitada_por_id INTEGER     NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    fecha_solicitud   TIMESTAMP   NOT NULL DEFAULT now(),
    resuelta_por_id   INTEGER     REFERENCES usuarios (id) ON DELETE RESTRICT,
    fecha_resolucion  TIMESTAMP,
    motivo_rechazo    TEXT,
    CONSTRAINT chk_reapertura_aprobador_distinto CHECK (estado <> 'APROBADA' OR resuelta_por_id <> solicitada_por_id),
    CONSTRAINT chk_reapertura_resuelta CHECK (estado = 'PENDIENTE' OR (resuelta_por_id IS NOT NULL AND fecha_resolucion IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_reapertura_pendiente ON solicitudes_reapertura (documento_id) WHERE estado = 'PENDIENTE';
