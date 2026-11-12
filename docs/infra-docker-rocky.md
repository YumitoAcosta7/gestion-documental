# Servidores de desarrollo y producción: aplicación en Docker, base en los servidores PostgreSQL

**Estado:** propuesta del 12/11/2026. Ningún paso se ejecutó todavía. **No se probó en un servidor:** la imagen no se pudo construir en la PC (no tiene Docker), y los scripts solo se revisaron por sintaxis. Lo primero es ensayar todo en desarrollo.

**No reemplaza a la producción actual (`192.168.129.73`)** hasta hacer el corte de la sección 8.

## Mapa

| Equipo | IP | Papel | Qué corre |
|---|---|---|---|
| `caliope` (`caliope_rockylinux`) | 192.168.129.82 | Producción | nginx y la aplicación en Docker, PDF, respaldo diario |
| `caliopedev` (`caliopedev_rockylinux`) | 192.168.129.83 | Desarrollo | Igual, con la imagen construida desde la rama |
| `atenea` | 192.168.129.72 | Base de producción | PostgreSQL 17.10 (PGDG), base `gestion_documental` |
| `ateneadev` | 192.168.129.73 | Base de desarrollo | PostgreSQL 17.10, base `gestion_documental_pruebas` (y hoy, también la producción) |
| Proxy interno | 192.168.129.111 | DNS interno y proxy (Apache) | `mesa.asuncion.gov.py` y `mesa-dev.asuncion.gov.py`, con el certificado `*.asuncion.gov.py` |
| Virtualización | 192.168.129.13 | Ubuntu 22.04, qemu-kvm | Las máquinas virtuales |
| `asclepio` | 192.168.129.71 | Respaldos | Todas las copias: base, huellas y PDF de los dos ambientes, como hoy los de `.73` |

```
usuario ──HTTPS *.asuncion.gov.py──► proxy .111 ──HTTPS verificado──► nginx (caliope) ──► app :8080 (Docker)
                                                                                           │ SSL verify-full
                                                                                           ▼
                                                                                 PostgreSQL (atenea)
```

## 1. Qué va dónde y por qué

| Capa | Dónde | Por qué |
|---|---|---|
| Aplicación | Docker en caliope / caliopedev | Es lo que cambia en cada versión: la misma imagen probada en desarrollo pasa a producción (`promover.sh`) |
| PostgreSQL | Nativo en atenea / ateneadev | Ya existen, tienen quien los administre y actualizarlos (`pg_upgrade`) es el camino documentado. En Docker la base gana poco |
| PDF | Carpeta del servidor Docker, en su volumen lógico | Respaldo, control de huellas y copia a otro equipo siguen igual |
| nginx | Servidor Docker | Recibe solo del proxy y le pasa a la aplicación la IP real del usuario |
| Proxy (Apache) | 192.168.129.111 | Presenta el certificado comodín: se renueva en un solo lugar y su clave no se copia a más equipos |
| Respaldo, hora, SSH, cortafuegos | Servidor Docker | Tienen que funcionar aunque Docker falle |

**La aplicación en Docker** compila en una etapa y corre en otra con solo el JRE, como usuario sin privilegios (10001), sistema de archivos de solo lectura, sin capacidades del núcleo y con memoria limitada. Solo escucha en `127.0.0.1:8080`.

**La conexión a la base** usa `sslmode=verify-full`: cifra y además comprueba con el certificado del servidor PostgreSQL que del otro lado esté atenea y no otro equipo. La contraseña se lee de un archivo (`/etc/gestion-documental/secretos/db/DB_PASSWORD`), no de una variable, porque las variables se ven con `docker inspect`.

**Franja "DESARROLLO":** con `AMBIENTE_VISIBLE=DESARROLLO` en `.env`, todas las pantallas muestran un aviso fijo abajo a la derecha y una línea amarilla arriba. En producción queda vacío y no aparece nada.

