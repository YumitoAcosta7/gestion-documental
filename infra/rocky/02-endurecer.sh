#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Paso 2: SSH, cortafuegos, SELinux y núcleo
#
#     bash 02-endurecer.sh <puerto SSH nuevo>               # abre el puerto nuevo, deja el 22
#     bash 02-endurecer.sh <puerto SSH nuevo> --cerrar-22   # cierra el 22, una vez probado el nuevo
#
# En dos pasadas a propósito: si el puerto nuevo no funciona, el 22 sigue abierto y
# nadie queda afuera. Entre una y otra, abra OTRA sesión con  ssh -p <puerto> root@<servidor>
# y no cierre la actual hasta confirmar que entra.
# =====================================================================
set -euo pipefail

PUERTO="${1:-}"; CERRAR="${2:-}"
[[ "$PUERTO" =~ ^[0-9]+$ ]] && [ "$PUERTO" -gt 1024 ] && [ "$PUERTO" -lt 65536 ] \
    || { echo "Uso: bash $0 <puerto SSH entre 1025 y 65535> [--cerrar-22]" >&2; exit 1; }
[ "$(id -u)" = "0" ] || { echo "Ejecútelo como root." >&2; exit 1; }
DIR="$(cd "$(dirname "$0")" && pwd)"

# Sin una llave cargada, prohibir la contraseña dejaría el servidor sin acceso
if [ ! -s /root/.ssh/authorized_keys ]; then
    echo "root no tiene llaves en /root/.ssh/authorized_keys. Cárguela primero:  ssh-copy-id root@<servidor>" >&2
    exit 1
fi

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

echo "== SSH: solo con llave, root solo con llave, en el puerto $PUERTO"
cat > /etc/ssh/sshd_config.d/10-gestion-documental.conf <<EOF
# Lo genera infra/rocky/02-endurecer.sh
Port $PUERTO
$( [ "$CERRAR" = "--cerrar-22" ] || echo "Port 22" )
PermitRootLogin prohibit-password
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

echo "== Cortafuegos: SSH, y 80/443 para nginx"
systemctl enable --now firewalld
ZONA="$(firewall-cmd --get-default-zone)"
firewall-cmd --permanent --zone="$ZONA" --add-port="$PUERTO/tcp"
firewall-cmd --permanent --zone="$ZONA" --add-service=http --add-service=https
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
    echo "SSH escucha en 22 y en $PUERTO. Pruebe en OTRA ventana:  ssh -p $PUERTO root@<servidor>"
    echo "Si entra, repita:  bash $0 $PUERTO --cerrar-22"
fi
echo "Puertos abiertos al exterior (no deben aparecer 5432 ni 8080 en 0.0.0.0):"
ss -tlnH | awk '{print $4}' | sort -u
