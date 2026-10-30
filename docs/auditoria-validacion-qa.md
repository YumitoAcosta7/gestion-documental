# Auditoría técnica, matriz de validaciones y plan de QA

**Alcance:** carga de documentos, derivaciones (simples y múltiples), documentos informativos y ubicación física en biblioratos.
**Base de la revisión:** código de la rama `feature/mejoras-operativas` (commit `c8a7fb8`) y el esquema **real** de PostgreSQL, inspeccionado en modo solo lectura el 30/10/2026.
**Estado:** solo diagnóstico y diseño. Ningún cambio de esta auditoría está aplicado todavía.

---

## 0. Resumen ejecutivo

| # | Hallazgo | Severidad | Requerimiento |
|---|---|---|---|
| H1 | La bitácora se borra en cascada: `auditorias_documento.documento_id … ON DELETE CASCADE` (también actuaciones y adjuntos). Un `DELETE` de un documento elimina su historial "inmutable". | 🔴 Crítica | Trazabilidad |
| H2 | Derivar **para conocimiento** un expediente que está en `EN_ESPERA_INFORME` lo pasa a `EN_REVISION`; los informes pendientes ya no lo cierran automáticamente. | 🔴 Crítica | Derivaciones sucesivas |
| H3 | No hay categoría de tipo documental: una **Circular** entra al circuito de trámite (estado, bandejas, alertas de demora). La base tiene 3 circulares en `EN_REVISION`/`PENDIENTE_SALIDA`. Son documentos de prueba de esta sesión, pero muestran que el sistema lo permite. | 🔴 Crítica | Informativos |
| H4 | El bibliorato es texto libre y **no se pide al ingresar**: solo se carga al registrar una actuación. No aparece en la bandeja de activos ni se puede buscar. Hoy 3 de 10 documentos lo tienen. | 🔴 Crítica | Biblioratos |
| H5 | No hay unicidad de pases pendientes: se puede derivar dos veces a la misma dependencia, o enviar el mismo ID dos veces en la petición, y se crean pases duplicados. Hoy no hay ninguno, pero nada lo impide. | 🟠 Alta | Derivaciones múltiples |
| H6 | No hay control de concurrencia (`@Version` ni bloqueo de fila). Dos operaciones simultáneas sobre el mismo expediente: gana la última escritura del estado. | 🟠 Alta | Derivaciones concurrentes |
| H7 | La base no tiene restricciones `CHECK`: `estado_actual`, `prioridad`, `tipo_actuacion`, `rol` y `tipo_anulacion` aceptan cualquier texto. | 🟠 Alta | Integridad de datos |
| H8 | "Pase para informe" se detecta por el texto de la descripción (`contains("PARA INFORME")`), no por un campo propio. | 🟠 Alta | Derivaciones |
| H9 | Un archivo que supera 20 MB produce la página de error de Spring (`MaxUploadSizeExceededException` sin manejar), no un mensaje para el usuario. | 🟡 Media | Carga |
| H10 | Archivos huérfanos: el PDF se escribe en disco antes del commit; si la transacción falla, el archivo queda sin registro. No se guarda hash (SHA-256) para verificar integridad. | 🟡 Media | Carga |
| H11 | Tipos de clave inconsistentes: `actuaciones_documento.dependencia_emisora_id` y `usuario_id` son `BIGINT`, pero referencian `INTEGER`. `documentos.fecha_hora_ingreso` admite NULL (la entidad dice obligatorio). Hay una columna sin uso, `documentos.usuario_registra_salida_id`, y un índice redundante (`idx_actuaciones_doc`). | 🟢 Baja | Modelo de datos |

### Avance de implementación (30/10/2026, migración V3)

