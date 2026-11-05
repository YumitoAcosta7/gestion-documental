# Diseño Fase 2 — Gestión Documental para toda la Municipalidad (uso interno)

> **Estado:** etapa 2.1 terminada (02/11/2026). Etapa 2.2 diseñada con el canal jerárquico y el CUTI por cabecera definidos por la Dirección de TIC (§4.3, §5 y §10.1). El resto, propuesta de diseño.
> **Base:** código de `chore/limpieza-proyecto` y estructura orgánica real de la base, relevada en modo solo lectura el 02/11/2026.
> **Objetivo:** que todas las áreas de la Municipalidad registren, deriven, controlen y archiven sus expedientes en una sola herramienta, cada una viendo solo lo que le corresponde. Es la base para eliminar el papel (Fase 3) y para el portal del contribuyente (Fase 4).

---

## 0. Punto de partida (datos reales)

| Dato | Valor |
|---|---|
| Dependencias cargadas | 92 (1 Intendencia, 1 Dir. Ejecutiva, 5 Direcciones, 1 Coordinación, 23 Departamentos, 60 Unidades) |
| Profundidad máxima del árbol | 5 niveles (Intendencia › DG › Dirección › Departamento › Unidad) |
| Ramas cargadas | **Solo** Dirección de TIC y Dirección General de Administración y Finanzas (Adquisiciones, Hacienda, Recaudaciones, Servicios Administrativos) |
| Mesas de entrada existentes de hecho | Cada dirección tiene una "Unidad de Secretaría Administrativa" (ids 11, 101, 111, 121, 131, 141) |
| Usuarios | 8, todos de TIC |
| Expedientes | 7, con 13 actuaciones |

**Lo que hoy impide el uso en toda la municipalidad** (ver la evaluación del 02/11):
1. IDs fijos de TIC (`obtenerDependenciasHijas` usa 10 y 11).
2. CUTI con prefijo fijo `TIC-`.
3. `DIRECTOR` y `SECRETARIA_ADMIN` ven todos los expedientes de la municipalidad.
4. Solo se deriva hacia las dependencias hijas: no se puede derivar entre direcciones ni hacia arriba.
5. Una sola mesa de entrada.
6. Indicadores globales.
7. Textos "TIC" en la interfaz.

**Observaciones del relevamiento:**
- **El alcance no puede salir de la dependencia del usuario.** El Director de TIC está cargado en "Coordinación de TIC" (nivel 5), no en "Dirección de TIC" (id 10). Si su alcance se dedujera de su dependencia, vería solo la Coordinación.
- **Los niveles tienen inconsistencias.** La "Dirección general de Administración y Finanzas" figura como *Dirección Ejecutiva*, y "Unidad de Secretaría Administrativa" (id 11) cuelga directo de la Dirección. Conviene que Organización y Métodos revise el organigrama antes de cargar el resto.
- **El organigrama está incompleto.** Antes del despliegue general hay que cargar todas las direcciones generales, direcciones y dependencias.

---

## 1. Principios de diseño

1. **El organigrama es la fuente de verdad.** Permisos, bandejas y derivaciones se calculan desde el árbol, nunca con IDs escritos en el código.
2. **Permiso = rol × ámbito × vigencia.** Un usuario puede tener varias asignaciones (por ejemplo, Responsable de su departamento y Mesa de Entrada de su dirección), cada una con su ámbito y su período. Las suplencias son asignaciones temporales.
3. **El expediente tiene un custodio.** Siempre se sabe qué dependencia tiene el original. Las copias para informe o conocimiento no mueven la custodia.
4. **Todo es configurable por un administrador**, sin tocar código: dependencias, mesas de entrada, reglas de derivación, feriados y plazos.
5. **Nada se borra.** Se mantiene la anulación lógica con motivo, el historial no borrable y la auditoría inmutable.

---

## 2. Modelo organizacional

### 2.1 Cambios en `dependencias`

