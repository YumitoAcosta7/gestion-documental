#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Paso 1: paquetes, hora, usuarios y carpetas del servidor
#
#     bash 01-preparar-host.sh <dev|prod> "<servidores NTP separados por espacio>"
#     ej.: bash 01-preparar-host.sh prod "ntp1.asuncion.gov.py ntp2.asuncion.gov.py"
#
# Rocky Linux 9 o 10, como root, recién instalado con el particionado de
# archivos/particionado.ks. Se puede repetir. No toca SSH ni el cortafuegos (paso 2).
# =====================================================================
set -euo pipefail

AMBIENTE="${1:-}"; NTP="${2:-}"
[[ "$AMBIENTE" =~ ^(dev|prod)$ ]] || { echo "Uso: bash $0 <dev|prod> \"<servidores NTP>\"" >&2; exit 1; }
[ -n "$NTP" ] || { echo "Indique al menos un servidor NTP (el de la Municipalidad, si existe)." >&2; exit 1; }
[ "$(id -u)" = "0" ] || { echo "Ejecútelo como root." >&2; exit 1; }
DIR="$(cd "$(dirname "$0")" && pwd)"

# Los números de usuario que esperan las imágenes: 10001 (aplicación) y 999 (postgres de la imagen oficial)
UID_APP=10001
UID_POSTGRES=999

echo "== Paquetes"
dnf -y install epel-release dnf-plugins-core
# EPEL necesita CRB para algunas dependencias (fail2ban)
dnf config-manager --set-enabled crb
dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo 2> /dev/null \
    || dnf config-manager addrepo --from-repofile=https://download.docker.com/linux/rhel/docker-ce.repo
dnf -y upgrade
dnf -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin \
               nginx chrony firewalld policycoreutils-python-utils container-selinux \
               rsync tar git curl jq lvm2 openssl audit dnf-automatic fail2ban

echo "== Hora: el servidor en UTC; la aplicación y la base usan la de Asunción dentro de sus contenedores"
timedatectl set-timezone UTC
{
    echo "# Gestión Documental: lo genera 01-preparar-host.sh"
    for s in $NTP; do echo "server $s iburst"; done
    echo "driftfile /var/lib/chrony/drift"
    # Corrige de golpe solo al arrancar; después ajusta despacio, sin saltos en las bitácoras
    echo "makestep 1.0 3"
    echo "rtcsync"
    echo "logdir /var/log/chrony"
} > /etc/chrony.conf
systemctl enable --now chronyd
systemctl restart chronyd
sleep 5
chronyc -n sources || true

echo "== Actualizaciones de seguridad automáticas (solo las de seguridad, sin reiniciar)"
sed -i 's/^upgrade_type.*/upgrade_type = security/; s/^apply_updates.*/apply_updates = yes/' /etc/dnf/automatic.conf
systemctl enable --now dnf-automatic.timer
systemctl enable --now auditd

echo "== Usuarios dueños de los datos (sin acceso al sistema)"
getent group gestion > /dev/null || groupadd --system --gid "$UID_APP" gestion
id gestion > /dev/null 2>&1 || useradd --system --uid "$UID_APP" --gid "$UID_APP" --no-create-home --shell /sbin/nologin gestion

echo "== Carpetas"
install -d -m 755 /var/lib/gestion-documental
install -d -o "$UID_POSTGRES" -g "$UID_POSTGRES" -m 700 /var/lib/gestion-documental/postgres
install -d -o "$UID_APP" -g "$UID_APP" -m 750 /var/lib/gestion-documental/pdf
install -d -m 750 /var/backups/gestion_documental
install -d -m 755 /opt/gestion-documental
install -d -m 750 /etc/gestion-documental
install -d -m 700 /etc/gestion-documental/tls
install -d -m 711 /etc/gestion-documental/secretos
install -d -o "$UID_APP" -g "$UID_POSTGRES" -m 750 /etc/gestion-documental/secretos/db

echo "== Contraseñas de la base: se generan una sola vez y nunca se muestran"
if [ ! -f /etc/gestion-documental/secretos/postgres_password ]; then
    openssl rand -base64 33 | tr -d '\n' > /etc/gestion-documental/secretos/postgres_password
fi
chown "$UID_POSTGRES:$UID_POSTGRES" /etc/gestion-documental/secretos/postgres_password
chmod 400 /etc/gestion-documental/secretos/postgres_password
if [ ! -f /etc/gestion-documental/secretos/db/DB_PASSWORD ]; then
    openssl rand -base64 33 | tr -d '\n' > /etc/gestion-documental/secretos/db/DB_PASSWORD
fi
# La lee la aplicación (10001) y, en el primer arranque, la base para crear el rol (grupo 999)
chown "$UID_APP:$UID_POSTGRES" /etc/gestion-documental/secretos/db/DB_PASSWORD
chmod 440 /etc/gestion-documental/secretos/db/DB_PASSWORD

echo "== Docker"
install -m 644 "$DIR/archivos/daemon.json" /etc/docker/daemon.json
systemctl enable --now docker
docker info --format '{{.Driver}} / SELinux: {{.SecurityOptions}}'

echo
echo "Listo el paso 1 ($AMBIENTE). Siga con:  bash $DIR/02-endurecer.sh <puerto SSH nuevo>"