| Hallazgo | Estado | Cómo se resolvió |
|---|---|---|
| H1 | ✅ | FK `fk_auditoria_documento`, `fk_actuacion_documento` y `fk_adjunto_documento` con `ON DELETE RESTRICT` |
| H2 | ✅ | Una derivación de conocimiento conserva `EN_ESPERA_INFORME` si quedan pases vigentes con `requiere_informe` |
| H5 | ✅ | Destinos sin repetir, rechazo de pase pendiente duplicado en el servicio e índice único parcial `uq_pase_pendiente_por_receptora` |
| H6 | ✅ | Bloqueo pesimista (`SELECT … FOR UPDATE`) del expediente y del pase en derivar, anular, confirmar, devolver, salida, acuse, actuaciones y cierre. Se eligió en lugar de `@Version` porque serializa las operaciones sin obligar al usuario a reintentar. |
| H7 | ⚠️ | `CHECK` de estado, prioridad, tipo de actuación y coherencia de anulación. **Falta el de rol:** hay dos usuarios con roles heredados (`JEFA_SECRETARIA`, `OPERADOR_MESA_ENTRADA`) que no pueden iniciar sesión y deben reasignarse. |
| H8 | ✅ | Columna `requiere_informe` (9 pases existentes migrados desde la descripción) |
| H11 | ✅ | `dependencia_emisora_id` y `usuario_id` a `INTEGER`, `fecha_hora_ingreso NOT NULL`, índice redundante eliminado, índice por estado |
| H3, H4, H9, H10 | ❌ | Pasos 3 y 4 del plan |

Pruebas ejecutadas: BD-01 a BD-05 (en una transacción revertida), DV-03, DV-04 y DV-07, todas correctas.

**Lo que ya está bien (verificado):**
- CUTI único con bloqueo transaccional.
- PDF validado por contenido (firma `%PDF-`) con límite de 20 MB.
- Archivos que nunca se sobrescriben, con versionado.
- Pases anulables con motivo.
- Control de acceso por dependencia.
- Claves foráneas presentes en todas las relaciones principales.

---

## 1. Modelo de datos

### 1.1 Estado actual (verificado en la base)

```
tipos_documentos (id INT PK, nombre UNIQUE, descripcion, activo)
        │ 1
        ▼ N
documentos (id BIGINT PK, codigo_cuti UNIQUE, tipo_documento_id FK, dependencia_origen_id FK,
            estado_actual VARCHAR(30), prioridad VARCHAR(20), ubicacion_bibliorato VARCHAR(255) ← texto libre,
            archivado BOOL, fecha_hora_archivado ← sin uso)
        │ 1
        ├──▶ N actuaciones_documento (pases, informes, devoluciones; ubicacion_bibliorato VARCHAR(150) ← texto libre)
        ├──▶ N adjuntos_documento   (versiones de PDF)
        └──▶ N auditorias_documento (bitácora)   ← ON DELETE CASCADE (H1)
```

### 1.2 Modelo propuesto

Cambios mínimos, compatibles con los datos existentes:

```sql
-- =====================================================================
-- PROPUESTA V3 (NO APLICADA) — revisar antes de ejecutar
-- =====================================================================
BEGIN;

-- H1: la bitácora y el historial no se borran nunca
ALTER TABLE auditorias_documento  DROP CONSTRAINT IF EXISTS auditorias_documento_documento_id_fkey,
    ADD CONSTRAINT auditorias_documento_documento_id_fkey  FOREIGN KEY (documento_id) REFERENCES documentos (id) ON DELETE RESTRICT;
ALTER TABLE actuaciones_documento DROP CONSTRAINT IF EXISTS actuaciones_documento_documento_id_fkey,
    ADD CONSTRAINT actuaciones_documento_documento_id_fkey FOREIGN KEY (documento_id) REFERENCES documentos (id) ON DELETE RESTRICT;
ALTER TABLE adjuntos_documento    DROP CONSTRAINT IF EXISTS adjuntos_documento_documento_id_fkey,
    ADD CONSTRAINT adjuntos_documento_documento_id_fkey    FOREIGN KEY (documento_id) REFERENCES documentos (id) ON DELETE RESTRICT;
-- (verificar los nombres reales de las FK con \d antes de ejecutar)

-- H3: categoría del tipo documental
ALTER TABLE tipos_documentos
    ADD COLUMN IF NOT EXISTS categoria VARCHAR(20) NOT NULL DEFAULT 'TRAMITE'
        CHECK (categoria IN ('TRAMITE', 'INFORMATIVO')),
    ADD COLUMN IF NOT EXISTS requiere_bibliorato BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE tipos_documentos SET categoria = 'INFORMATIVO', requiere_bibliorato = TRUE WHERE nombre = 'Circular';

-- H4: biblioratos como catálogo (ubicación física estructurada)
CREATE TABLE IF NOT EXISTS biblioratos (
    id              SERIAL PRIMARY KEY,
    codigo          VARCHAR(30)  NOT NULL UNIQUE,      -- Ej: BIB-DTIC-2026-007
    descripcion     VARCHAR(150) NOT NULL,             -- Ej: Circulares recibidas 2026 - Tomo 1
    dependencia_id  INTEGER      NOT NULL REFERENCES dependencias (id),
    anio            INTEGER      NOT NULL CHECK (anio BETWEEN 2000 AND 2100),
    ubicacion_fisica VARCHAR(150) NOT NULL,            -- Ej: Archivo DTIC · Armario 2 · Estante 3
    capacidad_fojas INTEGER      CHECK (capacidad_fojas > 0),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_hora_alta TIMESTAMP    NOT NULL DEFAULT now()
);

ALTER TABLE documentos
    ADD COLUMN IF NOT EXISTS bibliorato_id INTEGER REFERENCES biblioratos (id),
    ADD COLUMN IF NOT EXISTS foja_desde    INTEGER CHECK (foja_desde > 0),
    ADD COLUMN IF NOT EXISTS foja_hasta    INTEGER,
    ADD CONSTRAINT chk_documentos_fojas CHECK (foja_hasta IS NULL OR foja_hasta >= foja_desde);
ALTER TABLE actuaciones_documento
    ADD COLUMN IF NOT EXISTS bibliorato_id INTEGER REFERENCES biblioratos (id);
-- ubicacion_bibliorato (texto) se conserva solo para los registros históricos

-- H8: el pedido de informe deja de depender del texto
ALTER TABLE actuaciones_documento ADD COLUMN IF NOT EXISTS requiere_informe BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE actuaciones_documento SET requiere_informe = TRUE
 WHERE tipo_actuacion IN ('PASE_INTERNO','PASE_CONFIRMADO') AND descripcion_resumen LIKE '%PARA INFORME%';

-- H5: un solo pase pendiente por documento y dependencia receptora
CREATE UNIQUE INDEX IF NOT EXISTS uq_pase_pendiente_por_receptora
    ON actuaciones_documento (documento_id, dependencia_receptora_pase_id)
 WHERE tipo_actuacion = 'PASE_INTERNO' AND NOT anulada AND NOT COALESCE(pase_confirmado, FALSE);

-- H6: control de concurrencia optimista
ALTER TABLE documentos ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- H7: dominios cerrados
ALTER TABLE documentos
    ADD CONSTRAINT chk_documentos_estado CHECK (estado_actual IN
        ('INGRESADO','EN_REVISION','EN_ESPERA_INFORME','CON_RESPUESTA_TECNICA','PENDIENTE_SALIDA','FINALIZADO','ARCHIVADO')),
    ADD CONSTRAINT chk_documentos_prioridad CHECK (prioridad IN ('NORMAL','IMPORTANTE','URGENTE'));
ALTER TABLE actuaciones_documento
    ADD CONSTRAINT chk_actuaciones_tipo CHECK (tipo_actuacion IN
        ('PASE_INTERNO','PASE_CONFIRMADO','DEVOLUCION','INFORME_TECNICO','MEMO','ACTA_ENTREGA','PROVIDENCIA','CIERRE_EXCEPCIONAL')),
    ADD CONSTRAINT chk_actuaciones_anulacion CHECK (
        (anulada AND tipo_anulacion IN ('RETIRADO','RECHAZADO') AND motivo_anulacion IS NOT NULL)
        OR (NOT anulada AND tipo_anulacion IS NULL));
ALTER TABLE usuarios ADD CONSTRAINT chk_usuarios_rol CHECK (rol IN ('DIRECTOR','SECRETARIA_ADMIN','JEFE_DEPTO','JEFE_UNIDAD'));

-- H10: integridad del archivo
ALTER TABLE adjuntos_documento
    ADD COLUMN IF NOT EXISTS hash_sha256 CHAR(64),
    ADD COLUMN IF NOT EXISTS tipo_mime   VARCHAR(100);

-- H11: limpieza de tipos e índices
ALTER TABLE actuaciones_documento ALTER COLUMN dependencia_emisora_id TYPE INTEGER, ALTER COLUMN usuario_id TYPE INTEGER;
ALTER TABLE documentos ALTER COLUMN fecha_hora_ingreso SET NOT NULL;
DROP INDEX IF EXISTS idx_actuaciones_doc;   -- cubierto por idx_actuaciones_documento_doc_fecha
CREATE INDEX IF NOT EXISTS idx_documentos_estado     ON documentos (estado_actual);
CREATE INDEX IF NOT EXISTS idx_documentos_bibliorato ON documentos (bibliorato_id);

COMMIT;
```

