# =====================================================================
# Particionado para el instalador de Rocky Linux (kickstart). Ejemplo para dos discos:
#   sda (60 GB): sistema           sdb (100 GB): datos (base, PDF y respaldos)
#
# Con dos discos, reinstalar el sistema no toca los datos, y el disco de datos se
# puede agrandar o mover solo. Con un solo disco: usar solo vg_sistema y crear
# allí los volúmenes de datos. Los tamaños son un punto de partida: ajustarlos a
# los discos reales y dejar siempre espacio libre en cada grupo (ampliaciones y snapshots).
# =====================================================================
ignoredisk --only-use=sda,sdb
clearpart --all --initlabel --drives=sda,sdb
zerombr

# Arranque (UEFI). En BIOS, quitar la línea de /boot/efi
part /boot/efi --fstype=efi  --size=600  --ondisk=sda
part /boot     --fstype=xfs  --size=1024 --ondisk=sda --fsoptions="nodev,nosuid,noexec"

# --- Sistema: 60 GB, unos 47 GB asignados y el resto libre
part pv.sistema --size=1 --grow --ondisk=sda
volgroup vg_sistema pv.sistema
logvol /               --vgname=vg_sistema --name=lv_raiz      --size=12288 --fstype=xfs
logvol /var            --vgname=vg_sistema --name=lv_var       --size=6144  --fstype=xfs --fsoptions="nodev"
logvol /var/log        --vgname=vg_sistema --name=lv_log       --size=4096  --fstype=xfs --fsoptions="nodev,nosuid,noexec"
logvol /var/log/audit  --vgname=vg_sistema --name=lv_auditoria --size=2048  --fstype=xfs --fsoptions="nodev,nosuid,noexec"
logvol /var/tmp        --vgname=vg_sistema --name=lv_vartmp    --size=1024  --fstype=xfs --fsoptions="nodev,nosuid,noexec"
logvol /tmp            --vgname=vg_sistema --name=lv_tmp       --size=2048  --fstype=xfs --fsoptions="nodev,nosuid,noexec"
logvol /home           --vgname=vg_sistema --name=lv_home      --size=2048  --fstype=xfs --fsoptions="nodev,nosuid"
logvol /opt            --vgname=vg_sistema --name=lv_opt       --size=4096  --fstype=xfs --fsoptions="nodev"
# Imágenes y capas de Docker: separado para que una imagen grande no llene /var
logvol /var/lib/docker --vgname=vg_sistema --name=lv_docker    --size=12288 --fstype=xfs
logvol swap            --vgname=vg_sistema --name=lv_swap      --size=4096

# --- Datos: 100 GB, unos 75 GB asignados y el resto libre
# Base y PDF en volúmenes distintos: si los PDF llenan el suyo, la base sigue escribiendo
part pv.datos --size=1 --grow --ondisk=sdb
volgroup vg_datos pv.datos
logvol /var/lib/gestion-documental/postgres --vgname=vg_datos --name=lv_postgres  --size=20480 --fstype=xfs --fsoptions="noatime,nodev,nosuid,noexec"
logvol /var/lib/gestion-documental/pdf      --vgname=vg_datos --name=lv_pdf       --size=40960 --fstype=xfs --fsoptions="noatime,nodev,nosuid,noexec"
logvol /var/backups                         --vgname=vg_datos --name=lv_respaldos --size=15360 --fstype=xfs --fsoptions="noatime,nodev,nosuid,noexec"
