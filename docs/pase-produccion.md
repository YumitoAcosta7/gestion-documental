# Pase a producción: migraciones V13 a V20

Lleva a la base real (`gestion_documental`, servidor 192.168.129.73) lo probado en la base de pruebas:
- administración de usuarios;
- auditoría de seguridad, con sus residuales;
- doble control de asignaciones;
- calendario de feriados;
- índices de rendimiento.

Todo se aplicó y se probó primero en `gestion_documental_pruebas`.

**Quién hace cada paso:**
- Pasos en el servidor: un administrador con acceso `root`.
- Pasos en la pantalla: `juan.acosta`.

**Duración:** unos 30 minutos. Conviene hacerlo fuera del horario de atención: durante los pasos 2 a 4 la aplicación no está disponible.

## 0. Antes de empezar

- [ ] Avisar a los usuarios del corte.
- [ ] Tener la rama `feature/devolver-y-salida` actualizada en el equipo desde el que se arranca la aplicación:
  ```powershell
  cd C:\Projects\gestion-documental
  ```
  ```powershell
  git switch feature/devolver-y-salida
  ```
  ```powershell
  git pull
  ```
- [ ] Copiar los scripts al servidor, desde ese equipo:
  ```powershell
  scp db\seguridad\proteger_bitacoras.sh db\seguridad\aprobar_asignacion.sh root@192.168.129.73:/tmp/
  ```

## 1. Ver en qué versión está la base (servidor)

```bash
sudo -u postgres psql -d gestion_documental -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"
```

- La última versión aplicada puede ser cualquiera entre la **12** y la **19**. El 03/11/2026 producción estaba en la **14**. Todas las filas tienen que decir `success = t`.
- Si alguna dice `f`, **no seguir**: hay una migración fallida que se revisa antes de continuar.

**Verificación de datos (solo lectura, se puede hacer días antes).**
- La base de pruebas tiene la estructura y los catálogos de producción, pero no sus expedientes.
- La única migración que puede rechazar datos existentes es **V15**: exige que todo pase y toda devolución tengan dependencia receptora.

```bash
sudo -u postgres psql -d gestion_documental -c "SELECT tipo_actuacion, COUNT(*) FROM actuaciones_documento WHERE tipo_actuacion IN ('PASE_INTERNO','PASE_CONFIRMADO','DEVOLUCION') AND dependencia_receptora_pase_id IS NULL GROUP BY tipo_actuacion"
```

- **Sin filas:** se puede seguir. El 03/11/2026 no devolvió filas.
- **Con filas:** **no seguir.** Son pases antiguos que identificaban la receptora solo por el nombre. Hay que completarlos antes; enviar el resultado para preparar la corrección.

## 2. Respaldo (servidor y equipo de la aplicación)

1. **Detener la aplicación de producción.**
2. **Respaldar la base.** En el servidor:
   ```bash
   mkdir -p /var/backups/gestion_documental && chown postgres /var/backups/gestion_documental
   ```
   ```bash
   sudo -u postgres pg_dump -Fc -d gestion_documental -f /var/backups/gestion_documental/antes_V20_$(date +%Y%m%d_%H%M).dump
   ```
   ```bash
   ls -lh /var/backups/gestion_documental/
   ```
   El archivo tiene que tener un tamaño razonable, de varios MB; no 0.
3. **Respaldar los PDF.** Copiar la carpeta `C:\gestion-documental-uploads` del equipo de la aplicación a otro disco.

## 3. Aplicar las migraciones (equipo de la aplicación)

Arrancar la aplicación **sin** el perfil de pruebas, como se arranca siempre en producción:
```powershell
.\mvnw.cmd spring-boot:run
```

En la salida tienen que aparecer las migraciones y el arranque:
```
Migrating schema "public" to version "15 - organigrama concurrente y receptora"
…
Migrating schema "public" to version "20 - indice actuacion padre"
Successfully applied 6 migrations …
Started GestionDocumentalApplication in … seconds
```