**Antes de aplicarla, hay que comprobar:**
- Que ningún valor existente viole los nuevos `CHECK`.
- Que no haya pases pendientes duplicados, porque si los hay el índice único falla. Hoy no hay ninguno.

### 1.3 Ciclo de vida por categoría

| Categoría | Estados | Flujo |
|---|---|---|
| `TRAMITE` | `INGRESADO → EN_REVISION / EN_ESPERA_INFORME → PENDIENTE_SALIDA → FINALIZADO` | Derivaciones, informes, salida y acuse (como hoy). |
| `INFORMATIVO` | `ARCHIVADO` (estado nuevo, final) | Se registra y archiva **en el mismo acto**, con bibliorato y fojas obligatorios. No se deriva para informe. Se puede **difundir** (pase de conocimiento que no cambia el estado), no aparece en bandejas de trabajo ni en las alertas de demora, y sí en la búsqueda y en la consulta de biblioratos. |

---

## 2. Matriz de validaciones y reglas de negocio

Leyenda: **F** = frontend (HTML/JS), **B** = backend (servicio), **BD** = restricción en base de datos. Estado: ✅ implementado · ⚠️ parcial · ❌ falta.

### 2.1 Carga de documentos y archivos

| ID | Regla | F | B | BD | Estado |
|---|---|:-:|:-:|:-:|---|
| V-C01 | Tipo de documento, dependencia de origen y asunto obligatorios | ✔ | ✔ | ✔ | ✅ |
| V-C02 | N° de origen con formato `N/AAAA` o `S/N` | ✔ | ✔ | — | ✅ |
| V-C03 | CUTI único, correlativo anual, generado por el servidor (no editable) | — | ✔ | ✔ | ✅ |
| V-C04 | Solo se aceptan los campos del formulario (DTO, sin asignación masiva) | — | ✔ | — | ✅ |
| V-C05 | Archivo con contenido PDF real (firma `%PDF-`), no solo extensión | `accept` | ✔ | — | ✅ |
| V-C06 | Tamaño ≤ 20 MB con **mensaje amigable** al excederlo | ❌ | ⚠️ | — | ⚠️ H9: agregar chequeo JS de `file.size` y `@ControllerAdvice` para `MaxUploadSizeExceededException` |
| V-C07 | Lista blanca de formatos. Si se habilitan otros además de PDF (JPG/PNG para fotos de acuses), validar la firma de cada uno | — | ⚠️ | — | ⚠️ Hoy solo PDF; ampliar `ArchivoService` con un mapa extensión → firma |
| V-C08 | Hash SHA-256 y tipo MIME guardados en cada adjunto | — | ❌ | ❌ | ❌ H10 |
| V-C09 | Sin archivos huérfanos: si la transacción falla, se borra el archivo escrito | — | ❌ | — | ❌ H10: `TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)` |
| V-C10 | El nombre original se guarda solo como metadato; en disco se usa un nombre generado | — | ✔ | — | ✅ |
| V-C11 | Error de escritura en disco (sin espacio, sin permisos) → mensaje claro y rollback | — | ✔ | — | ✅ (la excepción revierte la transacción) |
| V-C12 | Tipo documental **activo** (no se puede ingresar con un tipo dado de baja) | ✔ | ❌ | — | ❌ El servicio acepta cualquier ID existente |
| V-C13 | Dependencia de origen **activa** | ✔ | ❌ | — | ❌ |
| V-C14 | Asunto con longitud máxima razonable (p. ej. 2.000) y sin solo espacios | ⚠️ | ⚠️ | — | ⚠️ Hoy se valida que no esté vacío; falta un máximo |