```sql
ALTER TABLE dependencias
    ADD COLUMN sigla             VARCHAR(20) UNIQUE,      -- DTIC, DGAF, DHAC, DREC...
    ADD COLUMN ruta              VARCHAR(500),            -- ruta materializada: /1/100/140/145/
    ADD COLUMN es_mesa_entrada   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN recibe_externos   BOOLEAN NOT NULL DEFAULT FALSE; -- recibe pases de otras direcciones
CREATE INDEX idx_dependencias_ruta ON dependencias (ruta text_pattern_ops);
```

- **`ruta`:** permite preguntar "¿está X dentro del área de Y?" con `x.ruta LIKE y.ruta || '%'` en una sola consulta indexada, sin recorrer el árbol. Se mantiene con un *trigger* al crear una dependencia o cambiar su padre, y se recalcula para todo el subárbol.
- **Dirección de referencia de una dependencia:** su primer ancestro de nivel *Dirección* o superior (`orden_nivel ≤ 4`). Reemplaza los IDs 10 y 11 escritos en el código.
- **Mesa de entrada de una dirección:** la dependencia marcada `es_mesa_entrada` dentro de esa dirección. Inicialmente, las "Unidad de Secretaría Administrativa" existentes.

### 2.2 Pantalla de organigrama (Administrador)

Árbol con alta, baja lógica, cambio de padre, sigla, nivel y marcas de mesa de entrada. Cada cambio queda en la auditoría de sistema (§6.3).

---

## 3. Roles, ámbitos y asignaciones

### 3.1 Roles nuevos

| Rol | Qué hace | Ámbito típico |
|---|---|---|
| `ADMIN_SISTEMA` | Organigrama, usuarios, catálogos, feriados, parámetros | Toda la municipalidad (TIC) |
| `MESA_ENTRADA` | Registra ingresos y salidas, deriva dentro de su dirección y a mesas de otras direcciones, archiva | Su dirección |
| `RESPONSABLE` | Director, coordinador o jefe: ve y gestiona su área, deriva, autoriza el cierre excepcional | Su dependencia y las subordinadas |
| `OPERADOR` | Trabaja los expedientes de su dependencia: actuaciones, confirmación de pases, devoluciones | Su dependencia |
| `CONSULTA` | Solo lectura (por ejemplo, Auditoría Interna o Intendencia) | El que se le asigne |

### 3.2 Tabla de asignaciones (reemplaza `usuarios.rol`)

```sql
CREATE TABLE asignaciones (
    id                    BIGSERIAL PRIMARY KEY,
    usuario_id            INTEGER NOT NULL REFERENCES usuarios (id),
    rol                   VARCHAR(30) NOT NULL CHECK (rol IN ('ADMIN_SISTEMA','MESA_ENTRADA','RESPONSABLE','OPERADOR','CONSULTA')),
    dependencia_ambito_id INTEGER NOT NULL REFERENCES dependencias (id),
    incluye_subordinadas  BOOLEAN NOT NULL DEFAULT TRUE,
    vigente_desde         DATE NOT NULL DEFAULT CURRENT_DATE,
    vigente_hasta         DATE,                         -- NULL = indefinida
    es_suplencia          BOOLEAN NOT NULL DEFAULT FALSE,
    titular_usuario_id    INTEGER REFERENCES usuarios (id), -- en suplencias
    acto_administrativo   VARCHAR(100),                 -- Resolución / Memo que la respalda
    activa                BOOLEAN NOT NULL DEFAULT TRUE,
    creada_por_id         INTEGER NOT NULL REFERENCES usuarios (id),
    fecha_hora_creacion   TIMESTAMP NOT NULL DEFAULT now(),
    CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde),
    CHECK (NOT es_suplencia OR (titular_usuario_id IS NOT NULL AND vigente_hasta IS NOT NULL))
);
```

- **Ámbito efectivo del usuario:** la unión de los subárboles de sus asignaciones vigentes hoy.
- **Suplencias:** el diseño de `docs/diseno-suplencias.md` queda absorbido aquí. Una suplencia es una asignación con `es_suplencia`, titular, fechas y acto administrativo. La auditoría registra "actuó X en suplencia de Y".
- **Spring Security:** los permisos se calculan **en cada petición** a partir de las asignaciones vigentes, no solo al iniciar sesión. Así una suplencia que empieza o termina se aplica sin volver a entrar.
- **Las reglas de autorización pasan a un único componente** `PoliticaAcceso.puede(usuario, accion, documento)`, en lugar de patrones de URL por rol y comprobaciones dispersas en el código.

