#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Respaldo diario de la base y de los PDF
#
# Lo ejecuta gestion-documental-respaldo.timer, como root. A mano:
#     bash /opt/gestion-documental/respaldar.sh
#
# Guarda en /var/backups/gestion_documental y conserva 30 días. Si
# RESPALDO_DESTINO está definido en /etc/gestion-documental/respaldo, copia
# además a ese equipo con rsync (usuario@equipo:/carpeta). Sin esa copia, un
# fallo del disco del servidor perdería la base y sus respaldos a la vez.
# Termina con error si algo falla, para que quede en el estado del servicio.
# =====================================================================
set -euo pipefail

BASE="gestion_documental"
CARPETA_PDF="/var/lib/gestion-documental/pdf"
CARPETA="/var/backups/gestion_documental"
DIAS=30
FECHA=$(date +%Y%m%d_%H%M)

[ -f /etc/gestion-documental/respaldo ] && . /etc/gestion-documental/respaldo

mkdir -p "$CARPETA"
chown postgres "$CARPETA"
chmod 750 "$CARPETA"

DUMP="$CARPETA/base_$FECHA.dump"
sudo -u postgres pg_dump -Fc -d "$BASE" -f "$DUMP"
# Un respaldo que no se puede leer no es un respaldo
sudo -u postgres pg_restore -l "$DUMP" > /dev/null

PDF="$CARPETA/pdf_$FECHA.tar.gz"
tar -czf "$PDF" -C "$(dirname "$CARPETA_PDF")" "$(basename "$CARPETA_PDF")"
tar -tzf "$PDF" > /dev/null

# Última huella de las bitácoras: viaja con el respaldo como anclaje externo
HUELLAS="$CARPETA/huellas_$FECHA.txt"
sudo -u postgres psql -X -tA -d "$BASE" -c "SELECT tabla, huella FROM bitacora_cadena ORDER BY tabla" > "$HUELLAS"

find "$CARPETA" -type f \( -name 'base_*.dump' -o -name 'pdf_*.tar.gz' -o -name 'huellas_*.txt' \) -mtime +"$DIAS" -delete

if [ -n "${RESPALDO_DESTINO:-}" ]; then
    rsync -a "$DUMP" "$PDF" "$HUELLAS" "$RESPALDO_DESTINO/"
    echo "Copiado a $RESPALDO_DESTINO"
else
    echo "AVISO: RESPALDO_DESTINO no está definido: el respaldo quedó solo en este servidor." >&2
fi

ls -lh "$DUMP" "$PDF" "$HUELLAS"

# Al final, para que un PDF faltante no impida guardar ni copiar el respaldo.
# Si falla, el servicio queda en error: el respaldo se hizo, pero hay algo que revisar.
if ! bash "$(dirname "$0")/revisar_pdf.sh"; then
    echo "ATENCIÓN: el respaldo se guardó, pero el control de PDF encontró problemas (detalle arriba)." >&2
    exit 1
fi
