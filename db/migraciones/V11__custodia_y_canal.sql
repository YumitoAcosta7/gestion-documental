-- =====================================================================
-- Gestión Documental - Migración V11: custodia, tipos de pase y series
--
-- Etapa 2.2 (docs/diseno-fase2-multiarea.md §4 y §5):
--  1. documentos: dependencia que registró el expediente y dependencia
--     que tiene el original (custodia).
--  2. actuaciones_documento.tipo_pase: TRAMITE (transfiere la custodia al
--     confirmarse), INFORME (espera respuesta) o CONOCIMIENTO (copia).
--  3. series_cuti: correlativo anual del CUTI de cada cabecera.
--  4. reglas_derivacion: atajos habilitados fuera del canal jerárquico.
-- Carga los datos actuales según §9. No borra nada. Idempotente.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Custodia y registro
-- ---------------------------------------------------------------------
ALTER TABLE documentos
    ADD COLUMN IF NOT EXISTS dependencia_registro_id INTEGER REFERENCES dependencias (id),
    ADD COLUMN IF NOT EXISTS dependencia_custodia_id INTEGER REFERENCES dependencias (id);

CREATE INDEX IF NOT EXISTS idx_documentos_custodia ON documentos (dependencia_custodia_id);

-- Registro: la mesa de entrada de quien cargó el ingreso (por su asignación MESA_ENTRADA);
-- si no tenía, su propia dependencia
UPDATE documentos d
   SET dependencia_registro_id = COALESCE(
           (SELECT m.id
              FROM asignaciones a
              JOIN dependencias m ON m.dependencia_padre_id = a.dependencia_ambito_id AND m.es_mesa_entrada
             WHERE a.usuario_id = d.usuario_carga_ingreso_id AND a.rol = 'MESA_ENTRADA' AND a.activa
             ORDER BY (m.nombre LIKE '%Secretaría%') DESC, m.id
             LIMIT 1),
           (SELECT u.dependencia_id FROM usuarios u WHERE u.id = d.usuario_carga_ingreso_id),
           d.dependencia_origen_id)
 WHERE d.dependencia_registro_id IS NULL;

-- ---------------------------------------------------------------------
-- 2. Tipo de pase (reemplaza a requiere_informe, que se conserva por ahora)
-- ---------------------------------------------------------------------
ALTER TABLE actuaciones_documento ADD COLUMN IF NOT EXISTS tipo_pase VARCHAR(20);

ALTER TABLE actuaciones_documento DROP CONSTRAINT IF EXISTS chk_actuaciones_tipo_pase;
ALTER TABLE actuaciones_documento ADD  CONSTRAINT chk_actuaciones_tipo_pase
    CHECK (tipo_pase IS NULL OR tipo_pase IN ('TRAMITE', 'INFORME', 'CONOCIMIENTO'));

UPDATE actuaciones_documento
   SET tipo_pase = CASE WHEN requiere_informe THEN 'INFORME' ELSE 'TRAMITE' END
 WHERE tipo_actuacion IN ('PASE_INTERNO', 'PASE_CONFIRMADO') AND tipo_pase IS NULL;

-- Custodia: la receptora del último pase de trámite confirmado o devolución vigente;
-- si no hubo ninguno, la dependencia que lo registró
UPDATE documentos d
   SET dependencia_custodia_id = COALESCE(
           (SELECT a.dependencia_receptora_pase_id
              FROM actuaciones_documento a
             WHERE a.documento_id = d.id AND NOT a.anulada AND a.dependencia_receptora_pase_id IS NOT NULL
               AND ((a.tipo_actuacion = 'PASE_CONFIRMADO' AND a.tipo_pase = 'TRAMITE') OR a.tipo_actuacion = 'DEVOLUCION')
             ORDER BY a.fecha_hora_actuacion DESC, a.id DESC
             LIMIT 1),
           d.dependencia_registro_id)
 WHERE d.dependencia_custodia_id IS NULL;

-- ---------------------------------------------------------------------
-- 3. Series del CUTI por cabecera
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS series_cuti (
    dependencia_id INTEGER NOT NULL REFERENCES dependencias (id),
    anio           INTEGER NOT NULL,
    ultimo         INTEGER NOT NULL DEFAULT 0 CHECK (ultimo >= 0),
    PRIMARY KEY (dependencia_id, anio)
);

-- ---------------------------------------------------------------------
-- 4. Atajos fuera del canal jerárquico (administrados sin tocar código)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reglas_derivacion (
    id                  SERIAL PRIMARY KEY,
    origen_id           INTEGER NOT NULL REFERENCES dependencias (id),
    destino_id          INTEGER NOT NULL REFERENCES dependencias (id),
    motivo              VARCHAR(200) NOT NULL,
    activa              BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_hora_creacion TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_reglas_derivacion_distintas CHECK (origen_id <> destino_id),
    CONSTRAINT uq_reglas_derivacion UNIQUE (origen_id, destino_id)
);
