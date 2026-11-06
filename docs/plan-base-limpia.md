# Plan: empezar el piloto con una base limpia

**Estado:** propuesta del 04/11/2026. La copia a `.71` ya funciona. Los scripts del paso 3 están en `db/piloto` y se ensayaron el 05/11/2026. El corte todavía no se hizo.

**Objetivo:** que el primer expediente real del piloto arranque sin datos de prueba: sin expedientes, cuentas ni bitácoras de prueba, y con la numeración desde el principio. Hoy es posible porque, según informó el usuario el 04/11/2026, todos los expedientes de producción son de prueba.

**Lo que no se hace:** no se borra nada. La base y los PDF actuales se conservan con otro nombre, y además en el respaldo.

## Qué resuelve

- Quedan fuera los expedientes `TIC-2026-…`, los 8 pases sin acuse, el PDF cualquiera de `TIC-2026-000003` y los 24 PDF sobrantes.
- La auditoría empieza sin los actos "de prueba" ni las acciones de `zlobo` que hizo otra persona.
- Las 6 cuentas genéricas no pasan a la base nueva.
- El CUTI empieza por 1 en cada serie.

## Lo que hay que tener en cuenta

- **Las migraciones no alcanzan para crear la base.** No hay V1: el esquema anterior a Flyway no está en el repositorio (`baseline-version=6`). La base nueva se arma como la de pruebas (`db/pruebas/crear_base_pruebas.sh`): estructura copiada de producción y solo los catálogos.
- **Ese script no sirve tal cual.** Es anterior a V16 y V18, y no copia `organigrama_version` ni `feriados`. Sin la fila de `organigrama_version`, lo esperable es que la aplicación falle al leer el organigrama.
- **Las cuentas no se copian, y V12 no vuelve a correr.** La base nueva no tendría ningún usuario: el primer administrador se crea desde el servidor.
- **Las protecciones de las bitácoras hay que instalarlas de nuevo:** `proteger_bitacoras.sh` y `reforzar_bitacoras.sh`.

## Qué pasa a la base nueva

| Tabla | Qué se hace |
|---|---|
| `niveles_jerarquicos`, `dependencias`, `tipos_documentos`, `reglas_derivacion` | Se copian |
| `flyway_schema_history` | Se copia, para que la aplicación sepa en qué versión está |
| `organigrama_version` | Se copia |
| `feriados` | Se copia, con `creado_por` vacío, porque esos usuarios no existirán |
| `usuarios`, `asignaciones` | Vacías; se cargan en el paso 6 |
| `documentos`, `actuaciones_documento`, `adjuntos_documento`, `legajos_salida`, `series_cuti` | Vacías |
| `auditorias_documento`, `auditoria_sistema`, `bitacora_cadena` | Vacías; las cadenas empiezan de nuevo |
| `solicitudes_reapertura`, `solicitudes_contrasena`, `solicitudes_traslado_custodia` | Vacías |
| `biblioratos`, `ubicaciones_documento` | Vacías, salvo que se decida otra cosa |

Esta lista salió de leer el código. En el ensayo se compara con todas las tablas reales de la base, por si alguna anterior a Flyway no figura aquí.

## Condiciones para ejecutarlo

No se pasa al corte hasta tener todo esto:

- [ ] El respaldo automático funcionando: la ejecución de las 22:00 generó los tres archivos.
- [ ] La copia a `192.168.129.71` funcionando (paso 1).
- [ ] La restauración completa probada, incluidos los PDF.
- [ ] El ensayo terminado sin errores (paso 3).
- [ ] La lista de personas del piloto, con su dependencia, rol y acto administrativo.
- [ ] Una resolución o acto real para los dos administradores.
- [ ] Zaimy Lobo presente, con su propia cuenta: el doble control tiene que ser real desde el primer día.
- [ ] El escudo oficial y la confirmación de Comunicación sobre el color y el logo "ASU".
- [ ] Certificado instalado en los equipos del piloto, y feriados 2026 cargados.

## Pasos

### 1. Copia de respaldos a 192.168.129.71

