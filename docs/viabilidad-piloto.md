# Viabilidad de la prueba de campo (piloto en producción)

Revisión del 03/11/2026, con producción en V22. Estado actualizado el 04/11/2026, con producción en V28.

## Veredicto

**Todavía no.** El código está listo, la infraestructura no. Con los 6 bloqueantes de abajo resueltos, sí se puede iniciar un piloto acotado.

Las reglas de negocio, la seguridad de la aplicación y la trazabilidad están en condiciones de operar con datos reales. Al hacer esta revisión, lo que faltaba era la forma de operar (el avance está en la tabla de estado):
- la aplicación de producción corre desde una PC de desarrollo;
- sin HTTPS;
- sin respaldos automáticos;
- con varias cuentas genéricas.

Un fallo de esa PC o una contraseña capturada en la red comprometerían expedientes reales.


## Estado de los bloqueantes (04/11/2026)

| # | Bloqueante | Estado |
|---|---|---|
| 1 | Servidor de aplicación | ✅ Servicio en 192.168.129.73 |
| 2 | HTTPS | ✅ Con certificado interno. Falta que TIC lo instale en los equipos, o reemplazarlo por uno institucional |
| 3 | Respaldos | 🟡 Diarios y con restauración de la base probada. **Falta la copia a otro equipo** (`RESPALDO_DESTINO`) |
| 4 | Cuentas nominales | 🟡 Administrar y tramitar ya están separados (`juan.admin` y `zlobo.admin`). **Faltan las cuentas por persona:** siguen las 6 genéricas |
| 5 | Autenticación de la base | ✅ Contraseña cambiada, SCRAM y sin `trust` |
| 6 | Datos del piloto anterior | 🟡 `revisar_pendientes_salida.sql` no encontró casos y `revisar_pdf.sh` no informa faltantes. Faltan los pases sin acuse: 8 en la base, 7 en el panel el 04/11/2026 |

## Lo que está listo

| Área | Estado |
|---|---|
| Unidad de expediente | Un asunto, un CUTI, con serie por cabecera. Se elevan los documentos sin crear expedientes nuevos. Se rechazan los documentos repetidos. Las respuestas quedan cosidas a su pedido, y se registra el legajo de la salida. Se quitó la salida directa. |
| Custodia | Solo quien tiene el original deriva, devuelve, da salida o agrega actuaciones. Con el original en camino, no se modifica nada. |
| Acceso | Ámbito por asignación y vigencia, con control en el servidor de cada operación. Matriz de autorización automatizada. La administración está separada de los expedientes, con doble control (dos administradores: `juan.admin` y `zlobo.admin`, que no tramitan). |
| Sesiones | Bloqueo por usuario y por IP. Las sesiones se cierran al dar de baja, restablecer o cambiar la contraseña. Vencen a los 30 minutos sin actividad. |
| Contenido | CSP estricta sin scripts en línea, SRI y cookies `HttpOnly`/`SameSite`. Los PDF se analizan con PDFBox. |
| Trazabilidad | Bitácoras inmutables, propiedad de otro rol, con huella SHA-256 encadenada e IP. Verificación disponible y ÍNTEGRA en las dos bases. |
| Rendimiento | Medido con 200 000 expedientes: listas de 12 a 84 ms, página principal en 1,5 s. Buscador con trigramas. |
| Pruebas | 230 automáticas: 229 pasan y 1 se saltea. Flujos probados en pantalla sobre la base de pruebas. |

## Bloqueantes antes del "Go"

1. **Servidor de aplicación.**
   - **Hoy:** producción se arranca con `mvnw spring-boot:run` desde una PC de desarrollo. Si esa PC se apaga, se reinicia o se usa para otra cosa, el sistema se cae. Al reiniciar se cierran todas las sesiones.
   - **Hace falta:** empaquetar el `.jar` y correrlo como servicio, con arranque automático, en un servidor o una PC dedicada.
2. **HTTPS.**
   - **Hoy:** las contraseñas y los expedientes viajan sin cifrar por la red municipal.
   - **Hace falta:** un certificado (un proxy inverso alcanza), `APP_COOKIE_SEGURA=true` y `server.forward-headers-strategy=native`, para que la bitácora registre la IP real.