### Diferencias con el pedido original

| Pedido | Cambio | Motivo |
|---|---|---|
| PostgreSQL en Docker | En los servidores PostgreSQL existentes | Decidido el 12/11/2026: ya existen y se administran; separar la base de la aplicación |
| Hora UTC | `America/Asuncion` en servidores y contenedores | Es la norma de los demás servidores, y la aplicación guarda la hora local y cuenta días hábiles |
| `tar.gz` diario de los PDF | Copia incremental con `rsync` a otro equipo (ya en uso) | Los PDF nunca se sobrescriben: copiar solo los nuevos ocupa menos. El `tar.gz` queda solo si no hay otro equipo |
| `ip_forward` apagado | Encendido | Sin él, Docker deja al contenedor sin red. El acceso lo limita el cortafuegos |
| Crear la base con Flyway | Restaurar un respaldo | Las migraciones empiezan en V2: el esquema anterior no está en el repositorio |

## 2. Máquinas virtuales (`infra/rocky/kvm`)

Se crean en el servidor de virtualización (192.168.129.13) con `crear-vm.sh`, igual que las actuales: i440fx, BIOS, discos qcow2 `virtio` (`vda`, `vdb`), red `virtio` por `br0`, SPICE solo local y consola serie. Además: agente de invitado, `discard` y `cache=none`. Instala Rocky Linux 9 sin intervención con `rocky9.ks.plantilla`.

- **Rocky 9, no 10:** la CPU de las máquinas (Westmere) no tiene AVX2, que Rocky 10 exige.
- **ISO:** la DVD de Rocky 9, en `/var/lib/libvirt/images` (AppArmor no deja a qemu leerla desde `/home`).
- **Acceso:** root bloqueado; se entra con el usuario administrador y su clave SSH. Su contraseña (para `sudo`) la genera el administrador en el servidor de virtualización con `openssl passwd -6`; nunca va al repositorio.
- **El script no pisa nada:** se detiene si la máquina o sus discos ya existen o si la IP responde.

| | caliope | caliopedev |
|---|---|---|
| vCPU / RAM | 4 / 6 GB | 2 / 4 GB |
| Disco de sistema / datos | 60 / 80 GB | 60 / 30 GB |

```bash
bash ~/kvm/crear-vm.sh caliopedev --solo-kickstart   # revisar, sin crear nada
sudo bash ~/kvm/crear-vm.sh caliopedev
```

## 3. Particionado

Dos discos: reinstalar el sistema no toca los datos, y el de datos se agranda o se mueve solo.

**Sistema (`vda`, `vg_sistema`, 60 GB):**

| Punto de montaje | Tamaño | Opciones | Por qué |
|---|---|---|---|
| `/boot` (fuera de LVM) | 1 GB | `nodev,nosuid,noexec` | |
| `/` | 12 GB | | |
| `/var` | 6 GB | `nodev` | |
| `/var/log` | 4 GB | `nodev,nosuid,noexec` | Un registro desbordado no llena `/var` |
| `/var/log/audit` | 2 GB | `nodev,nosuid,noexec` | auditd sigue aunque `/var/log` se llene |
| `/var/tmp`, `/tmp` | 1 y 2 GB | `nodev,nosuid,noexec` | |
| `/home` | 2 GB | `nodev,nosuid` | |
| `/opt` | 4 GB | `nodev` | El repositorio y los archivos de Compose |
| `/var/lib/docker` | 12 GB | | Una imagen grande no llena `/var` |
| swap | 4 GB | | |
| Libre en el grupo | ~10 GB | | Ampliaciones |

**Datos (`vdb`, `vg_datos`):**

| Punto de montaje | caliope | caliopedev | Opciones |
|---|---|---|---|
| `/var/lib/gestion-documental/pdf` | 40 GB | 15 GB | `noatime,nodev,nosuid,noexec` |
| `/var/backups` | 20 GB | 8 GB | `noatime,nodev,nosuid,noexec` |
| Libre en el grupo | ~20 GB | ~7 GB | |

