#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Carga de volumen en la base de PRUEBAS (auditoría §3.9)
#
# Genera expedientes y actuaciones sintéticos para medir las bandejas, el
# buscador y los contadores con un volumen parecido al de varios años:
#   - 200.000 expedientes (CUTI "VOL-0000001"…, asunto "VOLUMEN SINTÉTICO …"),
#     repartidos al azar entre todas las dependencias activas;
#   - 5 actuaciones por expediente (1.000.000): pases confirmados, memos,
#     informes y, en parte de los activos, un pase esperando acuse.
#
# Se ejecuta en el servidor 192.168.129.73, como postgres:
#
#     cd /tmp && sudo -u postgres bash cargar_volumen.sh            # carga
#     cd /tmp && sudo -u postgres bash cargar_volumen.sh --borrar   # quita todo lo sintético
#
# Solo toca la base gestion_documental_pruebas y solo los expedientes "VOL-…".
# Tarda unos minutos. Se puede borrar y volver a cargar.
# =====================================================================
set -euo pipefail

BASE_PRUEBAS="gestion_documental_pruebas"
EXPEDIENTES="${EXPEDIENTES:-200000}"

if [ "$(id -un)" != "postgres" ]; then
    echo "Ejecútelo como postgres:  sudo -u postgres bash $0" >&2
    exit 1
fi
if ! [[ "$EXPEDIENTES" =~ ^[0-9]+$ ]]; then
    echo "EXPEDIENTES debe ser un número." >&2
    exit 1
fi

if [ "${1:-}" = "--borrar" ]; then
    psql -X -v ON_ERROR_STOP=1 -d "$BASE_PRUEBAS" <<'SQL'
\timing on
DELETE FROM actuaciones_documento WHERE documento_id IN (SELECT id FROM documentos WHERE codigo_cuti LIKE 'VOL-%');
DELETE FROM documentos WHERE codigo_cuti LIKE 'VOL-%';
ANALYZE documentos;
ANALYZE actuaciones_documento;
SQL
    echo "Datos sintéticos borrados."
    exit 0
fi

if psql -X -tA -d "$BASE_PRUEBAS" -c "SELECT 1 FROM documentos WHERE codigo_cuti LIKE 'VOL-%' LIMIT 1" | grep -q 1; then
    echo "Ya hay datos sintéticos. Bórrelos primero:  sudo -u postgres bash $0 --borrar" >&2
    exit 1
fi

echo "Cargando $EXPEDIENTES expedientes y $((EXPEDIENTES * 5)) actuaciones en $BASE_PRUEBAS…"
psql -X -v ON_ERROR_STOP=1 -d "$BASE_PRUEBAS" -v expedientes="$EXPEDIENTES" <<'SQL'
\timing on
BEGIN;

CREATE TEMP TABLE azar AS
SELECT (SELECT array_agg(id) FROM dependencias WHERE activo)          AS deps,
       (SELECT array_agg(id) FROM tipos_documentos WHERE activo)     AS tipos,
       (SELECT array_agg(id) FROM usuarios)                          AS usuarios;

-- Expedientes: 60 % finalizados, el resto repartido entre los estados activos; 10 % urgentes
INSERT INTO documentos (codigo_cuti, numero_documento_origen, prioridad, tipo_documento_id,
                        dependencia_origen_id, dependencia_registro_id, dependencia_custodia_id,
                        asunto, estado_actual, fecha_hora_ingreso, usuario_carga_ingreso_id)
SELECT 'VOL-' || lpad(g::text, 7, '0'),
       g || '/2026',
       CASE WHEN random() < 0.1 THEN 'URGENTE' ELSE 'NORMAL' END,
       z.tipos[1 + floor(random() * array_length(z.tipos, 1))::int],
       z.deps[1 + floor(random() * array_length(z.deps, 1))::int],
       z.deps[1 + floor(random() * array_length(z.deps, 1))::int],
       z.deps[1 + floor(random() * array_length(z.deps, 1))::int],
       'VOLUMEN SINTÉTICO ' || g || ': solicitud de prueba para medir bandejas y buscador',
       (ARRAY['FINALIZADO','FINALIZADO','FINALIZADO','FINALIZADO','FINALIZADO','FINALIZADO',
              'EN_REVISION','INGRESADO','EN_ESPERA_INFORME','PENDIENTE_SALIDA'])[1 + floor(random() * 10)::int],
       now() - (random() * interval '730 days'),
       z.usuarios[1 + floor(random() * array_length(z.usuarios, 1))::int]
  FROM generate_series(1, :expedientes) g, azar z;

-- Actuaciones: 3 pases de trámite confirmados, 1 memo y 1 informe técnico o, en el 30 % de los activos,
-- un pase de trámite esperando acuse (como mucho uno por expediente y receptora)
INSERT INTO actuaciones_documento (documento_id, dependencia_emisora_id, tipo_actuacion, numero_actuacion,
                                   descripcion_resumen, fecha_hora_actuacion, usuario_id, pase_confirmado,
                                   requiere_informe, tipo_pase, dependencia_receptora_pase_id, anulada)
SELECT d.id,
       z.deps[1 + floor(random() * array_length(z.deps, 1))::int],
       t.tipo,
       n || '/2026',
       'VOLUMEN SINTÉTICO: actuación ' || n,
       d.fecha_hora_ingreso + n * interval '1 day',
       z.usuarios[1 + floor(random() * array_length(z.usuarios, 1))::int],
       t.tipo = 'PASE_CONFIRMADO',
       FALSE,
       CASE WHEN t.tipo IN ('PASE_CONFIRMADO', 'PASE_INTERNO') THEN 'TRAMITE' END,
       CASE WHEN t.tipo IN ('PASE_CONFIRMADO', 'PASE_INTERNO')
            THEN z.deps[1 + floor(random() * array_length(z.deps, 1))::int] END,
       FALSE
  FROM documentos d
 CROSS JOIN azar z
 CROSS JOIN generate_series(1, 5) n
 CROSS JOIN LATERAL (SELECT CASE
        WHEN n <= 3 THEN 'PASE_CONFIRMADO'
        WHEN n = 4 THEN 'MEMO'
        WHEN d.estado_actual NOT IN ('FINALIZADO', 'ARCHIVADO') AND random() < 0.3 THEN 'PASE_INTERNO'
        ELSE 'INFORME_TECNICO' END AS tipo) t
 WHERE d.codigo_cuti LIKE 'VOL-%';

COMMIT;
ANALYZE documentos;
ANALYZE actuaciones_documento;
SELECT count(*) AS expedientes_sinteticos FROM documentos WHERE codigo_cuti LIKE 'VOL-%';
SQL
echo "Listo. Para quitar estos datos:  sudo -u postgres bash $0 --borrar"