### 2.2 Derivaciones simples y múltiples

| ID | Regla | F | B | BD | Estado |
|---|---|:-:|:-:|:-:|---|
| V-D01 | Al menos un destino y observación de 5 caracteres o más | ✔ | ✔ | — | ✅ |
| V-D02 | Destinos dentro de los habilitados para el usuario | ✔ | ✔ | — | ✅ |
| V-D03 | **Destinos sin repetir** dentro de la misma petición (`distinct`) | ✔ | ❌ | — | ❌ H5 |
| V-D04 | **No crear un pase pendiente** a una dependencia que ya tiene uno pendiente del mismo documento. Responder "ya derivado, pendiente de acuse". | — | ❌ | ❌ | ❌ H5: validación en servicio + índice único parcial |
| V-D05 | Derivaciones **sucesivas** permitidas: un nuevo pase después de que el anterior se confirmó, devolvió o anuló | — | ✔ | — | ✅ |
| V-D06 | Si hay informes pendientes, una derivación de conocimiento **no** cambia el estado `EN_ESPERA_INFORME` | — | ❌ | — | ❌ H2: estado = `EN_ESPERA_INFORME` si existe algún pase vigente con `requiere_informe` |
| V-D07 | El pedido de informe se guarda en un campo (`requiere_informe`), no en el texto | — | ❌ | ❌ | ❌ H8 |
| V-D08 | No se deriva un expediente `FINALIZADO` (ni `ARCHIVADO`) | — | ✔ | — | ⚠️ Falta `ARCHIVADO` cuando exista |
| V-D09 | Concurrencia: dos derivaciones simultáneas del mismo expediente no pierden pases ni dejan un estado inconsistente | — | ❌ | ❌ | ❌ H6: `@Version` en `Documento` o `SELECT … FOR UPDATE` al inicio de derivar, anular, confirmar y cerrar |
| V-D10 | Una derivación múltiple es **atómica**: si un destino falla, no se crea ningún pase | — | ✔ | — | ✅ (una sola transacción) |
| V-D11 | Trazabilidad: cada destino genera un pase con emisora, receptora, usuario y fecha, más un evento de auditoría | — | ✔ | ✔ | ✅ |
| V-D12 | Anular un pase solo si está pendiente; retira el emisor y rechaza el receptor | — | ✔ | ⚠️ | ✅ en servicio; falta `CHECK` de coherencia (H7) |
| V-D13 | Doble clic en "Confirmar derivación" no duplica | ✔ | ❌ | ❌ | ⚠️ Solo JS (botón deshabilitado); V-D04 lo cubre en servidor |

### 2.3 Documentos informativos y biblioratos

