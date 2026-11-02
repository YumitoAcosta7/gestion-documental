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
| `app.upload.dir` | Carpeta única de todos los PDF (acuses, copias selladas, versiones). Debe incluirse en las copias de respaldo. Por defecto `C:/gestion-documental-uploads/acuses` |
| `spring.jpa.open-in-view=true` | Necesario: las vistas Thymeleaf leen relaciones LAZY |

Todos los PDF, incluidos los históricos, están en `app.upload.dir`. Los archivos no se versionan en git.

**Migraciones de base de datos (Flyway).** Como Hibernate solo valida el esquema, cada cambio de modelo viene con un script `V<n>__<descripcion>.sql` en `db/migraciones/`. Desde la V10, **Flyway los aplica solo al arrancar**: ejecuta en una transacción cada script que falte y lo registra en `flyway_schema_history`. Por eso los scripts no llevan `BEGIN`/`COMMIT` propios. Un script ya aplicado no se modifica; cada cambio nuevo va en el siguiente número. La base existente se tomó como línea base en la V6, porque V2 a V6 se aplicaron a mano. La consulta de verificación previa a V3 quedó en `db/verificaciones/`.

| Script | Contenido |
|---|---|
| `V2__mejoras_operativas.sql` | Anulación lógica de actuaciones y versionado de adjuntos |
| `V3__integridad_y_derivaciones.sql` | Historial no borrable (`RESTRICT`), `CHECK` de dominios, `requiere_informe`, unicidad de pases pendientes, tipos de clave corregidos |
| `V4__roles_usuarios.sql` | Reasigna roles heredados (`JEFA_SECRETARIA`, `OPERADOR_MESA_ENTRADA`) a `SECRETARIA_ADMIN` y agrega el `CHECK` de rol |
| `V5__biblioratos_informativos.sql` | Categoría del tipo documental (trámite/informativo), catálogo `biblioratos`, ubicación de cada documento (bibliorato + fojas) |
| `V6__integridad_adjuntos.sql` | Huella SHA-256 y tipo MIME de cada adjunto |
| `V7__limpieza_esquema.sql` | Elimina tablas heredadas vacías (`solicitudes_informes`, `historial_estados`, `movimientos_documento`) y la columna sin uso `documentos.usuario_registra_salida_id`. Se cancela si alguna tiene datos |
| `V8__organigrama_y_asignaciones.sql` | Fase 2: sigla, `ruta` materializada (con trigger que la recalcula y rechaza ciclos), mesa de entrada y `recibe_externos` en `dependencias`; tabla `asignaciones` cargada desde los roles actuales |
| `V9__organigrama_RI887.sql` | Organigrama completo (Res. N° 887/08-07-2026, 580 dependencias). **Generado** por `db/organigrama/ImportarOrganigrama.java` desde `db/organigrama/organigrama_RI887_2026.txt`: se corrige el .txt y se regenera, no se edita a mano |
| `V10__asignaciones_director.sql` | Quien tenía el rol `DIRECTOR` recibe además `MESA_ENTRADA` en su ámbito, para conservar las acciones que tenía |
| `V11__custodia_y_canal.sql` | Etapa 2.2. Agrega dependencia de registro y de custodia a cada expediente, `tipo_pase` (`TRAMITE`, `INFORME`, `CONOCIMIENTO`) y las tablas `series_cuti` y `reglas_derivacion`. Completa los datos de los expedientes existentes |

**Ambiente de pruebas:** perfil `pruebas` (`application-pruebas.properties`), con base y carpeta de PDF propias. La preparación está en `docs/ambiente-pruebas.md`.

**Pruebas automáticas:**
- Las que usan base llevan `@PruebaConBaseDePruebas`: corren con el perfil `pruebas` y solo si está definida `DB_PASSWORD_PRUEBAS`; si no, se saltean.
- `mvnw test` **nunca se conecta a producción**, y las migraciones nuevas se aplican primero en la base de pruebas.
- El resto son unitarias y corren siempre.

**Integridad de archivos (V6):** cada PDF guarda su huella SHA-256 al subirse. El botón *Verificar* (historial del PDF y modal *Versiones*) la compara con el archivo en disco. Se rechazan los PDF incompletos (sin `%%EOF`). Si la operación falla, el archivo escrito se elimina.

Hay dos límites de tamaño:
- `spring.servlet.multipart.max-file-size=50MB`: lo que Tomcat acepta recibir.
- `app.upload.max-pdf-size=20MB`: lo que acepta la aplicación, con un mensaje claro para el usuario.

El primero debe ser mayor que el segundo; si no, el filtro CSRF hace que Tomcat responda 413 sin mensaje.

