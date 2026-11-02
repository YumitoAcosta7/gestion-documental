# Diseño Fase 2 — Gestión Documental para toda la Municipalidad (uso interno)

> **Estado:** propuesta de diseño, no implementada.
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

### 4.3 Reglas de derivación

| Destino | Permitido a | Regla |
|---|---|---|
| Cualquier dependencia de **mi misma dirección** | Responsable, Mesa de entrada | Libre |
| **Hacia arriba** (dirección, dirección general, Intendencia) | Responsable | Libre |
| **Otra dirección** | Responsable, Mesa de entrada | A su **mesa de entrada**, que la redistribuye internamente. Excepción configurable: dependencias marcadas `recibe_externos` (por ejemplo, la UOC) |
| Dependencia inactiva | Nadie | Rechazado |

Las excepciones se configuran en una tabla `reglas_derivacion (origen, destino, permitido)`, administrada sin tocar código.

---

## 5. Mesas de entrada y numeración

- **Registro:** cada mesa registra los ingresos de su dirección. El expediente queda con `dependencia_registro_id` y la custodia inicial en la mesa.
- **Mesa de Entrada General:** si la Secretaría General centraliza el ingreso externo, se carga como mesa con `recibe_externos` y deriva a las mesas de cada dirección.
- **CUTI municipal:** una sola serie anual para toda la municipalidad, con prefijo configurable (`app.cuti.prefijo`, por ejemplo `MCA-2026-000123`). Los CUTI `TIC-` existentes se conservan y se siguen buscando. Se mantiene el bloqueo transaccional actual.
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

### 6.2 Inicio de sesión institucional (si existe)
Si la Municipalidad tiene **Active Directory/LDAP**, se integra con `spring-security-ldap`: la contraseña la valida el directorio y las asignaciones siguen en la herramienta. Así se evita mantener contraseñas propias.

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
| **2.1 Fundaciones** | `ruta`, sigla y mesas en el organigrama; asignaciones y `PoliticaAcceso`; sacar IDs fijos; CUTI configurable; Flyway; ambiente de pruebas | 2 |
| **2.2 Circulación** | Custodia y tipos de pase; bandejas paginadas por ámbito; reglas de derivación entre direcciones; mesas de entrada | 2 |
| **2.3 Administración** | Pantallas de organigrama, usuarios y asignaciones; política de contraseñas; suplencias; feriados; auditoría de seguridad | 1–2 |
| **2.4 Comunicación y control** | Notificaciones; reportes por ámbito; numeración de documentos emitidos | 2 |
| **Piloto ampliado** | TIC más **una** dirección de la DGAF (sugerida: Hacienda, por su volumen y su secretaría propia) | 4 semanas de uso |
| **Despliegue gradual** | Dirección por dirección, con capacitación de la mesa de entrada y los responsables | Según el organigrama |

Estimación orientativa: **3,5 a 4 meses** con uno o dos desarrolladores para las etapas 2.1 a 2.4. Cada etapa se entrega usable y con sus pruebas.

**Criterios de aceptación de la Fase 2:**
- Un responsable de Hacienda **no ve** expedientes de TIC, salvo que hayan pasado por su área.
- Un expediente derivado de TIC a Hacienda llega a la mesa de Hacienda, que lo redistribuye, y el árbol muestra todo el recorrido.
- Una suplencia con fecha de fin deja de tener efecto sola ese día.
- No queda ningún ID de dependencia escrito en el código.

---

## 11. Decisiones a tomar por la Dirección

| # | Pregunta | Recomendación |
|---|---|---|
| 1 | ¿La Municipalidad tiene Active Directory/LDAP? | Si existe, usarlo para el inicio de sesión |
| 2 | Entre direcciones, ¿siempre a través de la mesa de entrada o también directo? | A través de la mesa, con excepciones configurables |
| 3 | ¿CUTI único municipal o con prefijo por dirección? | Único municipal (`MCA-AAAA-NNNNNN`); la sigla del área se ve aparte |
| 4 | ¿Quién administra usuarios en cada dirección? | TIC centralizado al inicio; administración delegada después |
| 5 | ¿Hay correo institucional (SMTP) para avisos? | Sí, con resumen diario |
| 6 | ¿Volumen estimado de expedientes por día y por dirección? | Necesario para dimensionar el servidor |
| 7 | ¿Existe una Mesa de Entrada General (Secretaría General)? | Cargarla como mesa con `recibe_externos` |
| 8 | ¿Qué dirección acompaña a TIC en el piloto ampliado? | Hacienda |
| 9 | ¿Quién valida el organigrama completo? | Organización y Métodos (está dentro de TIC) |
