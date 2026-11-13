#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Paso 1: paquetes, hora, usuarios y carpetas del servidor Docker
#
#     sudo bash 01-preparar-host.sh <dev|prod> ["<servidores NTP separados por espacio>"]
#
# Rocky Linux 9, recién instalado con infra/rocky/kvm (crear-vm.sh). Se puede repetir.
# No toca SSH ni el cortafuegos (paso 2). La base no se instala aquí: está en el
# servidor PostgreSQL del ambiente (atenea o ateneadev); solo se instala el cliente,
# para el respaldo.
# =====================================================================
set -euo pipefail

AMBIENTE="${1:-}"
# Los mismos que usan atenea y ateneadev
NTP="${2:-201.217.3.85 201.217.3.86 162.159.200.1 162.159.200.123}"
[[ "$AMBIENTE" =~ ^(dev|prod)$ ]] || { echo "Uso: sudo bash $0 <dev|prod> [\"<servidores NTP>\"]" >&2; exit 1; }
[ "$(id -u)" = "0" ] || { echo "Ejecútelo como root." >&2; exit 1; }
DIR="$(cd "$(dirname "$0")" && pwd)"

# El número de usuario que espera la imagen de la aplicación
UID_APP=10001

echo "== Paquetes"
dnf -y install epel-release dnf-plugins-core
# EPEL necesita CRB para algunas dependencias (fail2ban)
dnf config-manager --set-enabled crb
dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo
# Primero a la última 9.x: PGDG publica solo las versiones menores vigentes, y desde una ISO
# vieja (9.5) su repositorio da 404 y frena todo dnf. Por eso se excluye hasta actualizar
dnf -y --disablerepo='pgdg*' upgrade
# Cliente de PostgreSQL 17, el mismo origen (PGDG) y versión que los servidores de base
rpm -q pgdg-redhat-repo > /dev/null 2>&1 \
    || dnf -y install https://download.postgresql.org/pub/repos/yum/reporpms/EL-9-x86_64/pgdg-redhat-repo-latest.noarch.rpm
dnf -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin \
               nginx chrony firewalld policycoreutils-python-utils container-selinux \
               rsync tar git curl jq lvm2 openssl audit dnf-automatic fail2ban \
               postgresql17 qemu-guest-agent
systemctl enable --now qemu-guest-agent 2> /dev/null || true

echo "== Hora: America/Asuncion, como el resto de los servidores (los contenedores usan la misma)"
timedatectl set-timezone America/Asuncion
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

echo "== Usuario dueño de los PDF (sin acceso al sistema)"
getent group gestion > /dev/null || groupadd --system --gid "$UID_APP" gestion
id gestion > /dev/null 2>&1 || useradd --system --uid "$UID_APP" --gid "$UID_APP" --no-create-home --shell /sbin/nologin gestion

echo "== Carpetas"
install -d -m 755 /var/lib/gestion-documental
install -d -o "$UID_APP" -g "$UID_APP" -m 750 /var/lib/gestion-documental/pdf
install -d -m 750 /var/backups/gestion_documental
install -d -m 755 /opt/gestion-documental
install -d -m 750 /etc/gestion-documental
install -d -m 700 /etc/gestion-documental/tls
# El certificado público del servidor PostgreSQL (lo da preparar-servidor.sh)
install -d -m 755 /etc/gestion-documental/pg
install -d -m 711 /etc/gestion-documental/secretos
install -d -o "$UID_APP" -g "$UID_APP" -m 700 /etc/gestion-documental/secretos/db

echo "== Contraseña de la aplicación en la base: se genera una sola vez y nunca se muestra"
# En desarrollo, si el rol ya existe en ateneadev, reemplace el contenido por su contraseña actual
if [ ! -f /etc/gestion-documental/secretos/db/DB_PASSWORD ]; then
    openssl rand -base64 33 | tr -d '\n' > /etc/gestion-documental/secretos/db/DB_PASSWORD
fi
chown "$UID_APP:$UID_APP" /etc/gestion-documental/secretos/db/DB_PASSWORD
chmod 400 /etc/gestion-documental/secretos/db/DB_PASSWORD

echo "== Docker"
install -d -m 755 /etc/docker
install -m 644 "$DIR/archivos/daemon.json" /etc/docker/daemon.json
systemctl enable --now docker
docker info --format '{{.Driver}} / SELinux: {{.SecurityOptions}}'

echo
echo "Listo el paso 1 ($AMBIENTE). Siga con:  sudo bash $DIR/02-endurecer.sh <puerto SSH nuevo>"
