-- =====================================================================
-- Gestión Documental - Migración V7: limpieza del esquema
--
-- Elimina estructuras heredadas de una versión anterior que la aplicación
-- no usa: tres tablas vacías y una columna sin mapear. Si alguna tuviera
-- datos, la migración se cancela sin cambios.
-- Idempotente.
-- =====================================================================

DO $$
DECLARE
    filas BIGINT;
BEGIN
    IF to_regclass('public.solicitudes_informes') IS NOT NULL THEN
        EXECUTE 'SELECT COUNT(*) FROM solicitudes_informes' INTO filas;
        IF filas > 0 THEN RAISE EXCEPTION 'solicitudes_informes tiene % filas: revisar antes de eliminar', filas; END IF;
    END IF;
    IF to_regclass('public.historial_estados') IS NOT NULL THEN
        EXECUTE 'SELECT COUNT(*) FROM historial_estados' INTO filas;
        IF filas > 0 THEN RAISE EXCEPTION 'historial_estados tiene % filas: revisar antes de eliminar', filas; END IF;
    END IF;
    IF to_regclass('public.movimientos_documento') IS NOT NULL THEN
        EXECUTE 'SELECT COUNT(*) FROM movimientos_documento' INTO filas;
        IF filas > 0 THEN RAISE EXCEPTION 'movimientos_documento tiene % filas: revisar antes de eliminar', filas; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'documentos' AND column_name = 'usuario_registra_salida_id') THEN
        EXECUTE 'SELECT COUNT(*) FROM documentos WHERE usuario_registra_salida_id IS NOT NULL' INTO filas;
        IF filas > 0 THEN RAISE EXCEPTION 'documentos.usuario_registra_salida_id tiene % valores: revisar antes de eliminar', filas; END IF;
    END IF;
END $$;

DROP TABLE IF EXISTS solicitudes_informes;
DROP TABLE IF EXISTS historial_estados;
DROP TABLE IF EXISTS movimientos_documento;
ALTER TABLE documentos DROP COLUMN IF EXISTS usuario_registra_salida_id;