- Cada migración corre en su propia transacción.
- Si una falla, esa queda sin aplicar, la aplicación no arranca y el error dice cuál fue. **Copiar el mensaje y no reintentar.**

Repetir la consulta del paso 1: la última versión tiene que ser la **20**.

## 4. Proteger las bitácoras (servidor)

Con la aplicación arrancada o detenida, da igual:
```bash
cd /tmp && sudo -u postgres bash proteger_bitacoras.sh gestion_documental gestion_documental
```

- Las dos bitácoras pasan a un rol propio. La aplicación queda solo con permiso para leer y agregar: no puede modificar, borrar ni vaciar.
- Si muestra un error, copiarlo. No impide usar la aplicación, pero la bitácora queda sin esta protección.

## 5. Comprobar (pantalla, con `juan.acosta`)

- [ ] Inicia sesión y abre la pantalla principal sin errores.
- [ ] Las bandejas y los indicadores muestran números del ámbito del usuario.
- [ ] Abrir un expediente existente: el historial y los PDF se ven.
- [ ] Administración → aparecen las secciones de solicitudes pendientes, pases trabados y feriados.

Hasta acá la aplicación queda **operativa**. Los pasos que siguen se pueden hacer después.

## 6. Segundo administrador

Las asignaciones sensibles se aprueban entre dos administradores. Con uno solo, al segundo lo aprueba el DBA una vez:

1. **En la pantalla**, con `juan.acosta`:
   - crear el usuario, si no existe;
   - pedirle `Administrador del sistema` sobre la Intendencia, con el acto administrativo que lo respalda.

   Queda "Pendiente de aprobación".
2. **En el servidor**, listar las pendientes y aprobarla por su id:
   ```bash
   cd /tmp && sudo -u postgres bash aprobar_asignacion.sh gestion_documental
   ```
   ```bash
   cd /tmp && sudo -u postgres bash aprobar_asignacion.sh gestion_documental <id>
   ```
3. **El nuevo administrador** entra con su contraseña temporal y la cambia.

Desde ese momento, los dos se aprueban entre sí en la pantalla.

## 7. Feriados de 2026

V18 carga los feriados nacionales de **fecha fija**. En **Administración → Feriados y asuetos**, cargar según el decreto del año:
- Día de los Héroes (1 de marzo);
- Paz del Chaco (12 de junio);
- Victoria de Boquerón (29 de septiembre);
- los asuetos.

Los feriados trasladables pueden caer en otra fecha según el decreto. Hay que cargar el día en que efectivamente no se trabaja.

## 8. Contraseñas de las bases (servidor y equipo de la aplicación)

La contraseña de la base de pruebas quedó expuesta durante el desarrollo. Conviene cambiar las dos.

1. **En el servidor.** La contraseña se escribe por teclado y no queda en el historial:
   ```bash
   sudo -u postgres psql -c "\password gestion_documental"
   ```
   ```bash
   sudo -u postgres psql -c "\password gestion_documental_pruebas"
   ```
2. **En el equipo de la aplicación,** actualizar:
   - `application-local.properties`, o la variable `DB_PASSWORD`, para producción;
   - la variable `DB_PASSWORD_PRUEBAS`, para pruebas.
3. **Reiniciar la aplicación.**

## Si algo sale mal

**Antes del paso 4,** volver al estado anterior con el respaldo. Primero, con la aplicación detenida:
```bash
sudo -u postgres dropdb gestion_documental
```
```bash
sudo -u postgres createdb -O gestion_documental gestion_documental
```
```bash
sudo -u postgres pg_restore -d gestion_documental /var/backups/gestion_documental/antes_V20_<fecha>.dump
```

Después, arrancar la versión anterior de la aplicación.

**Después del paso 4,** el respaldo se restaura igual. Las bitácoras vuelven a ser del rol de la aplicación, y el paso 4 se repite cuando se vuelva a migrar.

