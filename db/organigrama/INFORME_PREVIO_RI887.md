# Informe previo a la carga del organigrama (Res. N° 887/08-07-2026)

> **Aprobado y aplicado (02/11/2026)** con `db/migraciones/V8__organigrama_y_asignaciones.sql` y `V9__organigrama_RI887.sql`. La base tiene 581 dependencias: las 580 de la resolución más la Coordinación de TIC. Los puntos a confirmar se cargaron con la opción propuesta en cada uno. Si Organización y Métodos corrige algo, se edita el .txt, se regenera la migración y se aplica una nueva.

Informe previo a la importación (§12 del diseño de Fase 2).

## Archivos

- `organigrama_RI887_2026.txt`: transcripción de los 16 organigramas, sangría = jerarquía. Es la fuente editable.
- `organigrama_RI887_2026.csv`: el CSV del §12 generado a partir del .txt (separador `;`, UTF-8). Las columnas `responsable` y `correo_responsable` quedan vacías para completar después. Se agregaron dos columnas de control: `id_actual` (vínculo con la base) y `revisar`.

## Resumen

| Nivel | Cantidad |
|---|---|
| Intendencia | 1 |
| Dirección General | 12 |
| Dirección | 46 |
| Departamento | 156 |
| Unidad | 365 |
| **Total** | **580** |

- Mesas de entrada (Unidades de Secretaría Administrativa y el Dpto. Mesa Central de Entradas): 57.
- Ya cargadas en la base y vinculadas por id, que conservan su historial: 90 de 91.
- Altas nuevas: 490.
- Validaciones (siglas únicas, sin ciclos, niveles coherentes, sin nombres repetidos entre hermanos): **sin errores**.

Códigos: las Direcciones Generales y las Direcciones llevan una sigla propia (DGAF, DREC, DTIC, DAMA…). Departamentos y unidades toman un código derivado de su superior: `DREC-D02` para un departamento, `DREC-D02-U01` para una unidad, `DREC-SA` para la secretaría.

## Cambios sobre dependencias existentes

**Nivel (1)**
- 100 DGAF: Dirección Ejecutiva pasa a Dirección General.

**Nombre (24)**
- 114: "Departamento de Licitaciones" pasa a **"Departamento de Liquidaciones"**. Es el único cambio de fondo; el organigrama dice LIQUIDACIONES.
- 1433: "Unidad de Sueldo" pasa a "Unidad de Sueldos".
- 1144: "Desifestacion" pasa a "Desinfestación".
- Los otros 21 son tildes y mayúsculas (Atencion → Atención, Tesoreria → Tesorería, etc.), además de:
  - 60: se agrega "de".
  - 70: "Gestión Técnica **y** Administrativa".

**Superior (0)**
- Ninguna dependencia cambia de superior.

## Puntos a confirmar con Organización y Métodos

1. **Coordinación de TIC (id 12)**: está en la base pero no en el organigrama. Tiene 2 usuarios y 8 registros entre documentos y actuaciones, así que no se puede borrar.
   - Propuesta: dejarla activa como dependencia interna bajo la DTIC, fuera del organigrama oficial.
   - Alternativa: mover los usuarios a la DTIC y desactivarla.
2. **Dpto. de Gestión Judicial, de Cobro y de Urgencias** (DAJ-D07): en el gráfico de Asuntos Jurídicos aparece al lado de Procuraduría. Se cargó como departamento directo de la Dirección; puede que dependa de Procuraduría.
3. **DG de Gestión Ambiental**: el recuadro dice solo "Secretaría Administrativa", sin "Unidad". Se cargó como unidad y mesa de entrada.
4. **Dirección de CODENI** y **Dirección de Relaciones Internacionales y Cooperación Externa**: no muestran Unidad de Secretaría Administrativa. Hay que definir quién recibe sus documentos; por defecto, la mesa de su Dirección General.
5. **Policía Municipal Fiscalizadora**: según el organigrama general depende directamente de la Intendencia, no de la DG de la Policía Municipal. Se cargó así.
6. El PDF grande "Estructura Organizacional RI 887" no se pudo convertir a imagen en esta máquina. La transcripción se hizo con los 15 organigramas por área, que llevan la misma resolución. Conviene que Organización y Métodos dé una pasada al .txt.

## Próximo paso (al aprobar)

El script de importación de la etapa 2.1:
1. Aplica en una sola transacción los cambios de nombre y nivel sobre los 90 ids existentes.
2. Crea las 490 altas con su código, nivel, superior y marca de mesa de entrada.

Requiere antes la migración que agrega `codigo`, `ruta` y `es_mesa_entrada` a `dependencias`, que es parte de la etapa 2.1.
