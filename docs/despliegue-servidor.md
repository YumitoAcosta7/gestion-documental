# Despliegue en el servidor (192.168.129.73)

Pasa la aplicación de producción de la PC de desarrollo al servidor, como servicio, con HTTPS y respaldos diarios. Resuelve los bloqueantes 1 a 3 de [viabilidad-piloto.md](viabilidad-piloto.md).

```
Navegador ── HTTPS 443 ──> nginx ── 127.0.0.1:8080 ──> aplicación ──> PostgreSQL
                                                          └──> /var/lib/gestion-documental/pdf
```

| Qué | Dónde |
|---|---|
| Aplicación | `/opt/gestion-documental/gestion-documental.jar` |
| Servicio | `gestion-documental` (`systemctl`) |
| Configuración y contraseña | `/etc/gestion-documental/entorno` |
| Certificado | `/etc/gestion-documental/tls/` |
| PDF | `/var/lib/gestion-documental/pdf` |
| Respaldos | `/var/backups/gestion_documental`, todos los días a las 22:00, 30 días |

## 1. Diagnóstico (solo lectura)

En el servidor:
```bash
cat /etc/os-release | head -3
java -version
nginx -v
ss -ltnp | grep -E ':(80|443|8080) '
df -h /var /opt
sudo -u postgres psql -tA -c "SHOW hba_file"
```

Hace falta:
- **Java 25 o superior.** Si falta, instalarlo antes de seguir.
- **nginx, tar y rsync.** Si faltan, instalarlos con `dnf install -y nginx tar rsync`. El respaldo usa `tar` y una instalación mínima de Rocky no lo trae.
- **Puertos 80, 443 y 8080 libres.** Si otro sistema los usa, resolverlo antes de seguir.
- **Espacio** para la aplicación, los PDF y 30 días de respaldos.

## 1b. Autenticación de PostgreSQL (obligatorio)

**Lo encontrado el 03/11/2026 en el servidor:**
- `password_encryption` estaba en `md5`, pero la conexión por `localhost` exige `scram-sha-256`. Una contraseña guardada en md5 se rechaza con `password authentication failed`, aunque sea la correcta. El motivo real solo figura en el registro: `does not have a valid SCRAM secret`.
- `pg_hba.conf` tenía `trust` para `127.0.0.1`: cualquier programa del servidor podía entrar a la base como superusuario, sin contraseña. Con la aplicación en el mismo servidor, una falla en ella daría control total de la base, incluidas las bitácoras. El puerto 5432 no era accesible con esa regla desde otros equipos, pero sí desde cualquier proceso local.

**Corrección:**

1. Guardar las contraseñas en SCRAM. Es compatible con las reglas `md5` que ya existen:
   ```bash
   sudo -u postgres psql -c "ALTER SYSTEM SET password_encryption = 'scram-sha-256'" -c "SELECT pg_reload_conf()"
   ```
2. Volver a definir la contraseña de cada usuario, para que quede en el formato nuevo:
   ```bash
   sudo -u postgres psql -c "\password gestion_documental"
   ```
   ```bash
   psql -h localhost -U gestion_documental -d gestion_documental -c "SELECT 1"
   ```
3. Antes de quitar `trust`, ver si alguien conecta por esa vía:
   ```bash
   sudo -u postgres psql -c "SELECT usename, datname, client_addr, application_name FROM pg_stat_activity WHERE client_addr = '127.0.0.1'"
   ```
4. Quitar `trust`, con copia del archivo. Los scripts de esta guía usan `sudo -u postgres`, por socket local (`peer`), y no se ven afectados:
   ```bash
   H=$(sudo -u postgres psql -tAc "SHOW hba_file"); cp -p "$H" "$H.antes_scram" && sed -i -E '/^host[[:space:]].*127\.0\.0\.1\/32/ s/trust/scram-sha-256/' "$H" && sudo -u postgres psql -c "SELECT pg_reload_conf()" -c "SELECT line_number, type, address, auth_method, error FROM pg_hba_file_rules"
   ```
   Ninguna línea tiene que decir `trust`, y la columna `error` tiene que estar vacía.

## 2. Empaquetar y copiar (PC)

Desde `C:\Projects\gestion-documental`, en la rama `feature/devolver-y-salida` actualizada:
```powershell
.\mvnw.cmd clean package -DskipTests
```
```powershell
scp -r despliegue root@192.168.129.73:/tmp/
```
```powershell
scp target\gestion-documental-0.0.1-SNAPSHOT.jar root@192.168.129.73:/tmp/despliegue/gestion-documental.jar
```

## 3. Instalar (servidor)