| ID | Regla | F | B | BD | Estado |
|---|---|:-:|:-:|:-:|---|
| V-B01 | Tipo `INFORMATIVO` → no permite "Requiere informe" ni entra en `EN_ESPERA_INFORME` | ✔ | ✔ | — | ❌ H3 |
| V-B02 | Tipo `INFORMATIVO` → se registra directamente como `ARCHIVADO` con `fecha_hora_archivado` | — | ✔ | ✔ | ❌ H3 |
| V-B03 | Tipo con `requiere_bibliorato` → bibliorato **obligatorio** en el ingreso | ✔ | ✔ | — | ❌ H4 |
| V-B04 | El bibliorato se elige de un **catálogo activo** (no texto libre) | ✔ | ✔ | ✔ FK | ❌ H4 |
| V-B05 | Fojas: `foja_desde > 0`, `foja_hasta ≥ foja_desde`, sin superposición con otro documento del mismo bibliorato | ✔ | ✔ | ⚠️ | ❌ La superposición se valida en servicio |
| V-B06 | La ubicación física (código, descripción, armario/estante, fojas) se ve en el detalle, en las bandejas y en los resultados de búsqueda | ✔ | — | — | ❌ Hoy solo en Finalizados, como texto |
| V-B07 | Búsqueda por bibliorato (código o descripción) y listado "contenido del bibliorato X" | ✔ | ✔ | — | ❌ |
| V-B08 | Cambio de ubicación (traslado de bibliorato) auditado con origen, destino y motivo | — | ✔ | — | ❌ |
| V-B09 | Informativos excluidos de las alertas de demora y de los KPI de trámite | — | ✔ | — | ❌ |
| V-B10 | Difusión (pase de conocimiento) de un informativo permitida, sin cambiar su estado | — | ✔ | — | ❌ |

---

## 3. Casos de prueba

Columna **Hoy**: resultado esperado si se ejecuta sobre el código actual. ✅ pasa · ❌ falla (bug o falta implementar). **Nivel**: U = unitaria, I = integración (Spring + BD de prueba), E = extremo a extremo (navegador).

### 3.1 Carga de documentos

| ID | Escenario | Datos / pasos | Resultado esperado | Nivel | Hoy |
|---|---|---|---|---|---|
| CD-01 | Ingreso válido | Tipo Memorando, N° `120/2026`, prioridad NORMAL, asunto | CUTI siguiente, estado `INGRESADO`, evento `REGISTRO_INGRESO` | I | ✅ |
| CD-02 | N° origen inválido | `120-2026`, `ABC`, `12/26` | Rechazo "Formato no válido", sin CUTI consumido | U/I | ✅ |
| CD-03 | N° origen vacío | `""` | Se guarda `S/N` | I | ✅ |
| CD-04 | Sin tipo documental | `tipoDocumentoId` ausente | "Debe seleccionar el tipo de documento" | I | ✅ |
| CD-05 | ID de tipo inexistente (petición manipulada) | `tipoDocumentoId=9999` | "Tipo de documento no encontrado" | I | ✅ |
| CD-06 | Tipo documental inactivo | Tipo con `activo=false` | Rechazo | I | ❌ V-C12 |
| CD-07 | Asignación masiva | POST con `codigoCuti=X`, `estadoActual=FINALIZADO` | Se ignoran; CUTI y estado los asigna el servidor | I | ✅ |
| CD-08 | PDF válido de 19 MB | Escaneo real | Aceptado; versión vigente; tamaño registrado | I | ✅ |
| CD-09 | Archivo > 20 MB | PDF de 25 MB | Mensaje "supera el tamaño máximo (20 MB)" en la bandeja | E | ❌ H9 (página de error) |
| CD-10 | Extensión engañosa | `informe.pdf` con contenido de texto o `.exe` | "no es un PDF válido"; nada guardado | I | ✅ (probado) |
| CD-11 | Archivo vacío (0 bytes) | `vacio.pdf` | "Debe adjuntar un archivo PDF" | U | ✅ |
| CD-12 | PDF truncado (solo la cabecera `%PDF-`) | 5 bytes | Hoy se acepta; con hash y validación de `%%EOF`, rechazar | U | ❌ mejora opcional |
| CD-13 | Carpeta sin permisos o disco lleno | `app.upload.dir` de solo lectura | Mensaje de error; transacción revertida; sin filas en `adjuntos_documento` | I | ✅ |
| CD-14 | Falla de BD después de escribir el archivo | Forzar una excepción tras `guardarPdf` | El archivo escrito se elimina | I | ❌ H10 |
| CD-15 | Integridad | Subir, recalcular SHA-256 del archivo en disco | Coincide con `hash_sha256` | I | ❌ H10 |
| CD-16 | Nombre con caracteres especiales | `Nota Nº 5 – año.pdf` | Se guarda el nombre original como metadato; se descarga con ese nombre (UTF-8) | E | ✅ |
| CD-17 | Ingresos concurrentes | 10 hilos registrando a la vez | 10 CUTI distintos y correlativos, sin error de unicidad | I | ✅ por diseño (`pg_advisory_xact_lock`); falta la prueba de carga |