- **Sin `nobarrier`:** el almacenamiento del hipervisor tiene que respetar las escrituras confirmadas.
- **Respaldos locales:** `/var/backups` en la misma máquina no protege de una falla. La copia a otro equipo (`RESPALDO_DESTINO`) es obligatoria.

## 4. Paquetes, hora y endurecimiento del servidor Docker

`01-preparar-host.sh <dev|prod>` instala:
- **Repositorios:** EPEL, CRB, Docker y PGDG.
- **Docker:** `docker-ce`, `containerd.io` y los complementos de Buildx y Compose.
- **Cliente de PostgreSQL 17** (`pg_dump`, `psql`), para el respaldo por la red.
- **Servicios:** nginx, chrony, firewalld, fail2ban, auditd, `qemu-guest-agent` y `dnf-automatic` (solo parches de seguridad, sin reiniciar).
- **Hora:** `America/Asuncion`, con los mismos servidores NTP que atenea (`201.217.3.85`, `201.217.3.86` y los de Cloudflare). `makestep 1.0 3`: corrige de golpe solo al arrancar; después, sin saltos en las bitácoras.

`02-endurecer.sh <puerto>` se corre dos veces para no quedar afuera: la primera abre el puerto SSH nuevo y deja el 22; se prueba en **otra** ventana; la segunda, con `--cerrar-22`, cierra el 22.
- **SSH:** solo con llave, sin entrada directa de root (se usa el administrador con `sudo`), 3 intentos y 30 segundos, sin reenvíos.
- **firewalld:** el puerto SSH, 80 y 443, sin cockpit ni dhcpv6. Con `WEB_DESDE="192.168.129.111 <red de la DTIC>"`, 80 y 443 aceptan **solo** esos orígenes: nadie saltea el proxy.
- **SELinux en `Enforcing`:** `:Z` en los montajes de la aplicación y `httpd_can_network_connect` para que nginx llegue a `127.0.0.1:8080`.
- **Docker** (`archivos/daemon.json`): puertos en `127.0.0.1` por defecto, `live-restore`, `no-new-privileges`, sin IPv6, registros a journald.
- **Núcleo** (`archivos/90-gestion-documental.conf`), **fail2ban** y **auditd**.

## 5. Servidores PostgreSQL (`infra/postgres`)

Relevamiento del 12/11/2026 (`recolectar-postgres.sh`): atenea y ateneadev tienen PostgreSQL 17.10 de PGDG, `pg_trgm`, `C.UTF-8`, UTF8, `scram-sha-256` y `fsync`, `synchronous_commit` y `full_page_writes` encendidos. La base actual se restaura sin conversión. Atenea está vacía (4 vCPU, 15 GB); ateneadev comparte con unas 20 bases.

`preparar-servidor.sh <base> <rol> <IP del servidor Docker>`, como root en el servidor PostgreSQL. Cambia solo lo de esta base:

1. **SSL:** si está apagado, crea un certificado del servidor (5 años) y lo activa con `reload`, sin cortar. Las otras bases no quedan obligadas a usarlo.
2. **Roles:** el de la aplicación y `<rol>_respaldo`, de solo lectura (`default_transaction_read_only`). Las contraseñas se escriben con `\password`, que las cifra antes de enviarlas: no quedan en registros ni historial.
3. **La base**, si no existe, con `pg_trgm`. Solo sus dos roles pueden conectarse (`REVOKE CONNECT ... FROM PUBLIC`).
4. **`pg_hba.conf`:** un bloque al principio, marcado con `# >>> gestion-documental: <base>`, que deja entrar a esos dos roles **solo** desde el servidor Docker y **solo** con SSL, y rechaza cualquier otro origen **para esa base**. Las reglas de las demás bases no cambian. Guarda una copia y la repone si la nueva tiene errores.
   - Con `ADMINS="<IP>/32 ..."` se permite además entrar con SSL desde esos equipos (por ejemplo, la PC que corre el perfil `pruebas` contra ateneadev).
   - **Se detiene** si hay conexiones abiertas a la base desde otra IP: así no corta la producción actual de `.73` por error.