### 3.3 Migración de los roles actuales

| Hoy | Pasa a |
|---|---|
| `DIRECTOR` (en Coordinación de TIC) | `RESPONSABLE`, ámbito **Dirección de TIC (10)** con subordinadas |
| `SECRETARIA_ADMIN` | `MESA_ENTRADA`, ámbito Dirección de TIC (10) |
| `JEFE_DEPTO` | `RESPONSABLE`, ámbito su departamento |
| `JEFE_UNIDAD` | `RESPONSABLE` u `OPERADOR`, ámbito su unidad |
| (nuevo) | `ADMIN_SISTEMA` para el equipo de TIC que administre la herramienta |

---

## 4. Circulación del expediente

### 4.1 Custodia y tipos de pase

```sql
ALTER TABLE documentos ADD COLUMN dependencia_custodia_id INTEGER REFERENCES dependencias (id);
ALTER TABLE documentos ADD COLUMN dependencia_registro_id INTEGER REFERENCES dependencias (id); -- mesa que lo registró
ALTER TABLE actuaciones_documento ADD COLUMN tipo_pase VARCHAR(20)
    CHECK (tipo_pase IN ('TRAMITE','INFORME','CONOCIMIENTO'));  -- reemplaza requiere_informe
```

| Tipo de pase | Custodia | Espera respuesta | Uso |
|---|---|---|---|
| `TRAMITE` | **Se transfiere** al confirmar el acuse | No | "Pase a su consideración": el expediente cambia de manos |
| `INFORME` | Se mantiene en el emisor | Sí (informe o memo) | Lo que hoy es "para informe" |
| `CONOCIMIENTO` | Se mantiene en el emisor | No | Copias y difusión, por ejemplo de circulares |

La devolución transfiere la custodia de vuelta al emisor del último pase de trámite.

### 4.2 Bandejas por ámbito (reemplazan "Trámites activos")

| Bandeja | Contenido |
|---|---|
| **Por recibir** | Pases pendientes de acuse dirigidos a mi ámbito |
| **En mi poder** | Expedientes cuya custodia está en mi ámbito |
| **Informes pendientes** | Pases `INFORME` a mi ámbito sin respuesta |
| **En seguimiento** | Expedientes que mi ámbito originó o derivó y siguen abiertos |
| **Archivo** | Finalizados y archivados en los que mi ámbito participó |

Todas son **paginadas** y se resuelven con una sola consulta SQL por ámbito (unión con `ruta`). Esto elimina el filtrado expediente por expediente que hoy hace el buscador.

### 4.3 Canal jerárquico (definido por la Dirección de TIC, 02/11)

El expediente **sube por la jerarquía hasta la cabecera y baja en la cabecera destino**. Nunca se saltea un nivel. Ejemplo:

```
Unidad → Departamento → Dirección ⇒ Dirección destino → Departamento → Unidad
```

**Cabecera** de una dependencia: su primer superior (o ella misma) de nivel Dirección o mayor. Puede ser una Dirección, una Dirección General o la Intendencia. Cada cabecera recibe en su **mesa de entrada** (la Unidad de Secretaría Administrativa). Si no tiene una (por ejemplo CODENI), recibe la cabecera misma.

Desde la dependencia que tiene la custodia, los destinos permitidos son tres:

| Movimiento | Destino | Ejemplo |
|---|---|---|
| **Subir** | Su superior inmediato. Si el superior es una cabecera, el pase llega a su mesa de entrada | Unidad de Redes → Dpto. Operativo de Informática → DTIC |
| **Bajar** | Sus subordinadas directas | Dpto. de Contabilidad → Unidad de Bancos |
| **Pasar a otra cabecera** | Solo entre las cabeceras que dependen de la Intendencia, y a su mesa de entrada | DTIC → DGAF; DGAF → DGO |