### 3.2 Derivaciones simples y múltiples de un mismo documento

| ID | Escenario | Pasos | Resultado esperado | Nivel | Hoy |
|---|---|---|---|---|---|
| DV-01 | Derivación simple | Derivar a Dpto. A para conocimiento | 1 pase pendiente; estado `EN_REVISION`; A lo ve en su bandeja | I | ✅ |
| DV-02 | Derivación múltiple | Derivar a A, B y C para informe | 3 pases independientes; estado `EN_ESPERA_INFORME` | I | ✅ |
| DV-03 | Destino repetido en la petición | `dependenciaDestinoId=40&dependenciaDestinoId=40` | 1 solo pase | I | ❌ H5 |
| DV-04 | Re-derivar con pase pendiente | Derivar a A; volver a derivar a A antes del acuse | Rechazo "ya tiene un pase pendiente de A" | I | ❌ H5 |
| DV-05 | Derivación sucesiva válida | Derivar a A → A confirma → derivar de nuevo a A | 2 pases (1 confirmado, 1 pendiente), ambos en el árbol | I | ✅ |
| DV-06 | Múltiple + sucesiva | Derivar a A y B (informe); B confirma y responde; derivar a C (informe) | Sigue `EN_ESPERA_INFORME` hasta que respondan A y C; luego `PENDIENTE_SALIDA` | I | ✅ |
| DV-07 | Conocimiento con informes pendientes | Derivar a A (informe); luego derivar a D (conocimiento) | **Sigue `EN_ESPERA_INFORME`**; al responder A pasa a `PENDIENTE_SALIDA` | I | ❌ H2 (pasa a `EN_REVISION` y no cierra solo) |
| DV-08 | Atomicidad | Derivar a A, B y un ID inexistente | Ningún pase creado; mensaje de error | I | ✅ |
| DV-09 | Destino no habilitado | JEFE_DEPTO deriva a una dependencia que no es hija | 403 / "No está habilitado" | I | ✅ |
| DV-10 | Concurrencia | 2 usuarios derivan el mismo expediente al mismo tiempo (A informe / B conocimiento) | Ambos pases creados; estado final `EN_ESPERA_INFORME`; o uno recibe "el expediente fue modificado, recargue" | I | ❌ H6 |
| DV-11 | Retirar uno de varios | Derivar a A y B (informe); retirar A; B responde | Pasa a `PENDIENTE_SALIDA` (A no cuenta) | I | ✅ |
| DV-12 | Rechazar el único pase | Derivar a A (informe); A rechaza | Vuelve a `EN_REVISION`; A pierde el acceso | E | ✅ (probado) |
| DV-13 | Expediente finalizado | Derivar un `FINALIZADO` | "No se puede derivar un expediente FINALIZADO" | I | ✅ |
| DV-14 | Trazabilidad completa | Tras DV-02 + DV-11 | Árbol: 3 pases (1 tachado con motivo), auditoría: `DERIVACION_MULTIPLE` + `PASE_RETIRADO` | E | ✅ |

### 3.3 Documentos informativos y biblioratos

