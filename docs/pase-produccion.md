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