3. **Respaldos automáticos y restauración probada.**
   - **Hoy:** los respaldos son manuales, y los PDF están en el disco de la PC de la aplicación.
   - **Hace falta:** un `pg_dump` diario de `gestion_documental` y una copia diaria de la carpeta de PDF a otro equipo, más una restauración completa probada en la base de pruebas.
4. **Cuentas nominales.**
   - **Hoy:** en producción hay cuentas genéricas: `director_tic`, `jefe_bd`, `jefe_operativo`, `jefe_soporte`, `encargado_soporte`, `secretaria_tic`. Una bitácora que registra "Jefe Dpto." no identifica a una persona y no sirve como prueba.
   - **Hace falta:** crear una cuenta por persona y dar de baja las genéricas. La baja conserva su historial.
   - **Hoy el doble control no es real:** Juan Carlos Acosta operó también `zlobo` y `zlobo.admin` durante la separación de cuentas. Antes del piloto, Zaimy Lobo cambia esas dos contraseñas, y cada administrador usa solo su cuenta (`docs/pase-produccion.md`, 04/11/2026, 13:07).
   - **Separar administrar de tramitar (decisión D3): hecho el 04/11/2026,** antes de instalar la V28. Administran `juan.admin` y `zlobo.admin`; `juan.acosta` (`RESPONSABLE`) y `zlobo` (`MESA_ENTRADA`) solo tramitan. La consulta de la sección "Cuentas" de `docs/auditoria-logica-negocio.md` debe seguir saliendo vacía.
5. **Autenticación de la base de producción.**
   - Cambiar la contraseña con `\password gestion_documental` y guardarla en SCRAM.
   - Quitar la regla `trust` de `127.0.0.1`, encontrada el 03/11/2026: cualquier programa del servidor podía entrar como superusuario sin contraseña.
   - Los pasos están en `docs/despliegue-servidor.md`, sección 1b.
6. **Datos del piloto anterior.**
   - Resolver los pases sin acuse desde agosto y septiembre (8 en la base). Administración → Pases trabados muestra 7, porque no incluye el de `TIC-2026-000003`, que está finalizado.
   - Revisar los expedientes que muestre `db/herramientas/revisar_pendientes_salida.sql`.
   - **Todos los expedientes de producción son de prueba, según informó el usuario el 04/11/2026; todavía no hay ninguno real.** Decidir antes del piloto si se cierran o si se empieza con una base limpia, conservando un respaldo de la actual.
   - `TIC-2026-000003` es de prueba, según informó el usuario: su acuse vigente es un PDF cualquiera, sin valor de constancia. Si estos expedientes se conservan, deben quedar identificados como datos de prueba (`docs/pase-produccion.md`, 04/11/2026, 13:07).

## Antes del "Go", sin ser bloqueantes

- Cargar los feriados trasladables y los asuetos de 2026.
- Pasar OWASP ZAP (modo baseline) contra la base de pruebas.
- Capacitar a las mesas de entrada y a los responsables del piloto. Para el operador, el punto clave es "Derivar / Elevar" en lugar de crear un documento nuevo.
- Acordar quién hace la verificación semanal de la huella y dónde se guarda la última.

## Propuesta de alcance del piloto

- **Duración:** 4 semanas.
- **Alcance:** la DTIC y una dirección que intercambie expedientes con ella, por ejemplo la DGAF.
- **En paralelo:** se sigue llevando el libro de mesa de entrada en papel.
- **Seguimiento semanal:** pases trabados, demoras en rojo, verificación de la huella y observaciones de los usuarios.
- **Criterio para ampliar:** ningún expediente perdido o duplicado, las bitácoras ÍNTEGRAS y ningún error que obligue a corregir datos a mano.

## Pendientes técnicos que pueden esperar al piloto

- Registrar los inicios de sesión en la bitácora.
- Cargar los contadores después de mostrar la página, para ámbitos muy grandes como la Intendencia.
- OWASP Dependency-Check en cada versión.
- Inicio de sesión con LDAP de Carbonio.