**Las direcciones de una Dirección General se comunican por ella**, incluso entre sí (decisión del 05/11, que reemplaza el pase directo entre direcciones hermanas): Hacienda → DGAF → Recaudaciones. Tampoco se saltea un nivel para pedir un informe: la mesa de una Dirección pasa por el Departamento antes de llegar a una Unidad.

Queda por consultar con Asuntos Jurídicos cómo se concilia esta regla con el art. 50 de la Ley 6715/21 y el art. 3 de la Res. 2653/12, que hablan de comunicar o remitir directamente los pedidos de informe.

**Una copia o un pedido de informe también baja de a un nivel** (decisión del 05/11). Quien lo recibe lo reparte a su nivel inmediato inferior sin mover el original, y una Dirección General puede hacerlo a todas sus direcciones a la vez. Cada nivel consolida las respuestas de sus áreas y responde a quien le pidió.

Por ejemplo, de Hacienda a la DTIC:

```
Hacienda → DGAF → DTIC
```

Y de la DTIC a Hacienda:

```
DTIC → DGAF → Hacienda
```

La regla general es: subir hasta la cabecera hermana del destino, pasar a ella y bajar. El sistema muestra en cada paso solo los destinos permitidos y, para un destino lejano, sugiere el recorrido.

- **Documentos propios en cualquier nivel.** Una unidad, un departamento o una dirección puede iniciar un expediente con un documento propio (memo, informe, nota) y darle salida hacia su superior. Hoy solo la mesa de entrada registra documentos.
- **Dependencias inactivas:** no reciben pases.
- **Excepciones:** una tabla `reglas_derivacion (origen, destino, permitido)` permite habilitar atajos sin tocar código. Por ejemplo, que una UOC reciba directo de cualquier dirección.

---

## 5. Mesas de entrada y numeración

- **Registro:** cada mesa registra los ingresos de su dirección. El expediente queda con `dependencia_registro_id` y la custodia inicial en la mesa.
- **Mesa de Entrada General:** si la Secretaría General centraliza el ingreso externo, se carga como mesa con `recibe_externos` y deriva a las mesas de cada dirección.
- **CUTI por cabecera** (decisión del 02/11). Cada Dirección, Dirección General y la Intendencia tiene su propia serie anual con su sigla: `DTIC-2026-000001`, `DHAC-2026-000001`, `DGAF-2026-000001`.
  - El expediente toma el número de la cabecera de la dependencia que lo inicia y **lo conserva en todo el recorrido**.
  - Cada mesa que lo recibe lo anota en su libro de entradas con su propio número de orden, sin crear otro expediente.
  - El correlativo sale de una tabla `series_cuti (dependencia_id, anio, ultimo)`, que se incrementa con `SELECT … FOR UPDATE` dentro de la transacción.
  - Los CUTI `TIC-` existentes se conservan y se siguen buscando.
  - Reemplaza a `app.cuti.prefijo`.
- **Numeración de documentos emitidos** ("Nota DTIC N° 45/2026"), con correlativo por dependencia, tipo y año:

```sql
CREATE TABLE correlativos (
    dependencia_id    INTEGER NOT NULL REFERENCES dependencias (id),
    tipo_documento_id INTEGER NOT NULL REFERENCES tipos_documentos (id),
    anio              INTEGER NOT NULL,
    ultimo            INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (dependencia_id, tipo_documento_id, anio)
);  -- se incrementa con SELECT ... FOR UPDATE dentro de la transacción
```

---

## 6. Usuarios, seguridad y auditoría

### 6.1 Administración de usuarios
- **Pantallas:** alta, baja lógica, reseteo de contraseña y asignaciones. El `ADMIN_SISTEMA` administra todo; opcionalmente, un administrador delegado por dirección gestiona solo su ámbito.
- **Primer ingreso y reseteo:** contraseña temporal con **cambio obligatorio**.
- **Política de contraseñas:** longitud mínima, sin reutilizar las anteriores. Bloqueo temporal después de 5 intentos fallidos (`intentos_fallidos`, `bloqueado_hasta`).

