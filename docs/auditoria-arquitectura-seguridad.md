# Auditoría de arquitectura, seguridad y QA — Gestión Documental

**Fecha:** 02/11/2026.
**Alcance:** código de `feature/administracion-usuarios` (etapas 2.1 a 2.3) y base de producción, consultada en modo solo lectura.

**Método:** cada hallazgo se verificó contra el código o contra la base. No hubo pruebas de penetración ni escaneos automáticos.

**Severidad:**
- **Crítico:** un usuario accede o modifica lo que no debe, en cualquier dirección.
- **Alto:** compromete la seguridad o la integridad en condiciones realistas.
- **Medio:** caso de borde o deuda con riesgo acotado.
- **Bajo:** rendimiento o endurecimiento.

## Lo que está bien

- **Inyección SQL:** las consultas nativas usan parámetros. `BandejaService` concatena solo constantes del `enum`, nunca datos del usuario.
- **CUTI:** `INSERT … ON CONFLICT … RETURNING` sobre `series_cuti` es atómico.
  - Bloquea solo la fila de esa cabecera y año.
  - Si la transacción se deshace, el número no se consume, así que no quedan huecos.
  - Un expediente que no se guarda no deja el número tomado.
- **Concurrencia:** derivar, confirmar y anular toman `SELECT … FOR UPDATE` sobre el expediente. Además, no puede haber dos pases de trámite pendientes a la vez.
- **Sesión y formularios:** CSRF activo, BCrypt, contraseñas que nunca se registran (ni la temporal) y plantillas con `th:text`, que escapa el contenido.
- **Rol de administrador:** `ADMIN_SISTEMA` no cuenta para la visibilidad de expedientes (`CTE_AMBITO`).
- **Emisor de las actuaciones:** se toma del servidor (`usuarioCarga.getDependencia()`), no del formulario.

---

## 1. Hallazgos

### CRÍTICO

**C1 — Acciones sobre expedientes ajenos por id (IDOR).** ✅ **Corregido (02/11/2026).**
- Salida, acuse final, cierre forzado, ubicación y reemplazo del acuse pasan por `exigirCustodia`, que verifica el acceso y que el usuario actúe por quien tiene el original.
- Confirmar y anular pases verifican el acceso, y `SUPERVISAR_PASES` quedó acotado al ámbito (`supervisaPase`).
- Prueba: `ControlAccesoExpedienteTest`.

Estas operaciones verifican el **permiso**, pero no el **acceso al expediente** ni la **custodia**:

| Operación | Permiso | ¿Verifica acceso? | ¿Verifica custodia? |
|---|---|---|---|
| `registrarSalidaDocumento` | `REGISTRAR_SALIDA` | No | No |
| `confirmarEntregaYAcuse` (finaliza) | `REGISTRAR_SALIDA` | No | No |
| `forzarCierreInformes` | `FORZAR_CIERRE` | No | No |
| `cambiarUbicacion` | `GESTIONAR_ARCHIVO` | No | No |
| `reemplazarAdjuntoDocumento` | `REGISTRAR_SALIDA` | No | No |
| `confirmarRecepcionPaseInterno`, `anularPase` | `SUPERVISAR_PASES` es **global** | No | — |

*Escenario:*
- La mesa de entrada de cualquier dirección envía `POST /documentos/confirmar-acuse` con el `documentoId` de un expediente de otra dirección y lo **finaliza**.
- Cualquier `RESPONSABLE` fuerza el cierre de informes de cualquier expediente.

Los ids son secuenciales y fáciles de adivinar.

*Mitigación:*
- Validar dos cosas en el servicio, no en el controlador:
  - **acceso:** `verificarAccesoDocumento(usuario, id)`;
  - **custodia:** `canalService.puedeActuarPor(usuario, custodiaDe(doc))`.
- Acotar `SUPERVISAR_PASES` al ámbito: el supervisor tiene que poder actuar por la emisora o por la receptora del pase.

```java
private Documento exigirCustodia(Long documentoId, Usuario usuario, String accion) {
    verificarAccesoDocumento(usuario, documentoId);
    Documento doc = documentoRepository.findByIdParaActualizar(documentoId).orElseThrow();
    if (!canalService.puedeActuarPor(usuario, custodiaDe(doc))) {
        throw new AccessDeniedException("Solo quien tiene el original puede " + accion + ".");
    }
    return doc;
}
```