La ejecuta un administrador con acceso `root`. Conviene una cuenta sin privilegios en el destino, solo para recibir respaldos.

En **192.168.129.71**:
```bash
command -v rsync || dnf install -y rsync
```
```bash
useradd -m respaldo; mkdir -p /srv/respaldos/gestion_documental && chown respaldo:respaldo /srv/respaldos/gestion_documental && chmod 700 /srv/respaldos/gestion_documental
```
```bash
passwd respaldo
```

En **192.168.129.73**, sin contraseña en la clave, porque la usa el temporizador. Si `/root/.ssh/id_ed25519` ya existe, omitir el primer comando:
```bash
ssh-keygen -t ed25519 -N '' -f /root/.ssh/id_ed25519
```
```bash
ssh-copy-id respaldo@192.168.129.71
```
```bash
ssh respaldo@192.168.129.71 "ls -ld /srv/respaldos/gestion_documental"
```
```bash
echo 'RESPALDO_DESTINO=respaldo@192.168.129.71:/srv/respaldos/gestion_documental' > /etc/gestion-documental/respaldo
```
```bash
systemctl start gestion-documental-respaldo.service; systemctl is-failed gestion-documental-respaldo.service; ssh respaldo@192.168.129.71 "ls -lh /srv/respaldos/gestion_documental | tail -n 3"
```

Tiene que decir `inactive` y mostrar la base y las huellas con la hora actual, más la carpeta `pdf`.

**A tener en cuenta:**
- Los respaldos tienen datos reales: el acceso a esa carpeta queda restringido.
- `respaldar.sh` conserva en `.71` la base y las huellas 90 días, y los PDF sin borrarlos nunca. Falta definir si se cifran.
- Falta confirmar que `.71` no comparta disco ni equipo físico con `.73`. Si comparten, esta copia no protege de un fallo de hardware.

### 2. Decisiones

| Decisión | Recomendación |
|---|---|
| Fecha del corte | Fuera del horario de atención, justo antes de empezar el piloto |
| Cuánto se conserva lo anterior | Hasta el final del piloto, y después se archiva el respaldo |
| Biblioratos | Vacíos: se cargan los reales |
| Alcance | DTIC y DGAF, como en `docs/viabilidad-piloto.md` |

### 3. Ensayo, sin tocar producción

1. Copiar al servidor `db/piloto/*.sh`, `db/seguridad/proteger_bitacoras.sh` y `db/seguridad/reforzar_bitacoras.sh`. `crear_base_limpia.sh` lee producción, nunca la modifica, y se detiene si la base de destino ya existe.
2. Crear con él `gestion_documental_ensayo`.
3. Instalar las protecciones de las bitácoras en esa base y verificarlas.
4. Arrancar la aplicación contra esa base, con otra carpeta de PDF.
5. Crear los dos administradores y recorrer el circuito completo: ingreso, pase, acuse, informe, salida y acuse final.
6. Comparar las tablas reales con la lista de este plan.
7. Probar la vuelta atrás del paso 5.
8. Borrar la base de ensayo.

Se corrige lo que aparezca y, si hubo cambios, se repite.

### 4. Corte (unos 30 minutos)

Lo ejecuta un administrador en el servidor. Los usuarios ya avisados.

1. Detener la aplicación: `systemctl stop gestion-documental`.
2. Hacer el respaldo final y confirmar que llegó a `.71`.
3. Crear la base nueva con el script, con un nombre temporal.
4. Renombrar la base actual a `gestion_documental_prueba_<fecha>`, y la nueva a `gestion_documental`.
5. Mover `/var/lib/gestion-documental/pdf` a `pdf_prueba_<fecha>`, y crear la carpeta vacía con su dueño `gestion_documental`. Mover también en `.71` la carpeta `pdf` a `pdf_prueba_<fecha>`, para que los PDF de prueba no se mezclen con los reales.
6. Instalar `proteger_bitacoras.sh` y `reforzar_bitacoras.sh`, y comprobar con `--verificar`.
7. Crear el primer administrador con `db/piloto/crear_primer_admin.sh`, con el acto real. Esa persona cambia la contraseña temporal al entrar.
8. Arrancar la aplicación y confirmar `Started GestionDocumentalApplication`.