Con el nombre o la IP con que van a entrar los usuarios:
```bash
cd /tmp/despliegue && sed -i 's/\r$//' instalar.sh respaldar.sh entorno.ejemplo *.service *.timer *.conf && bash instalar.sh 192.168.129.73
```

Instala todo, sin arrancar la aplicación. Si no hay certificado, genera uno interno; el institucional se copia después en `/etc/gestion-documental/tls/` como `certificado.crt` y `certificado.key`, y se aplica con `systemctl reload nginx`.

Completar la contraseña de la base. Conviene hacer aquí el cambio de contraseña (paso 8 de [pase-produccion.md](pase-produccion.md)), para no escribirla dos veces:
```bash
sudo -u postgres psql -c "\password gestion_documental"
```
```bash
nano /etc/gestion-documental/entorno
```

## 4. Mudar los PDF

Detener la aplicación de la PC (`Ctrl+C`) para que nadie suba archivos durante la copia. Desde la PC:
```powershell
scp -r C:\gestion-documental-uploads\acuses root@192.168.129.73:/tmp/acuses
```

En el servidor, comprobando que la cantidad de archivos coincida con la de la carpeta de la PC:
```bash
cp -n /tmp/acuses/* /var/lib/gestion-documental/pdf/ && chown -R gestion_documental:gestion_documental /var/lib/gestion-documental/pdf
```
```bash
ls /var/lib/gestion-documental/pdf | wc -l
```

Las rutas de Windows que guarda la base no molestan: la aplicación busca cada archivo por su nombre en esta carpeta.

## 5. Arrancar y comprobar

```bash
systemctl start gestion-documental
```
```bash
journalctl -u gestion-documental -n 30 --no-pager
```

Tiene que decir `Started GestionDocumentalApplication`.

En un navegador de otro equipo, en `https://192.168.129.73`:
- [ ] Con un certificado interno aparece una advertencia, una vez por equipo, hasta que TIC lo instale.
- [ ] `http://192.168.129.73` redirige a HTTPS.
- [ ] Se puede iniciar sesión y se ve la pantalla principal.
- [ ] Un PDF antiguo se abre y "Verificar" dice "Íntegro".
- [ ] Un movimiento nuevo registra en Auditoría la IP del equipo, no `127.0.0.1`.
- [ ] Al reiniciar el servidor, la aplicación vuelve sola.

Comprobar la cadena de las bitácoras:
```bash
cd /tmp && sudo -u postgres bash reforzar_bitacoras.sh gestion_documental --verificar
```

## 6. Respaldos

Probar el respaldo a mano:
```bash
bash /opt/gestion-documental/respaldar.sh
```

**Copia a otro equipo (obligatoria para el piloto).** Mientras la base, los PDF y los respaldos estén en un solo disco, un fallo lo pierde todo. Con una clave SSH sin contraseña hacia el equipo de destino:
```bash
echo 'RESPALDO_DESTINO=usuario@equipo:/carpeta/de/respaldos' > /etc/gestion-documental/respaldo
```

**Restauración de prueba (obligatoria para el piloto).** Restaurar el último respaldo en una base temporal y comprobar que tiene los datos:
```bash
sudo -u postgres createdb prueba_restauracion
```
```bash
sudo -u postgres pg_restore --no-owner -d prueba_restauracion /var/backups/gestion_documental/base_<fecha>.dump
```
```bash
sudo -u postgres psql -d prueba_restauracion -c "SELECT count(*) FROM documentos"
```
```bash
sudo -u postgres dropdb prueba_restauracion
```

Comprobar también que `pdf_<fecha>.tar.gz` se pueda abrir y tenga los PDF.

## Actualizar a una versión nueva

Repetir los pasos 2 y 3. `instalar.sh` no toca la contraseña, el certificado ni los PDF, y reinicia el servicio. Antes, hacer un respaldo; Flyway aplica al arrancar las migraciones que falten.

## Volver atrás

Si en el paso 5 algo falla: detener el servicio (`systemctl stop gestion-documental`) y volver a arrancar desde la PC con `.\mvnw.cmd spring-boot:run`. Los PDF originales siguen en la PC. Si se cambió la contraseña de la base, actualizar antes `application-local.properties`.

Si ya se cargaron documentos en el servidor, copiar primero sus PDF nuevos de `/var/lib/gestion-documental/pdf` a `C:\gestion-documental-uploads\acuses`.

## Administración diaria

```bash
systemctl status gestion-documental
```
```bash
journalctl -u gestion-documental -f
```
```bash
systemctl list-timers gestion-documental-respaldo.timer
```
