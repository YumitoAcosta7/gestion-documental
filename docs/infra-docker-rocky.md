# Servidores de desarrollo y producción con Rocky Linux y Docker

**Estado:** propuesta del 11/11/2026. Ningún paso se ejecutó todavía. Los archivos están en la raíz (`Dockerfile`, `docker-compose*.yml`) y en `infra/`. **No se probaron en un servidor:** la imagen no se pudo construir en la PC (no tiene Docker), y los scripts solo se revisaron por sintaxis. Lo primero es ensayarlos en el servidor de desarrollo.

**No reemplaza a producción actual (`192.168.129.73`)** hasta hacer el corte de la sección 7.

## Resumen

| Capa | Dónde | Por qué |
|---|---|---|
| Aplicación (Spring Boot) | Docker | Es lo que cambia en cada versión: la misma imagen probada en desarrollo pasa a producción |
| PostgreSQL 17 | Docker, con los datos en el servidor | Misma versión y configuración en los dos ambientes; los datos no viven en el contenedor |
| PDF | Carpeta del servidor, montada en la aplicación | Respaldo, control de huellas y copia a otro equipo siguen funcionando igual |
| nginx (HTTPS) | Servidor | Termina el HTTPS y reemplaza `X-Forwarded-For`: la IP de las bitácoras no se puede falsificar |
| Respaldos, hora, SSH, cortafuegos, SELinux | Servidor | Tienen que funcionar aunque Docker esté detenido o roto |

## 1. Qué se dockeriza y qué no

**Aplicación: sí.** El `Dockerfile` compila en una etapa y corre en otra con solo el JRE, como usuario sin privilegios (10001), sistema de archivos de solo lectura, sin capacidades del núcleo y con memoria limitada. La etiqueta de la imagen es el commit: lo que se probó en desarrollo es exactamente lo que corre en producción (`promover.sh`).

**PostgreSQL: sí, con condiciones.**
- **Datos fuera del contenedor:** en `/var/lib/gestion-documental/postgres`, un volumen lógico propio. Borrar o actualizar el contenedor no los toca.
- **Versión menor fijada:** la misma que la base de origen al restaurar. Un cambio de versión mayor (17 a 18) nunca se hace cambiando la imagen: requiere `pg_upgrade` o volcar y restaurar.
- **Bitácoras inmutables:** sus protecciones viven dentro de la base (roles, permisos y triggers de `proteger_bitacoras.sh` y `reforzar_bitacoras.sh`), así que funcionan igual en Docker. `cargar-base.sh` las instala y verifica.
- **Apagado ordenado:** `stop_grace_period: 90s`. Por defecto Docker corta a los 10 segundos, y PostgreSQL arrancaría en recuperación.

**nginx: no.** Del servidor llega más fácil el certificado institucional, y su configuración ya está probada. La aplicación publica su puerto solo en `127.0.0.1:8080`, así que nginx es la única entrada.

**Respaldos, chrony, firewalld, SSH, fail2ban: no.** Son del servidor y tienen que funcionar aunque Docker falle. El respaldo llega a la base con `docker exec`.

### Diferencias con el pedido original

| Pedido | Cambio | Motivo |
|---|---|---|
| Volúmenes de Docker para base y PDF | Carpetas del servidor montadas, cada una en su volumen lógico | Los volúmenes de Docker quedan dentro de `/var/lib/docker`: un `docker volume prune` los borra, y respaldo y control no los encuentran por su ruta |
| Hora UTC también en los contenedores | Servidor en UTC; aplicación y base en hora de Asunción | La aplicación guarda la hora local y cuenta días hábiles; en UTC todo quedaría corrido 3 horas respecto de los datos existentes |
| `tar.gz` diario de los PDF | Copia incremental con `rsync` a otro equipo (ya en uso) | Los PDF nunca se sobrescriben: copiar solo los nuevos ocupa menos y no repite lo ya copiado. El `tar.gz` queda solo si no hay otro equipo |
| Desactivar IPv6 | En todas las interfaces menos la interna (`lo`) | Algunos servicios la usan internamente |
| `ip_forward` apagado (guías de hardening) | Encendido | Sin él, Docker deja a los contenedores sin red. El acceso lo limita el cortafuegos |
| Crear la base con Flyway | Restaurar un respaldo | Las migraciones empiezan en V2: el esquema anterior no está en el repositorio |