**Documentos informativos y archivo físico (V5):**
- **Tipos informativos** (hoy: Circular): se registran directamente como `ARCHIVADO`, con bibliorato obligatorio. No se derivan para informe; solo se difunden para conocimiento, sin cambiar su estado. Quedan fuera de las bandejas de trabajo, de las alertas de demora y de los indicadores.
- **Biblioratos:** catálogo administrado por Dirección/Secretaría (botón *Biblioratos*), con código único, ubicación física (armario/estante) y capacidad. Un bibliorato cerrado no recibe documentos nuevos.
- **Ubicación:** cada documento puede tener bibliorato y fojas. Se valida el rango, la capacidad y la superposición con otros documentos del mismo bibliorato.
  - El traslado exige motivo y queda auditado como `CAMBIO_UBICACION`.
  - El texto libre `ubicacion_bibliorato` se conserva solo como dato histórico.
- **Consulta:** pestaña *Archivo físico* (con filtro por bibliorato), filtro en el buscador, búsqueda por código o descripción y ubicación visible en Finalizados.

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
- `sigla` (única: `DTIC`, `DGAF`, `DREC-D02`...), `ruta` (`/1/10/40/`, la mantiene un trigger), `es_mesa_entrada`, `recibe_externos`, `resolucion`

### `asignaciones` — permiso = rol × ámbito × vigencia
- `usuario_id`, `rol` (`ADMIN_SISTEMA`, `MESA_ENTRADA`, `RESPONSABLE`, `OPERADOR`, `CONSULTA`), `dependencia_ambito_id`, `incluye_subordinadas`
- `vigente_desde`, `vigente_hasta` (NULL = indefinida), `es_suplencia`, `titular_usuario_id`, `acto_administrativo`, `activa`, `creada_por_id` (NULL = carga inicial V8)

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
- `tipo_actuacion`: `PASE_INTERNO`, `PASE_CONFIRMADO`, `DEVOLUCION`, `INFORME_TECNICO`, `MEMO`, `ACTA_ENTREGA`, `PROVIDENCIA`, `CIERRE_EXCEPCIONAL`
- `numero_actuacion` (`N/AAAA` o `S/N`), `descripcion_resumen` (TEXT), `ubicacion_bibliorato`
- `ruta_copia_sellada_pdf` (PDF vigente de la actuación), `pase_confirmado` (BOOLEAN), `fecha_hora_actuacion`
- `actuacion_padre_id` (FK recursiva, reservada; el árbol se arma por dependencias, ver §5)
- Anulación lógica (V2): `anulada`, `tipo_anulacion` (`RETIRADO`, `RECHAZADO`), `motivo_anulacion`, `fecha_hora_anulacion`, `usuario_anulacion_id`

### `auditorias_documento` — bitácora de eventos
- `id` (BIGINT, PK), `documento_id` (FK), `tipo_evento`, `estado_anterior`, `estado_nuevo`, `descripcion_detalle`
- `usuario_username`, `usuario_nombre_completo`, `dependencia_usuario`, `direccion_ip` (IP real de la petición), `fecha_hora`

### `adjuntos_documento` — todas las versiones de los PDF
- `id` (BIGINT, PK), `documento_id` (FK), `nombre_archivo`, `ruta_almacenamiento`
- `tipo_adjunto` (`ACUSE_SALIDA`, `ACUSE_ENTREGA`, `COPIA_SELLADA`, `ACUSE_PASE`), `tamanio_bytes`, `fecha_hora_subida`, `usuario_subida_id` (FK)
- Versionado (V2): `actuacion_id` (FK, null = adjunto del documento), `vigente`, `reemplazado_por_id` (FK), `motivo_reemplazo`

---

## 4. 👥 ROLES Y CONTROL DE ACCESO

Las acciones las decide un único componente, `PoliticaAcceso`, a partir de las **asignaciones vigentes hoy** (rol × ámbito × vigencia). Se recalculan en cada petición (`ActualizacionPermisosFilter`): una asignación nueva o una suplencia vencida rige sin volver a iniciar sesión. Spring Security las recibe como autoridades `PERMISO_<ACCION>`, que usan tanto las reglas por URL como los botones de la pantalla.

