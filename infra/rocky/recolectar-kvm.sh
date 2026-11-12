#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Recolecta la configuración del servidor de virtualización
#
#     sudo bash recolectar-kvm.sh [máquina de referencia]
#     ej.: sudo bash recolectar-kvm.sh srv-postgres-dev
#
# Solo lee: no crea, modifica ni detiene nada. Escribe un archivo de texto en la
# carpeta actual con el sistema, qemu-kvm/libvirt, almacenamiento, red y, si se
# indica, la definición completa de una máquina existente para tomarla de modelo.
#
# Las contraseñas de consola (VNC/SPICE) y los identificadores de secretos se
# reemplazan por [OCULTO]. Igual conviene leer el archivo antes de compartirlo.
# =====================================================================
set -uo pipefail

[ "$(id -u)" = "0" ] || { echo "Ejecútelo con sudo o como root." >&2; exit 1; }
VM="${1:-}"
if [ -n "$VM" ] && ! virsh dominfo "$VM" > /dev/null 2>&1; then
    echo "No existe la máquina '$VM'. Estas son las disponibles:" >&2
    virsh list --all --name >&2
    exit 1
fi
SALIDA="$PWD/kvm-info-$(hostname -s)-$(date +%Y%m%d_%H%M).txt"

seccion() { printf '\n\n########## %s\n' "$*"; }
# Muestra el comando y su salida; un comando que falta o falla no corta la recolección
ver() { printf '\n$ %s\n' "$*"; bash -c "$*" 2>&1 || true; }

# Contraseñas y secretos que pueden aparecer en las definiciones de libvirt
ocultar() {
    sed -E \
        -e "s/((passwd|password|passphrase|psk)=)('[^']*'|\"[^\"]*\")/\1'[OCULTO]'/Ig" \
        -e "s/(<secret[^>]*(uuid|usage)=)('[^']*'|\"[^\"]*\")/\1'[OCULTO]'/g" \
        -e "s/(<(passwd|password)>)[^<]*/\1[OCULTO]/Ig"
}

{
    echo "Recolectado el $(date -Is) en $(hostname -f 2>/dev/null || hostname)"

    seccion "SISTEMA"
    ver "cat /etc/os-release | grep -E '^(NAME|VERSION|ID)='"
    ver "uname -r"
    ver "lscpu | grep -E '^(Model name|Socket|Core|Thread|CPU\(s\)|Virtualization|NUMA node\(s\))'"
    ver "free -h"
    ver "uptime"
    ver "timedatectl | grep -E 'Time zone|NTP|synchronized'"

    seccion "QEMU-KVM Y LIBVIRT"
    ver "rpm -q qemu-kvm libvirt libvirt-daemon-kvm virt-install edk2-ovmf swtpm qemu-guest-agent 2> /dev/null || dpkg -l | grep -E 'qemu-system|libvirt-daemon|virtinst|ovmf'"
    ver "virsh version --daemon"
    ver "virt-install --version"
    ver "/usr/libexec/qemu-kvm --version 2> /dev/null || qemu-system-x86_64 --version"
    ver "virt-host-validate qemu"
    ver "virsh domcapabilities | grep -E '<machine>|<loader|<value>(pc-|q35|virtio|scsi|sata|ide|efi|bios)' | sort -u | head -40"
    ver "ls -1 /usr/share/edk2/ovmf/ /usr/share/OVMF/ 2> /dev/null"
    ver "systemctl is-active libvirtd virtqemud 2> /dev/null"
    ver "grep -vE '^\s*(#|$)' /etc/libvirt/qemu.conf"

    seccion "MÁQUINAS VIRTUALES"
    ver "virsh list --all"
    ver "virsh list --all --autostart --name"
    # Cómo están armadas todas: firmware, tipo de máquina, CPU, discos, red y agente
    for d in $(virsh list --all --name); do
        printf '\n--- %s\n' "$d"
        virsh dumpxml "$d" 2> /dev/null | grep -oE \
            "<memory[^>]*>[0-9]+|<vcpu[^>]*>[0-9]+|machine='[^']+'|<loader[^>]*>|<cpu mode='[^']+'|<driver name='qemu' type='[^']+'[^/]*|<target dev='[^']+' bus='[^']+'|<controller type='scsi'[^>]*model='[^']+'|<model type='[^']+'|<source (bridge|network)='[^']+'|<graphics type='[^']+'|<channel type='unix'|org.qemu.guest_agent.0|<console type='[^']+'" \
            | sort | uniq -c
    done

    seccion "ALMACENAMIENTO"
    ver "virsh pool-list --all --details"
    for p in $(virsh pool-list --all --name); do
        ver "virsh pool-dumpxml '$p' | grep -E '<(type|path|name|format|device|capacity|available)'"
        ver "virsh vol-list '$p' --details"
    done
    ver "df -hT -x tmpfs -x devtmpfs"
    ver "lsblk -o NAME,SIZE,TYPE,FSTYPE,MOUNTPOINT"
    ver "vgs; lvs"
    ver "find /var/lib/libvirt /home /srv /opt -maxdepth 4 -iname '*rocky*.iso' -exec ls -lh {} + 2> /dev/null"

    seccion "RED DE LIBVIRT"
    ver "virsh net-list --all"
    for n in $(virsh net-list --all --name); do
        ver "virsh net-dumpxml '$n'"
    done

    seccion "RED DEL SERVIDOR"
    ver "ip -br link"
    ver "ip -br addr"
    ver "ip route"
    ver "bridge link"
    ver "bridge vlan show"
    ver "nmcli -f NAME,TYPE,DEVICE,AUTOCONNECT connection show"
    # Puentes, VLAN y enlaces: solo los datos de red (nmcli sin -s no muestra secretos)
    nmcli -t -f NAME,TYPE connection show 2> /dev/null | grep -E ':(bridge|vlan|bond|802-3-ethernet)$' | cut -d: -f1 \
    | while IFS= read -r c; do
        ver"nmcli -f connection.id,connection.interface-name,connection.master,connection.slave-type,ipv4.method,ipv4.addresses,ipv4.gateway,ipv4.dns,ipv4.dns-search,bridge.stp,vlan.id,vlan.parent,bond.options connection show '$c'"
    done
    ver "cat /etc/resolv.conf"
    ver "sysctl net.ipv4.ip_forward net.bridge.bridge-nf-call-iptables 2> /dev/null"
    ver "firewall-cmd --get-active-zones"
    ver "firewall-cmd --list-all-zones | grep -A12 -E '^(libvirt|public|trusted) '"

    if [ -n "$VM" ]; then
        seccion "MÁQUINA DE REFERENCIA: $VM"
        ver "virsh dominfo '$VM'"
        ver "virsh domblklist '$VM' --details"
        ver "virsh domiflist '$VM'"
        ver "virsh domifaddr '$VM' --source agent 2> /dev/null || virsh domifaddr '$VM' --source lease"
        for disco in $(virsh domblklist "$VM" --details | awk '$2=="disk" {print $4}'); do
            ver "qemu-img info --force-share '$disco'"
        done
        ver "virsh dumpxml '$VM'"
    fi
} 2>&1 | ocultar > "$SALIDA"

chmod 600 "$SALIDA"
echo "Listo: $SALIDA ($(wc -l < "$SALIDA") líneas). Revíselo antes de compartirlo."