## 2. Particionado

Recomiendo **dos discos**: uno de sistema y otro de datos. Reinstalar el sistema no toca los datos, y el de datos se agranda o se mueve solo. El ejemplo completo para el instalador está en `infra/rocky/archivos/particionado.ks`.

**Disco de sistema (`vg_sistema`, ej. 60 GB):**

| Punto de montaje | Tamaño | Opciones | Por qué |
|---|---|---|---|
| `/boot` (fuera de LVM) | 1 GB | `nodev,nosuid,noexec` | |
| `/` | 12 GB | | |
| `/var` | 6 GB | `nodev` | |
| `/var/log` | 4 GB | `nodev,nosuid,noexec` | Un registro desbordado no llena `/var` |
| `/var/log/audit` | 2 GB | `nodev,nosuid,noexec` | auditd sigue funcionando aunque `/var/log` se llene |
| `/var/tmp`, `/tmp` | 1 y 2 GB | `nodev,nosuid,noexec` | |
| `/home` | 2 GB | `nodev,nosuid` | |
| `/opt` | 4 GB | `nodev` | El repositorio y los archivos de Docker Compose |
| `/var/lib/docker` | 12 GB | | Imágenes y capas: una imagen grande no llena `/var` |
| swap | 4 GB | | |
| **Libre en el grupo** | ~13 GB | | Ampliaciones y snapshots |

**Disco de datos (`vg_datos`, ej. 100 GB):**

| Punto de montaje | Tamaño | Opciones |
|---|---|---|
| `/var/lib/gestion-documental/postgres` | 20 GB | `noatime,nodev,nosuid,noexec` |
| `/var/lib/gestion-documental/pdf` | 40 GB | `noatime,nodev,nosuid,noexec` |
| `/var/backups` | 15 GB | `noatime,nodev,nosuid,noexec` |
| **Libre en el grupo** | ~25 GB | |

- **Base y PDF separados:** si los PDF llenan su volumen, la base sigue escribiendo y no se cae.
- **Sistema de archivos XFS:** es el de Rocky, y el que Docker necesita para `overlay2` (con `ftype=1`, que ya es el valor por defecto).
- **Sin `nobarrier`:** esa opción desactiva la protección ante cortes de energía. En una máquina virtual, el almacenamiento del hipervisor tiene que respetar las escrituras confirmadas.
- **Snapshots:** para un snapshot de la base, detener antes `gd-db` o usar el respaldo. Un snapshot con la base escribiendo se recupera como un corte de energía.
- **Respaldos locales:** `/var/backups` en el mismo disco no protege de una falla del disco. La copia a otro equipo (`RESPALDO_DESTINO`) es obligatoria.

## 3. Paquetes y hora

`01-preparar-host.sh` instala:
- **Repositorios:** EPEL y CRB.
- **Docker:** `docker-ce`, `containerd.io` y los complementos de Buildx y Compose.
- **Servicios del servidor:** nginx, chrony, firewalld, fail2ban, auditd y `dnf-automatic`, este último solo para parches de seguridad y sin reiniciar.
- **Herramientas:** rsync, tar, git, curl, jq, openssl y las de SELinux.

**Hora:**
- **Servidor:** en UTC, sincronizado con chrony contra los servidores NTP que se le indiquen. Si la Municipalidad tiene uno, ese; si no, `pool.ntp.org`.
- **Ajustes de reloj:** `makestep 1.0 3` corrige de golpe solo en los primeros ajustes del arranque; después, chrony ajusta despacio, sin saltos de hora en las bitácoras.
- **Contenedores:** la aplicación y la base corren con `TZ=America/Asuncion`, la aplicación además con `-Duser.timezone`.
- **Respaldo:** el temporizador usa `22:00 America/Asuncion`. Con el servidor en UTC, `22:00` a secas serían las 19:00, en horario de uso.

## 4. Red, cortafuegos y hardening

`02-endurecer.sh` se corre dos veces, para no quedar afuera:

1. **Primera vez:** abre el puerto SSH nuevo y deja el 22. Prueba entrar por el puerto nuevo en **otra** ventana, sin cerrar la actual.
2. **Segunda vez, con `--cerrar-22`:** cierra el 22.