**C2 — Escalada por autoasignación.** ✅ **Corregido en lo principal (02/11/2026).**
- Un administrador ya no puede asignarse roles de trámite ni cambiar su propia dependencia; lo tiene que hacer otro administrador.
- Prueba: `AdministracionServiceTest`.
- **Riesgo residual:** un administrador todavía puede crear un usuario "de paja", darle permisos y resetearle la contraseña. Eso queda en la auditoría, pero solo se evita con el doble control que se describe abajo, que está pendiente.

Un `ADMIN_SISTEMA` puede asignarse a sí mismo `MESA_ENTRADA` sobre la Intendencia, y así obtener acceso a **todos** los expedientes. Queda auditado, pero nada lo impide. El aislamiento del rol es solo nominal.

*Mitigación:*
- Prohibir que el administrador se asigne roles de trámite: `usuarioId == admin.getId()` y `rol != ADMIN_SISTEMA`.
- Exigir el **doble control** de un segundo administrador para:
  - `MESA_ENTRADA` y `RESPONSABLE` en cabeceras (Dirección o superior);
  - cualquier `ADMIN_SISTEMA`.
- Avisar por correo a los demás administradores, cuando exista el SMTP.

### ALTO

**A1 — Un usuario dado de baja conserva su sesión y sus permisos.** ✅ **Corregido (02/11/2026).**
- `ActualizacionPermisosFilter` cierra la sesión en la siguiente petición y redirige a `/login?baja`.
- Prueba: `ActualizacionPermisosFilterTest`.

`ActualizacionPermisosFilter` usa `findByUsernameAndActivoTrue(...).ifPresent(...)`. Si el usuario ya no está activo, **no hace nada** y quedan vigentes las autoridades guardadas en la sesión hasta que esta vence.

*Mitigación:* si el usuario no está activo, limpiar el contexto, invalidar la sesión y redirigir a `/login?baja`.

**A2 — Sesiones y fuerza bruta.** ✅ **Corregido (02/11/2026).**
- Bloqueo de 15 minutos tras 5 intentos fallidos (`LimiteIntentosService`), sin revelar si el usuario existe.
- El reseteo y la baja cierran las sesiones abiertas (`SessionRegistry`).
- La sesión vence tras 30 minutos sin actividad.
- **Queda:** contar también por IP, y que al cambiar la propia contraseña se cierren las otras sesiones.
- Pruebas: `LimiteIntentosServiceTest` y `AdministracionServiceTest`.
- El reseteo o el cambio de contraseña no cierran las otras sesiones del usuario: no hay `SessionRegistry`.
- No hay límite de intentos de inicio de sesión ni bloqueo temporal.
- No hay registro de los inicios fallidos.

*Mitigación:*
- `sessionManagement().maximumSessions(1)` con `SessionRegistry`, y expirar sus sesiones al resetear, dar de baja o cambiar la contraseña.
- Contador de intentos por usuario y por IP, por ejemplo 5 intentos y 15 minutos de bloqueo.
- Registrar los eventos de autenticación en `auditoria_sistema`.

**A3 — Suplencias sin vínculo con el titular.** ✅ **Corregido (02/11/2026).**
- La suplencia exige que el titular tenga ese rol, sobre un ámbito que abarque el de la suplencia, durante todo el período.
- No se aceptan asignaciones iguales superpuestas ni que empiecen antes de hoy.
- Prueba: `AdministracionServiceTest`.

La suplencia guarda `titular`, pero:
- no se valida que el titular **tenga** ese rol en ese ámbito durante el período: con la figura de "suplencia" se puede dar cualquier permiso;
- no se controla el solapamiento de asignaciones iguales;
- se aceptan fechas pasadas.

*Mitigación:* al crearla, exigir que el titular tenga una asignación vigente que cubra el rol, el ámbito y las fechas de la suplencia.

```sql
SELECT 1 FROM asignaciones t JOIN dependencias dt ON dt.id = t.dependencia_ambito_id
 WHERE t.usuario_id = :titular AND t.rol = :rol AND t.activa
   AND (SELECT ruta FROM dependencias WHERE id = :ambito) LIKE dt.ruta || '%'
   AND t.vigente_desde <= :desde AND (t.vigente_hasta IS NULL OR t.vigente_hasta >= :hasta);
```

Además, rechazar una asignación activa idéntica (mismo usuario, rol y ámbito) cuyas fechas se solapen. Se puede resolver con una restricción `EXCLUDE USING gist` sobre `daterange(vigente_desde, vigente_hasta, '[]')` (requiere `btree_gist`).

