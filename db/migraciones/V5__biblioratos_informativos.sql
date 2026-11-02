-- =====================================================================
-- Gestión Documental - Migración V5: documentos informativos y biblioratos
--
-- Hallazgos H3 y H4 de docs/auditoria-validacion-qa.md:
--   * Categoría del tipo documental (TRAMITE / INFORMATIVO).
--   * Catálogo de biblioratos con su ubicación física.
--   * Ubicación de cada documento: bibliorato + fojas.
-- El estado ARCHIVADO ya está permitido por chk_documentos_estado (V3).
-- Idempotente.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- Categoría del tipo documental
-- ---------------------------------------------------------------------
ALTER TABLE tipos_documentos
    ADD COLUMN IF NOT EXISTS categoria           VARCHAR(20) NOT NULL DEFAULT 'TRAMITE',
    ADD COLUMN IF NOT EXISTS requiere_bibliorato BOOLEAN     NOT NULL DEFAULT FALSE;

ALTER TABLE tipos_documentos DROP CONSTRAINT IF EXISTS chk_tipos_documentos_categoria;
ALTER TABLE tipos_documentos ADD  CONSTRAINT chk_tipos_documentos_categoria
    CHECK (categoria IN ('TRAMITE', 'INFORMATIVO'));

-- Las circulares son informativas: se archivan, no se tramitan
UPDATE tipos_documentos SET categoria = 'INFORMATIVO', requiere_bibliorato = TRUE
 WHERE nombre = 'Circular' AND categoria <> 'INFORMATIVO';

-- ---------------------------------------------------------------------
-- Catálogo de biblioratos (ubicación física)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biblioratos (
    id               SERIAL       PRIMARY KEY,
    codigo           VARCHAR(30)  NOT NULL UNIQUE,   -- Ej: BIB-DTIC-2026-001
    descripcion      VARCHAR(150) NOT NULL,          -- Ej: Circulares recibidas 2026 - Tomo 1
    dependencia_id   INTEGER      NOT NULL REFERENCES dependencias (id),
    anio             INTEGER      NOT NULL CHECK (anio BETWEEN 2000 AND 2100),
    ubicacion_fisica VARCHAR(150) NOT NULL,          -- Ej: Archivo DTIC · Armario 2 · Estante 3
    capacidad_fojas  INTEGER      CHECK (capacidad_fojas > 0),
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_hora_alta  TIMESTAMP    NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Ubicación de cada documento
-- (documentos.ubicacion_bibliorato, texto libre, queda solo como histórico)
-- ---------------------------------------------------------------------
ALTER TABLE documentos
    ADD COLUMN IF NOT EXISTS bibliorato_id INTEGER REFERENCES biblioratos (id),
    ADD COLUMN IF NOT EXISTS foja_desde    INTEGER,
    ADD COLUMN IF NOT EXISTS foja_hasta    INTEGER;

ALTER TABLE documentos DROP CONSTRAINT IF EXISTS chk_documentos_fojas;
ALTER TABLE documentos ADD  CONSTRAINT chk_documentos_fojas CHECK (
        (foja_desde IS NULL AND foja_hasta IS NULL)
     OR (bibliorato_id IS NOT NULL AND foja_desde > 0 AND foja_hasta >= foja_desde));

CREATE INDEX IF NOT EXISTS idx_documentos_bibliorato ON documentos (bibliorato_id, foja_desde);

COMMIT;
