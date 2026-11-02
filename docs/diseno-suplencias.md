# Diseño: Delegación temporal / Suplencias

> Estado: **diseño propuesto, no implementado**. Requiere definir las decisiones de la sección 1 antes de programarlo.

## 1. Decisiones a tomar por la Dirección

| # | Pregunta | Recomendación |
|---|---|---|
| 1 | ¿El suplente **actúa como** el titular (mismo rol y dependencia) o **suma** permisos a los suyos? | Actúa como el titular, solo mientras la suplencia esté vigente. |
| 2 | ¿El suplente debe ser de la misma dependencia? | Sí, o de la dependencia superior inmediata (el caso típico: el jefe de departamento suple al jefe de una unidad sin otro funcionario). |
| 3 | ¿Quién registra la suplencia? | El propio titular, o Dirección/Secretaría (cuando la ausencia no fue prevista). |
| 4 | ¿Debe citarse un acto administrativo? | Sí: campo obligatorio "Resolución / Memo N°". |
| 5 | ¿Dirección y Secretaría pueden delegar? | Sí, con el mismo mecanismo. |
| 6 | ¿Se puede terminar antes de la fecha? | Sí, anulándola con motivo (queda registrada). |

## 2. Modelo de datos

```sql
CREATE TABLE suplencias (
    id                     BIGSERIAL PRIMARY KEY,
    usuario_titular_id     INTEGER   NOT NULL REFERENCES usuarios (id),
    usuario_suplente_id    INTEGER   NOT NULL REFERENCES usuarios (id),
    fecha_desde            DATE      NOT NULL,
    fecha_hasta            DATE      NOT NULL,
    acto_administrativo    VARCHAR(100) NOT NULL,       -- Ej: Memo DTIC N° 45/2026
    motivo                 TEXT,
    activa                 BOOLEAN   NOT NULL DEFAULT TRUE,
    motivo_anulacion       TEXT,
    usuario_registro_id    INTEGER   NOT NULL REFERENCES usuarios (id),
    fecha_hora_registro    TIMESTAMP NOT NULL,
    CHECK (fecha_hasta >= fecha_desde),
    CHECK (usuario_titular_id <> usuario_suplente_id)
);
CREATE INDEX idx_suplencias_suplente_vigencia ON suplencias (usuario_suplente_id, fecha_desde, fecha_hasta) WHERE activa;

-- Auditoría: quién actuó y en nombre de quién
ALTER TABLE auditorias_documento ADD COLUMN en_suplencia_de VARCHAR(150);
ALTER TABLE actuaciones_documento ADD COLUMN usuario_titular_id INTEGER REFERENCES usuarios (id);
```

Reglas de integridad (en el servicio): no se permiten dos suplencias activas superpuestas para el mismo titular, ni que el suplente esté a su vez ausente (con su propia suplencia vigente) en el mismo período.

## 3. Cómo se aplica en la aplicación

**Identidad efectiva.** Al iniciar sesión, `CustomUserDetailsService` busca una suplencia vigente hoy para el usuario. Si existe:

- Agrega al usuario autenticado el rol del titular (`ROLE_JEFE_DEPTO`, etc.) y una autoridad `SUPLENCIA_<idTitular>`.
- La sesión guarda el titular; `getUsuarioAutenticado()` devuelve un objeto con **rol y dependencia efectivos** (los del titular) y una referencia al usuario real.

Todas las validaciones existentes (`esRolAdministrativo`, `obtenerDependenciasHijas`, `puedeAccederDocumento`, retirar/rechazar pases) pasan a usar la dependencia y el rol **efectivos**, sin duplicar reglas.

**Vigencia durante la sesión.** Como Spring Security calcula los permisos al iniciar sesión, un filtro liviano verifica en cada petición si la suplencia sigue vigente (`fecha_hasta`, `activa`). Si venció o fue anulada, invalida la sesión y pide volver a ingresar. Una suplencia que empieza mientras el suplente ya está conectado se aplica en su próximo inicio de sesión (se muestra un aviso en la pantalla).

**Auditoría.** `AuditoriaService.registrarEvento` registra siempre al **usuario real** (`usuario_username`) y, si actúa en suplencia, completa `en_suplencia_de` con el nombre del titular. En la bitácora se ve: *"Juan Pérez (jperez) — en suplencia de María Gómez"*. Las actuaciones guardan `usuario_id` = suplente y `usuario_titular_id` = titular.

## 4. Pantallas

- **"Mis suplencias"** (titular y Dirección/Secretaría): tabla con suplente, período, acto administrativo y estado; botón *Nueva suplencia* (selector de usuario filtrado por dependencia, fechas, acto administrativo) y *Anular* (con motivo).
- **Encabezado:** cuando el usuario actúa en suplencia, una franja visible: *"Actuando en suplencia de María Gómez (Dpto. Operativo) hasta el 15/11/2026"*.
- **Bitácora y árbol:** la etiqueta "en suplencia de …" junto al usuario.

## 5. Seguridad

- Solo el titular o Dirección/Secretaría crean o anulan una suplencia; el suplente no puede extenderla.
- La suplencia no se encadena: un suplente no puede delegar los permisos que recibió.
- Todas las altas, anulaciones y vencimientos se registran en la auditoría.

## 6. Esfuerzo estimado

Medio-alto: tabla nueva, cambios en `CustomUserDetailsService`, un filtro de vigencia, la noción de "usuario efectivo" en el controlador y los servicios, pantalla de administración y pruebas de seguridad. Es el punto de mayor riesgo porque toca la autenticación; conviene hacerlo después de que las demás mejoras estén en uso.