**A4 — Cierre de informes.** ✅ **Corregido (02/11/2026).**
- Cada pedido se considera respondido solo con un informe o memo del área receptora posterior al pedido (`informeRespondido`). Si llegó a una mesa, cuenta la respuesta de su dirección o de una subordinada; antes, una respuesta así no contaba nunca.
- El cierre forzado anula los pedidos pendientes (`CIERRE_EXCEPCIONAL`) y lo registra a nombre de quien tiene el original.
- Prueba: `RespuestaInformeTest`.
- `verificarYActualizarEstadoGlobal` cuenta las respuestas **por dependencia, sin mirar la fecha**. Un informe que una dependencia emitió en una ronda anterior "responde" a un pedido nuevo, y el expediente pasa a `PENDIENTE_SALIDA` antes de tiempo.
- `forzarCierreInformes` no anula los pases `INFORME` pendientes. Siguen en "Por recibir" de la receptora, que puede confirmarlos y responder sobre un expediente ya cerrado.

*Mitigación:*
- Vincular cada respuesta a su pase, con `actuacion_padre_id` o, como mínimo, con fecha posterior al pase y misma receptora.
- En el cierre excepcional, anular los pases pendientes con `tipo_anulacion = 'CIERRE_EXCEPCIONAL'`.

**A5 — La bitácora no es realmente inmutable.** 🟡 **Corregido en la base de pruebas (02/11/2026); falta producción.**
- V14 protege `auditorias_documento` contra UPDATE y DELETE, y las dos bitácoras contra `TRUNCATE`.
- **Base de pruebas (hecho):** `proteger_bitacoras.sh` dejó las dos bitácoras a nombre de `gestion_documental_bitacora`; la aplicación solo lee y agrega, y sigue registrando eventos con normalidad.
- **Producción (pendiente):** arrancar la aplicación real (aplica V13 y V14) y correr `sudo -u postgres bash proteger_bitacoras.sh gestion_documental gestion_documental`.

El trigger de `auditoria_sistema` frena `UPDATE` y `DELETE`, pero:
- **no frena `TRUNCATE`**;
- la tabla pertenece al mismo rol de la aplicación (`gestion_documental`), así que la aplicación, o quien tenga sus credenciales, puede hacer `ALTER TABLE … DISABLE TRIGGER`, `TRUNCATE` o `DROP`.

Lo mismo aplica a `auditorias_documento`.

*Mitigación*, con una migración que ejecute un superusuario:

```sql
CREATE ROLE gestion_documental_owner NOLOGIN;
ALTER TABLE auditoria_sistema    OWNER TO gestion_documental_owner;
ALTER TABLE auditorias_documento OWNER TO gestion_documental_owner;
GRANT SELECT, INSERT ON auditoria_sistema, auditorias_documento TO gestion_documental;
GRANT USAGE ON SEQUENCE auditoria_sistema_id_seq TO gestion_documental;
CREATE TRIGGER trg_auditoria_sistema_sin_truncate BEFORE TRUNCATE ON auditoria_sistema
    FOR EACH STATEMENT EXECUTE FUNCTION fn_auditoria_sistema_inmutable();
```

A futuro, conviene copiar periódicamente la bitácora fuera de la base, por ejemplo a un syslog o a un archivo firmado.

### MEDIO

**M1 — Movimientos del original con un trámite pendiente.**

La devolución, la salida exterior y el acuse final no controlan `existePaseTramitePendiente`. Ejemplo:
1. Se emite un trámite hacia la dependencia B.
2. Antes de que B confirme, la custodia se mueve por una devolución.
3. Cuando B confirma, la custodia vuelve a B.

Queda un recorrido incoherente, con dos "dueños" sucesivos del original.

*Mitigación:* bloquear esas acciones mientras haya un trámite pendiente, o anularlo automáticamente con motivo.

**M2 — Bloqueo por pases sin receptor.**

Si la receptora de un trámite se da de baja, o se queda sin usuarios, el pase queda pendiente para siempre y bloquea nuevos trámites. Hoy solo lo destraba `SUPERVISAR_PASES`, que es justamente el permiso global de C1.

*Mitigación:*
- Al derivar, validar que la receptora esté activa y tenga al menos un usuario que actúe por ella.
- Agregar al panel de administración una acción "retirar pase", acotada y auditada.
- Avisar cuando un pase supere el SLA.