| Rol de la asignación | Acciones |
|---|---|
| `MESA_ENTRADA` | Todas: ingreso, salida y acuse final (`REGISTRAR_SALIDA`), derivar, actuaciones, archivo físico y biblioratos, cierre forzado, auditoría, supervisar los pases de otras dependencias del ámbito |
| `RESPONSABLE` | Derivar, actuaciones, cierre forzado, auditoría |
| `OPERADOR` | Actuaciones: informes, memos, devoluciones, confirmar pases recibidos |
| `ADMIN_SISTEMA` | Archivo físico y biblioratos, auditoría |
| `CONSULTA` | Solo lectura |
| (sin asignaciones vigentes) | Como `OPERADOR` de su propia dependencia |

Un usuario con varias asignaciones suma sus acciones. `usuarios.rol` ya no se usa para autorizar; se conserva hasta retirarlo.

**Acceso por ámbito** (`DocumentoService.puedeAccederDocumento`): cada usuario accede a los expedientes de su ámbito, es decir, de las dependencias que abarcan sus asignaciones vigentes hoy (con sus subordinadas, por la `ruta` del organigrama). Un expediente está en el ámbito si una de esas dependencias lo envió, lo registró, lo derivó o lo recibió. Sin asignaciones vigentes, el ámbito es solo la propia dependencia. Además, como antes, se accede a los expedientes en los que la propia dependencia emitió o recibió una actuación. Se aplica a bandejas, árbol, informes, PDF, acuses (HTTP 403), buscador, archivo físico, registrar actuación y derivar. Las asignaciones iniciales (V8): Director → `RESPONSABLE` de la Dirección de TIC; Secretaría → `MESA_ENTRADA` de la Dirección de TIC; jefes → `RESPONSABLE` de su dependencia.

Reglas adicionales:
- Solo se deriva a las dependencias que cuelgan directamente de los ámbitos de gestión del usuario (`MESA_ENTRADA` o `RESPONSABLE`); sin ellos, a las hijas de su propia dependencia. No hay IDs de dependencia escritos en el código.
- Solo la dependencia receptora confirma un pase, salvo quien tenga `SUPERVISAR_PASES` (mesa de entrada).
- El ingreso usa `NuevoDocumentoDTO`: el navegador no puede fijar CUTI, estado ni usuarios.
- Los botones de la interfaz se muestran según las acciones habilitadas.

---

## 5. 💼 LÓGICA DE NEGOCIO

- **CUTI por cabecera:** `SIGLA-AAAA-NNNNNN`, con una serie anual por Dirección, Dirección General e Intendencia (`DTIC-2026-000001`, `DHAC-2026-000001`).
  - El número sale de la cabecera de la dependencia que registra el expediente, y el expediente lo conserva en todo su recorrido.
  - El correlativo se incrementa en `series_cuti` con una sola sentencia que bloquea la fila hasta el commit, así dos registros simultáneos no repiten número.
  - Los CUTI `TIC-2026-…` anteriores se conservan.
- **Custodia:** cada expediente sabe qué dependencia tiene el original (`dependencia_custodia_id`).
  - Al registrarse queda en la mesa (o dependencia) que lo registra.
  - Cambia solo cuando el receptor confirma un pase de **trámite**, o con una devolución.
- **Canal jerárquico** (`Organigrama`, `CanalService`, diseño §4.3): se deriva **desde quien tiene el original** y solo a estos destinos:
  - su superior inmediato;
  - sus subordinadas directas;
  - otra cabecera con el mismo superior.

  Un pase dirigido a una cabecera llega a su mesa de entrada, que actúa en su nombre. Entre direcciones de distintas Direcciones Generales se pasa por ellas (Hacienda → DGAF → DTIC). La tabla `reglas_derivacion` permite habilitar atajos sin tocar código. Lo controla el servidor; el modal de derivación pide los destinos a `GET /api/documentos/{id}/destinos`.
- **Tipos de pase:**
  - `TRAMITE` entrega el original a una sola dependencia y no puede haber dos pendientes a la vez.
  - `INFORME` (`EN_ESPERA_INFORME`) y `CONOCIMIENTO` (`EN_REVISION`) van a una o varias dependencias y no mueven la custodia.

  Cada destino genera un `PASE_INTERNO` pendiente de acuse.
- **Documento propio:** cualquier dependencia (unidad, departamento, dirección) inicia un expediente con su documento (`POST /documentos/propio`), con el CUTI de su cabecera, y puede elevarlo en el mismo acto a su superior con un pase de trámite.
- **Bandejas** (`BandejaService`, selector arriba de "Trámites en Curso", `GET /?bandeja=…&pagina=…`). Todas se calculan sobre el ámbito del usuario (ver §4):
  - **Todos:** la lista completa de antes; se mantiene durante la transición.
  - **Por recibir:** pases pendientes de acuse dirigidos al ámbito.
  - **En mi poder:** expedientes abiertos cuyo original está en el ámbito.
  - **Informes pendientes:** pases de informe al ámbito sin informe ni memo de respuesta.
  - **En seguimiento:** expedientes abiertos que el ámbito registró o derivó y cuyo original está en otra dependencia.

  Salvo "Todos", se resuelven con una sola consulta paginada (20 por página) y muestran su contador. En "Todos", los pases antiguos sin receptora se identifican por la descripción `➔ <dependencia>:`.