`restaurar-base.sh <base> <rol> <respaldo .dump>` carga una base vacía: restaura con dueño el rol de la aplicación, instala y verifica las protecciones de las bitácoras y da lectura al rol del respaldo. Se niega si la base ya tiene tablas.

**Observaciones del relevamiento** (no bloquean; son del administrador):
- En ateneadev, el rol `gitlab` es superusuario: quien tenga su contraseña puede leer y cambiar todas las bases del servidor. Conviene quitárselo.
- En ateneadev, `126.10.10.0/24` (la VLAN 1) entra con `md5`, y `pg_hba` admite `10.10.15.0/24`, que el cortafuegos no deja pasar.
- Atenea todavía no tiene respaldo del servidor completo (las bases de otros proyectos que se agreguen). Decidido el 12/11/2026: todos los respaldos van a `192.168.129.71`; ese es del administrador, aparte del de esta aplicación.
- Las dos están en Rocky 9.5 sin actualizar desde la instalación: un `dnf upgrade` en atenea antes de poner producción.

## 6. Proxy interno (`infra/proxy/gestion-documental.conf`)

Apache en 192.168.129.111 (opción A, decidida el 12/11/2026): el proxy presenta el certificado `*.asuncion.gov.py` y reenvía por HTTPS a caliope o caliopedev, **verificando** su certificado (`SSLProxyVerify require`).
- **Nombres solo en el DNS interno:** `mesa` y `mesa-dev` apuntan al proxy. No se publican afuera.
- **`ProxyPreserveHost On`:** la aplicación ve `mesa.asuncion.gov.py`; sin eso fallan las redirecciones del inicio de sesión.
- **IP real en las bitácoras:** Apache agrega la IP del usuario en `X-Forwarded-For`, y el nginx de caliope le cree **solo** a 192.168.129.111 (`set_real_ip_from`), tomando la última dirección, la que agregó el proxy. Quien entre directo queda registrado con su propia IP.
- **SELinux del proxy:** si es la primera vez que reenvía a otro equipo, `setsebool -P httpd_can_network_connect 1`.

## 7. Respaldo diario

`despliegue/respaldar.sh`, el mismo de hoy, a las 22:00 con un temporizador de systemd en el servidor Docker:
- **Cómo llega a la base:** `03-nginx-y-respaldos.sh` escribe `/etc/gestion-documental/respaldo` con `COMO_POSTGRES="env"` y las variables `PG*`: `pg_dump` y `psql` se conectan por la red a atenea, con SSL verificado y el rol `<rol>_respaldo`, de solo lectura. Su contraseña está en `/etc/gestion-documental/secretos/respaldo.pgpass` (600, root).
- **Qué guarda:** base y huellas de las bitácoras, 30 días aquí y 90 en el otro equipo, más los PDF nuevos con `rsync`. Verifica que el respaldo se pueda leer, los PDF contra sus huellas y el espacio en disco.
- **Dónde:** en `192.168.129.71` (asclepio), el mismo equipo que hoy recibe los de `.73`, una carpeta por ambiente:
  - producción: `respaldo@192.168.129.71:/srv/respaldos/gestion_documental`, la misma de hoy. Los PDF que ya están allá no se vuelven a copiar, y la historia de respaldos sigue en un solo lugar.
  - desarrollo: `respaldo@192.168.129.71:/srv/respaldos/gestion_documental_pruebas`.
