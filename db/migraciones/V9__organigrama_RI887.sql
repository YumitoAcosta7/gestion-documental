-- =====================================================================
-- Gestión Documental - Migración V9: carga del organigrama completo
--
-- Res. N° 887/08-07-2026. GENERADA por db/organigrama/ImportarOrganigrama.java
-- desde organigrama_RI887_2026.txt: no editar a mano; se corrige el .txt
-- y se regenera. Requiere V8. Actualiza por id las dependencias ya
-- cargadas (conservan su historial) y da de alta las nuevas, ubicando
-- a cada superior por su sigla. Idempotente.
-- =====================================================================

BEGIN;

-- Los ids actuales se cargaron a mano (1131, 1455...): la secuencia queda detrás del máximo
SELECT setval('dependencias_id_seq', (SELECT MAX(id) FROM dependencias));

-- Dependencias ya cargadas
UPDATE dependencias SET nombre = 'Intendencia Municipal', sigla = 'INT', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Intendencia'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1;
UPDATE dependencias SET nombre = 'Dirección General de Administración y Finanzas', sigla = 'DGAF', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección General'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 100;
UPDATE dependencias SET nombre = 'Unidad de Secretaría Administrativa', sigla = 'DGAF-SA', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = true, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 101;
UPDATE dependencias SET nombre = 'Unidad Revisora de Documentos', sigla = 'DGAF-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 102;
UPDATE dependencias SET nombre = 'Unidad de Rendición de Cuentas', sigla = 'DGAF-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 103;
UPDATE dependencias SET nombre = 'Unidad Operativa de Contrataciones (UOC N° 3)', sigla = 'DGAF-U03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 104;
UPDATE dependencias SET nombre = 'Unidad Operativa de Contrataciones (UOC N° 5)', sigla = 'DGAF-U04', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 105;
UPDATE dependencias SET nombre = 'Dirección de Recaudaciones', sigla = 'DREC', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 110;
UPDATE dependencias SET nombre = 'Unidad de Secretaría Administrativa', sigla = 'DREC-SA', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = true, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 111;
UPDATE dependencias SET nombre = 'Unidad de Control Interno', sigla = 'DREC-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 112;
UPDATE dependencias SET nombre = 'Departamento de Atención al Contribuyente', sigla = 'DREC-D01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 113;
UPDATE dependencias SET nombre = 'Unidad de Gestión Tributaria', sigla = 'DREC-D01-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1131;
UPDATE dependencias SET nombre = 'Unidad de Créditos y Prescripción', sigla = 'DREC-D01-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1132;
UPDATE dependencias SET nombre = 'Unidad de Exoneraciones', sigla = 'DREC-D01-U03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1133;
UPDATE dependencias SET nombre = 'Unidad de Facturación', sigla = 'DREC-D01-U04', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1134;
UPDATE dependencias SET nombre = 'Unidad de Constancias y Certificados', sigla = 'DREC-D01-U05', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1135;
UPDATE dependencias SET nombre = 'Departamento de Liquidaciones', sigla = 'DREC-D02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 114;
UPDATE dependencias SET nombre = 'Unidad de Inmuebles', sigla = 'DREC-D02-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1141;
UPDATE dependencias SET nombre = 'Unidad de Liquidación de Necrópolis', sigla = 'DREC-D02-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1142;
UPDATE dependencias SET nombre = 'Unidad de Ingresos Varios', sigla = 'DREC-D02-U03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1143;
UPDATE dependencias SET nombre = 'Unidad de Desinfestación y Salubridad', sigla = 'DREC-D02-U04', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1144;
UPDATE dependencias SET nombre = 'Departamento de Patentes y Actividades', sigla = 'DREC-D03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 115;
UPDATE dependencias SET nombre = 'Unidad de Patentes', sigla = 'DREC-D03-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1151;
UPDATE dependencias SET nombre = 'Unidad de Servicios Especiales', sigla = 'DREC-D03-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1152;
UPDATE dependencias SET nombre = 'Unidad de Patentes de Rodados y Licencias de Conducir', sigla = 'DREC-D03-U03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1153;
UPDATE dependencias SET nombre = 'Departamento de Cobranzas', sigla = 'DREC-D04', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 116;
UPDATE dependencias SET nombre = 'Unidad de Apremios', sigla = 'DREC-D04-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1161;
UPDATE dependencias SET nombre = 'Unidad de Pagarés', sigla = 'DREC-D04-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1162;
UPDATE dependencias SET nombre = 'Departamento de Grandes Contribuyentes', sigla = 'DREC-D05', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 117;
UPDATE dependencias SET nombre = 'Unidad de Atención Personalizada', sigla = 'DREC-D05-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1171;
UPDATE dependencias SET nombre = 'Unidad de Gestión de Cobro', sigla = 'DREC-D05-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1172;
UPDATE dependencias SET nombre = 'Unidad de Call Center Tributario', sigla = 'DREC-D05-U03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1173;
UPDATE dependencias SET nombre = 'Departamento de Ingresos', sigla = 'DREC-D06', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 118;
UPDATE dependencias SET nombre = 'Unidad de Valores', sigla = 'DREC-D06-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1181;
UPDATE dependencias SET nombre = 'Unidad de Cajas', sigla = 'DREC-D06-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1182;
UPDATE dependencias SET nombre = 'Departamento de Centralización de Recaudaciones Externa', sigla = 'DREC-D07', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 119;
UPDATE dependencias SET nombre = 'Unidad de Entes Externos', sigla = 'DREC-D07-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1191;
UPDATE dependencias SET nombre = 'Unidad de Documentación de Pagos', sigla = 'DREC-D07-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1192;
UPDATE dependencias SET nombre = 'Dirección de Adquisiciones', sigla = 'DADQ', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 120;
UPDATE dependencias SET nombre = 'Unidad de Secretaría Administrativa', sigla = 'DADQ-SA', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = true, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 121;
UPDATE dependencias SET nombre = 'Departamento de Planeamiento y Control', sigla = 'DADQ-D01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 122;
UPDATE dependencias SET nombre = 'Departamento de Licitaciones de Menor Cuantía', sigla = 'DADQ-D02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 123;
UPDATE dependencias SET nombre = 'Departamento de Licitaciones Públicas', sigla = 'DADQ-D03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 124;
UPDATE dependencias SET nombre = 'Departamento de Contratos', sigla = 'DADQ-D04', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 125;
UPDATE dependencias SET nombre = 'Dirección de Servicios Administrativos', sigla = 'DSADM', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 130;
UPDATE dependencias SET nombre = 'Unidad de Secretaría Administrativa', sigla = 'DSADM-SA', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = true, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 131;
UPDATE dependencias SET nombre = 'Departamento de Bienes Patrimoniales', sigla = 'DSADM-D01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 136;
UPDATE dependencias SET nombre = 'Unidad de Inventario', sigla = 'DSADM-D01-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1361;
UPDATE dependencias SET nombre = 'Unidad de Depósito de Excedentes', sigla = 'DSADM-D01-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1362;
UPDATE dependencias SET nombre = 'Departamento de Servicios Generales', sigla = 'DSADM-D02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 137;
UPDATE dependencias SET nombre = 'Unidad de Telecomunicaciones', sigla = 'DSADM-D02-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1371;
UPDATE dependencias SET nombre = 'Unidad de Mantenimiento Electromecánico', sigla = 'DSADM-D02-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1372;
UPDATE dependencias SET nombre = 'Departamento de Transporte', sigla = 'DSADM-D03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 138;
UPDATE dependencias SET nombre = 'Unidad de Almacenes', sigla = 'DSADM-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 132;
UPDATE dependencias SET nombre = 'Unidad de Combustibles', sigla = 'DSADM-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 133;
UPDATE dependencias SET nombre = 'Unidad de Mantenimiento Civil', sigla = 'DSADM-U03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 134;
UPDATE dependencias SET nombre = 'Unidad de Imprenta', sigla = 'DSADM-U04', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 135;
UPDATE dependencias SET nombre = 'Dirección de Hacienda', sigla = 'DHAC', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 140;
UPDATE dependencias SET nombre = 'Unidad de Secretaría Administrativa', sigla = 'DHAC-SA', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = true, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 141;
UPDATE dependencias SET nombre = 'Departamento de Tesorería', sigla = 'DHAC-D01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 143;
UPDATE dependencias SET nombre = 'Unidad de Recursos', sigla = 'DHAC-D01-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1431;
UPDATE dependencias SET nombre = 'Unidad de Gastos Generales', sigla = 'DHAC-D01-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1432;
UPDATE dependencias SET nombre = 'Unidad de Sueldos', sigla = 'DHAC-D01-U03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1433;
UPDATE dependencias SET nombre = 'Departamento de Presupuesto', sigla = 'DHAC-D02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 144;
UPDATE dependencias SET nombre = 'Unidad de Formulación Presupuestaria', sigla = 'DHAC-D02-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1441;
UPDATE dependencias SET nombre = 'Unidad de Ejecución Presupuestaria', sigla = 'DHAC-D02-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1442;
UPDATE dependencias SET nombre = 'Unidad de Análisis y Programación de Servicios Personales', sigla = 'DHAC-D02-U03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1443;
UPDATE dependencias SET nombre = 'Departamento de Contabilidad', sigla = 'DHAC-D03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 145;
UPDATE dependencias SET nombre = 'Unidad de Patrimonio', sigla = 'DHAC-D03-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1451;
UPDATE dependencias SET nombre = 'Unidad de Ingresos', sigla = 'DHAC-D03-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1452;
UPDATE dependencias SET nombre = 'Unidad de Egresos', sigla = 'DHAC-D03-U03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1453;
UPDATE dependencias SET nombre = 'Unidad de Rendición de Fondos', sigla = 'DHAC-D03-U04', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1454;
UPDATE dependencias SET nombre = 'Unidad de Bancos', sigla = 'DHAC-D03-U05', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 1455;
UPDATE dependencias SET nombre = 'Unidad de Finanzas', sigla = 'DHAC-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 142;
UPDATE dependencias SET nombre = 'Dirección de Tecnologías de la Información y Comunicación', sigla = 'DTIC', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 10;
UPDATE dependencias SET nombre = 'Unidad de Secretaría Administrativa', sigla = 'DTIC-SA', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = true, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 11;
UPDATE dependencias SET nombre = 'Departamento de Organización y Métodos', sigla = 'DTIC-D01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 20;
UPDATE dependencias SET nombre = 'Unidad de Organización', sigla = 'DTIC-D01-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 71;
UPDATE dependencias SET nombre = 'Unidad de Procesos', sigla = 'DTIC-D01-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 72;
UPDATE dependencias SET nombre = 'Unidad de Gestión de Papel Cero', sigla = 'DTIC-D01-U03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 73;
UPDATE dependencias SET nombre = 'Departamento de Investigación y Desarrollo', sigla = 'DTIC-D02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 30;
UPDATE dependencias SET nombre = 'Unidad de Análisis y Desarrollo', sigla = 'DTIC-D02-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 74;
UPDATE dependencias SET nombre = 'Departamento Operativo de Informática', sigla = 'DTIC-D03', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 40;
UPDATE dependencias SET nombre = 'Unidad de Redes', sigla = 'DTIC-D03-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 75;
UPDATE dependencias SET nombre = 'Unidad de Soporte Técnico', sigla = 'DTIC-D03-U02', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 76;
UPDATE dependencias SET nombre = 'Departamento de Base de Datos y Sistemas Operativos', sigla = 'DTIC-D04', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 50;
UPDATE dependencias SET nombre = 'Unidad de Data Center', sigla = 'DTIC-D04-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 77;
UPDATE dependencias SET nombre = 'Departamento de Administración de Seguridad de la Información', sigla = 'DTIC-D05', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 60;
UPDATE dependencias SET nombre = 'Unidad de Documentación', sigla = 'DTIC-D05-U01', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 78;
UPDATE dependencias SET nombre = 'Departamento de Gestión Técnica y Administrativa', sigla = 'DTIC-D06', nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), es_mesa_entrada = false, recibe_externos = false, resolucion = 'Res. N° 887/08-07-2026' WHERE id = 70;