- **Confirmación de pase:** la dependencia receptora (o quien actúa por ella, como la mesa de su dirección) sube la copia sellada en PDF y el pase pasa a `PASE_CONFIRMADO`. Si era de trámite, el original pasa al receptor.
- **Cierre automático de informes:** cuando **cada** dependencia a la que se pidió informe emitió un `INFORME_TECNICO` o `MEMO`, el expediente pasa solo de `EN_ESPERA_INFORME` a `PENDIENTE_SALIDA`.
- **Devolución rápida:** la dependencia devuelve el expediente a quien le envió el último pase recibido (actuación `DEVOLUCION`), el estado vuelve a `EN_REVISION`. No se puede devolver dos veces el mismo pase ni devolver a la propia dependencia.
- **Salida exterior:** registra nota, destino, mensajero y funcionario. No aplica a expedientes `FINALIZADO`.
- **Salida directa (documentos propios):** crea un documento originado en la dependencia y lo deja en `PENDIENTE_SALIDA` con la salida ya registrada.
- **Acuse final:** exige `PENDIENTE_SALIDA`, salida física registrada y PDF; el expediente pasa a `FINALIZADO`.
- **Árbol de trazabilidad:** cada actuación cuelga del último pase o devolución que recibió la dependencia que la emitió, formando un árbol de varios niveles.
- **Auditoría:** cada ingreso, derivación, devolución, actuación, confirmación, salida y cambio de estado queda registrado con usuario, dependencia e IP.

---

## 6. 🌐 ENDPOINTS (`HomeController`, `LoginController`)

| Método y ruta | Descripción | Permiso |
|---|---|---|
| `GET /login` | Formulario de acceso | Público |
| `GET /` | Dashboard: KPIs, activos, salidas propias, finalizados | Autenticado |
| `GET /documentos/buscar` | Buscador multicriterio (filtrado por acceso) | Autenticado |
| `POST /documentos/guardar` | Ingreso de expediente | `REGISTRAR_INGRESO` |
| `POST /documentos/derivar` | Derivación única o múltiple | `DERIVAR` |
| `POST /documentos/devolver` | Devolución rápida | `REGISTRAR_ACTUACION` (según pases recibidos) |
| `POST /documentos/salida` | Salida exterior | `REGISTRAR_SALIDA` |
| `POST /documentos/salida-directa` | Documento propio + salida | `REGISTRAR_SALIDA` |
| `POST /documentos/confirmar-acuse` | Acuse final → `FINALIZADO` | `REGISTRAR_SALIDA` |
| `POST /documentos/actuaciones/guardar` | Informe, memo, acta o providencia | `REGISTRAR_ACTUACION` |
| `POST /documentos/actuaciones/confirmar-pase` | Confirmar recepción de pase con PDF | Dependencia receptora |
| `GET /documentos/actuaciones/{id}/pdf` | PDF de una actuación | Con acceso al expediente |
| `GET /documentos/descargar-acuse/{documentoId}` | Último adjunto (acuse) del documento | Con acceso al expediente |
| `GET /api/documentos/{id}/actuaciones` | JSON para el árbol de trazabilidad | Con acceso al expediente |
| `GET /api/documentos/{id}/informes-disponibles` | JSON de informes/memos/providencias | Con acceso al expediente |
| `GET /api/documentos/{id}/auditoria` | JSON de la bitácora | `VER_AUDITORIA` |

| `POST /documentos/actuaciones/anular-pase` | Retirar (emisor) o rechazar (receptor) un pase pendiente, con motivo | Dependencia emisora/receptora |
| `POST /documentos/forzar-cierre` | Cierre excepcional de informes → `PENDIENTE_SALIDA`, con justificación | `FORZAR_CIERRE` |
| `POST /documentos/actuaciones/reemplazar-pdf` | Nueva versión del PDF de una actuación | Dependencia que lo cargó o `SUPERVISAR_PASES` |
| `POST /documentos/reemplazar-acuse` | Nueva versión del acuse del expediente | `REGISTRAR_SALIDA` |
| `GET /documentos/adjuntos/{id}/pdf` | Descarga de una versión concreta (vigente o reemplazada) | Con acceso al expediente |
| `GET /api/documentos/{id}/adjuntos` | JSON de versiones de los acuses del expediente | Con acceso al expediente |

