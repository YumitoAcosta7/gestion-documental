# 📁 SISTEMA DE GESTIÓN DOCUMENTAL - MUNICIPALIDAD DE ASUNCIÓN (DIRECCIÓN DE TIC)

Especificación técnica del sistema **Gestión Documental**: mesa de entrada digital de la Dirección de TIC para el registro, derivación, trazabilidad, auditoría y despacho de expedientes identificados por CUTI.

> Documento actualizado a partir del código fuente (rama `fix/fase1-estabilizacion`). Reemplaza a la versión anterior, que tenía diferencias con la implementación real.

---

## 1. 🛠️ STACK TECNOLÓGICO

- **Backend:** Java 25, Spring Boot 4.1.0 (Spring MVC)
- **Seguridad:** Spring Security — login por formulario contra la BD, contraseñas BCrypt, autorización por rol, token CSRF en cookie
- **Persistencia:** Spring Data JPA / Hibernate, `ddl-auto=validate` (el esquema se administra en la BD, la aplicación solo lo valida)
- **Base de Datos:** PostgreSQL (servidor externo)
- **Construcción:** Maven (`./mvnw`)
- **Frontend:** Thymeleaf + `thymeleaf-extras-springsecurity6`, Bootstrap 5.3.0, Bootstrap Icons 1.11.0, JavaScript con Fetch API
- **Utilidades:** Lombok

---

## 2. ⚙️ CONFIGURACIÓN Y EJECUCIÓN

`src/main/resources/application.properties` no contiene credenciales. La contraseña de la BD se toma, en este orden:

1. Archivo local `./application-local.properties` (en la raíz del proyecto, **ignorado por git**):
   ```properties
   spring.datasource.password=********
   ```
2. Variable de entorno `DB_PASSWORD`.

Otras propiedades relevantes:

| Propiedad | Uso |
|---|---|
| `spring.datasource.url` | `jdbc:postgresql://<servidor>:5432/gestion_documental` |
| `app.upload.dir` | Carpeta donde se guardan todos los PDF nuevos (acuses, copias selladas). Por defecto `C:/gestion-documental-uploads/acuses` |
| `spring.jpa.open-in-view=true` | Necesario: las vistas Thymeleaf leen relaciones LAZY |

Los PDF antiguos que quedaron en `./uploads/acuses` se siguen encontrando; los nuevos van a `app.upload.dir`. La carpeta `uploads/` no se versiona.

Ejecutar:

```bash
./mvnw spring-boot:run
```

La aplicación queda en `http://localhost:8080`.

Si se publica detrás de un proxy inverso, agregar `server.forward-headers-strategy=native` para que la auditoría registre la IP real del cliente.

---

## 3. 🗄️ ESTRUCTURA DE BASE DE DATOS

Nombres de tablas y columnas tal como están mapeados en las entidades JPA.

### `dependencias` — estructura orgánica jerárquica
- `id` (INT, PK), `nombre`, `nivel_jerarquico_id` (1: Intendencia, 3: Dirección General, 4: Dirección, 6: Departamento, 7: Unidad)
- `dependencia_padre_id` (FK recursiva a `dependencias.id`), `es_interna_tic` (BOOLEAN), `activo` (BOOLEAN)

### `niveles_jerarquicos`
- `id` (INT, PK), `nombre` (único), `orden_nivel`

### `usuarios`
- `id` (INT, PK), `nombre_completo`, `username` (único), `password_hash` (BCrypt), `email`
- `dependencia_id` (FK), `rol` (VARCHAR, un rol por usuario), `activo`

### `tipos_documentos`
- `id` (INT, PK), `nombre` (único: Nota, Proveído, Memorándum, Circular, etc.), `descripcion`, `activo`

### `documentos` — expedientes centralizados por CUTI
- `id` (BIGINT, PK), `codigo_cuti` (único, `TIC-AAAA-NNNNNN`), `anio`
- `numero_documento_origen` (`N/AAAA` o `S/N`), `tipo_documento_id` (FK), `dependencia_origen_id` (FK)
- `prioridad` (`NORMAL`, `IMPORTANTE`, `URGENTE`), `asunto` (TEXT)
- `estado_actual` (`INGRESADO`, `EN_REVISION`, `EN_ESPERA_INFORME`, `CON_RESPUESTA_TECNICA`, `PENDIENTE_SALIDA`, `FINALIZADO`)
- Ingreso: `fecha_hora_ingreso`, `persona_entrega`, `nro_orden_libro`, `nro_pagina_libro`, `funcionario_recibe_fisico`, `usuario_carga_ingreso_id`, `fecha_hora_carga_ingreso`, `usuario_recepcion_id` (**NOT NULL**)
- Salida: `dependencia_destino_salida_id`, `nro_nota_salida`, `fecha_hora_salida`, `persona_retira_salida`, `funcionario_entrega_fisico`, `usuario_carga_salida_id`, `fecha_hora_carga_salida`
- Archivo: `archivado`, `ubicacion_bibliorato`, `fecha_hora_archivado`

