#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Instalación en el servidor (192.168.129.73)
#
# Se ejecuta como root, desde la carpeta donde se copiaron estos archivos y el .jar:
#     bash instalar.sh <nombre o IP con que entran los usuarios>
#
# Se puede repetir: actualiza el .jar y la configuración, sin tocar la contraseña
# (/etc/gestion-documental/entorno), el certificado ni los PDF. No arranca la aplicación
# la primera vez, porque antes hay que completar la contraseña. La guía completa está en
# docs/despliegue-servidor.md.
# =====================================================================
set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Uso: bash $0 <nombre o IP con que entran los usuarios>" >&2
    exit 1
fi
NOMBRE="$1"
if ! [[ "$NOMBRE" =~ ^[A-Za-z0-9.-]+$ ]]; then
    echo "Nombre no válido: $NOMBRE" >&2
    exit 1
fi
if [ "$(id -u)" != "0" ]; then
    echo "Ejecútelo como root." >&2
    exit 1
fi
cd "$(dirname "$(readlink -f "$0")")"

# Comprobaciones: mejor detenerse acá que dejar el servicio a medias
for archivo in gestion-documental.jar gestion-documental.service entorno.ejemplo nginx-gestion-documental.conf \
               respaldar.sh revisar_pdf.sh gestion-documental-respaldo.service gestion-documental-respaldo.timer; do
    [ -f "$archivo" ] || { echo "Falta $archivo en $(pwd)." >&2; exit 1; }
done
VERSION_JAVA=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1 || true)
if [ -z "$VERSION_JAVA" ] || [ "$VERSION_JAVA" -lt 25 ]; then
    echo "Hace falta Java 25 o superior (hay: ${VERSION_JAVA:-ninguno})." >&2
    exit 1
fi
# Lo que usan el proxy, el certificado y los respaldos: una instalación mínima puede no traerlos
for programa in nginx openssl sudo tar rsync; do
    command -v "$programa" > /dev/null || { echo "Falta $programa: instálelo con  dnf install -y $programa" >&2; exit 1; }
done

# Usuario y carpetas
id gestion_documental > /dev/null 2>&1 || useradd --system --no-create-home --shell /sbin/nologin gestion_documental
install -d -o root -g root -m 755 /opt/gestion-documental
install -d -o gestion_documental -g gestion_documental -m 750 /var/lib/gestion-documental /var/lib/gestion-documental/pdf
install -d -o root -g gestion_documental -m 750 /etc/gestion-documental
install -d -o root -g root -m 700 /etc/gestion-documental/tls

# Aplicación y respaldos
install -o root -g root -m 644 gestion-documental.jar /opt/gestion-documental/gestion-documental.jar
install -o root -g root -m 750 respaldar.sh revisar_pdf.sh /opt/gestion-documental/
install -o root -g root -m 644 gestion-documental.service gestion-documental-respaldo.service \
        gestion-documental-respaldo.timer /etc/systemd/system/

# Configuración: solo la primera vez, para no pisar la contraseña
PRIMERA_VEZ=no
if [ ! -f /etc/gestion-documental/entorno ]; then
    install -o root -g gestion_documental -m 640 entorno.ejemplo /etc/gestion-documental/entorno
    PRIMERA_VEZ=si
fi

# Certificado: si no hay uno, se genera uno interno válido por 2 años. Se reemplaza por el
# institucional copiando certificado.crt y certificado.key a /etc/gestion-documental/tls/
if [ ! -f /etc/gestion-documental/tls/certificado.crt ]; then
    if [[ "$NOMBRE" =~ ^[0-9.]+$ ]]; then SAN="IP:$NOMBRE"; else SAN="DNS:$NOMBRE"; fi
    openssl req -x509 -newkey rsa:2048 -nodes -days 730 -subj "/CN=$NOMBRE" -addext "subjectAltName=$SAN" \
        -keyout /etc/gestion-documental/tls/certificado.key -out /etc/gestion-documental/tls/certificado.crt 2> /dev/null
    chmod 600 /etc/gestion-documental/tls/certificado.key
    echo "Se generó un certificado interno para $NOMBRE: el navegador lo advierte hasta que TIC lo instale en los equipos."
fi

# nginx
sed "s/NOMBRE_SERVIDOR/$NOMBRE/g" nginx-gestion-documental.conf > /etc/nginx/conf.d/gestion-documental.conf
nginx -t

# SELinux y cortafuegos, si están en uso en este servidor
if command -v setsebool > /dev/null && [ "$(getenforce 2> /dev/null || echo Disabled)" != "Disabled" ]; then
    setsebool -P httpd_can_network_connect 1
fi
if command -v firewall-cmd > /dev/null && firewall-cmd --state > /dev/null 2>&1; then
    firewall-cmd --permanent --add-service=http --add-service=https > /dev/null
    firewall-cmd --reload > /dev/null
fi

systemctl daemon-reload
systemctl enable --now nginx
systemctl reload nginx
systemctl enable gestion-documental > /dev/null 2>&1
systemctl enable --now gestion-documental-respaldo.timer > /dev/null 2>&1

if [ "$PRIMERA_VEZ" = "si" ]; then
    echo
    echo "Falta completar la contraseña de la base en /etc/gestion-documental/entorno. Después:"
    echo "    systemctl start gestion-documental"
else
    systemctl restart gestion-documental
    echo "Aplicación actualizada y reiniciada."
fi
echo "Dirección de entrada: https://$NOMBRE"
