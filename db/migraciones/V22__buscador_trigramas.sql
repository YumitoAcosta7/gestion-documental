-- =====================================================================
-- Gestión Documental - Migración V22: índices de trigramas del buscador
--
-- El buscador busca "contiene" (ILIKE '%texto%') en el CUTI, el asunto y los
-- números de origen y de salida. Sin índice, cada búsqueda lee todos los
-- expedientes (unos 3 s con 200.000). Los índices de trigramas (pg_trgm)
-- resuelven esas búsquedas con el índice.
--
-- pg_trgm es una extensión "de confianza" (PostgreSQL 13+): la puede instalar
-- el dueño de la base. Si el rol de la aplicación no puede, esta migración no
-- falla: avisa y no crea los índices. En ese caso el DBA ejecuta, como postgres:
--     CREATE EXTENSION pg_trgm;
-- y la próxima versión que la necesite los creará (o se crean a mano con las
-- sentencias de abajo). Idempotente.
-- =====================================================================

DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS pg_trgm;
    EXCEPTION WHEN insufficient_privilege OR feature_not_supported OR undefined_file THEN
        RAISE NOTICE 'No se pudo instalar pg_trgm (%): el buscador funciona sin índices de trigramas. '
                     'El DBA puede ejecutar CREATE EXTENSION pg_trgm como postgres.', SQLERRM;
    END;

    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        CREATE INDEX IF NOT EXISTS idx_documentos_cuti_trgm    ON documentos USING gin (codigo_cuti gin_trgm_ops);
        CREATE INDEX IF NOT EXISTS idx_documentos_asunto_trgm  ON documentos USING gin (asunto gin_trgm_ops);
        CREATE INDEX IF NOT EXISTS idx_documentos_origen_trgm  ON documentos USING gin (numero_documento_origen gin_trgm_ops);
        CREATE INDEX IF NOT EXISTS idx_documentos_salida_trgm  ON documentos USING gin (nro_nota_salida gin_trgm_ops);
        CREATE INDEX IF NOT EXISTS idx_documentos_ubicacion_trgm ON documentos USING gin (ubicacion_bibliorato gin_trgm_ops);
    END IF;
END $$;
