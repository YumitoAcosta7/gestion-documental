# Auditoría de la lógica de negocio

**Fecha:** 04/11/2026. **Versión revisada:** rama `feature/devolver-y-salida`, commit `c543a6a`, migración V22.

## Veredicto

**No conviene abrir un bloque de desarrollo nuevo todavía.** La base es sólida: custodia, canal, concurrencia de pases y bitácoras resisten bien. Pero hay 6 fallas de lógica que el uso diario va a encontrar, y varias se corrigen con cambios chicos. Recomiendo corregir primero el Bloque 1 (ver [Plan](#plan-propuesto)) y recién después seguir con el piloto y la interfaz.

Tres decisiones eran de negocio y no se podían resolver desde el código. Están en [Decisiones tomadas](#decisiones-tomadas).

### Cómo se comprobó

| Marca | Significado |
|---|---|
| **Reproducido** | Una prueba temporal contra el servicio real dio ese resultado. Usa simulaciones de los repositorios: prueba la lógica de Java, no la base. La prueba no quedó en el repositorio. |
| **Por lectura** | Surge de leer el código y las migraciones, sin ejecutarlo. |

Nada se probó con usuarios reales ni contra la base de producción.

### Datos del pedido que están desactualizados o no son exactos

- **Pruebas:** hay 136 métodos de prueba, no 105.
- **"Un CUTI por cabecera":** el número sale de la serie de la cabecera, pero el control de duplicados es global para toda la institución (A3).
- **"Inmutabilidad de actuaciones":** existe solo en la aplicación. Las bitácoras sí son inmutables en la base; las actuaciones no (M5).
- **"Aislamiento estricto de `ADMIN_SISTEMA`":** ese rol no da acceso a expedientes, pero quien lo tiene trabaja como operador de su propia dependencia (M6).

## Hallazgos de prioridad alta

### A1. Se puede registrar la salida sin responder los informes ni justificarlo

- **Evidencia:** Reproducido. Con un informe sin responder, la salida se aceptó, el expediente pasó a `PENDIENTE_SALIDA` y no quedó constancia de cierre. `registrarSalidaDocumento` no mira el estado.
- **Caso de uso:** Hacienda pide informe a TIC. La mesa, en lugar del "Cierre excepcional" con su justificación de 20 caracteres, pulsa "Registrar salida" en el menú "Más".
- **Impacto:** el cierre excepcional queda como un control optativo. No hay rastro de por qué salió sin el informe.
- **Solución:** en `registrarSalidaDocumento`, con pedidos abiertos, rechazar con "use el cierre excepcional". En `SituacionExpediente`, ofrecer la salida solo si no hay pedidos abiertos. **Depende de la decisión D1.**

### A2. Devolver un pedido de informe o una copia altera todo el expediente

- **Evidencia:** Reproducido. La dependencia que recibió un pedido sin confirmar lo devolvió: el expediente pasó de `EN_ESPERA_INFORME` a `EN_REVISION`, aunque otro pedido seguía abierto, y el pase quedó pendiente, sin confirmar ni anular. `devolverDocumento` fija `EN_REVISION` siempre.
- **Caso de uso:** Hacienda pide informes a TIC y a Obras. Obras contesta "no nos corresponde" con Devolver.
- **Impacto:**
  - El expediente deja de esperar el informe de TIC. Como el avance automático solo actúa desde `EN_ESPERA_INFORME`, ya no llega solo a `PENDIENTE_SALIDA`.
  - El pedido de Obras sigue abierto y en su bandeja.
- **Solución:**
  - Quien no tiene el original no devuelve: rechaza el pase (o responde el informe). Exigir custodia en `devolverDocumento`.
  - Si tiene el original, exigir que el último pase esté confirmado, y recalcular el estado en lugar de fijarlo.
  - La pantalla ya no lo ofrece sobre lo no recibido, pero sí sobre un pedido confirmado; el servidor lo acepta en los dos casos.

### A3. El control de duplicados impide que otra dirección registre el mismo documento

- **Evidencia:** Reproducido. `findFirstByDependenciaOrigenIdAndNumeroDocumentoOrigen` no filtra por cabecera.
- **Caso de uso:** la Intendencia envía la nota 45/2026 a TIC y a Hacienda. TIC la registra. Hacienda recibe "ya está registrado como DTIC-2026-000003… regístrelo como actuación ahí", en un expediente al que no tiene acceso. Pasa también con cualquier circular.
- **Impacto:**
  - Bloquea el trabajo. La única salida es cargarlo como S/N, que falsea el dato.
  - Revela a otra dirección el CUTI de un expediente que no puede ver.
  - Si el expediente ya está `FINALIZADO`, el mensaje también lleva a un callejón sin salida, porque no admite actuaciones.
- **Solución:** comparar dentro de la misma cabecera, que es lo que dice la regla. Sumar un índice único en la base (M8).

### A4. Un memo cualquiera puede cerrar un pedido de informe

- **Evidencia:** Reproducido. Un memo de un departamento, sin relación con el pedido, quedó cosido al pedido hecho a su Dirección y el expediente pasó a `PENDIENTE_SALIDA`.
- **Caso de uso:** Hacienda pide informe a TIC. TIC pasa el original a un departamento y este carga un memo interno pidiendo insumos. El sistema lo toma como la respuesta oficial de TIC.
- **Causa:** `registrarActuacion` cose automáticamente todo informe o memo del área al pedido abierto. El usuario no elige ni ve qué se cerró. Por ejemplo, una dependencia con custodia no puede cargar un memo sin que cuente como respuesta.
- **Impacto:** se cierra un pedido sin respuesta real, y puede salir con eso. Corrompe la unidad del expediente.
- **Solución:**
  - El formulario pregunta "¿Responde a un pedido?" y lista los abiertos. Solo cose si se elige uno, enviado como `pedidoId`, y el servidor valida que corresponda.
  - Si la respuesta viene de una subordinada y el pedido era a la Dirección, debe confirmarla quien actúa por la Dirección.

### A5. El doble control de asignaciones se puede evitar

- **Evidencia:** Por lectura (`requiereDobleControl`, `actuaPor`, `dependenciaDeRegistro`).
- **Caso de uso 1:** un solo administrador asigna `MESA_ENTRADA` con ámbito en la *Unidad de Secretaría Administrativa* de una dirección. Como esa unidad está por debajo del nivel Dirección, no pide aprobación. Pero es donde queda el original de todo lo que entra a esa dirección, y `MESA_ENTRADA` da salida, cierre excepcional y auditoría.
- **Caso de uso 2:** un administrador crea un usuario en esa unidad, sin asignaciones. Trabaja como operador de esa dependencia y puede confirmar los pases que llegan y mover la custodia.
- **Caso de uso 3:** un administrador restablece la contraseña de una mesa, recibe la temporal y entra en su nombre. Queda el evento, pero no hay segunda firma ni aviso.
- **Impacto:** el control C2 cubre el caso visible y deja abierto el de mayor efecto real.
- **Solución:**
  - En `requiereDobleControl`, tratar como sensible todo rol de trámite cuyo ámbito sea una mesa de entrada, además del nivel.
  - Doble control al dar de alta o mover un usuario hacia una mesa de entrada.
  - En los restablecimientos de cuentas con roles sensibles: aprobación de otro administrador, o aviso al correo del titular.

### A6. No hay forma de corregir un expediente mal ingresado, ni de reabrir uno finalizado

- **Evidencia:** Por lectura. Ningún endpoint edita los datos de cabecera ni anula un expediente. `FINALIZADO` solo se alcanza con el acuse y no tiene vuelta.
- **Caso de uso 1:** la mesa carga mal el número (120/2026 en lugar de 102/2026) o el origen. El dato queda para siempre, y el número equivocado bloquea el registro del documento real que tenga ese número.
- **Caso de uso 2:** se confirma el acuse final en el expediente equivocado. Queda finalizado sin remedio.
- **Impacto:** hoy la única corrección posible es por SQL, lo que rompe la trazabilidad que el sistema protege. Es el pedido de soporte más probable del piloto.
- **Solución:**
  - "Corregir datos del ingreso" para quien tiene el original, con motivo y valores anterior y nuevo en la bitácora.
  - "Anular expediente": estado nuevo `ANULADO`, solo sin movimientos, con motivo. El CUTI no se reutiliza.
  - "Reabrir" con doble control, solo con `FORZAR_CIERRE`. **Depende de la decisión D3.**

## Hallazgos de prioridad media

### M1. La devolución mueve el original sin acuse, y puede repetirse

- **Evidencia:** Reproducido. Quien recibió una devolución puede devolverla, y la custodia vuelve al otro lado sin que nadie confirme, porque `findTop…AnuladaFalse…` toma la `DEVOLUCION` como si fuera un pase recibido.
- **Caso de uso:** dos direcciones se pasan un expediente incómodo. Cada una ve que "no lo tiene" sin haber firmado nada.
- **Impacto:** todo pase de trámite exige acuse con copia sellada, pero la devolución mueve el mismo papel solo con un motivo. Tampoco comprueba que el destino tenga quién lo reciba.
- **Solución:** **Depende de la decisión D2.** Como mínimo, excluir `DEVOLUCION` del cálculo del "último pase recibido" y registrar `CAMBIO_CUSTODIA` también en este camino, que hoy no lo hace.

### M2. Pases imposibles de resolver en expedientes finalizados

- **Evidencia:** Reproducido. En un `FINALIZADO`, `anularPase` y `retirarPaseTrabado` rechazan. Por lectura: el acuse final no anula los pases de informe o conocimiento pendientes, y las consultas de demorados no filtran por estado.
- **Caso de uso:** se confirma el acuse con una copia para conocimiento sin recibir. Ese pase queda para siempre en los avisos de la mesa y en el panel de pases trabados, sin ningún botón que lo resuelva.
- **Nota:** el comentario de `BandejaService` dice que al cerrar se anulan los pases pendientes. No es así.
- **Solución:** en `confirmarEntregaYAcuse`, anular los pendientes con un tipo nuevo `CIERRE_EXPEDIENTE`, y filtrar por estado en las consultas. Para los que ya existan, una migración de datos.

### M3. Un pedido anterior a un cierre excepcional traba el avance automático

- **Evidencia:** Reproducido. Tras un cierre, se envió un pedido nuevo y se respondió: el expediente siguió en `EN_ESPERA_INFORME`, porque el pedido confirmado y sin respuesta de antes del cierre sigue contando.
- **Solución:** el cierre cierra también los pedidos confirmados, con una columna `pedido_cerrado_en` o una actuación de cierre vinculada, y el cálculo los excluye.

### M4. Tipos de actuación reservados sin permiso

- **Evidencia:** Reproducido en el servicio; por lectura en la base (`chk_actuaciones_tipo` lo admite y `chk_actuaciones_pase_con_receptora` no lo impide). `registrarActuacion` recibe `tipoActuacion` del formulario sin lista de permitidos.
- **Caso de uso:** alguien con custodia y sin `FORZAR_CIERRE` altera la petición y crea un nodo `CIERRE_EXCEPCIONAL` con el texto que quiera. Aparece en el árbol como un cierre, aunque no cambia el estado. `DEVOLUCION` y los tipos de pase los frena la base, por falta de receptora.
- **Solución:** en el servicio, admitir solo `INFORME_TECNICO`, `ACTA_ENTREGA`, `MEMO` y `PROVIDENCIA`. Es una línea.

### M5. Las actuaciones y la custodia no están protegidas en la base

- **Evidencia:** Por lectura. Los triggers de inmutabilidad y la huella encadenada cubren solo las dos bitácoras. El rol de la aplicación puede cambiar `descripcion_resumen`, `usuario_id` o `dependencia_custodia_id` por SQL, y nada lo detecta.
- **Solución:**
  - Trigger en `actuaciones_documento` que permita actualizar solo los campos de confirmación, anulación y PDF.
  - Control de consistencia, en la línea de `revisar_pdf.sh`, que compare la custodia con el último pase confirmado o devolución, y el estado con la bitácora.

### M6. `ADMIN_SISTEMA` no está aislado del negocio

- **Evidencia:** Por lectura, y lo fija una prueba (`elAdministradorDelSistemaNoTramitaPorEsaAsignacion`): solo con ese rol recibe `REGISTRAR_ACTUACION` y su propia dependencia como ámbito.
- **Caso de uso:** un administrador cuya dependencia es la mesa de TIC ve y confirma pases de esa mesa, además de gestionar usuarios.
- **Solución:** **parte de la decisión D3**. Si se quiere aislamiento real: quien solo tiene `ADMIN_SISTEMA` no tramita ni ve expedientes, en `PoliticaAcceso` y en `CTE_AMBITO`. Si necesita tramitar, usa otra cuenta.

### M7. Custodia en una dependencia sin nadie

- **Evidencia:** Por lectura. `setDependenciaCustodia` solo se usa al ingresar, al confirmar y al devolver. El panel de trabados cubre pases, no custodias.
- **Caso de uso:** una dependencia se disuelve, o se dan de baja sus usuarios, con originales a su cargo. Nadie puede moverlos. También aplica a las reorganizaciones del organigrama.
- **Solución:** "Traslado de custodia", con doble control, motivo y acto administrativo. Ampliar el panel con custodias en dependencias sin receptor o inactivas.

### M8. Faltan restricciones en la base

- **Evidencia:** Por lectura. Hoy el duplicado se controla solo en Java y sin bloqueo: dos registros simultáneos pueden pasar. El límite de un trámite pendiente depende del bloqueo de aplicación.
- **Solución (V23, con revisión previa de los datos existentes):**

```sql
CREATE UNIQUE INDEX uq_tramite_pendiente ON actuaciones_documento (documento_id)
 WHERE tipo_pase = 'TRAMITE' AND tipo_actuacion = 'PASE_INTERNO' AND NOT anulada AND NOT pase_confirmado;

-- Tras revisar que no queden expedientes sin custodia
ALTER TABLE documentos ALTER COLUMN dependencia_custodia_id SET NOT NULL;
```

Para el duplicado por cabecera hace falta guardar la cabecera en `documentos`, porque un índice no puede calcularla desde el organigrama.

### M9. Interbloqueo real, pero acotado: cierre excepcional contra confirmar o retirar

- **Evidencia:** Por lectura, sin reproducir. `confirmarRecepcionPaseInterno`, `anularPase`, `retirarPaseTrabado` y `reemplazarPdfActuacion` bloquean primero la actuación y después el expediente. `forzarCierreInformes` bloquea el expediente y después actualiza la actuación.
- **Impacto:** si coinciden, PostgreSQL aborta una de las dos con un error al usuario. No corrompe nada, porque la transacción se revierte. Puede demorar unos instantes también a otros, porque quien espera ya puede tener tomada la fila de la huella de la bitácora.
- **Lo demás:** dos derivaciones simultáneas no se interbloquean: `derivarDocumento` toma un solo bloqueo y se ejecutan en fila.
- **Solución:** orden único: leer la actuación sin bloqueo, bloquear el expediente y después la actuación.

### M10. "Informes pendientes" usa una regla distinta de la del servicio

- **Evidencia:** Por lectura. La bandeja compara `r.dependencia_emisora_id = a.dependencia_receptora_pase_id`. El servicio usa área, fecha y `actuacion_padre_id`.
- **Impacto:** un pedido a una mesa, respondido por un departamento de esa dirección, sigue en la bandeja. Y una respuesta de un pedido anterior oculta uno nuevo a la misma dependencia.
- **Solución:** usar `actuacion_padre_id` y el mismo criterio en SQL, con una prueba contra PostgreSQL.

### M11. Un trámite interno solo termina con una salida externa

- **Evidencia:** Por lectura. El único camino a `FINALIZADO` es salida más acuse. `ARCHIVADO` solo se asigna al ingresar un documento informativo.
- **Caso de uso:** un memo interno resuelto en la propia dirección no sale a ningún lado. Se le inventa una salida o queda abierto para siempre, y distorsiona los contadores.
- **Solución:** "Concluir sin salida", con motivo y bibliorato, para quien tiene el original.

## Hallazgos de prioridad baja

| Hallazgo | Solución |
|---|---|
| `registrarActuacion` cambia `ubicacion_bibliorato` del expediente sin `GESTIONAR_ARCHIVO` y sin evento propio (por lectura). | Quitar ese efecto. La ubicación se cambia solo por `cambiarUbicacion`. |
| Se puede reemplazar el PDF de una actuación que ya integra el legajo entregado, o de un expediente finalizado. Quedan las versiones, pero lo vigente difiere de lo entregado (por lectura). | Bloquearlo después de la salida o exigir doble control. |
| La mesa receptora ve "Retirar" en el historial y queda registrado como `PASE_RETIRADO`, no como rechazo (visto en pantalla). | Con `supervisaPase`, limitar el retiro al lado emisor. |
| `forzarCierreInformes` acepta expedientes sin pedidos abiertos: la pantalla no lo ofrece, el servidor sí (por lectura). | Exigir `EN_ESPERA_INFORME`. |
| Retirar un trámite enviado desde `PENDIENTE_SALIDA` deja `EN_REVISION` y pierde el estado anterior (por lectura). | Recalcular el estado al anular. |
| Dos definiciones de ámbito: `deps` en SQL y `actuaPor` en Java. Un `OPERADOR` con subordinadas ve en "En mi poder" lo que no puede operar, y alguien con asignaciones en otro lado no ve en sus bandejas lo de su propia dependencia (por lectura). | Unificar la regla y probarla contra la base. |
| Estado `CON_RESPUESTA_TECNICA` sin uso (por lectura). | Quitarlo en una migración, si no hay filas. |
| Control de fojas superpuestas sin bloqueo: dos cargas simultáneas podrían pisarse (por lectura). | Restricción de exclusión o bloqueo del bibliorato. |
| La huella encadenada toma una sola fila por bitácora hasta el commit: toda escritura auditada va en fila (por lectura). | Sin problema para el piloto. Medir con volumen antes de ampliar. |
| Posible lectura desactualizada: si el expediente se cargó antes del `FOR UPDATE`, Hibernate podría no refrescarlo. `open-in-view` está activo. Sin confirmar, y la ventana es de milisegundos. | Comprobar con una prueba de integración; `entityManager.refresh` tras el bloqueo. |

## Comprobado que funciona bien

- **Emisora de la actuación:** la fija el servidor con la dependencia del usuario; no se puede falsificar desde el formulario.
- **Pases simultáneos:** el bloqueo del expediente y `uq_pase_pendiente_por_receptora` impiden dos trámites cruzados o duplicados.
- **Confirmación:** solo quien actúa por la receptora, con PDF; el emisor queda afuera (probado en pantalla).
- **CUTI:** la serie toma la fila hasta el commit y `codigo_cuti` es único.
- **Con el original en camino:** no se deriva, ni se da salida, ni se confirma el acuse.
- **Con la salida registrada:** no se deriva, no se devuelve, no se agregan actuaciones (probado en pantalla).
- **Suplencias:** vencen solas, y no dan más de lo que tiene el titular.
- **Administrador:** no se asigna roles de trámite a sí mismo, y no aprueba lo que pidió.
- **Bitácoras:** inmutables, con huella encadenada verificada en las dos bases.

## Decisiones tomadas

El responsable del proyecto aceptó las tres recomendaciones el 04/11/2026.

**D1. No se da salida con informes sin responder.** Se bloquea hasta responderlos o hacer el cierre excepcional, para que siempre quede la justificación.

**D2. La devolución del original necesita acuse.** Será un pase de vuelta con confirmación, porque el papel se mueve igual. Queda para el Bloque 2.

**D3. Reabrir, anular y administrar.** Reabrir solo con doble control, anular solo sin movimientos, y quien administra no tramita con esa misma cuenta. Queda para el Bloque 2.

## Estado de las correcciones

Bloque 1, en el PR de esta fecha. Cómo se resolvió lo que el informe dejaba abierto:

- **A4:** quien responde elige el pedido. Las respuestas sin vínculo anteriores al 04/11/2026 siguen contando por área y fecha; las nuevas no responden nada. Un departamento no cierra el pedido hecho a su Dirección.
- **M3:** no hizo falta una columna nueva. Un pedido anterior a un cierre excepcional se da por cerrado, y ya no se puede responder.
- **M10:** se adelantó, porque A4 la agravaba. "Informes pendientes" usa el vínculo con el pedido y excluye los cerrados.
- **M2:** los pases sin recibir se anulan al finalizar (migración V23). Los que ya estaban en esa situación no se modifican; dejan de aparecer en los avisos.
- **Baja:** el cierre excepcional ya exige informes pendientes. Con esto también se resolvió ese hallazgo.
- **Límite conocido de M9:** el orden de bloqueos se corrigió, pero no se probó con concurrencia real.

Bloque 2, en PR separados:

- **M1 y D2, devolución con acuse:** es un pase de trámite de vuelta (`es_devolucion`, migración V24). El original sigue a cargo de quien devuelve hasta el acuse, se registra `CAMBIO_CUSTODIA` al confirmar, y se puede retirar o rechazar. Una devolución no cuenta como entrega: no se devuelve de vuelta. Las devoluciones anteriores quedan como están.
- **A5, casos 1 y 2, doble control de las mesas:** todo rol de trámite con ámbito en una mesa de entrada requiere la aprobación de otro administrador. Pertenecer a una mesa, sin asignación vigente, ya no permite actuar, ver sus expedientes ni iniciar documentos. Antes de instalarlo se corre `db/herramientas/revisar_cuentas_mesa.sql`. Las asignaciones ya vigentes no se tocan: esa consulta las lista para revisarlas.
- **A6, corregir y anular:**
  - "Corregir datos del ingreso" (tipo, número, origen, asunto y prioridad), con motivo, para quien tiene el original y es mesa de entrada o cargó el expediente. Solo antes de la salida. Los valores anteriores quedan en la bitácora (`CORRECCION_INGRESO`) y el CUTI no cambia.
  - "Anular expediente" (estado `ANULADO`, migración V25), mismas personas, solo sin movimientos. No se borra, el CUTI no se reutiliza, su número queda libre y se liberan las fojas.
- **A6 y D3, reabrir un finalizado:** la pide quien tiene `FORZAR_CIERRE` y el original, con motivo, y la aprueba otra persona con ese permiso (`solicitudes_reapertura`, migración V26). La base impide aprobar la propia y que haya dos pendientes. Al aprobarse vuelve a `PENDIENTE_SALIDA` con su salida registrada, y el acuse final deja de estar vigente, sin borrarse. Los pases anulados al finalizar no se restituyen.
- **A5 caso 3, contraseñas de cuentas sensibles:** se consideran sensibles las cuentas con una asignación que requiere doble control. Las pide un administrador con motivo y las aprueba otro, que no sea el dueño de la cuenta (`solicitudes_contrasena`, migración V27). Quien aprueba recibe la temporal, una sola vez, y no se guarda. Con un solo administrador se hace igual, y la auditoría lo dice.
- **M6 y D3, aislar al administrador:** una cuenta con `ADMIN_SISTEMA` vigente solo administra, aunque tenga otras asignaciones: no tramita, no actúa por ninguna dependencia y no ve expedientes. Una cuenta no puede sumar administrar y tramitar: el servidor lo rechaza. **`juan.acosta` y `zlobo` tienen los dos roles en producción** (consulta del 04/11/2026) y hay que separarlos antes de instalar: ver `docs/viabilidad-piloto.md`.
- **M12, altas y roles (decisión del 04/11/2026):** toda asignación requiere la aprobación de otro administrador, y una cuenta sin asignación vigente no puede trabajar ni ver expedientes. Así un administrador no puede crear una cuenta que tramite por su cuenta. Antes de instalar se corre `db/herramientas/revisar_cuentas_sin_asignacion.sql`.
- **Riesgo aceptado:** las contraseñas de cuentas comunes se restablecen sin segunda firma, para no trabar el trabajo diario. Un administrador podría entrar en nombre de un operador: queda en la auditoría y se limita a lo que ese operador puede hacer. Se revisa cada semana, y lo resuelve de fondo el inicio de sesión institucional.
- **M7, traslado de custodia:** un panel lista los originales a cargo de una dependencia de baja o sin nadie que actúe por ella. Un administrador pide el traslado con motivo y acto administrativo, y otro lo aprueba (`solicitudes_traslado_custodia`, migración V28). La base impide aprobar el propio y que haya dos pendientes por expediente. Queda `CAMBIO_CUSTODIA` en la bitácora del expediente y el pedido en la de administración. No vale para una dependencia que funciona, que mueve lo suyo con un pase, ni con el original en camino. Las condiciones se revisan otra vez al aprobar. **Límite:** se pide por expediente; disolver una dependencia con muchos originales requiere un pedido por cada uno.
- **Corrección junto con M7, quién puede recibir:** la consulta de receptores (`findConReceptor`) seguía contando cuentas sin asignación, de administración y miembros de una mesa sin rol, que desde M6, M12 y A5 ya no pueden actuar. Ahora sigue la misma regla que `CanalService.actuaPor` y excluye las dependencias de baja. Afecta también a los avisos al derivar y al panel de pases trabados.
- **Bloque 2 completo.**

### Cuentas

Ninguna cuenta debería tener los dos roles. Esta consulta, de solo lectura, debe salir vacía:

```sql
SELECT u.username, string_agg(DISTINCT a.rol, ', ') AS roles
  FROM usuarios u JOIN asignaciones a ON a.usuario_id = u.id
 WHERE u.activo AND a.activa AND a.vigente_desde <= CURRENT_DATE AND (a.vigente_hasta IS NULL OR a.vigente_hasta >= CURRENT_DATE)
 GROUP BY u.username
HAVING bool_or(a.rol = 'ADMIN_SISTEMA') AND bool_or(a.rol <> 'ADMIN_SISTEMA');
```

## Plan propuesto

**Bloque 1: antes del piloto, cambios chicos.**
- A1, A2, A3, A4.
- M2, M3, M4.
- El orden de bloqueos de M9.

**Bloque 2: antes del piloto, cambios de gobernanza.**
- A5.
- A6.
- M1, M6, M7, según lo que se decida.

**Bloque 3: al ampliar más allá del piloto.**
- M5, M8, M10, M11.
- Los hallazgos de prioridad baja.
- Las etapas 2 y 3 de la interfaz. La tabla que no entra a lo ancho conviene adelantarla, por el impacto en el uso diario.

**Siguen pendientes los bloqueantes de operación** de [viabilidad-piloto.md](viabilidad-piloto.md): copia de respaldos a otro equipo, cuentas nominales y pases sin acuse. Este plan no los reemplaza.

**Limitaciones de esta auditoría:**
- No se probó concurrencia real contra PostgreSQL.
- Las pruebas de servicio no ejercitan las restricciones de la base.
- Las consultas de bandejas se revisaron por lectura.
- No se midió con volumen en esta revisión.