## Registro

**03/11/2026.** Primer pase, de V14 a V20.

- **Antes:**
  - producción en V14, sin pases sin receptora;
  - respaldo `antes_V20_20261103_1314.dump` (82 KB, 14 tablas: 7 expedientes, 13 actuaciones, 581 dependencias, 9 usuarios);
  - carpeta de PDF copiada a otro disco.
- **Migraciones:** V15 a V20 aplicadas sin errores.
- **Bitácoras:** ya estaban protegidas; el script lo confirmó.
- **Comprobación con `juan.acosta`:**
  - pantalla principal en 345 ms;
  - CSP estricta y sin cabecera de diagnóstico;
  - consola sin errores;
  - administración con 18 feriados;
  - panel de pases con **8 pases del piloto de la DTIC** (agosto y septiembre de 2026, de 33 a 58 días hábiles sin acuse). Los resuelven la Coordinación de TIC y las dependencias receptoras: confirmar, rechazar o retirar.
- **Paso 6, 13:42 a 13:43:** segunda administradora `zlobo` (Zaimy Lobo), con `ADMIN_SISTEMA` sobre la Intendencia y sus subordinadas.
  - La pidió `juan.acosta` (asignación 12) y la aprobó el DBA con `aprobar_asignacion.sh`.
  - En la auditoría: `SOLICITUD_ASIGNACION` y `APROBACION_ASIGNACION_DBA`.
- **Quedan:** los pasos 7 (feriados trasladables) y 8 (contraseñas).