### `actuaciones_documento` — pases, informes, memos y devoluciones
- `id` (BIGINT, PK), `documento_id` (FK), `usuario_id` (FK)
- `dependencia_emisora_id` (FK), `dependencia_receptora_pase_id` (FK, en pases y devoluciones)
- `tipo_actuacion`: `PASE_INTERNO`, `PASE_CONFIRMADO`, `DEVOLUCION`, `INFORME_TECNICO`, `MEMO`, `ACTA_ENTREGA`, `PROVIDENCIA`
- `numero_actuacion` (`N/AAAA` o `S/N`), `descripcion_resumen` (TEXT), `ubicacion_bibliorato`
- `ruta_copia_sellada_pdf` (nombre del archivo PDF), `pase_confirmado` (BOOLEAN), `fecha_hora_actuacion`
- `actuacion_padre_id` (FK recursiva, reservada; el árbol se arma por dependencias, ver §5)

### `auditorias_documento` — bitácora de eventos
- `id` (BIGINT, PK), `documento_id` (FK), `tipo_evento`, `estado_anterior`, `estado_nuevo`, `descripcion_detalle`
- `usuario_username`, `usuario_nombre_completo`, `dependencia_usuario`, `direccion_ip` (IP real de la petición), `fecha_hora`

### `adjuntos_documento` — PDF a nivel de documento
- `id` (BIGINT, PK), `documento_id` (FK), `nombre_archivo`, `ruta_almacenamiento`
- `tipo_adjunto` (`ACUSE_SALIDA`, `ACUSE_ENTREGA`), `tamanio_bytes`, `fecha_hora_subida`, `usuario_subida_id` (FK)

---

## 4. 👥 ROLES Y CONTROL DE ACCESO

| Rol | Puede |
|---|---|
| `DIRECTOR` | Todo: ingreso, salida, salida directa, acuse final, derivar, actuaciones, auditoría. Ve todos los expedientes. |
| `SECRETARIA_ADMIN` | Igual que `DIRECTOR`. |
| `JEFE_DEPTO` | Derivar a sus unidades hijas, registrar actuaciones, devolver, confirmar pases recibidos. |
| `JEFE_UNIDAD` | Registrar actuaciones, devolver, confirmar pases recibidos. |

**Acceso por dependencia** (`DocumentoService.puedeAccederDocumento`): Dirección y Secretaría acceden a todo; los demás solo a expedientes originados en su dependencia o en los que su dependencia emitió o recibió una actuación. Se aplica a árbol, informes, PDF, acuses (HTTP 403), buscador, registrar actuación y derivar.

Reglas adicionales:
- Solo se deriva a las dependencias habilitadas para el usuario (hijas de su dependencia; Dirección/Secretaría: hijas de la Dirección de TIC).
- Solo la dependencia receptora confirma un pase (o Dirección/Secretaría).
- El ingreso usa `NuevoDocumentoDTO`: el navegador no puede fijar CUTI, estado ni usuarios.
- Los botones de la interfaz se muestran según el rol.

---

## 5. 💼 LÓGICA DE NEGOCIO

