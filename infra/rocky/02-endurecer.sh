#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Paso 2: SSH, cortafuegos, SELinux y núcleo
#
#     sudo bash 02-endurecer.sh <puerto SSH nuevo>               # abre el puerto nuevo, deja el 22
#     sudo bash 02-endurecer.sh <puerto SSH nuevo> --cerrar-22   # cierra el 22, una vez probado el nuevo
#
# Con WEB_DESDE="192.168.129.111 <red o equipo de la DTIC>", 80 y 443 aceptan solo esos orígenes:
# los usuarios entran por el proxy interno y nadie lo saltea. Sin WEB_DESDE quedan abiertos a la red.
#
# En dos pasadas a propósito: si el puerto nuevo no funciona, el 22 sigue abierto y
# nadie queda afuera. Entre una y otra, abra OTRA sesión con  ssh -p <puerto> <usuario>@<servidor>
# y no cierre la actual hasta confirmar que entra.
# =====================================================================
set -euo pipefail

PUERTO="${1:-}"; CERRAR="${2:-}"
[[ "$PUERTO" =~ ^[0-9]+$ ]] && [ "$PUERTO" -gt 1024 ] && [ "$PUERTO" -lt 65536 ] \
    || { echo "Uso: bash $0 <puerto SSH entre 1025 y 65535> [--cerrar-22]" >&2; exit 1; }
[ "$(id -u)" = "0" ] || { echo "Ejecútelo como root." >&2; exit 1; }
DIR="$(cd "$(dirname "$0")" && pwd)"

# Sin una llave cargada, prohibir la contraseña dejaría el servidor sin acceso. Se administra con un
# usuario con sudo (el de la instalación); root no entra por SSH
ADMIN="${SUDO_USER:-root}"
if [ ! -s "$(getent passwd "$ADMIN" | cut -d: -f6)/.ssh/authorized_keys" ]; then
    echo "$ADMIN no tiene llaves en ~/.ssh/authorized_keys. Cárguela primero:  ssh-copy-id $ADMIN@<servidor>" >&2
    exit 1
fi
WEB_DESDE="${WEB_DESDE:-}"

echo "== SELinux en Enforcing"
if [ "$(getenforce)" = "Disabled" ]; then
    sed -i 's/^SELINUX=.*/SELINUX=enforcing/' /etc/selinux/config
    touch /.autorelabel
    echo "SELinux estaba deshabilitado: queda activo después de reiniciar (el reetiquetado tarda). Reinicie y repita este paso." >&2
    exit 1
fi
sed -i 's/^SELINUX=.*/SELINUX=enforcing/' /etc/selinux/config
setenforce 1
# nginx del servidor reenvía a la aplicación en 127.0.0.1:8080
setsebool -P httpd_can_network_connect 1
# El puerto SSH nuevo, con su etiqueta: sin ella SELinux no deja que sshd lo use
semanage port -a -t ssh_port_t -p tcp "$PUERTO" 2> /dev/null || semanage port -m -t ssh_port_t -p tcp "$PUERTO"

echo "== SSH: solo con llave, root sin entrada directa, en el puerto $PUERTO"
cat > /etc/ssh/sshd_config.d/10-gestion-documental.conf <<EOF
# Lo genera infra/rocky/02-endurecer.sh
Port $PUERTO
$( [ "$CERRAR" = "--cerrar-22" ] || echo "Port 22" )
$( [ "$ADMIN" = root ] && echo "PermitRootLogin prohibit-password" || echo "PermitRootLogin no" )
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
PermitEmptyPasswords no
X11Forwarding no
AllowAgentForwarding no
MaxAuthTries 3
LoginGraceTime 30
ClientAliveInterval 300
ClientAliveCountMax 2
EOF
sshd -t
systemctl reload sshd

echo "== Cortafuegos: SSH, y 80/443 para nginx${WEB_DESDE:+ solo desde $WEB_DESDE}"
systemctl enable --now firewalld
ZONA="$(firewall-cmd --get-default-zone)"
firewall-cmd --permanent --zone="$ZONA" --add-port="$PUERTO/tcp"
if [ -z "$WEB_DESDE" ]; then
    firewall-cmd --permanent --zone="$ZONA" --add-service=http --add-service=https
else
    firewall-cmd --permanent --zone="$ZONA" --remove-service=http --remove-service=https 2> /dev/null || true
    for origen in $WEB_DESDE; do
        for servicio in http https; do
            firewall-cmd --permanent --zone="$ZONA" --add-rich-rule="rule family=ipv4 source address=$origen service name=$servicio accept"
        done
    done
fi
for servicio in cockpit dhcpv6-client; do
    firewall-cmd --permanent --zone="$ZONA" --remove-service="$servicio" 2> /dev/null || true
done
if [ "$CERRAR" = "--cerrar-22" ]; then
    firewall-cmd --permanent --zone="$ZONA" --remove-service=ssh 2> /dev/null || true
fi
firewall-cmd --reload
firewall-cmd --zone="$ZONA" --list-all

echo "== fail2ban: bloquea IP tras intentos fallidos de SSH"
cat > /etc/fail2ban/jail.d/sshd.local <<EOF
[sshd]
enabled = true
port = $PUERTO
backend = systemd
maxretry = 5
bantime = 1h
EOF
systemctl enable --now fail2ban
systemctl restart fail2ban

echo "== Núcleo"
install -m 644 "$DIR/archivos/90-gestion-documental.conf" /etc/sysctl.d/90-gestion-documental.conf
sysctl --system > /dev/null
# Docker vuelve a crear sus reglas con el cortafuegos recargado
systemctl restart docker

echo
if [ "$CERRAR" = "--cerrar-22" ]; then
    echo "Listo: SSH solo en el puerto $PUERTO."
else
    echo "SSH escucha en 22 y en $PUERTO. Pruebe en OTRA ventana:  ssh -p $PUERTO $ADMIN@<servidor>"
    echo "Si entra, repita:  bash $0 $PUERTO --cerrar-22"
fi
echo "Puertos abiertos al exterior (no deben aparecer 5432 ni 8080 en 0.0.0.0):"
ss -tlnH | awk '{print $4}' | sort -u
