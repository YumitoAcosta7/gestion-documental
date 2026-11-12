#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Crea una máquina virtual Rocky Linux 9 para Docker
#
# En el servidor de virtualización (Ubuntu 22.04, qemu-kvm con libvirt):
#     sudo bash crear-vm.sh caliopedev                  # crea la máquina e instala Rocky sin intervención
#     bash crear-vm.sh caliopedev --solo-kickstart      # solo muestra el kickstart, sin crear nada
#
# Lee maquinas.conf (o el archivo de CONF). Igual que las máquinas actuales del
# servidor: i440fx, BIOS, discos qcow2 virtio, red virtio por br0, SPICE solo local
# y consola serie (virsh console). Además: agente de invitado, discard y cache=none.
#
# No toca nada que exista: se detiene si ya hay una máquina con ese nombre, si
# los discos ya existen o si la IP responde.
# =====================================================================
set -euo pipefail

DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
CONF="${CONF:-$DIR/maquinas.conf}"
CLAVE="${1:-}"; MODO="${2:-}"
uso() { echo "Uso: bash $0 <máquina> [--solo-kickstart]   (máquinas en $CONF)" >&2; exit 1; }
falla() { echo "ERROR: $*" >&2; exit 1; }

[ -f "$CONF" ] || falla "no está $CONF"
# shellcheck source=maquinas.conf
. "$CONF"
[ -n "$CLAVE" ] || uso
LINEA="$(awk -v c="$CLAVE" '$1 == c' <<< "$MAQUINAS")"
[ -n "$LINEA" ] || { echo "No está '$CLAVE' en $CONF. Hay:" >&2; awk 'NF {print "  " $1}' <<< "$MAQUINAS" >&2; exit 1; }
read -r _ NOMBRE EQUIPO IP VCPU RAM SISTEMA DATOS PDF RESPALDOS <<< "$LINEA"

# ---- Validaciones de la configuración (no necesitan root)
FALTAN=""
for v in ISO PUERTA DNS ADMIN CLAVE_SSH; do
    [ -n "${!v}" ] && [ "${!v}" != "COMPLETAR" ] || FALTAN+=" $v"