- **CUTI:** `TIC-AAAA-NNNNNN`. Se toma un bloqueo transaccional de PostgreSQL (`pg_advisory_xact_lock`) y se usa el último correlativo del año + 1, de modo que dos registros simultáneos no repiten número.
- **Bandejas:** Dirección y Secretaría ven todo. Departamentos y unidades ven los expedientes en los que su dependencia es emisora o receptora de un pase. Para pases antiguos sin receptora se usa la descripción `➔ <dependencia>:`.
- **Derivación flexible:** a una dependencia (modo único) o a varias (modo copias), para conocimiento (`EN_REVISION`) o para informe (`EN_ESPERA_INFORME`). Cada destino genera un `PASE_INTERNO` pendiente de acuse.
- **Confirmación de pase:** la dependencia receptora sube la copia sellada en PDF; el pase pasa a `PASE_CONFIRMADO`.
- **Cierre automático de informes:** cuando **cada** dependencia a la que se pidió informe emitió un `INFORME_TECNICO` o `MEMO`, el expediente pasa solo de `EN_ESPERA_INFORME` a `PENDIENTE_SALIDA`.
- **Devolución rápida:** la dependencia devuelve el expediente a quien le envió el último pase recibido (actuación `DEVOLUCION`), el estado vuelve a `EN_REVISION`. No se puede devolver dos veces el mismo pase ni devolver a la propia dependencia.
- **Salida exterior:** registra nota, destino, mensajero y funcionario. No aplica a expedientes `FINALIZADO`.
- **Salida directa (documentos propios):** crea un documento originado en la dependencia y lo deja en `PENDIENTE_SALIDA` con la salida ya registrada.
- **Acuse final:** exige `PENDIENTE_SALIDA`, salida física registrada y PDF; el expediente pasa a `FINALIZADO`.
- **Árbol de trazabilidad:** cada actuación cuelga del último pase o devolución que recibió la dependencia que la emitió, formando un árbol de varios niveles.
- **Auditoría:** cada ingreso, derivación, devolución, actuación, confirmación, salida y cambio de estado queda registrado con usuario, dependencia e IP.

---

## 6. 🌐 ENDPOINTS (`HomeController`, `LoginController`)

| Método y ruta | Descripción | Roles |
|---|---|---|
| `GET /login` | Formulario de acceso | Público |
| `GET /` | Dashboard: KPIs, activos, salidas propias, finalizados | Autenticado |
| `GET /documentos/buscar` | Buscador multicriterio (filtrado por acceso) | Autenticado |
| `POST /documentos/guardar` | Ingreso de expediente | DIRECTOR, SECRETARIA_ADMIN |
| `POST /documentos/derivar` | Derivación única o múltiple | DIRECTOR, SECRETARIA_ADMIN, JEFE_DEPTO |
| `POST /documentos/devolver` | Devolución rápida | Autenticado (según pases recibidos) |
| `POST /documentos/salida` | Salida exterior | DIRECTOR, SECRETARIA_ADMIN |
| `POST /documentos/salida-directa` | Documento propio + salida | DIRECTOR, SECRETARIA_ADMIN |
| `POST /documentos/confirmar-acuse` | Acuse final → `FINALIZADO` | DIRECTOR, SECRETARIA_ADMIN |
| `POST /documentos/actuaciones/guardar` | Informe, memo, acta o providencia | Todos los roles |
| `POST /documentos/actuaciones/confirmar-pase` | Confirmar recepción de pase con PDF | Dependencia receptora |
| `GET /documentos/actuaciones/{id}/pdf` | PDF de una actuación | Con acceso al expediente |
| `GET /documentos/descargar-acuse/{documentoId}` | Último adjunto (acuse) del documento | Con acceso al expediente |
| `GET /api/documentos/{id}/actuaciones` | JSON para el árbol de trazabilidad | Con acceso al expediente |
| `GET /api/documentos/{id}/informes-disponibles` | JSON de informes/memos/providencias | Con acceso al expediente |
| `GET /api/documentos/{id}/auditoria` | JSON de la bitácora | DIRECTOR, SECRETARIA_ADMIN |

Todos los formularios muestran el resultado como mensaje en pantalla (éxito o error).

---

## 7. 🎯 ESTADO ACTUAL Y PENDIENTES

**Estado:** compila (`BUILD SUCCESS`). Flujos probados contra la BD: ingreso, salida directa, devolución, bloqueo de doble devolución, control de acceso por dependencia, árbol y PDF de actuaciones.

**Pendientes conocidos:**
- Probar en uso real el acuse final y la confirmación de pases con la nueva carpeta de PDF.
- `DocumentoService.obtenerDependenciasHijas` y `obtenerDireccionRaiz` usan IDs fijos (1, 10, 11); conviene pasarlos a configuración.
- El modal de salida envía `tipoRemision` e `informeActuacionId` (legajo), pero todavía no se guardan.
- `spring.jpa.show-sql=true` está activo; desactivarlo en producción.
- Expedientes que pasaron a `PENDIENTE_SALIDA` antes de la corrección del conteo de informes (por ejemplo TIC-2026-000001) deben revisarse manualmente.
- No hay pruebas automatizadas además de `contextLoads`.
