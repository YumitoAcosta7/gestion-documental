# Viabilidad de la prueba de campo (piloto en producción)

Revisión del 03/11/2026, con producción en V22. Estado actualizado el 05/11/2026, con producción en V28.

## Veredicto

**En lo funcional sirve para un piloto acotado, pero todavía no conviene arrancarlo.** Lo que falta es sobre todo de operación y de personas, no de desarrollo. Con designaciones a tiempo, la preparación se estima en 1 a 2 semanas.

Todos los expedientes de producción son de prueba, según informó el usuario el 04/11/2026. El piloto arranca con una base limpia ([plan-base-limpia.md](plan-base-limpia.md)).

## Estado de los bloqueantes (05/11/2026)

| # | Bloqueante | Estado |
|---|---|---|
| 1 | Servidor de aplicación | ✅ Servicio en 192.168.129.73 |
| 2 | HTTPS | 🟡 Funciona con certificado interno. **Falta instalarlo en los equipos del piloto,** o reemplazarlo por uno institucional |
| 3 | Respaldos | 🟡 El 06/11/2026, el servicio copió la base y los PDF a `192.168.129.71`, los PDF traídos de vuelta resultaron idénticos, y la base se restauró en una base temporal con los mismos conteos. **Falta confirmar la ejecución del temporizador con este esquema y definir el cifrado en `.71`** |
| 4 | Cuentas nominales | 🟡 Administrar y tramitar están separados (`juan.admin` y `zlobo.admin`). **Faltan las cuentas por persona:** la base limpia no copia las 6 genéricas ni a ningún usuario |
| 5 | Autenticación de la base | ✅ Contraseña cambiada, SCRAM y sin `trust` |
| 6 | Datos de prueba | 🟡 El corte a la base limpia los deja fuera, y se conservan en la base anterior y en el respaldo. Está ensayado de punta a punta. **Falta hacerlo:** es el último paso |

## Lo que falta antes de arrancar

### Técnico

- [x] Instalar en producción los PR #56 (derivación interna y externa) y #57 (reparto): instalados el 05/11/2026, servicio activo desde las 14:09:40. Falta verlos en pantalla en producción.
- [ ] Confirmar la copia automática de las 22:00 en `.71`.
- [ ] Definir el cifrado de las copias en `.71`.
- [x] Pasar los PDF a copia incremental y traerlos de vuelta desde `.71`: idénticos el 06/11/2026.
- [x] Probar la restauración de la base con el respaldo actual: mismos conteos el 06/11/2026.
- [ ] Instalar el certificado en los equipos del piloto.
- [ ] Cargar los feriados trasladables y los asuetos de 2026.
- [ ] Separar los roles de PostgreSQL antes del corte.
- [ ] Revisar M11 (`docs/auditoria-logica-negocio.md`): un trámite interno solo termina con una salida externa.
- [ ] Pasar OWASP ZAP (modo baseline) contra la base de pruebas.
- [ ] Hacer el corte a la base limpia.

### Personas y actos

- [ ] Lista de personas del piloto, con su dependencia, rol y acto administrativo. Producción todavía no tiene cuentas en la DGAF.
- [ ] Al menos dos personas por mesa, o cuentas en sus dependencias subordinadas: quien envía un pase no puede confirmarlo.
- [ ] Un acto real para los dos administradores.
- [ ] Zaimy Lobo con sus propias contraseñas. Juan Carlos Acosta operó `zlobo` y `zlobo.admin` durante las pruebas: hasta que eso cambie, el doble control no es real (`docs/pase-produccion.md`, 04/11/2026, 13:07).
- [ ] Capacitación a mesas y responsables. Para el operador, el punto clave es "Derivar / Elevar" en lugar de crear un documento nuevo.
- [ ] Quién hace la verificación semanal de la huella, y dónde se guarda la última.
- [ ] Escudo oficial y confirmación de Comunicación sobre el color y el logo "ASU".

### Decisiones

- [ ] **Consulta a Asuntos Jurídicos:** cómo se concilia la regla de pasar por la Dirección General y de nivel en nivel con el art. 50 de la Ley 6715/21 y el art. 3 de la Res. 2653/12. También el cómputo de plazos entre niveles, la constancia de las copias y el acto necesario para el piloto. No frena técnicamente el piloto, pero conviene tener la respuesta antes de presentarlo como procedimiento.
- [ ] Copias para conocimiento: acuse en PDF, como hoy, o un "visto" registrado en el sistema.
- [ ] Dirección que acompaña a la DTIC. Con la DGAF, sus direcciones solo participan si tienen usuarios.

## Lo que está listo