### 6.2 Inicio de sesión con el LDAP del correo institucional (Zextras Carbonio)

La Municipalidad no tiene Active Directory, pero el servidor de correo **Zextras Carbonio (edición gratuita)** incluye un directorio **LDAP (OpenLDAP)** que ya se usa en otras herramientas. Se reutiliza así:

- **Contraseña única:** el funcionario entra con su usuario y su contraseña del correo. La herramienta **no guarda esas contraseñas**: las valida contra el LDAP con `spring-security-ldap`, por *bind* del usuario.
- **Autorización en la herramienta:** el LDAP solo responde "quién es". Qué puede hacer sale de las **asignaciones** (§3.2). Un usuario del correo sin asignaciones no puede operar.
- **Alta de usuarios:** el administrador da de alta al funcionario **con el mismo usuario del correo** (`uid`) y le asigna rol y ámbito. Nombre y correo se completan desde el LDAP (`displayName`, `mail`). No se crean cuentas automáticamente por el solo hecho de tener correo.
- **Bajas:** si la cuenta de correo está cerrada o bloqueada (en el esquema heredado de Zimbra, el atributo `zimbraAccountStatus`), el ingreso se rechaza aunque siga activo en la herramienta. *A confirmar en el servidor.*
- **Cuentas locales de respaldo:** solo para el administrador de la herramienta y para casos sin correo, con la política de contraseñas del §6.1. El orden es: LDAP primero y, si el usuario está marcado como local, la contraseña propia.
- **Seguridad de la conexión:**
  - LDAPS (puerto 636) o StartTLS, nunca LDAP en texto plano.
  - Una **cuenta de servicio de solo lectura** para buscar al usuario; no se usa la cuenta administrativa de Carbonio.
  - Regla de firewall que permita al servidor de la herramienta consultar el LDAP.

**Datos que TIC debe proveer:**
- Host y puerto del LDAP, y si usa TLS.
- *Base DN* de las cuentas (por ejemplo, `ou=people,dc=…`).
- Atributo de inicio de sesión: `uid` o `mail`.
- La cuenta de servicio de solo lectura.
- Si hay más de un dominio de correo.

**Correo para avisos:** se usa el **SMTP de Carbonio** con una cuenta remitente propia (por ejemplo `gestion.documental@…`), autenticada y por TLS.

### 6.3 Auditoría de sistema
Tabla `auditoria_seguridad` con inicios de sesión correctos y fallidos, bloqueos, cambios de asignaciones y de organigrama, y cambios de parámetros. Complementa la auditoría por expediente que ya existe.

---

## 7. Avisos, plazos y reportes

- **Avisos:**
  - **Eventos:** pase recibido, pase rechazado o retirado, devolución, informe recibido y demora crítica.
  - **Canales:** una campana en la interfaz (tabla `notificaciones`) y, si hay correo institucional, un **resumen diario** por email a las 7:00 en lugar de un mensaje por evento. Se envían en segundo plano, sin demorar la operación.
- **Plazos:**
  - Tabla `feriados` (nacionales y municipales) cargada por el administrador, que el cálculo de demoras pasa a respetar.
  - Plazos configurables por tipo de documento y por tipo de pase.
- **Reportes por ámbito:**
  - Expedientes ingresados y egresados por período.
  - Tiempo promedio por dependencia (entre la recepción y la respuesta) y ranking de demoras.
  - Volumen por tipo de documento.
  - Exportación a CSV y PDF.
  - Los indicadores del inicio pasan a ser **del ámbito del usuario**.

---

## 8. Infraestructura para producción municipal