**M3 — `ruta` del organigrama con movimientos concurrentes.**

Dos cambios de superior simultáneos sobre nodos de la misma rama pueden dejar rutas desactualizadas en el subárbol: cada transacción recalcula a partir de la ruta que ve.

*Mitigación:*
- Tomar `pg_advisory_xact_lock(hashtext('organigrama'))` dentro de `fn_dependencias_ruta`.
- Agregar una consulta de verificación periódica:

```sql
WITH RECURSIVE a AS (SELECT id, '/' || id || '/' r FROM dependencias WHERE dependencia_padre_id IS NULL
  UNION ALL SELECT d.id, a.r || d.id || '/' FROM dependencias d JOIN a ON d.dependencia_padre_id = a.id)
SELECT d.id FROM dependencias d JOIN a ON a.id = d.id WHERE d.ruta <> a.r;   -- debe devolver 0 filas
```

**M4 — Coincidencia por nombre en pases antiguos.**

Las consultas de acceso y de bandejas mantienen el respaldo `LIKE '%➔ <nombre>:%'` para pases sin receptora. Con el organigrama completo hay **22 nombres repetidos que abarcan 134 dependencias**, por ejemplo 55 "Unidad de Secretaría Administrativa". Si existiera un pase así, otra dirección con el mismo nombre lo vería.

Hoy hay **0 pases sin receptora en producción**, así que no hay exposición.

*Mitigación:* quitar ese respaldo y agregar `CHECK (tipo_actuacion NOT IN ('PASE_INTERNO','PASE_CONFIRMADO','DEVOLUCION') OR dependencia_receptora_pase_id IS NOT NULL)`.

**M5 — Alcance por defecto del OPERADOR.**

`incluye_subordinadas` vale `TRUE` por defecto. Un `OPERADOR` asignado a una dirección actúa por todas sus subordinadas (confirmar pases, devolver) en `CanalService.puedeActuarPor`.

*Mitigación:* que el valor por defecto sea `FALSE` para `OPERADOR` y `CONSULTA`, y que la pantalla lo proponga así.

### BAJO — rendimiento y endurecimiento

**B1 — Organigrama recalculado en cada llamada.**

`CanalService.organigrama()` carga las 581 dependencias y los niveles **en cada llamada**, varias veces por petición (registro, CUTI, destinos, título). `obtenerDependenciasAgrupadas()` hace lo mismo en cada carga de pantalla.

*Mitigación:* caché en memoria invalidada cuando cambia `dependencias`, por ejemplo con un contador de versión o un `LISTEN/NOTIFY` desde el trigger.

**B2 — Bandejas.**
- Cada carga de pantalla ejecuta 5 consultas con `CTE_AMBITO`: la lista y los 4 contadores.
- "Todos" no está paginada.
- El buscador y el archivo filtran con `puedeAccederDocumento` expediente por expediente (N+1).

*Mitigación:*
- Calcular los contadores en una sola consulta con `COUNT(*) FILTER (WHERE …)`.
- Paginar "Todos" y llevar el filtro de ámbito al SQL del buscador.
- Índices que faltan:

```sql
CREATE INDEX idx_actuaciones_emisora ON actuaciones_documento (dependencia_emisora_id);
CREATE INDEX idx_actuaciones_pendientes ON actuaciones_documento (dependencia_receptora_pase_id, documento_id)
    WHERE tipo_actuacion = 'PASE_INTERNO' AND NOT pase_confirmado AND NOT anulada;
CREATE INDEX idx_documentos_registro ON documentos (dependencia_registro_id);
CREATE INDEX idx_documentos_carga ON documentos (usuario_carga_ingreso_id);
CREATE INDEX idx_documentos_ingreso ON documentos (fecha_hora_ingreso DESC, id DESC);
```

**B3 — PDF y cabeceras.**
- Los PDF se sirven `inline`. Un PDF con JavaScript se ejecuta en el visor del navegador, dentro del origen de la aplicación.

  *Mitigación:* agregar `Content-Security-Policy: sandbox` a esas respuestas, u ofrecer descarga (`attachment`).
- Faltan cabeceras y atributos de seguridad:
  - CSP general;
  - SRI (`integrity=`) en los recursos de cdn.jsdelivr.net;
  - cookie de sesión `Secure` y `SameSite=Lax` cuando haya HTTPS;
  - `server.servlet.session.timeout` explícito, por ejemplo 30 minutos.

---

## 2. Orden de corrección propuesto