| Área | Estado |
|---|---|
| Unidad de expediente | Un asunto, un CUTI, con serie por cabecera. Se elevan los documentos sin crear expedientes nuevos. Se rechazan los documentos repetidos. Las respuestas quedan cosidas a su pedido, y se registra el legajo de la salida. Se quitó la salida directa. |
| Custodia | Solo quien tiene el original deriva, devuelve, da salida o agrega actuaciones. Con el original en camino, no se modifica nada. Traslado de custodia con doble control. |
| Canal | Se sube y se baja de a un nivel, y las direcciones de una Dirección General se comunican por ella. Cada envío toma un solo camino: dentro del área, a otras direcciones o al superior. El servidor lo controla. |
| Reparto | Quien recibe una copia o un pedido de informe, sin tener el original, lo baja a su nivel inferior. Cada nivel responde por el suyo. |
| Acceso | Ámbito por asignación y vigencia, con control en el servidor de cada operación. Matriz de autorización automatizada. La administración está separada de los expedientes, con doble control. |
| Sesiones | Bloqueo por usuario y por IP. Las sesiones se cierran al dar de baja, restablecer o cambiar la contraseña. Vencen a los 30 minutos sin actividad. |
| Contenido | CSP estricta sin scripts en línea, bibliotecas servidas por la propia aplicación (sin CDN) y cookies `HttpOnly`/`SameSite`. Los PDF se analizan con PDFBox. |
| Trazabilidad | Bitácoras inmutables, propiedad de otro rol, con huella SHA-256 encadenada e IP. Verificación disponible. |
| Rendimiento | Medido con 200 000 expedientes: listas de 12 a 84 ms, página principal en 1,5 s. Buscador con trigramas. No se probó con muchos usuarios a la vez. |
| Pruebas | 266 automáticas: 265 pasan y 1 se saltea. Pedido de PC y reparto probados en pantalla, con varias cuentas, sobre la base de pruebas. |
| Material | Manual de uso y presentación ejecutiva en `docs/material`. |

## Sin probar con cuentas reales

Están cubiertos por pruebas automáticas, salvo el marcado de todas las dependencias y el texto del aviso al responder. La base de pruebas no tiene receptores en las direcciones de la DGAF.

- El reparto de una Dirección General a sus direcciones, y el marcado de todas.
- Responder sin esperar a las áreas, y el aviso que lo anticipa.
- El bloqueo de pases entre direcciones de una misma Dirección General.

## Riesgos conocidos para el piloto

- **Plazos:** el sistema usa un plazo general, de 5 y 10 días, o de 2 y 4 si es urgente. La Res. 2653/12 fija 48 horas para las mesas y 8 días hábiles para informes y dictámenes, así que avisa tarde a las mesas. Durante el piloto se controla en el seguimiento semanal.
- **Protección en la base:** quedan pendientes M5 y M8. Afectan a quien tenga acceso directo a la base.
- **Interfaz:** a 1280 píxeles la tabla corta el botón "Más", algunos encabezados de ventanas no siguen el tema, y aparecen códigos crudos como `PASE_CONFIRMADO`.
- **Documentos informativos archivados:** la opción de repartir no aparece en la pestaña de Archivo físico.
- **Quitar una ubicación:** se puede cambiar, pero no eliminar. Si una dependencia ya no tiene el papel, hoy no hay forma de dejarlo registrado.
- **Una sola persona en una mesa:** no puede enviar a una subordinada sin usuarios, porque nadie más podría recibirlo.

## Propuesta de alcance del piloto

- **Preparación:** plan de arranque con una base limpia en [plan-base-limpia.md](plan-base-limpia.md).
- **Duración:** 4 semanas.
- **Alcance:** la DTIC y una dirección que intercambie expedientes con ella, por ejemplo la DGAF.
- **En paralelo:** se sigue llevando el libro de mesa de entrada en papel.
- **Seguimiento semanal:** pases trabados, demoras en rojo, verificación de la huella y observaciones de los usuarios.
- **Criterio para ampliar:** ningún expediente perdido o duplicado, las bitácoras ÍNTEGRAS y ningún error que obligue a corregir datos a mano.
- **Fuera de alcance:** la consulta de ciudadanos, la carátula y la contraseña, y la integración con el seguimiento de expedientes (SISE), que queda para una fase futura.

## Pendientes técnicos que pueden esperar al piloto

- Registrar los inicios de sesión en la bitácora.
- Cargar los contadores después de mostrar la página, para ámbitos muy grandes como la Intendencia.
- OWASP Dependency-Check en cada versión.
- Inicio de sesión con LDAP de Carbonio.
- Plazos por etapa según la Res. 2653/12, y registro del urgimiento de la Ley 4679/12.