| Tema | Propuesta |
|---|---|
| Servidor | Servidor dedicado o VM, la app como servicio, **HTTPS** con proxy inverso, `server.forward-headers-strategy=native` |
| Base de datos | PostgreSQL con respaldo diario (`pg_dump`) y retención de 30 días más copias mensuales, con restauración probada |
| Archivos PDF | Almacenamiento en red (NAS) incluido en el respaldo, en lugar del disco de la PC |
| Migraciones | **Flyway**: los scripts de `db/migraciones` se aplican solos y quedan versionados |
| Ambientes | Desarrollo, **pruebas** con su propia base y producción |
| Calidad | Pruebas de integración con Testcontainers e integración continua en GitHub Actions |
| Monitoreo | Logs centralizados, estado de salud (Spring Actuator) y alerta si la app o el respaldo fallan |

---

## 9. Migración de los datos actuales

1. Revisar y completar el organigrama con Organización y Métodos. Cargar siglas, mesas de entrada y `ruta`.
2. Crear las asignaciones desde los roles actuales (§3.3) y validar con cada usuario.
3. `dependencia_custodia_id` de cada expediente: el receptor del último pase de trámite confirmado; si no hay, la dependencia de registro.
4. `tipo_pase`: `INFORME` si `requiere_informe`, y `TRAMITE` en el resto.
5. CUTI: los existentes se conservan; los nuevos usan el prefijo municipal.
6. Retirar `usuarios.rol` recién cuando todo funcione con asignaciones (convivencia de una versión).

---

## 10. Plan por etapas (iteraciones de 2 semanas)

| Etapa | Contenido | Iteraciones |
|---|---|---|
| **2.1 Fundaciones** ✅ | `ruta`, sigla y mesas en el organigrama; asignaciones y `PoliticaAcceso`; sacar IDs fijos; CUTI configurable; Flyway; ambiente de pruebas (falta crear la base en el servidor) | 2 |
| **2.2 Circulación** | Custodia y tipos de pase; canal jerárquico (§4.3); documentos propios en cualquier nivel; CUTI por cabecera; bandejas paginadas por ámbito | 2 |
| **2.3 Administración** | Pantallas de organigrama, usuarios y asignaciones; política de contraseñas; suplencias; feriados; auditoría de seguridad | 1–2 |
| **2.4 Comunicación y control** | Notificaciones; reportes por ámbito; numeración de documentos emitidos | 2 |
| **Piloto ampliado** | TIC más **una** dirección de la DGAF (sugerida: Hacienda, por su volumen y su secretaría propia) | 4 semanas de uso |
| **Despliegue gradual** | Dirección por dirección, con capacitación de la mesa de entrada y los responsables | Según el organigrama |

Estimación orientativa: **3,5 a 4 meses** con uno o dos desarrolladores para las etapas 2.1 a 2.4. Cada etapa se entrega usable y con sus pruebas.

### 10.1 Etapa 2.2 en detalle

| Paso | Contenido |
|---|---|
| 1. Migración V11 | En `documentos`: `dependencia_custodia_id` y `dependencia_registro_id`. En `actuaciones_documento`: `tipo_pase`. Tablas nuevas `series_cuti` y `reglas_derivacion`. Carga de los datos actuales según §9 |
| 2. `CanalService` | Calcula los destinos permitidos desde la custodia (subir, bajar o pasar a una cabecera hermana) y el recorrido sugerido hacia un destino lejano. Reemplaza a `obtenerDependenciasHijas`. Pruebas unitarias con el árbol real |
| 3. Custodia | Un pase `TRAMITE` confirmado transfiere la custodia; `INFORME` y `CONOCIMIENTO` no. La devolución la regresa al emisor |
| 4. Documentos propios | Cualquier dependencia inicia un expediente con su documento y le da salida hacia su superior |
| 5. CUTI por cabecera | `series_cuti` con bloqueo de fila; el número sale de la cabecera de quien inicia el expediente |
| 6. Bandejas | Por recibir, En mi poder, Informes pendientes, En seguimiento y Archivo, paginadas y con una consulta por ámbito |
| 7. Prueba de punta a punta | Expediente de una unidad de TIC hasta una unidad de Hacienda, por la base de pruebas |