**03/11/2026, 14:12.** Segundo pase, de V20 a V22 (gobernanza: PR #23).
- **Respaldo:** `antes_V22_20261103_1412.dump` (98 KB). Se comprobó que no tiene `legajos_salida`, así que es anterior a las migraciones.
- **Migraciones:** V21 (legajo de la salida) y V22 (índices de trigramas) aplicadas a las 14:12:24, sin errores. `pg_trgm` se instaló desde la aplicación.
- **Huella encadenada:** instalada con `reforzar_bitacoras.sh gestion_documental`. Las dos bitácoras quedaron ÍNTEGRAS, con 25 filas la de expedientes y 2 la de administración.
- **Últimas huellas,** para el anclaje externo:
  - `auditorias_documento`: `83a594ade7b4934bc31086284740331bc67a0e788d4bd26901794d4c2694944d`
  - `auditoria_sistema`: `9d5eb223f71f83fcea35b8ad174bd4a5652c7cc78979c458ba882ce3777a3288`

**03/11/2026, 22:00.** Mudanza de la aplicación al servidor (`docs/despliegue-servidor.md`).
- **Autenticación de la base:** contraseña de producción cambiada y guardada en SCRAM. Se quitó la regla `trust` de `127.0.0.1` (copia en `pg_hba.conf.antes_scram`).
- **Instalado:** Java 25 (Temurin), nginx, `rsync` y `tar`.
- **Aplicación:** servicio `gestion-documental`, con entrada en `https://192.168.129.73` y certificado interno válido hasta noviembre de 2028. El puerto 80 redirige y el 8080 no se ve desde la red.
- **PDF:** 26 en la PC y 26 en el servidor. Un PDF antiguo se abre y "Verificar" dice "Íntegro".
- **Respaldo:** `respaldar.sh` genera la base, los PDF (26) y las huellas. El timer está programado para las 22:00. La base se restauró en una base temporal: 7 expedientes, 13 actuaciones y 25 filas de bitácora.
- **Bitácoras:** ÍNTEGRAS.
- **Sin comprobar todavía:**
  - la IP real del usuario en la auditoría, hasta el primer movimiento;
  - la restauración completa de los PDF.
- **Desde esta fecha, producción es la del servidor:** no se arranca desde la PC sin el perfil de pruebas.

**03/11/2026, 23:56.** Actualización con los PR #29 (bandeja por situación, bloqueo con la salida registrada, Corregir salida), #30 (falso duplicado de "S/N") y #31 (solo la receptora confirma un pase).
- **Respaldo previo:** `base_20261103_2355.dump`, con sus PDF y huellas.
- **Base:** sigue en V22, sin migraciones nuevas. La aplicación arrancó bien.
- **En pantalla:** la bandeja muestra una acción principal por expediente (con `secretaria_tic`).
- **Falta un PDF:** comparando la base con el disco se encontró que falta el acuse final de `TIC-2026-000003`, del 13/08/2026 (`TIC-2026-000003_ACUSE_CONFIRMADO_1786648022337.pdf`). No está en el servidor ni en el disco C de la PC.
  - Faltaba antes de la mudanza: la PC y el servidor tienen los mismos 26 archivos.
  - Corrige lo anotado a las 22:00: los 26 PDF se contaron, pero no se compararon con la base, así que ese control no probaba que estuvieran todos.
  - **Reposición:** el 04/11/2026 el usuario lo repuso con "Reemplazar acuse", según informó. Lo que queda vigente es una versión nueva, con huella propia, y la fila original queda como versión anterior sin archivo.
- **Corrección sobre "Verificar":** lo anotado a las 22:00 ("Un PDF antiguo se abre y Verificar dice Íntegro") no puede haber sido en producción. El único PDF con huella era ese acuse perdido, y "Verificar" lo informa como "Falta el archivo". Lo más probable es que esa prueba se hiciera en la base de pruebas. En producción sí se comprobó que los PDF se abren (`TIC-2026-000001`) y que "Verificar" detecta el faltante (`TIC-2026-000003`).
- **PDF sin huella:** el acuse del pase de `TIC-2026-000001` es anterior al registro de huellas y no tiene "Verificar". Los PDF nuevos sí lo tienen.
- **Archivos sobrantes:** de los 26, solo 2 son los que pide la base. Los otros 24 no figuran en ella. Sus fechas sugieren una carga anterior con otra numeración, sin que esté confirmado. No se borraron.
- **Actualización del 04/11/2026, 08:27:** PR #34 (Bloque 1 de `docs/auditoria-logica-negocio.md`). Respaldo previo `base_20261104_0826.dump`. Migración a V23 aplicada y confirmada en `flyway_schema_history`. Desde esta versión, un expediente con informes sin responder no puede registrar salida.
- **Sin comprobar todavía:**
  - "Verificar" dando "Íntegro" en producción, con el acuse repuesto;
  - la IP real del usuario en la auditoría, hasta el primer movimiento;
  - la restauración completa de los PDF.

**04/11/2026, 13:07.** Actualización hasta el PR #43 (Bloque 2 de `docs/auditoria-logica-negocio.md`), de V23 a V28.
- **Antes de instalar, separación de cuentas:**
  - Se crearon `juan.admin` y `zlobo.admin`, las únicas con `ADMIN_SISTEMA` vigente (desde el 04/11/2026). `juan.acosta` (`RESPONSABLE`) y `zlobo` (`MESA_ENTRADA`) quedaron solo con su rol de trámite.
  - La consulta de "Cuentas" de `docs/auditoria-logica-negocio.md` salió vacía.
  - `revisar_cuentas_mesa.sql` y `revisar_cuentas_sin_asignacion.sql` salieron vacías, antes de la separación.
- **Respaldo previo:** `base_20261104_1306.dump`, con `pdf_20261104_1306.tar.gz` y `huellas_20261104_1306.txt`. Quedó solo en el servidor, porque `RESPALDO_DESTINO` no está definido.
- **Control de PDF:** 4 referencias en la base y 27 archivos en disco. 0 faltantes, 0 alterados, 1 versión anterior sin archivo y 24 sobrantes, que no se borran.
- **Migraciones:** V24 a V28 aplicadas a las 13:07:12–13, con `success = t` en `flyway_schema_history`. La aplicación arrancó a las 13:07:25 y `/login` responde 200.
- **En pantalla, según informó el usuario:**
  - `juan.admin`: 0 originales en dependencias sin personal, y 7 pases trabados o fuera de plazo, ninguno con el botón "Retirar".
  - `juan.acosta` y `zlobo`: ven su bandeja y ya no tienen Administración.
  - `juan.admin` ve la bandeja vacía, sin expedientes.
  - Acuse de `TIC-2026-000003`: "Verificar" dice "Íntegro" en la versión vigente y "Falta el archivo" en la reemplazada, como se esperaba.
- **El acuse vigente de `TIC-2026-000003` no es el documento real:** el usuario informó que subió un PDF cualquiera para reponerlo. "Íntegro" solo prueba que el archivo no cambió desde que se subió. El usuario informó que `TIC-2026-000003` es un expediente de prueba: es un dato de prueba, sin valor de constancia, y no hay papel que escanear.
- **Todos los expedientes de producción son de prueba,** según informó el usuario: todavía no hay ninguno real. Incluye los 8 pases sin acuse.
- **Dónde están los respaldos:** solo en el servidor, en `/var/backups/gestion_documental`. No hay copia en otro equipo.
- **De 8 a 7 pases en el panel:** ninguno se confirmó ni se anuló: los 8 siguen sin acuse en la base. El panel, desde el PR #34, no muestra los pases de un expediente finalizado, y `TIC-2026-000003`, con un pase sin acuse, está `FINALIZADO`: esa es la diferencia.
- **Separación de cuentas, 12:57 a 13:04, según la auditoría:**
  - Se usaron "de prueba" y "prueba" como acto administrativo de los dos `ADMIN_SISTEMA` y como motivo de las bajas. La auditoría no se puede modificar: si hay un acto real, se deja constancia aparte.
  - `juan.acosta` restableció la contraseña de `zlobo` y generó las temporales de `juan.admin` y `zlobo.admin`. 
  - **Las acciones de `zlobo` y `zlobo.admin` no las hizo Zaimy Lobo:** el usuario Juan Carlos Acosta informó que las hizo personalmente, porque estaba probando sin nadie más. Eso incluye la aprobación de `juan.admin` (13:00) y la baja de `ADMIN_SISTEMA` de `juan.acosta` (13:04). La auditoría no se puede modificar y seguirá mostrando esas cuentas: esta nota es la aclaración.
  - **Hasta que Zaimy Lobo cambie las contraseñas de `zlobo` y `zlobo.admin`,** una sola persona controla las dos cuentas de administración, y el doble control no es real.
- **Sin comprobar todavía:**
  - las bitácoras con la verificación de huella, después de esta actualización;
  - la IP real del usuario en la auditoría;
  - la restauración completa de los PDF.
- **Sin instalar:** el PR #44 (entrada directa a Administración y auditoría con filtros). No tiene migraciones.

**04/11/2026, 14:42.** Actualización con los PR #44 (la cuenta de administración entra a su pantalla, auditoría con filtros) y #46 (tema Petróleo, menú lateral). Sin migraciones: sigue en V28.
- **Respaldo previo:** `base_20261104_1442.dump`, con sus PDF y huellas.
- **Aplicación:** arrancó a las 14:42:39, sin errores en el log revisado. `/login` responde 200 y sirve `institucional.css`. El `502` que se vio al confirmar coincidió con el arranque, que tarda unos 25 segundos.
- **En pantalla, según informó el usuario:** `juan.admin` entra a Administración y `juan.acosta` ve solo la bandeja.
- **Escudo:** se ve el lugar reservado "Escudo oficial". Falta el archivo oficial.
- **Respaldo automático, fallo encontrado:**
  - La ejecución del 03/11/2026 a las 22:00 falló: `tar: command not found`. Dejó solo el `.dump`, sin PDF ni huellas, y el servicio quedó en error sin que nadie lo notara.
  - `tar` figura entre lo instalado en la mudanza de esa misma noche. No hay registro de la hora exacta, así que lo más probable es que se haya instalado después de esa ejecución.
  - El 04/11/2026 a las 15:07 se ejecutó el servicio a mano, por el mismo camino que el temporizador: terminó bien (`inactive`) y generó `pdf_20261104_1507.tar.gz`.
  - **Falta confirmar:** la ejecución automática de las 22:00 del 04/11/2026.
- **Sin comprobar todavía:**
  - la búsqueda con filtros de la auditoría, en producción;
  - las bitácoras con la verificación de huella;
  - la IP real del usuario en la auditoría;
  - la restauración completa de los PDF.

**05/11/2026, 07:18.** Actualización con los PR #48 (logos, plan de base limpia) y #49 (Bootstrap y sus íconos servidos desde la aplicación). Sin migraciones: sigue en V28.
- **Respaldo automático:** el del 04/11/2026 a las 22:00 terminó bien, con base, PDF y huellas. Queda confirmado el arreglo del fallo anterior.
- **Respaldo previo:** `base_20261105_0718.dump`, con sus PDF y huellas. Control de PDF: 0 faltantes, 0 alterados, 1 versión anterior sin archivo y 24 sobrantes.
- **Aplicación:** arrancó a las 07:18:45. `/login` responde 200 y ya no referencia `cdn.jsdelivr.net`.
- **En pantalla, según informó el usuario:** el acceso muestra los logos, `juan.admin` ve solo Administración y `juan.acosta`, su bandeja.
- **Bitácoras, verificadas con `--verificar`:** ÍNTEGRAS.
  - `auditorias_documento`, 26 filas: `4a72c3eac157d8eaeeae8bb40fc119357d1bd6f065fc0ea54c4f78a2baff466e`
  - `auditoria_sistema`, 16 filas: `99af1d50ee92e7dce23365d42c6b86b2d3583433608c3ec08d9f4a8d22087da2`
- **Cuenta `griveros`, aclarada:** es de Giovanni Riveros, de la mesa de entrada de la Dirección de TIC, según informó el usuario. Es una cuenta nominal, de trabajo diario.
- **Sin hacer:** la copia a `192.168.129.71`. El usuario confirmó que `.71` y `.73` son equipos distintos.

**05/11/2026, 08:21.** Actualización con el PR #50 (quien envía un pase no lo confirma, "Respuestas recibidas"). Sin migraciones: sigue en V28.
- **Respaldo previo:** `base_20261105_0820.dump`, copiado también a `192.168.129.71`.
- **Aplicación:** arrancó a las 08:21:28. `/login` responde 200.
- **Copia a otro equipo, configurada:** `respaldo@192.168.129.71:/srv/respaldos/gestion_documental`. A las 08:11, las huellas de la base y de los PDF coincidieron en los dos servidores. El usuario confirmó que son equipos físicos distintos.
- **Falta confirmar:** la copia automática de las 22:00 del 05/11/2026, y que la bandeja cargue en producción con esta versión.
- **Sin resolver:** la limpieza de los respaldos en `.71` y su cifrado.

**05/11/2026, 08:57.** Actualización con el PR #51 (estilos de la pantalla de clave temporal). Sin migraciones: sigue en V28.
- **Respaldo previo:** `base_20261105_0856.dump`, copiado también a `192.168.129.71`. Control de PDF: 0 faltantes y 0 alterados.
- **Aplicación:** arrancó a las 08:57:24. `/login` responde 200.
- **En pantalla, según informó el usuario:** `juan.acosta` ve su bandeja sin errores, y sin expedientes en ella. Es la primera comprobación de las bandejas en producción desde el PR #50.
- **Sin comprobar:** la pantalla de clave temporal vista en un navegador, y la copia automática de las 22:00.