- **Llave SSH:** el respaldo corre como root y copia con `rsync` y `ssh` al usuario `respaldo`. En cada servidor Docker, una vez:
  ```bash
  sudo ssh-keygen -t ed25519 -N "" -f /root/.ssh/id_ed25519
  sudo ssh-copy-id -i /root/.ssh/id_ed25519.pub respaldo@192.168.129.71
  sudo ssh -o BatchMode=yes respaldo@192.168.129.71 "mkdir -p /srv/respaldos/gestion_documental_pruebas"   # solo en caliopedev
  ```
  `ssh-copy-id` pide una vez la contraseña de `respaldo`; quien la conoce la escribe. Después entra solo con la llave.
- **Antes de cada despliegue de producción**, `desplegar.sh` hace un respaldo y se detiene si falla.
- **Recuperación a un punto en el tiempo:** apagada. Se puede perder hasta un día; para menos, archivar el WAL de atenea en otro equipo (pgBackRest, por ejemplo).

## 8. Orden de ejecución

Primero **todo en desarrollo**; producción solo cuando desarrollo funcionó completo.

### Desarrollo (caliopedev + ateneadev)

1. **Máquina:** `sudo bash ~/kvm/crear-vm.sh caliopedev` en 192.168.129.13.
2. **En caliopedev**, con el usuario administrador: clonar el repositorio en `/opt/gestion-documental` y
   ```bash
   sudo bash infra/rocky/01-preparar-host.sh dev
   ```
   El rol `gestion_documental_pruebas` ya existe en ateneadev: reemplazar el contenido de `/etc/gestion-documental/secretos/db/DB_PASSWORD` por su contraseña actual, o cambiarla en el paso 5 con `--clave` (y entonces también en la PC, `DB_PASSWORD_PRUEBAS`).
3. `sudo bash infra/rocky/02-endurecer.sh <puerto>`, probar en otra ventana, y repetir con `--cerrar-22`. Con `WEB_DESDE` si se quiere limitar 80/443 al proxy.
4. Copiar `.env.ejemplo` a `.env`, dejar activo el bloque de desarrollo y:
   ```bash
   sudo bash infra/rocky/03-nginx-y-respaldos.sh mesa-dev.asuncion.gov.py ateneadev.asuncion.gov.py respaldo@192.168.129.71:/srv/respaldos/gestion_documental_pruebas
   ```
5. **En ateneadev**, como root, desde una copia del repositorio (con la IP de la PC en `ADMINS` para seguir usando el perfil `pruebas`):
   ```bash
   sudo ADMINS="<IP de la PC>/32" bash infra/postgres/preparar-servidor.sh gestion_documental_pruebas gestion_documental_pruebas 192.168.129.83
   ```
   Pide la contraseña de `gestion_documental_pruebas_respaldo`: el último campo de `respaldo.pgpass` en caliopedev.
6. Copiar el certificado de ateneadev a caliopedev, como indica el script, en `/etc/gestion-documental/pg/servidor.crt`.
7. **En el proxy:** copiar `/etc/gestion-documental/tls/certificado.crt` de caliopedev a `/etc/httpd/gestion-documental/caliopedev.crt`, instalar `gestion-documental.conf`, `apachectl configtest` y `systemctl reload httpd`. En el DNS interno, `mesa-dev` → 192.168.129.111.
8. `sudo bash infra/rocky/desplegar.sh dev`: comprueba la conexión, construye y levanta.
9. **Comprobar:**
   - `https://mesa-dev.asuncion.gov.py` abre con el candado normal y muestra la franja DESARROLLO.
   - Una acción nueva queda en la auditoría con la IP de la PC, no con la del proxy.
   - `ss -tlnp` no muestra 8080 fuera de `127.0.0.1`.
   - `sudo systemctl start gestion-documental-respaldo.service` termina sin error, y el respaldo se restaura en una base de ensayo.
   - La hora de un registro nuevo coincide con la de Asunción.

### Producción (caliope + atenea)

