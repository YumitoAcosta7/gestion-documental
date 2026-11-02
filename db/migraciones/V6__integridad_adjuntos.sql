-- =====================================================================
-- Gestión Documental - Migración V6: integridad de los archivos adjuntos
--
-- Hallazgo H10 de docs/auditoria-validacion-qa.md: cada PDF guarda su
-- huella SHA-256 y su tipo, para poder verificar que el archivo en disco
-- no fue alterado ni reemplazado. Los adjuntos anteriores quedan sin
-- hash (NULL) y se informan como "sin huella registrada".
-- Idempotente.
-- =====================================================================

BEGIN;

ALTER TABLE adjuntos_documento
    ADD COLUMN IF NOT EXISTS hash_sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS tipo_mime   VARCHAR(100);

ALTER TABLE adjuntos_documento DROP CONSTRAINT IF EXISTS chk_adjuntos_hash;
ALTER TABLE adjuntos_documento ADD  CONSTRAINT chk_adjuntos_hash
    CHECK (hash_sha256 IS NULL OR hash_sha256 ~ '^[0-9a-f]{64}$');

COMMIT;
