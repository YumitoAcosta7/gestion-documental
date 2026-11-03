-- =====================================================================
-- Gestión Documental - Migración V21: legajo de la salida
--
-- Unidad de expediente: cuando la respuesta de salida remite informes o
-- memos de las áreas (dictámenes que la Dirección recabó de sus
-- departamentos), queda registrado qué actuaciones componen ese legajo.
-- Así el árbol del expediente muestra la respuesta "cosida" con sus
-- antecedentes. No borra nada. Idempotente.
-- =====================================================================

CREATE TABLE IF NOT EXISTS legajos_salida (
    id                BIGSERIAL PRIMARY KEY,
    documento_id      BIGINT      NOT NULL REFERENCES documentos (id) ON DELETE RESTRICT,
    actuacion_id      BIGINT      NOT NULL REFERENCES actuaciones_documento (id) ON DELETE RESTRICT,
    tipo_remision     VARCHAR(30) NOT NULL CHECK (tipo_remision IN ('NOTA_DIRECCION', 'DIRECTO_INFORME')),
    nro_nota_salida   VARCHAR(50),
    registrado_por_id INTEGER     REFERENCES usuarios (id) ON DELETE RESTRICT,
    fecha_hora        TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_legajo_salida UNIQUE (documento_id, actuacion_id)
);

-- Toda referencia lleva su índice (ver V20): sin él, borrar actuaciones recorre esta tabla entera
CREATE INDEX IF NOT EXISTS idx_legajos_salida_actuacion ON legajos_salida (actuacion_id);