1. Crear caliope y repetir los pasos 2 a 4 con `prod`, `mesa.asuncion.gov.py`, `atenea.asuncion.gov.py` y, **hasta el corte**, `respaldo@192.168.129.71:/srv/respaldos/gestion_documental_ensayo`, más la llave SSH de la sección 7. Así los respaldos del ensayo no se mezclan con los de producción, que hasta el corte sigue mandando `.73`.
2. En atenea: `dnf upgrade`, y
   ```bash
   sudo bash infra/postgres/preparar-servidor.sh gestion_documental gestion_documental 192.168.129.82
   ```
   Pide dos contraseñas: la de `DB_PASSWORD` y la de `respaldo.pgpass`, ambas en caliope.
3. Copiar el certificado de atenea a caliope y el de caliope al proxy (`caliope.crt`).
4. **Ensayo:** restaurar en atenea un respaldo reciente de `.73` (`restaurar-base.sh`), copiar los PDF, levantar caliope (`promover.sh` desde caliopedev y `desplegar.sh prod`) y entrar por `https://caliope.asuncion.gov.py` o con el proxy. Después, borrar esa base de ensayo (`DROP DATABASE`) y los PDF copiados para hacer el corte limpio.
5. **Corte**, fuera del horario de uso:
   - **En `.73`:** `systemctl stop gestion-documental` y `systemctl disable --now gestion-documental-respaldo.timer` (si no, esa noche mandaría a `.71` un respaldo de la base vieja, en la misma carpeta); respaldo final (`respaldar.sh`) y confirmar que llegó al otro equipo; copiar `/var/lib/gestion-documental/pdf` a caliope con `rsync -a`.
   - **En atenea:** `restaurar-base.sh gestion_documental gestion_documental <respaldo final>`.
   - **En caliope:** `bash despliegue/revisar_pdf.sh` con 0 faltantes y 0 alterados, y `desplegar.sh prod`.
   - **En caliope:** en `/etc/gestion-documental/respaldo`, cambiar `RESPALDO_DESTINO` a `respaldo@192.168.129.71:/srv/respaldos/gestion_documental`, la carpeta que usaba `.73`. Desde esta noche, los respaldos de producción siguen llegando al mismo lugar.
   - **En el DNS interno:** `mesa` → 192.168.129.111. Avisar a los usuarios la dirección nueva.
6. **Vuelta atrás:** volver a arrancar el servicio en `.73`, que quedó intacto. Solo vale antes de cargar datos nuevos en caliope.
7. **Retiro en `.73`**, una semana después y con la nueva funcionando:
   - `systemctl disable gestion-documental`, quitar `/etc/nginx/conf.d/gestion-documental.conf` y cerrar 80/443 si nada más los usa.
   - Renombrar la base vieja (`ALTER DATABASE gestion_documental RENAME TO gestion_documental_retirada`) y borrarla 30 días después, con un respaldo guardado.
   - `https://192.168.129.73` deja de funcionar: avisar antes.

## Lo que falta decidir o comprobar

- **Prueba real:** nada de esto se ejecutó todavía.
- **Espacio en `.71`:** el 05/11 tenía 67 GB libres de 70. Con producción y desarrollo copiando allí, y si se suma el respaldo completo de atenea, revisar el disco antes del piloto.
- **Salida a internet de caliope y caliopedev:** hace falta para Docker Hub, Maven Central y los repositorios de Rocky, EPEL, Docker y PGDG.
- **Rutas del certificado comodín** en el proxy (`SSLCertificateFile` y `SSLCertificateKeyFile`).
- **Imágenes base:** fijarlas por su digest (`@sha256:...`) antes del piloto.
- **`.73` en esta versión:** `respaldar.sh` escribe el respaldo como root en lugar de pasarle el archivo a `postgres`, y el nginx nuevo confía en el proxy. Conviene correr el respaldo una vez a mano al instalarla.