| Prioridad | Hallazgos | Esfuerzo |
|---|---|---|
| Inmediata, antes de sumar otra dirección | C1, C2, A1 (✅ corregidos; falta el doble control de C2) | 1–2 días |
| Antes del piloto con Hacienda | A2, A3, A4 (✅ corregidos), A5 (falta el script en el servidor), M1 | 3–4 días |
| Antes del despliegue general | M2, M3, M4, M5, B1, B2 | 3–5 días |
| Con el pase a HTTPS | B3 | 1 día |

## 3. Pruebas de integración recomendadas

Todas sobre la base de pruebas, con `@PruebaConBaseDePruebas` y `@Transactional`, para que se deshagan al terminar.

1. **Matriz de autorización (MockMvc + `spring-security-test`).** Para cada rol × endpoint × expediente propio o ajeno:
   - esperar 403 o redirección cuando no corresponde;
   - es la prueba que habría detectado C1.
2. **Canal de punta a punta.** Automatizar el recorrido TIC → DGAF → Hacienda que hoy se hizo a mano:
   - CUTI, custodia en cada paso y bandejas de cada usuario;
   - destinos rechazados.
3. **Concurrencia del CUTI.** 50 hilos registrando en la misma cabecera: 50 números distintos y consecutivos. Con dos cabeceras en paralelo, series independientes.
4. **Concurrencia de custodia.** Dos confirmaciones simultáneas del mismo pase: una sola transfiere la custodia. Un trámite y una devolución simultáneos: estado coherente (M1).
5. **Informes.** Pedido → respuesta → nuevo pedido a la misma dependencia: no debe cerrarse solo (A4). Cierre forzado: pases pendientes anulados.
6. **Administración.**
   - Baja con sesión abierta: la siguiente petición es rechazada (A1).
   - Suplencia fuera del alcance del titular: rechazada (A3).
   - Autoasignación: rechazada (C2).
7. **Bitácora.** `UPDATE`, `DELETE` y `TRUNCATE` sobre `auditoria_sistema` fallan desde el rol de la aplicación (A5).
8. **Organigrama.** La consulta de verificación de `ruta` (M3) devuelve 0 filas después de mover dependencias.
9. **Volumen.**
   - Cargar en la base de pruebas unos 200.000 expedientes y 1.000.000 de actuaciones sintéticos.
   - Medir con `EXPLAIN (ANALYZE, BUFFERS)` las consultas de bandeja y de acceso. Objetivo: menos de 200 ms por bandeja.

## 4. Análisis de vulnerabilidades web

- **OWASP ZAP**, modo *baseline* y luego autenticado, contra la aplicación con el perfil `pruebas`:
  - XSS reflejado y almacenado;
  - CSRF;
  - cabeceras;
  - cookies.
- **Dependencias:** `mvn org.owasp:dependency-check-maven:check` en cada versión.
- **Revisión manual:**
  - ids secuenciales en todos los endpoints (IDOR, ver C1);
  - carga de archivos: PDF políglota, PDF con JavaScript, nombre con `../`;
  - fijación y expiración de sesión;
  - mensajes de error que revelen datos.

## 5. Checklist antes del despliegue masivo

- [x] C1: todas las operaciones sobre un expediente verifican acceso y custodia en el servicio.
- [ ] C2: el administrador no puede autoasignarse roles de trámite; las asignaciones sensibles tienen doble control.
- [x] A1–A2: un usuario dado de baja o reseteado pierde la sesión; hay límite de intentos de inicio de sesión.
- [x] A3: las suplencias quedan acotadas a lo que tiene el titular y no se solapan.
- [x] A4: las respuestas a informes se vinculan a su pase; el cierre forzado anula los pases pendientes.
- [ ] A5: la bitácora pertenece a otro rol y resiste `TRUNCATE`, `DISABLE TRIGGER` y `DROP` desde la aplicación.
- [ ] M1–M5 resueltos o aceptados por escrito.
- [ ] Índices de B2 creados y bandejas medidas con volumen real.
- [ ] Matriz de autorización automatizada en verde.
- [ ] HTTPS, cookies seguras, CSP y SRI configurados.
- [ ] Respaldos de la base y de la carpeta de PDF probados con una restauración real.
- [ ] Contraseña de producción rotada; credenciales fuera del repositorio (ya resuelto con `application-local.properties`).
- [ ] ZAP y OWASP Dependency-Check sin hallazgos altos abiertos.