**Qué configura:**
- **SSH:**
  - Solo con llave; se detiene si root no tiene una cargada.
  - Root solo con llave (`prohibit-password`).
  - Sin reenvíos, con 3 intentos y 30 segundos para entrar.
  - El puerto nuevo, con su etiqueta de SELinux.
- **firewalld:** solo el puerto SSH, 80 y 443, sin cockpit ni dhcpv6. La base (5432) y la aplicación (8080) nunca se abren: no se publican, o se publican solo en `127.0.0.1`.
- **SELinux en `Enforcing`:**
  - **Datos montados:** `:Z` (etiqueta privada) en datos, PDF y configuración; `:z` (compartida) en el secreto que leen los dos contenedores.
  - **nginx:** `httpd_can_network_connect` le permite llegar a `127.0.0.1:8080`.
  - **Si estaba deshabilitado:** se activa con un reetiquetado al reiniciar.
- **Docker** (`infra/rocky/archivos/daemon.json`):
  - Los puertos se publican por defecto en `127.0.0.1`.
  - `live-restore`: reiniciar Docker no detiene los contenedores.
  - `no-new-privileges`, sin IPv6 y con registros a journald.
  - Docker crea sus propias reglas de cortafuegos. Como nada se publica fuera de `127.0.0.1`, esas reglas no abren nada a la red.
- **Núcleo** (`infra/rocky/archivos/90-gestion-documental.conf`):
  - SYN cookies y una cola de conexiones a medio abrir más grande.
  - `rp_filter`, sin redirecciones ni rutas de origen.
  - `kptr_restrict`, `dmesg_restrict` y `ptrace_scope`.
- **fail2ban:** bloquea por una hora una IP con 5 intentos fallidos de SSH.
- **Red interna de la base** (`datos`): es `internal`, sin salida a internet ni entrada desde fuera de Docker. Solo la aplicación llega a PostgreSQL.

## 5. Datos, respaldos y reinicios

- **Reinicios:**
  - Desarrollo usa `unless-stopped`, producción `always`.
  - La aplicación espera a que la base responda (`depends_on` con `service_healthy`), y se reinicia si la base se reinicia.
  - La base además tiene que estar cargada: `desplegar.sh` se detiene si está vacía.
- **Durabilidad de PostgreSQL** (`infra/postgres/postgresql.conf`):
  - `fsync`, `synchronous_commit` y `full_page_writes` encendidos. Nunca apagarlos: la cadena de huellas depende de que cada fila confirmada esté en el disco.
  - Sumas de verificación de páginas al crear la base (`--data-checksums`).
- **Respaldo diario:** `despliegue/respaldar.sh`, el mismo de hoy, con un cambio que conserva el comportamiento actual.
  - **Cómo llega a la base:** con Docker se configura `COMO_POSTGRES="docker exec -i -u postgres gd-db"` en `/etc/gestion-documental/respaldo`. Sin esa línea sigue usando `sudo -u postgres`.
  - **Qué guarda y dónde:** base y huellas de las bitácoras, 30 días aquí y 90 en el otro equipo, más los PDF nuevos con `rsync`.
  - **Controles:** verifica que el respaldo se pueda leer, que los PDF coincidan con sus huellas y el espacio en disco.
- **Recuperación a un punto en el tiempo:** queda apagada (`archive_mode = off`). Con el respaldo diario se puede perder hasta un día. Para menos, hace falta archivar el WAL en otro equipo, con pgBackRest por ejemplo. Conviene decidirlo antes de ampliar el piloto.
- **Energía:** ninguna configuración reemplaza a una UPS con apagado ordenado del servidor.

## 6. Orden de ejecución

En cada servidor, como root. Primero **todo en desarrollo**; producción solo cuando desarrollo funcionó completo.

