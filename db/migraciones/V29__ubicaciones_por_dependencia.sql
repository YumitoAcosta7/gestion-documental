-- =====================================================================
-- Gestión Documental - Migración V29: ubicación física por dependencia
--
-- Cada bibliorato pertenece a una dependencia, y cada dependencia guarda su
-- propio papel. Un documento puede estar en la carpeta de la Dirección General
-- (su copia) y en la del Departamento (el original): una ubicación por dependencia.
--
-- Las columnas bibliorato_id, foja_desde y foja_hasta de documentos quedan solo
-- como dato histórico, igual que ubicacion_bibliorato.
-- =====================================================================

CREATE TABLE ubicaciones_documento (
    id              BIGSERIAL PRIMARY KEY,
    documento_id    BIGINT    NOT NULL REFERENCES documentos (id),
    dependencia_id  INTEGER   NOT NULL REFERENCES dependencias (id),
    bibliorato_id   INTEGER   NOT NULL REFERENCES biblioratos (id),
    foja_desde      INTEGER,
    foja_hasta      INTEGER,
    usuario_id      INTEGER   REFERENCES usuarios (id),
    fecha_hora      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ubicacion_documento_dependencia UNIQUE (documento_id, dependencia_id),
    CONSTRAINT chk_ubicacion_fojas CHECK (
        (foja_desde IS NULL AND foja_hasta IS NULL)
        OR (foja_desde > 0 AND foja_hasta >= foja_desde))
);

CREATE INDEX idx_ubicaciones_bibliorato ON ubicaciones_documento (bibliorato_id, foja_desde);

-- La ubicación actual pasa a la dependencia dueña de su bibliorato
INSERT INTO ubicaciones_documento (documento_id, dependencia_id, bibliorato_id, foja_desde, foja_hasta, fecha_hora)
SELECT d.id, b.dependencia_id, d.bibliorato_id, d.foja_desde, d.foja_hasta,
       COALESCE(d.fecha_hora_archivado, CURRENT_TIMESTAMP)
  FROM documentos d
  JOIN biblioratos b ON b.id = d.bibliorato_id;

COMMENT ON COLUMN documentos.bibliorato_id IS 'Histórico desde V29: la ubicación está en ubicaciones_documento';
COMMENT ON COLUMN documentos.foja_desde IS 'Histórico desde V29: la ubicación está en ubicaciones_documento';
COMMENT ON COLUMN documentos.foja_hasta IS 'Histórico desde V29: la ubicación está en ubicaciones_documento';