done
[ -z "$FALTAN" ] || falla "complete en $CONF:$FALTAN"
[[ "$EQUIPO" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?$ ]] || falla "nombre de equipo inválido: '$EQUIPO' (solo minúsculas, números y guiones; el guion bajo no vale en un nombre de red)"
[[ "$IP" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || falla "IP inválida: $IP"
[[ "$ADMIN" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]] || falla "usuario administrador inválido: $ADMIN"
[ "$DATOS" -ge $((PDF + RESPALDOS + 2)) ] || falla "el disco de datos ($DATOS GB) no alcanza para pdf ($PDF) + respaldos ($RESPALDOS) + 2 GB"
[ "$SISTEMA" -ge 55 ] || falla "el disco de sistema necesita al menos 55 GB"
[ -r "$CLAVE_SSH" ] || falla "no se puede leer la clave pública $CLAVE_SSH"
CLAVE_PUB="$(head -n1 "$CLAVE_SSH")"
[[ "$CLAVE_PUB" =~ ^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp[0-9]+)\  ]] || falla "$CLAVE_SSH no parece una clave pública SSH (.pub)"
[ -r "$HASH_ADMIN" ] || falla "no está $HASH_ADMIN. Créelo con:  openssl passwd -6 > $HASH_ADMIN && chmod 600 $HASH_ADMIN"
HASH="$(head -n1 "$HASH_ADMIN")"
[[ "$HASH" =~ ^\$6\$ ]] || falla "$HASH_ADMIN no contiene un hash SHA-512 (\$6\$...). Créelo con openssl passwd -6"
FQDN="$EQUIPO${DOMINIO:+.$DOMINIO}"

# ---- Kickstart, en una carpeta temporal privada que se borra al terminar
TMP="$(mktemp -d)"; chmod 700 "$TMP"
trap 'rm -rf "$TMP"' EXIT
esc() { printf '%s' "$1" | sed -e 's/[\\|&]/\\&/g'; }
sed -e "s|@IP@|$(esc "$IP")|g" \
    -e "s|@MASCARA@|$(esc "$MASCARA")|g" \
    -e "s|@PUERTA@|$(esc "$PUERTA")|g" \
    -e "s|@DNS@|$(esc "${DNS// /}")|g" \
    -e "s|@FQDN@|$(esc "$FQDN")|g" \
    -e "s|@ADMIN@|$(esc "$ADMIN")|g" \
    -e "s|@HASH@|$(esc "$HASH")|g" \
    -e "s|@CLAVE_SSH@|$(esc "$CLAVE_PUB")|g" \
    -e "s|@PDF_MB@|$((PDF * 1024))|g" \
    -e "s|@RESPALDOS_MB@|$((RESPALDOS * 1024))|g" \
    "$DIR/rocky9.ks.plantilla" > "$TMP/ks.cfg"
if grep -qE '@[A-Z_]+@' "$TMP/ks.cfg"; then
    falla "quedaron campos sin reemplazar: $(grep -oE '@[A-Z_]+@' "$TMP/ks.cfg" | sort -u | tr '\n' ' ')"
fi

if [ "$MODO" = "--solo-kickstart" ]; then
    # El hash no se muestra
    sed -E 's/(--password=)[^ ]+/\1[OCULTO]/' "$TMP/ks.cfg"
    exit 0
fi
[ -z "$MODO" ] || uso

# ---- Comprobaciones en el servidor de virtualización
[ "$(id -u)" = "0" ] || falla "para crear la máquina, ejecútelo con sudo"
command -v virt-install > /dev/null || falla "falta virt-install (apt install virtinst)"
command -v xorriso > /dev/null || command -v isoinfo > /dev/null \
    || falla "virt-install necesita leer la ISO: apt install xorriso"
virsh dominfo "$NOMBRE" > /dev/null 2>&1 && falla "ya existe una máquina llamada $NOMBRE"
DISCO_SISTEMA="$CARPETA_DISCOS/$NOMBRE.qcow2"
DISCO_DATOS="$CARPETA_DISCOS/$NOMBRE-datos.qcow2"
for d in "$DISCO_SISTEMA" "$DISCO_DATOS"; do
    [ ! -e "$d" ] || falla "ya existe $d"
done
ip link show "$PUENTE" > /dev/null 2>&1 || falla "no existe el puente $PUENTE"
ping -c 2 -W 1 "$IP" > /dev/null 2>&1 && falla "la IP $IP ya responde: está en uso"
[ -r "$ISO" ] || falla "no se puede leer la ISO $ISO"
# AppArmor y los permisos de /home impiden que qemu lea la ISO desde una carpeta personal
case "$ISO" in /home/*) falla "mueva la ISO a $CARPETA_DISCOS: qemu no puede leerla desde /home" ;; esac
LIBRE_GB=$(( $(df -Pk "$CARPETA_DISCOS" | awk 'NR==2 {print $4}') / 1024 / 1024 ))
echo "Espacio libre en $CARPETA_DISCOS: $LIBRE_GB GB (los discos qcow2 crecen hasta $((SISTEMA + DATOS)) GB)."
[ "$LIBRE_GB" -ge $((SISTEMA + DATOS)) ] || echo "AVISO: si los dos discos se llenan, no hay espacio para todo." >&2

# El nombre que conoce la base de sistemas operativos de este Ubuntu
VARIANTE=""
DISPONIBLES="$(osinfo-query os -f short-id 2> /dev/null | awk 'NR > 2 {print $1}')"
for v in rocky9 rocky9.0 rhel9.4 rhel9.2 rhel9.0 centos-stream9 linux2022 linux2020; do
    if grep -qx "$v" <<< "$DISPONIBLES"; then VARIANTE="$v"; break; fi
done
VARIANTE="${VARIANTE:-generic}"

echo "== Creando $NOMBRE: $FQDN, $IP, $VCPU vCPU, $RAM MB, discos de $SISTEMA y $DATOS GB (variante $VARIANTE)"
virt-install \
    --name "$NOMBRE" \
    --memory "$RAM" --vcpus "$VCPU" \
    --cpu host-model --machine pc \
    --os-variant "$VARIANTE" \
    --disk "path=$DISCO_SISTEMA,size=$SISTEMA,format=qcow2,bus=virtio,cache=none,discard=unmap" \
    --disk "path=$DISCO_DATOS,size=$DATOS,format=qcow2,bus=virtio,cache=none,discard=unmap" \
    --network "bridge=$PUENTE,model=virtio" \
    --graphics spice,listen=127.0.0.1 --video qxl --sound none \
    --channel unix,target_type=virtio,name=org.qemu.guest_agent.0 \
    --memballoon virtio \
    --location "$ISO" \
    --initrd-inject "$TMP/ks.cfg" \
    --extra-args "inst.ks=file:/ks.cfg console=tty0 console=ttyS0,115200n8" \
    --noautoconsole --wait -1

virsh autostart "$NOMBRE"
echo "$(date -Is) creada $NOMBRE ($FQDN, $IP)" >> "$DIR/creadas.log"

cat <<FIN

Listo: $NOMBRE instalada y configurada para arrancar con el servidor.
  Consola:   sudo virsh console $NOMBRE        (salir: Ctrl+])
  Entrar:    ssh $ADMIN@$IP
Siga con la guía (docs/infra-docker-rocky.md), dentro de la máquina:
  sudo bash infra/rocky/01-preparar-host.sh <dev|prod> "<servidores NTP>"
FIN