Todos los formularios muestran el resultado como mensaje en pantalla (éxito o error).

---

## 7. 🧩 MEJORAS OPERATIVAS (rama `feature/mejoras-operativas`)

Requieren aplicar antes `db/migraciones/V2__mejoras_operativas.sql` (ver §2).

**Principio común: nada se borra.** Pases y archivos se anulan o reemplazan de forma lógica, con motivo, usuario y fecha, y quedan visibles en el historial y en la auditoría.

- **Retirar / rechazar pases** (`actuaciones_documento.anulada`, `tipo_anulacion`, `motivo_anulacion`, `fecha_hora_anulacion`, `usuario_anulacion_id`):
  - Solo pases `PASE_INTERNO` todavía sin copia sellada.
  - *Retirar*: la dependencia emisora o Dirección/Secretaría. *Rechazar*: la dependencia receptora.
  - El pase anulado sale de la bandeja del receptor, no cuenta para informes pendientes ni para devoluciones, y en el árbol aparece tachado con el motivo.
  - Si ya no queda ningún pase vigente pidiendo informe, el expediente vuelve de `EN_ESPERA_INFORME` a `EN_REVISION`.
- **Cierre excepcional** (Dirección/Secretaría): pasa el expediente a `PENDIENTE_SALIDA` sin esperar las respuestas. Justificación obligatoria (mín. 20 caracteres). Queda como actuación `CIERRE_EXCEPCIONAL` en el árbol y como evento `CIERRE_FORZADO` en la auditoría, con la lista de dependencias que no respondieron.
- **Versionado de PDF** (`adjuntos_documento.actuacion_id`, `vigente`, `reemplazado_por_id`, `motivo_reemplazo`):
  - Cada PDF subido es una fila. Reemplazar crea una versión nueva y marca la anterior como reemplazada, con motivo.
  - Los archivos nunca se sobrescriben en disco.
  - El árbol muestra el historial de versiones; los finalizados tienen el botón *Versiones*.
- **Alertas de demora (SLA):** días **hábiles** (lunes a viernes) desde el último movimiento vigente (o el ingreso).
  - Badge amarillo desde `app.sla.dias-advertencia` (5) y rojo desde `app.sla.dias-critico` (10).
  - Los expedientes URGENTES usan umbrales propios (2 / 4).
  - Se calcula con una sola consulta por bandeja. No contempla feriados.
- **Validación de PDF:** límite de 20 MB (antes Spring rechazaba todo archivo de más de 1 MB). Se comprueba la firma `%PDF-` del contenido, no solo la extensión.
- **Suplencias:** diseño en [docs/diseno-suplencias.md](docs/diseno-suplencias.md), pendiente de decisiones institucionales.

---

## 8. 🎯 ESTADO ACTUAL Y PENDIENTES

**Estado:** compila (`BUILD SUCCESS`). Flujos probados contra la BD: ingreso, salida directa, devolución, bloqueo de doble devolución, control de acceso por dependencia, árbol y PDF de actuaciones.

**Pendientes conocidos:**
- Probar en uso real el acuse final y la confirmación de pases con la nueva carpeta de PDF.
- El modal de salida envía `tipoRemision` e `informeActuacionId` (legajo), pero todavía no se guardan.
- Expedientes que pasaron a `PENDIENTE_SALIDA` antes de la corrección del conteo de informes (por ejemplo TIC-2026-000001) deben revisarse manualmente.
- Fase 2, etapa 2.2 (circulación): completa en código, con custodia, canal jerárquico, tipos de pase, CUTI por cabecera, documentos propios y bandejas. Etapa 2.1 (`docs/diseno-fase2-multiarea.md` §10): completa en código. Están hechos el organigrama, las asignaciones, la visibilidad por ámbito, `PoliticaAcceso`, la eliminación de los IDs fijos, el CUTI configurable y Flyway. Falta crear la base de pruebas en el servidor (`docs/ambiente-pruebas.md`).
- Pruebas automatizadas: 36 unitarias (`DiasHabilesTest`, `ValidacionFojasTest`, `ArchivoServiceTest`, `PoliticaAccesoTest`, `OrganigramaTest`) y el arranque de la aplicación sobre la base de pruebas. Faltan pruebas de integración de los flujos (ingreso, derivación, confirmación) sobre esa base.
- Feriados nacionales/municipales para el SLA (requiere una tabla de feriados).
- Paginación de bandejas para grandes volúmenes (hoy se cargan todos los expedientes activos).