### 5. Comprobar, o volver atrás

- [ ] El primer administrador entra y solo ve Administración.
- [ ] El organigrama, los tipos de documento y los feriados están completos.
- [ ] No hay expedientes y la auditoría está vacía, salvo lo recién hecho.
- [ ] `reforzar_bitacoras.sh --verificar` da ÍNTEGRAS.
- [ ] `revisar_pdf.sh` da 0 faltantes y 0 sobrantes.

**Si algo falla:** detener la aplicación, volver a poner los nombres anteriores de la base y de la carpeta de PDF, y arrancar. No hay que restaurar nada, porque lo anterior quedó intacto. **Esta vuelta atrás solo vale antes de cargar datos reales:** después, volver atrás los perdería.

### 6. Cuentas reales

1. El primer administrador pide `ADMIN_SISTEMA` para Zaimy Lobo, con el acto real. La aprueba el DBA, una vez, con `aprobar_asignacion.sh`.
2. Zaimy Lobo entra, y **pone su contraseña en persona**.
3. Se crean las cuentas de trámite, una por persona, cada una con su acto. Las aprueba el otro administrador.
4. Quien administra usa una cuenta para administrar y otra, si le corresponde, para tramitar.
5. Se corren `revisar_cuentas_sin_asignacion.sql`, `revisar_cuentas_mesa.sql` y la consulta "Cuentas" de `docs/auditoria-logica-negocio.md`.

### 7. Primer día

- Registrar el corte en `docs/pase-produccion.md`, con la última huella de las bitácoras.
- Revisar con la mesa el primer ingreso real.
- Confirmar al día siguiente el respaldo de las 22:00, tanto en el servidor como en `.71`.

## Riesgos

| Riesgo | Cómo se cubre |
|---|---|
| Falta un catálogo o una tabla | El ensayo compara y recorre el circuito completo |
| Falla el corte | Vuelta atrás renombrando; lo anterior no se toca |
| Queda un dato real en la base anterior | Antes del corte, confirmar por escrito que no hay ninguno, porque alguien pudo cargarlo después del 04/11/2026 |
| Una sola persona con las dos cuentas de administración | Zaimy Lobo presente en el paso 6 |
| Falla el respaldo y nadie se entera | Revisión semanal, o agregar un aviso antes del piloto (ya pasó el 03/11/2026) |

## Resultado del ensayo (05/11/2026)

En `gestion_documental_ensayo`, sin tocar producción:

- **Creación:** los catálogos coinciden con producción (581 dependencias, 6 tipos, 7 niveles, 18 feriados, 23 migraciones). Las demás tablas quedaron vacías, y no hubo tablas sin prever.
- **Aplicación:** arrancó contra esa base sin migraciones pendientes.
- **Cuentas:** primer administrador por script, segundo aprobado por el DBA, mesas aprobadas por otro administrador.
- **Circuito:** `DGAF-2026-000001` se registró, se derivó, se recibió, tuvo acta y salida, y finalizó. "Verificar" dio "Íntegro", y la DGAF lo vio en "Respuestas recibidas".
- **Bitácoras:** ÍNTEGRAS, con 7 y 14 filas.
- **Control comprobado:** una cuenta sin asignación aprobada recibió 403 al registrar.
- **Encontrado:** con clave temporal, la pantalla de cambio quedaba sin estilos, por no excluir `/vendor` del control. Corregido.

**También ensayado:** el script corregido terminó sin avisos, y la vuelta atrás renombrando bases funcionó. Las dos bases de ensayo se borraron: quedan `gestion_documental` y `gestion_documental_pruebas`.

**No se ensayó:** mover la carpeta de PDF. Se hace con la aplicación detenida, y se vuelve atrás renombrándola.

**Para el piloto:** una mesa de una sola persona no puede recibir por sus subordinadas sin usuarios, porque quien envía no confirma. Cada mesa debe tener al menos dos personas, o las unidades, sus cuentas.