-- Coordinación de TIC: no figura en la Res. 887; se conserva como dependencia interna de la DTIC
UPDATE dependencias SET sigla = 'DTIC-COORD' WHERE id = 12 AND sigla IS NULL;

-- Altas, en orden jerárquico (cada superior se crea antes que sus subordinadas)
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Secretaría Privada', 'DSPRIV', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSPRIV');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DSPRIV-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSPRIV'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSPRIV-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Ceremonial', 'DSPRIV-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DSPRIV'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSPRIV-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Técnica y Administrativa', 'DSPRIV-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSPRIV'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSPRIV-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Secretaría General', 'DSG', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSG');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DSG-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSG'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSG-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Mesa Central de Entradas', 'DSG-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DSG'), FALSE, TRUE, true, true, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSG-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Documentación', 'DSG-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DSG'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSG-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Archivo General', 'DSG-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSG-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSG-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Transparencia y Anticorrupción', 'DTA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DTA-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTA'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTA-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Transparencia', 'DTA-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DTA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTA-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Detección e Investigación', 'DTA-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DTA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTA-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Asuntos Jurídicos', 'DAJ', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DAJ-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Acciones Judiciales', 'DAJ-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DAJ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de lo Contencioso Administrativo', 'DAJ-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Juicios Ordinarios y Especiales', 'DAJ-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Recuperación de Predios Municipales', 'DAJ-D01-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D01-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Asuntos Urbanísticos', 'DAJ-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DAJ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Urbana', 'DAJ-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Ciudadana', 'DAJ-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Patrimonio Inmobiliario', 'DAJ-D02-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D02-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Tributario y de Apelación', 'DAJ-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DAJ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Tributaria', 'DAJ-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Apelación', 'DAJ-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Contrataciones y Convenios', 'DAJ-D04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DAJ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Contratos y Convenios', 'DAJ-D04-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D04-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Licitaciones', 'DAJ-D04-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D04-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Relaciones Laborales', 'DAJ-D05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DAJ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Sumarial', 'DAJ-D05-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ-D05'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D05-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Laboral', 'DAJ-D05-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAJ-D05'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D05-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Procuraduría', 'DAJ-D06', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DAJ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D06');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Gestión Judicial, de Cobro y de Urgencias', 'DAJ-D07', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DAJ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAJ-D07');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Contraloría Interna', 'DCI', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCI');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DCI-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCI'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCI-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Auditoría de Cumplimiento', 'DCI-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCI'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCI-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Auditoría Financiera', 'DCI-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCI'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCI-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Auditoría de Desempeño', 'DCI-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCI'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCI-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Auditoría Especializada', 'DCI-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCI-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCI-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Control Operativo', 'DCI-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCI-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCI-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección General de Gabinete', 'DGG', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección General'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGG');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DGG-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGG'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGG-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Planificadora de Proyectos', 'DGG-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGG'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGG-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección General de Comunicaciones', 'DGC', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección General'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGC');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DGC-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGC'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGC-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Comunicaciones', 'DCOM', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DCOM-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCOM'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Marketing', 'DCOM-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCOM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Audiovisuales', 'DCOM-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCOM-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Informativa', 'DCOM-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCOM-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Prensa', 'DCOM-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCOM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Monitoreo', 'DCOM-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCOM-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Respuestas a Medios de Comunicación', 'DCOM-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCOM-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Comunicación Digital', 'DCOM-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCOM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Atención al Ciudadano', 'DCOM-D04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCOM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM-D04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Denuncias y Reclamos', 'DCOM-D04-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCOM-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM-D04-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Informaciones', 'DCOM-D04-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCOM-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM-D04-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Trámites Municipales', 'DCOM-D04-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCOM-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCOM-D04-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección General de Planificación', 'DGP', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección General'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGP');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DGP-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGP'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGP-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Planeamiento Estratégico', 'DPE', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPE');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DPE-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPE'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPE-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Desarrollo del Plan Estratégico', 'DPE-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPE-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Monitoreo y Seguimiento', 'DPE-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPE-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Estadística', 'DPE-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPE-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPE-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Estudios Económicos', 'DEE', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEE');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DEE-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DEE'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEE-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Ingresos', 'DEE-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DEE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEE-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Egresos', 'DEE-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DEE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEE-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Costos', 'DEE-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DEE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEE-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Sistema Municipal de Inversión Pública (SMIP)', 'DGP-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DGP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGP-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Admisibilidad y Viabilidad', 'DGP-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGP-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGP-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Programación Presupuestaria y de Control', 'DGP-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGP-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGP-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección General de Obras', 'DGO', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección General'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGO');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DGO-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGO'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGO-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Obras Municipales', 'DOM', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGO'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOM');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DOM-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOM'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOM-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Ingeniería', 'DOM-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DOM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOM-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Proyectos Viales y Civiles', 'DOM-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOM-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOM-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Digitalización de Planos', 'DOM-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOM-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOM-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Proyectos de Desagüe Pluvial', 'DOM-D01-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOM-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOM-D01-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Proyectos Especiales (FPE)', 'DOM-D01-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOM-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOM-D01-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Fiscalización de Obras', 'DOM-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DOM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOM-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Fiscalización de Obras Viales', 'DOM-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOM-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOM-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Fiscalización de Obras Civiles y Desagüe Pluvial', 'DOM-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOM-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOM-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Vialidad', 'DVIA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGO'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DVIA-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Adecuación Ambiental', 'DVIA-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Mantenimiento de Obras y Desagüe Pluvial', 'DVIA-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo', 'DVIA-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DVIA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Depósito de Materiales', 'DVIA-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Abastecimiento de Combustible', 'DVIA-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Báscula', 'DVIA-D01-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-D01-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Servicios Generales', 'DVIA-D01-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-D01-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Operativo', 'DVIA-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DVIA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Planta Asfáltica', 'DVIA-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Vial', 'DVIA-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Obras Civiles', 'DVIA-D02-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-D02-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Transporte, Equipos y Talleres', 'DVIA-D02-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-D02-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Fiscalización y Monitoreo', 'DVIA-D02-U05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-D02-U05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Marcas y Señales', 'DVIA-D02-U06', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DVIA-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DVIA-D02-U06');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección General de Desarrollo Urbano', 'DGDU', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección General'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGDU');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DGDU-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGDU'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGDU-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad "Centro de Formación y Capacitación Laboral" (Escuela Taller)', 'DGDU-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGDU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGDU-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Técnica y Administrativa', 'DGDU-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGDU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGDU-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Obras Particulares', 'DOP', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGDU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DOP-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Aprobación de Planos', 'DOP-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DOP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Evaluaciones Legales y Técnicas', 'DOP-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Proyectos y Regularizaciones', 'DOP-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Estudios Viales y Estacionamiento', 'DOP-D01-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D01-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Tasación y Liquidación', 'DOP-D01-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D01-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Resoluciones y Certificados', 'DOP-D01-U05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D01-U05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Archivo de Planos de Construcciones', 'DOP-D01-U06', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D01-U06');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Fiscalización de Obras', 'DOP-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DOP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Fiscalización de Planos', 'DOP-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Fiscalización Final de Obras', 'DOP-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Intervención de Obras', 'DOP-D02-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D02-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Prevención contra Incendios (PCI)', 'DOP-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DOP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Intervención de PCI', 'DOP-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Fiscalización de PCI', 'DOP-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Estudio de Aplicación de Normativas de PCI', 'DOP-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DOP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DOP-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Catastro Municipal', 'DCAT', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGDU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DCAT-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCAT'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Bienes Inmobiliarios', 'DCAT-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCAT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Patrimonio Inmobiliario', 'DCAT-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCAT-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento del Sistema de Información Geográfica', 'DCAT-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCAT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Cartografía y Geoprocesamiento', 'DCAT-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCAT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Archivo de Planos de Loteamientos', 'DCAT-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCAT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Topografía', 'DCAT-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCAT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Relevamiento y Proyectos', 'DCAT-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCAT-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Loteamiento', 'DCAT-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCAT-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Técnico Catastral', 'DCAT-D04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCAT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Mantenimiento Catastral', 'DCAT-D04-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCAT-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D04-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Valuaciones e Informes', 'DCAT-D04-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCAT-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D04-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Levantamiento Catastral', 'DCAT-D04-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCAT-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D04-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Necrópolis', 'DCAT-D05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCAT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Cementerios', 'DCAT-D05-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCAT-D05'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCAT-D05-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Planeamiento Urbano', 'DPU', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGDU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DPU-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPU'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Revisora de Documentos y Gestión de Licencias', 'DPU-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Proyectos Urbanos', 'DPU-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Diseño y Presupuesto', 'DPU-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPU-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Patrimonio Cultural de la Ciudad', 'DPU-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Planificación Urbana y Territorial', 'DPU-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Ordenamiento Urbano', 'DPU-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPU-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Intervención en el Dominio Público', 'DPU-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPU-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Rehabilitación Urbana', 'DPU-D03-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPU-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU-D03-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Licencias de Establecimientos', 'DPU-D04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU-D04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Técnica de Licencias', 'DPU-D04-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPU-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU-D04-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Desarrollo y Seguimiento de Obras', 'DPU-D05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPU-D05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección General de Gestión Ambiental', 'DGGA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección General'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Secretaría Administrativa', 'DGGA-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGGA'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Bienes Naturales y Adecuación Ambiental', 'DGGA-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DGGA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Recursos Hídricos y Suelo', 'DGGA-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGGA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Recursos Atmosféricos', 'DGGA-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGGA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Biodiversidad y Áreas Silvestres Protegidas', 'DGGA-D01-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGGA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA-D01-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Arbolado Urbano', 'DGGA-D01-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGGA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA-D01-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Prevención de Polución Sonora y Visual', 'DGGA-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DGGA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Centro de Educación y Promoción Ambiental (CEPAM)', 'DGGA-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DGGA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Centro de Conservación y Educación Ambiental (CECEAM)', 'DGGA-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGGA-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Integral de Residuos', 'DGGA-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGGA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Técnica de Medio Ambiente', 'DGGA-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGGA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Monitoreo de Licencia Ambiental Institucional', 'DGGA-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGGA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGA-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección General del Área Social', 'DGAS', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección General'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGAS');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DGAS-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGAS'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGAS-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Defensa al Consumidor', 'DDC', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGAS'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DDC-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DDC'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Protección del Consumidor y Usuario', 'DDC-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DDC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Laboratorio', 'DDC-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DDC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Fisicoquímico', 'DDC-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DDC-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Microbiología', 'DDC-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DDC-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Salubridad', 'DDC-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DDC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Controles en Establecimientos', 'DDC-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DDC-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Productos Naturales', 'DDC-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DDC-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Productos en Importación', 'DDC-D03-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DDC-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-D03-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Productos Procesados', 'DDC-D03-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DDC-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-D03-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Higiene', 'DDC-D04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DDC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-D04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Desinfestación', 'DDC-D04-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DDC-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-D04-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Prevención de Zoonosis', 'DDC-D04-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DDC-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DDC-D04-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Promoción de la Participación Ciudadana', 'DPPC', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGAS'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DPPC-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Fondos para Proyectos Especiales', 'DPPC-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Centro Municipal N° 1 "Ita Ybate"', 'DPPC-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Administrativa', 'DPPC-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa', 'DPPC-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Centro Municipal N° 2 "Oñondivepa"', 'DPPC-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Administrativa', 'DPPC-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa', 'DPPC-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Centro Municipal N° 3 "Jopoi"', 'DPPC-D04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Administrativa', 'DPPC-D04-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D04-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa', 'DPPC-D04-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D04-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Centro Municipal N° 4 "Mburukuja"', 'DPPC-D05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Administrativa', 'DPPC-D05-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D05'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D05-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa', 'DPPC-D05-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D05'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D05-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Centro Municipal N° 5 "Ko''eti"', 'DPPC-D06', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D06');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Administrativa', 'DPPC-D06-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D06'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D06-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa', 'DPPC-D06-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D06'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D06-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Centro Municipal N° 6 "Jeruti"', 'DPPC-D07', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D07');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Administrativa', 'DPPC-D07-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D07'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D07-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa', 'DPPC-D07-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D07'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D07-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Centro Municipal N° 7 "Marangatu"', 'DPPC-D08', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D08');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Administrativa', 'DPPC-D08-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D08'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D08-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa', 'DPPC-D08-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D08'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D08-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Centro Municipal N° 8 "Tenondete"', 'DPPC-D09', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D09');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Administrativa', 'DPPC-D09-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D09'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D09-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa', 'DPPC-D09-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D09'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D09-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Centro Municipal N° 9 "Ñasaindy"', 'DPPC-D10', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D10');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Administrativa', 'DPPC-D10-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D10'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D10-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa', 'DPPC-D10-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D10'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D10-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Centro Municipal N° 10 "Ñepytyvo Jovai"', 'DPPC-D11', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D11');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Administrativa', 'DPPC-D11-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D11'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D11-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa', 'DPPC-D11-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D11'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D11-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Centro Municipal N° 11 "Ñu Guazu"', 'DPPC-D12', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D12');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión Administrativa', 'DPPC-D12-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D12'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D12-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa', 'DPPC-D12-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D12'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D12-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Servicios', 'DPPC-D13', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPPC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D13');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Asistencia Social', 'DPPC-D13-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D13'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D13-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Servicios Ciudadanos', 'DPPC-D13-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D13'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D13-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Tierras', 'DPPC-D13-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D13'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D13-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Adultos Mayores', 'DPPC-D13-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPPC-D13'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPPC-D13-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Juventud y Deportes', 'DJD', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGAS'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJD');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DJD-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DJD'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJD-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Juventud', 'DJD-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DJD'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJD-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Deportes', 'DJD-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DJD'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJD-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección del Policlínico Municipal', 'DPOLI', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGAS'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPOLI');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DPOLI-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPOLI'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPOLI-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Prevención de Adicciones', 'DPOLI-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPOLI'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPOLI-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo', 'DPOLI-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPOLI'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPOLI-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Contabilidad', 'DPOLI-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPOLI-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPOLI-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Servicios Operativos', 'DPOLI-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPOLI-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPOLI-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Servicios Asistenciales', 'DPOLI-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPOLI'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPOLI-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Asistencial y de Diagnóstico', 'DPOLI-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPOLI-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPOLI-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Clínicas Periféricas y Centros Especializados', 'DPOLI-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPOLI-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPOLI-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Servicios de Enfermería', 'DPOLI-D02-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPOLI-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPOLI-D02-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión y Control de Servicio', 'DPOLI-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPOLI'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPOLI-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión de Personas', 'DPOLI-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPOLI'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPOLI-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Políticas de Género', 'DPG', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGAS'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPG');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DPG-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPG'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPG-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Formación y Equidad de Género', 'DPG-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPG'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPG-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Promoción y Atención a la Mujer', 'DPG-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPG'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPG-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de CODENI, Niñez y Adolescencia', 'DCODENI', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGAS'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCODENI');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de CODENI', 'DCODENI-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCODENI'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCODENI-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Educación y Servicios', 'DCODENI-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCODENI'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCODENI-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Guarderías, Comedores y SIAP', 'DCODENI-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCODENI-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCODENI-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Escuelas y AMICAS', 'DCODENI-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCODENI-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCODENI-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Centro Integral de Atención a la 1ª Infancia "Tapytami"', 'DCODENI-D02-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCODENI-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCODENI-D02-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad "Taller de Expresión y Desarrollo Infantil Municipal" (TEDIM)', 'DCODENI-D02-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCODENI-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCODENI-D02-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección del Centro Municipal de Promoción Empresarial (CEMUPE)', 'DCEMUPE', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGAS'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCEMUPE');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DCEMUPE-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCEMUPE'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCEMUPE-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo', 'DCEMUPE-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCEMUPE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCEMUPE-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Emprendedurismo y Capacitación', 'DCEMUPE-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCEMUPE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCEMUPE-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Promoción de Empleos', 'DCEMUPE-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCEMUPE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCEMUPE-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección General de Cultura y Turismo', 'DGCT', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección General'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGCT');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DGCT-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGCT'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGCT-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión de Personas', 'DGCT-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGCT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGCT-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo', 'DGCT-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DGCT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGCT-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Prensa y Promoción Cultural', 'DGCT-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DGCT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGCT-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Turismo', 'DGCT-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DGCT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGCT-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección del Instituto Municipal de Arte (IMA)', 'DIMA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGCT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DIMA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DIMA-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DIMA'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DIMA-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Académico', 'DIMA-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DIMA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DIMA-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Escuela de Arte Infantil', 'DIMA-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DIMA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DIMA-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Escuela de Arte Dramático', 'DIMA-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DIMA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DIMA-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Escuela de Danza', 'DIMA-D01-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DIMA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DIMA-D01-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Escuela de Canto', 'DIMA-D01-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DIMA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DIMA-D01-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Escuela de Música', 'DIMA-D01-U05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DIMA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DIMA-D01-U05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Escuela de Locución', 'DIMA-D01-U06', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DIMA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DIMA-D01-U06');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad "Taller de Teatro María Elena Sachero"', 'DIMA-D01-U07', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DIMA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DIMA-D01-U07');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo y de Servicios', 'DIMA-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DIMA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DIMA-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección del Centro Paraguayo Japonés (CPJ)', 'DCPJ', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGCT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DCPJ-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Comunicación y Marketing', 'DCPJ-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo', 'DCPJ-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Tesorería', 'DCPJ-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Contabilidad', 'DCPJ-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Presupuesto', 'DCPJ-D01-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D01-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Operaciones y Servicios', 'DCPJ-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Mantenimientos', 'DCPJ-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Servicios Esenciales', 'DCPJ-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Cultural', 'DCPJ-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión de Eventos y Espacios', 'DCPJ-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Producción de Eventos', 'DCPJ-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Académico', 'DCPJ-D04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Idiomas', 'DCPJ-D04-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D04-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Computación', 'DCPJ-D04-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D04-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gimnasio', 'DCPJ-D04-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D04-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Conservatorio', 'DCPJ-D04-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D04-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Danzas', 'DCPJ-D04-U05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D04-U05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Arte', 'DCPJ-D04-U06', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DCPJ-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DCPJ-D04-U06');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección del Teatro Municipal Ignacio A. Pane', 'DTMIAP', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGCT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTMIAP');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DTMIAP-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTMIAP'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTMIAP-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Programación y Comunicación', 'DTMIAP-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTMIAP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTMIAP-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo', 'DTMIAP-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DTMIAP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTMIAP-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Escenotecnia', 'DTMIAP-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DTMIAP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTMIAP-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad "Orquesta Sinfónica de la Ciudad de Asunción"', 'DTMIAP-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTMIAP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTMIAP-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad "Orquesta de Cámara Municipal de Asunción"', 'DTMIAP-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTMIAP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTMIAP-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad "Ballet Clásico y Moderno Municipal de Asunción"', 'DTMIAP-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTMIAP'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTMIAP-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección del Centro Cultural de la Ciudad Carlos Colombino "Manzana de la Rivera"', 'DMR', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGCT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMR');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DMR-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMR'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMR-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Comunicación', 'DMR-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMR'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMR-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Audiovisual', 'DMR-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DMR'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMR-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Museos y Centro Numismático Municipal', 'DMR-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DMR'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMR-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Biblioteca y Extensión Bibliotecaria', 'DMR-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMR'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMR-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Servicios Generales', 'DMR-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMR'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMR-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Espacio Cultural', 'DMR-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMR'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMR-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Acción Cultural y Comunitaria', 'DACC', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGCT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DACC');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DACC-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DACC'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DACC-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Eventos y Logística', 'DACC-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DACC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DACC-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Escenario Móvil', 'DACC-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DACC-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DACC-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Plazas Temáticas de Cultura y Eventos Barriales', 'DACC-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DACC-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DACC-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Elencos Municipales', 'DACC-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DACC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DACC-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Banda Folklórica Municipal de Asunción', 'DACC-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DACC-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DACC-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Ballet Folklórico Municipal de Asunción', 'DACC-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DACC-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DACC-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Conjunto Folklórico Municipal de Asunción', 'DACC-D02-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DACC-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DACC-D02-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección General de Relaciones Interinstitucionales y Cooperación Externa', 'DGRICE', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección General'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGRICE');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DGRICE-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGRICE'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGRICE-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Relaciones Internacionales y Cooperación Externa', 'DRICE', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGRICE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRICE');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Vinculación Internacional', 'DRICE-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DRICE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRICE-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Cooperación Internacional', 'DRICE-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DRICE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRICE-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Técnico y de Gestión de Proyectos', 'DRICE-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DRICE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRICE-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Gestión Formativa y Representación Externa', 'DRICE-D04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DRICE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRICE-D04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Relaciones Intergubernamentales y Organizaciones Civiles', 'DRIOC', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGRICE'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRIOC');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DRIOC-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DRIOC'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRIOC-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Relaciones con Entes Públicos', 'DRIOC-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DRIOC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRIOC-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Relaciones con Entes Privados', 'DRIOC-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DRIOC'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRIOC-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección General de la Policía Municipal', 'DGPM', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección General'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGPM');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DGPM-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGPM'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGPM-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de la Policía Municipal Instructora', 'DPMI', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGPM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMI');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DPMI-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMI'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMI-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Academia de Policía Municipal', 'DPMI-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPMI'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMI-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Formación Integral', 'DPMI-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMI-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMI-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Capacitación', 'DPMI-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPMI'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMI-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Formación Vial', 'DPMI-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMI-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMI-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de la Policía Municipal de Seguridad', 'DPMS', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGPM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMS');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DPMS-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMS'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMS-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Operativo de Seguridad', 'DPMS-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPMS'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMS-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Seguridad de Plazas y Parques', 'DPMS-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPMS'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMS-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Policía Municipal de Tránsito (PMT)', 'DPMT', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGPM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DPMT-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo', 'DPMT-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPMT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Servicios Generales', 'DPMT-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Operativo y de Tráfico', 'DPMT-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPMT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Base 1', 'DPMT-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Base 2', 'DPMT-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Base 3', 'DPMT-D02-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D02-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Base 4', 'DPMT-D02-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D02-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Base 5', 'DPMT-D02-U05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D02-U05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Base 6', 'DPMT-D02-U06', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D02-U06');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Base 7 y Comunicaciones', 'DPMT-D02-U07', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D02-U07');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Base 8', 'DPMT-D02-U08', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D02-U08');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Base 10', 'DPMT-D02-U09', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D02-U09');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Motorizada', 'DPMT-D02-U10', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D02-U10');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Estacionamiento Controlado de Asunción', 'DPMT-D02-U11', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-D02-U11');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Denuncia y Parte Policial', 'DPMT-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMT-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Tránsito y Transporte', 'DTT', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGPM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTT');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DTT-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTT'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTT-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Licencias e Infracciones', 'DTT-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DTT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTT-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Licencias de Conducir', 'DTT-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTT-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTT-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Infracciones', 'DTT-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTT-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTT-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Rodados', 'DTT-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DTT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTT-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Inscripción y Habilitación', 'DTT-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTT-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Fiscalización e Inspección Técnica Vehicular', 'DTT-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTT-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTT-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Ingeniería de Tráfico', 'DTT-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DTT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTT-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Semáforos y Señalética', 'DTT-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTT-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTT-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Sala de Examen', 'DTT-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DTT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DTT-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de la Policía Municipal de Vigilancia', 'DPMV', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGPM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMV');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DPMV-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMV'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMV-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Fiscalización de Venta en la Vía Pública', 'DPMV-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPMV'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMV-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Fiscalización de Aseo Urbano', 'DPMV-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPMV'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMV-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Planificación y Monitoreo', 'DPMV-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMV-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMV-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección General de Gestión y Reducción de Riesgos de Desastres Municipal', 'DGGRRDM', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección General'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGRRDM');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DGGRRDM-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGGRRDM'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGRRDM-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa de Contrataciones', 'DGGRRDM-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGGRRDM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGGRRDM-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección Administrativa', 'DADMR', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGGRRDM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DADMR');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DADMR-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DADMR'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DADMR-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo', 'DADMR-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DADMR'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DADMR-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Financiero', 'DADMR-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DADMR'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DADMR-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Control Interno', 'DADMR-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DADMR'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DADMR-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Gestión y Reducción de Riesgos de Desastres', 'DGRRD', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'DGGRRDM'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGRRD');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DGRRD-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DGRRD'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGRRD-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Gestión de Riesgos', 'DGRRD-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DGRRD'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGRRD-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Respuesta y Recuperación de Desastre', 'DGRRD-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DGRRD'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DGRRD-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Recursos Humanos', 'DRRHH', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DRRHH-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Administración del Personal', 'DRRHH-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Liquidación de Haberes', 'DRRHH-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Fiscalización', 'DRRHH-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Beneficios del Personal', 'DRRHH-D01-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-D01-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa Descentralizada', 'DRRHH-D01-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-D01-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Documentaciones', 'DRRHH-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Legajos', 'DRRHH-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Resoluciones y Contratos', 'DRRHH-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Desarrollo y Bienestar del Personal', 'DRRHH-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad del Bienestar del Personal', 'DRRHH-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Selección y Capacitación', 'DRRHH-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Salud y Seguridad Ocupacional', 'DRRHH-D03-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-D03-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Atención al Funcionario', 'DRRHH-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DRRHH'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DRRHH-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Abastecimiento de la Municipalidad de Asunción (DAMA)', 'DAMA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DAMA-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Asuntos Legales', 'DAMA-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo', 'DAMA-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DAMA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa de Contrataciones N° 4', 'DAMA-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Tesorería', 'DAMA-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Cobros Tributarios', 'DAMA-D01-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D01-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Presupuesto', 'DAMA-D01-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D01-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Contabilidad', 'DAMA-D01-U05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D01-U05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Servicios Generales', 'DAMA-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DAMA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Obras Civiles, Viales y Pluvial', 'DAMA-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Transporte, Equipos y Talleres', 'DAMA-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Patrimonio y Suministro', 'DAMA-D02-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D02-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Protección Ambiental', 'DAMA-D02-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D02-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Gestión Operativa', 'DAMA-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DAMA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Higiene y Salubridad', 'DAMA-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Policía de DAMA', 'DAMA-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Técnica', 'DAMA-D03-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-D03-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Registro de Permisionarios', 'DAMA-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión de la Información', 'DAMA-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión de Personas', 'DAMA-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Tecnología de la Información', 'DAMA-U05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DAMA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DAMA-U05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Estación de Buses de Asunción (DEBA)', 'DEBA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DEBA-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DEBA'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Asuntos Legales', 'DEBA-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DEBA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Conservación y Mantenimiento', 'DEBA-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DEBA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Policía Municipal de la DEBA', 'DEBA-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DEBA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo', 'DEBA-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DEBA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Contabilidad y Presupuesto', 'DEBA-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DEBA-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Tesorería', 'DEBA-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DEBA-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Cobros Tributarios', 'DEBA-D03-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DEBA-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-D03-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Gestión de Personas', 'DEBA-D04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DEBA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-D04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Gestión Operativa', 'DEBA-D05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DEBA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-D05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Operativa de Contrataciones', 'DEBA-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DEBA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión de la Información', 'DEBA-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DEBA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Atención Social', 'DEBA-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DEBA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Inspectoría Tributaria', 'DEBA-U05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DEBA'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DEBA-U05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Servicios Urbanos', 'DSU', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DSU-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo', 'DSU-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DSU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Adquisiciones', 'DSU-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Inventario y Suministro', 'DSU-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Revisora de Documentos', 'DSU-D01-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D01-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Áreas Verdes y Mantenimiento', 'DSU-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DSU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Mantenimiento de Plazas, Parques y Paseos', 'DSU-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Mantenimiento', 'DSU-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Aseo Urbano', 'DSU-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DSU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Barrido y Cuadrilla', 'DSU-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Servicios Varios', 'DSU-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de UNILIMER', 'DSU-D03-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D03-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Recolección', 'DSU-D04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DSU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Recolección Domiciliaria', 'DSU-D04-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D04-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Desechos Patológicos', 'DSU-D04-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D04'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D04-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Servicios Generales', 'DSU-D05', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DSU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D05');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Mantenimiento de Planta', 'DSU-D05-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D05'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D05-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Transporte', 'DSU-D05-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D05'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D05-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Talleres', 'DSU-D05-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D05'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D05-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Mingas Ambientales', 'DSU-D06', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DSU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D06');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Limpieza y Mantenimiento', 'DSU-D06-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D06'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D06-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Fumigación', 'DSU-D06-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU-D06'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-D06-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Disposición y Tratamiento Final', 'DSU-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DSU'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DSU-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de Mercados Zonales', 'DMZ', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DMZ-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMZ'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Asuntos Legales', 'DMZ-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMZ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Administrativo', 'DMZ-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DMZ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Registro de Permisionarios y Vendedores', 'DMZ-D01-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMZ-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-D01-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Cobros Tributarios', 'DMZ-D01-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMZ-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-D01-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Tesorería', 'DMZ-D01-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMZ-D01'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-D01-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Gestión de Operaciones y de Control', 'DMZ-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DMZ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Servicios de Mantenimiento y Conservación', 'DMZ-D02-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMZ-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-D02-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Control de Aplicación de Normativas', 'DMZ-D02-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMZ-D02'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-D02-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Mercados', 'DMZ-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DMZ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Mercado 1', 'DMZ-D03-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMZ-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-D03-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Mercado 2', 'DMZ-D03-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMZ-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-D03-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Mercado 3', 'DMZ-D03-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMZ-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-D03-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Mercado 5', 'DMZ-D03-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMZ-D03'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-D03-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Gestión de Personas', 'DMZ-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DMZ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DMZ-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección del Jardín Botánico y Zoológico', 'DJBZ', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJBZ');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DJBZ-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DJBZ'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJBZ-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Botánica', 'DJBZ-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DJBZ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJBZ-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Zoológico', 'DJBZ-D02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DJBZ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJBZ-D02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento de Mantenimiento', 'DJBZ-D03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DJBZ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJBZ-D03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Depósito', 'DJBZ-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DJBZ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJBZ-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Museo de Historia Natural e Indigenista', 'DJBZ-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DJBZ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJBZ-U02');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Guarda Zoo y Guarda Parques', 'DJBZ-U03', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DJBZ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJBZ-U03');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Cría', 'DJBZ-U04', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DJBZ'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DJBZ-U04');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Dirección de la Policía Municipal Fiscalizadora', 'DPMF', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Dirección'), (SELECT id FROM dependencias WHERE sigla = 'INT'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMF');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad de Secretaría Administrativa', 'DPMF-SA', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMF'), FALSE, TRUE, true, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMF-SA');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Departamento Fiscalizador de Negocios', 'DPMF-D01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Departamento'), (SELECT id FROM dependencias WHERE sigla = 'DPMF'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMF-D01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Fiscalizadora de Juegos de Entretenimiento y de Azar', 'DPMF-U01', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMF'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMF-U01');
INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)
    SELECT 'Unidad Fiscalizadora de Anuncios Publicitarios e Identificatorios', 'DPMF-U02', (SELECT id FROM niveles_jerarquicos WHERE nombre = 'Unidad'), (SELECT id FROM dependencias WHERE sigla = 'DPMF'), FALSE, TRUE, false, false, 'Res. N° 887/08-07-2026'
    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = 'DPMF-U02');

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM dependencias WHERE resolucion = 'Res. N° 887/08-07-2026') <> 580 THEN
        RAISE EXCEPTION 'La carga no coincide con el organigrama: se esperaban 580 dependencias de la resolución';
    END IF;
    IF EXISTS (SELECT 1 FROM dependencias WHERE sigla IS NULL) THEN
        RAISE EXCEPTION 'Quedaron dependencias sin sigla';
    END IF;
END $$;

COMMIT;
