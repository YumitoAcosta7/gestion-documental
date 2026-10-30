-- Nombres de las claves foráneas hacia documentos
SELECT conname, conrelid::regclass, pg_get_constraintdef(oid) FROM pg_constraint
WHERE contype = 'f' AND confrelid = 'documentos'::regclass ORDER BY 2;

-- Estados fuera del dominio (deben ser 0 filas)
SELECT estado_actual, COUNT(*) FROM documentos
WHERE estado_actual NOT IN ('INGRESADO','EN_REVISION','EN_ESPERA_INFORME','CON_RESPUESTA_TECNICA','PENDIENTE_SALIDA','FINALIZADO','ARCHIVADO')
GROUP BY 1;

-- Prioridades fuera del dominio (deben ser 0 filas)
SELECT prioridad, COUNT(*) FROM documentos WHERE prioridad NOT IN ('NORMAL','IMPORTANTE','URGENTE') GROUP BY 1;

-- Tipos de actuación fuera del dominio (deben ser 0 filas)
SELECT tipo_actuacion, COUNT(*) FROM actuaciones_documento
WHERE tipo_actuacion NOT IN ('PASE_INTERNO','PASE_CONFIRMADO','DEVOLUCION','INFORME_TECNICO','MEMO','ACTA_ENTREGA','PROVIDENCIA','CIERRE_EXCEPCIONAL')
GROUP BY 1;

-- Anulaciones incoherentes (deben ser 0 filas)
SELECT id, anulada, tipo_anulacion, motivo_anulacion IS NULL AS sin_motivo FROM actuaciones_documento
WHERE NOT ((anulada AND tipo_anulacion IN ('RETIRADO','RECHAZADO') AND motivo_anulacion IS NOT NULL)
        OR (NOT anulada AND tipo_anulacion IS NULL));

-- Roles fuera del dominio (deben ser 0 filas)
SELECT rol, COUNT(*) FROM usuarios WHERE rol NOT IN ('DIRECTOR','SECRETARIA_ADMIN','JEFE_DEPTO','JEFE_UNIDAD') GROUP BY 1;

-- Documentos sin fecha de ingreso (deben ser 0 filas)
SELECT id, codigo_cuti FROM documentos WHERE fecha_hora_ingreso IS NULL;

-- IDs fuera del rango INTEGER (deben ser 0 filas)
SELECT id FROM actuaciones_documento WHERE dependencia_emisora_id > 2147483647 OR usuario_id > 2147483647;

-- Pases pendientes duplicados (deben ser 0 filas)
SELECT documento_id, dependencia_receptora_pase_id, COUNT(*) FROM actuaciones_documento
WHERE tipo_actuacion = 'PASE_INTERNO' AND NOT anulada AND NOT COALESCE(pase_confirmado, FALSE)
GROUP BY 1, 2 HAVING COUNT(*) > 1;

-- Pases que se marcarán como "requiere informe"
SELECT COUNT(*) FROM actuaciones_documento
WHERE tipo_actuacion IN ('PASE_INTERNO','PASE_CONFIRMADO') AND descripcion_resumen LIKE '%PARA INFORME%';

-- pase_confirmado nulo en pases (se normalizará a FALSE)
SELECT COUNT(*) FROM actuaciones_documento WHERE pase_confirmado IS NULL