**Criterios de aceptación de la Fase 2:**
- Un responsable de Hacienda **no ve** expedientes de TIC, salvo que hayan pasado por su área.
- Un expediente de TIC para Hacienda sigue el canal DTIC → DGAF → Hacienda y llega a la mesa de Hacienda, que lo redistribuye. El árbol muestra todo el recorrido y el CUTI no cambia.
- Una unidad puede iniciar un memo propio y darle salida solo hacia su departamento.
- Una suplencia con fecha de fin deja de tener efecto sola ese día.
- No queda ningún ID de dependencia escrito en el código.

---

## 11. Decisiones a tomar por la Dirección

| # | Pregunta | Recomendación |
|---|---|---|
| 1 | ~~¿La Municipalidad tiene Active Directory/LDAP?~~ **Respondida (02/11):** no hay Active Directory; se usará el LDAP de Zextras Carbonio (§6.2) | Faltan los datos de conexión y la cuenta de servicio |
| 2 | ~~Entre direcciones, ¿siempre a través de la mesa de entrada o también directo?~~ **Respondida (02/11):** canal jerárquico. Se sube hasta la cabecera, se pasa entre cabeceras hermanas y se baja, pasando por las Direcciones Generales (§4.3) | — |
| 3 | ~~¿CUTI único municipal o con prefijo por dirección?~~ **Respondida (02/11):** una serie por cabecera (Dirección, Dirección General, Intendencia), que el expediente conserva en todo el recorrido (§5) | — |
| 4 | ¿Quién administra usuarios en cada dirección? | TIC centralizado al inicio; administración delegada después |
| 5 | ~~¿Hay correo institucional (SMTP) para avisos?~~ **Respondida (02/11):** sí, el de Zextras Carbonio | Crear la cuenta remitente `gestion.documental@…` |
| 6 | ¿Volumen estimado de expedientes por día y por dirección? | Necesario para dimensionar el servidor |
| 7 | ¿Existe una Mesa de Entrada General (Secretaría General)? | Cargarla como mesa con `recibe_externos` |
| 8 | ¿Qué dirección acompaña a TIC en el piloto ampliado? | Hacienda |
| 9 | ¿Quién valida el organigrama completo? | Organización y Métodos (está dentro de TIC). **En curso (02/11):** TIC va a proveer la estructura completa (formato en §12) |

Confirmado (02/11): la **Dirección de TIC depende directamente de la Intendencia**, la jerarquía más alta, tal como está cargada (id 10 bajo id 1).

---

## 12. Carga del organigrama completo

La estructura se recibe como planilla (Excel o CSV) con **una fila por dependencia** y se carga con un script de importación que valida antes de grabar.

| Columna | Obligatoria | Ejemplo | Notas |
|---|---|---|---|
| `codigo` | Sí | `DTIC` | Sigla única. Si no existe una oficial, se define una |
| `nombre` | Sí | `Dirección de Tecnologías de la Información y Comunicación` | Nombre oficial completo |
| `nivel` | Sí | `Dirección` | Intendencia, Dirección General, Dirección Ejecutiva, Dirección, Coordinación, Departamento o Unidad |
| `codigo_superior` | Sí (salvo Intendencia) | `INT` | Sigla de la dependencia de la que depende |
| `es_mesa_entrada` | No | `SI` | La secretaría que recibe y registra los documentos de esa dirección |
| `responsable` | No | `Nombre Apellido` | Titular actual, para preparar las asignaciones |
| `correo_responsable` | No | `usuario@…` | Debe coincidir con su cuenta de Carbonio |
| `activa` | No | `SI` | `NO` para dependencias que ya no existen pero tienen historial |
| `resolucion` | No | `Res. N° 123/2025` | Acto que crea o modifica la dependencia |

**Controles del script de importación:**
- Siglas únicas.
- Cada superior existe y no hay ciclos.
- Los niveles son coherentes: una Unidad no puede tener por debajo una Dirección.
- Las 92 dependencias ya cargadas se **emparejan por nombre** para no duplicarlas ni perder su historial.
- Los cambios de nivel o de superior se muestran en un informe previo para aprobar antes de grabar.

**También sirve:** el organigrama oficial (PDF o resolución) aunque no esté en planilla. En ese caso se transcribe a este formato y se valida con Organización y Métodos.