1. Instalar Rocky Linux con `particionado.ks` (o su equivalente a mano).
2. Cargar la llave SSH de root (`ssh-copy-id`) y clonar el repositorio en `/opt/gestion-documental`.
3. `bash infra/rocky/01-preparar-host.sh <dev|prod> "<servidores NTP>"`
4. `bash infra/rocky/02-endurecer.sh <puerto>`, probar el puerto en otra ventana, y `bash infra/rocky/02-endurecer.sh <puerto> --cerrar-22`
5. `bash infra/rocky/03-nginx-y-respaldos.sh <nombre o IP> [respaldo@192.168.129.71:/srv/respaldos/<carpeta>]`. Usar una carpeta distinta para cada ambiente.
6. Revisar `/opt/gestion-documental/.env` (se crea en el primer intento de desplegar).
7. Levantar solo la base: `docker compose -f docker-compose.yml -f docker-compose.<ambiente>.yml up -d --wait db`
8. Cargarla: `bash infra/rocky/cargar-base.sh <respaldo .dump>`. En desarrollo, un respaldo de la base de pruebas, nunca de producción. En producción, ver la sección 7.
9. Desarrollo: `bash infra/rocky/desplegar.sh dev` construye y levanta.
10. Producción:
    - En desarrollo: `bash infra/rocky/promover.sh <imagen> root@<prod> <puerto>`.
    - En producción: poner esa etiqueta en `IMAGEN_APP` de `.env` y ejecutar `bash infra/rocky/desplegar.sh prod`.
11. Comprobar:
    - **Servicio:** `docker compose ps` muestra los dos servicios como `healthy`, y la aplicación responde en `https://<servidor>`.
    - **Puertos:** `ss -tlnp` no muestra 5432 ni 8080 fuera de `127.0.0.1`.
    - **Respaldo:** `systemctl start gestion-documental-respaldo.service` termina sin error, y se prueba una restauración.
    - **Bitácoras:** `docker exec -u postgres gd-db bash /opt/scripts-db/seguridad/reforzar_bitacoras.sh gestion_documental --verificar` da ÍNTEGRAS.
    - **Hora:** la hora de un registro nuevo en la bitácora coincide con la de Asunción.

**Base de desarrollo desde la PC:** solo por túnel SSH, porque el puerto está publicado en `127.0.0.1` del servidor:

```bash
ssh -p <puerto> -L 5432:127.0.0.1:5432 root@<servidor-dev>
```

## 7. Pasar producción a los servidores nuevos

1. Ensayar todo en desarrollo, incluida la restauración de una copia de la base de pruebas, el control de PDF y la verificación de las bitácoras.
2. Fijar `IMAGEN_POSTGRES` en la misma versión menor que la base actual (hoy 17.10).
3. Corte, fuera del horario de uso:
   - **En `.73`:**
     - Detener la aplicación.
     - Hacer el respaldo final y confirmar que llegó a `.71`.
     - Copiar `/var/lib/gestion-documental/pdf` al servidor nuevo.
   - **En el servidor nuevo:**
     - `cargar-base.sh` con ese respaldo.
     - `revisar_pdf.sh` con 0 faltantes y 0 alterados.
     - Verificar las bitácoras y desplegar.
4. Cambiar la dirección de entrada (DNS o IP) y el certificado.
5. **Vuelta atrás:** volver a arrancar la aplicación en `.73`, que quedó intacto. Solo vale antes de cargar datos nuevos en el servidor nuevo.

## Lo que falta decidir o comprobar

- **Prueba real:** construir la imagen y correr los scripts en el servidor de desarrollo. Nada de esto se ejecutó todavía.
- **Salida a internet de los servidores:**
  - **Hace falta para:** Docker Hub, Maven Central, el repositorio de Docker y EPEL.
  - **Si no la hay:** la imagen se construye en otro equipo y se lleva con `docker save`, y los paquetes salen de un espejo interno.
- **Servidores NTP de la Municipalidad.**
- **Imágenes base:** fijarlas por su digest (`@sha256:...`) antes del piloto.
- **Intercalación de la base:** comprobar la de la base actual con `SHOW lc_collate`. Si no es `C.UTF-8`, ajustar `POSTGRES_INITDB_ARGS` antes de crear la base, porque cambia el orden alfabético.
- **Memoria de PostgreSQL:** `shared_buffers = 1GB` supone un servidor de 8 GB. Ajustarlo al real.
- **Copia del WAL a otro equipo:** decidir si se quiere perder como máximo un día (como ahora) o minutos.
- **Respaldo en el servidor actual:** `respaldar.sh` ahora escribe el respaldo como root, en lugar de pasarle el archivo a `postgres`. Conviene correrlo una vez a mano en `.73` antes de las 22:00, al instalar esta versión.