| ID | Escenario | Pasos | Resultado esperado | Nivel | Hoy |
|---|---|---|---|---|---|
| BI-01 | Ingreso de circular | Tipo Circular, bibliorato `BIB-DTIC-2026-007`, fojas 12–14 | Estado `ARCHIVADO`; `fecha_hora_archivado`; no aparece en la bandeja de trabajo | I | ❌ H3 |
| BI-02 | Circular sin bibliorato | Tipo Circular sin bibliorato | "El bibliorato es obligatorio para Circular" | I/E | ❌ H4 |
| BI-03 | Bibliorato inexistente o inactivo | `bibliorato_id=999` / inactivo | Rechazo | I | ❌ |
| BI-04 | Fojas inválidas | `foja_desde=0`, `foja_hasta < foja_desde` | Rechazo (servicio y `CHECK`) | U/I | ❌ |
| BI-05 | Fojas superpuestas | Otro documento en el mismo bibliorato ocupa 10–15; cargar 14–16 | Advertencia o rechazo según la política | I | ❌ |
| BI-06 | Circular para informe | Intentar derivar una Circular "requiere informe" | Opción no visible; el servidor lo rechaza | E/I | ❌ |
| BI-07 | Difusión de circular | Pase de conocimiento a 5 dependencias | 5 pases; el estado sigue `ARCHIVADO` | I | ❌ |
| BI-08 | Consulta por ubicación | Buscar `BIB-DTIC-2026-007` | Lista de documentos con fojas, ordenada; muestra armario/estante | E | ❌ |
| BI-09 | Ubicación visible | Abrir cualquier documento archivado | Código, descripción, ubicación física y fojas en el detalle y en los resultados | E | ❌ (solo texto en Finalizados) |
| BI-10 | Traslado de bibliorato | Mover el documento de BIB-007 a BIB-008 | Nueva ubicación + evento `CAMBIO_UBICACION` con origen y destino | I | ❌ |
| BI-11 | Informativos fuera de SLA | Circular archivada hace 30 días | Sin alerta de demora; no cuenta en los KPI | I | ❌ |
| BI-12 | Trámite con bibliorato | Memorando finalizado con bibliorato | La ubicación se ve igual que en los informativos | E | ⚠️ (texto libre) |

### 3.4 Integridad de base de datos (ejecutar como `psql`/script)

| ID | Prueba | Esperado | Hoy |
|---|---|---|---|
| BD-01 | `DELETE FROM documentos WHERE id = <prueba>` | Falla por `RESTRICT`; la auditoría permanece | ❌ H1 (borra en cascada) |
| BD-02 | `UPDATE documentos SET estado_actual = 'XYZ'` | Falla por `CHECK` | ❌ H7 |
| BD-03 | Insertar 2 pases pendientes iguales | Falla por índice único parcial | ❌ H5 |
| BD-04 | `actuaciones_documento.anulada = true` sin motivo | Falla por `CHECK` | ❌ H7 |
| BD-05 | `documentos.fecha_hora_ingreso = NULL` | Falla por `NOT NULL` | ❌ H11 |

---

## 4. Plan de implementación sugerido

1. **Integridad (½ día):** H1, H7, H11 y el índice de H5 en la migración V3. Antes, el script de verificación de datos.
2. **Derivaciones (1 día):** H2, H5, H6 y H8. Agregar `requiere_informe`, `distinct` de destinos, validar pases pendientes y `@Version` en `Documento` con mensaje "recargue".
3. **Informativos y biblioratos (2–3 días):** H3 y H4.
   - Categoría de tipo, estado `ARCHIVADO`, catálogo de biblioratos con su pantalla de administración.
   - Selector de bibliorato y fojas en el ingreso, consulta por bibliorato y ubicación visible.
4. **Carga (½ día):** H9 y H10. Manejador de tamaño excedido, hash SHA-256, tipo MIME y limpieza de huérfanos.
5. **QA automatizado:** usar Testcontainers (PostgreSQL) para las pruebas de nivel I. Las de §3.1–3.3 marcadas ❌ deben pasar a ✅ antes del pase a producción.

La base de datos de las pruebas de integración debe ser **independiente** de la de desarrollo compartida. Hoy `contextLoads` usa la base real.
