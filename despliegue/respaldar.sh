#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Respaldo diario de la base y de los PDF
#
# Lo ejecuta gestion-documental-respaldo.timer, como root. A mano:
#     bash /opt/gestion-documental/respaldar.sh
#
# La base y las huellas se guardan en /var/backups/gestion_documental, 30 días.
#
# Si RESPALDO_DESTINO está definido en /etc/gestion-documental/respaldo
# (usuario@equipo:/carpeta), copia además a ese equipo:
#   - la base y las huellas, que allí se conservan 90 días;
#   - los PDF en la subcarpeta pdf/, solo los que todavía no están.
#
# Los PDF nunca se sobrescriben: cada versión es un archivo nuevo. Por eso la
# copia no reemplaza ni borra lo que ya recibió: un archivo que se dañe o se
# borre en este servidor sigue sano en el otro equipo. Tampoco hace falta volver
# a empaquetarlos cada día, y el espacio crece solo con los PDF nuevos.
#
# Sin RESPALDO_DESTINO no hay otra copia: guarda los PDF en un .tar.gz local,
# 7 días, y avisa. Un fallo del disco perdería la base y sus respaldos a la vez.
#
# Termina con error si algo falla, o si un disco pasa del umbral, para que quede
# en el estado del servicio.
#
# En los servidores Docker (caliope, caliopedev) la base está en otro equipo:
# /etc/gestion-documental/respaldo, que escribe infra/rocky/03-nginx-y-respaldos.sh, define
#     COMO_POSTGRES="env"  y  export PGHOST=... PGUSER=<rol>_respaldo PGSSLMODE=verify-full ...
# y pg_dump y psql se conectan por la red, con SSL y un rol de solo lectura.
# Sin esas líneas se usan las del servidor como postgres, como en 192.168.129.73.
# =====================================================================
set -euo pipefail

BASE="${BASE:-gestion_documental}"
CARPETA_PDF="${CARPETA_PDF:-/var/lib/gestion-documental/pdf}"
CARPETA="${CARPETA:-/var/backups/gestion_documental}"
CONFIG="${CONFIG:-/etc/gestion-documental/respaldo}"
DIAS=30
DIAS_DESTINO=90
DIAS_PDF_LOCAL=7
UMBRAL_DISCO=80
FECHA=$(date +%Y%m%d_%H%M)
PROBLEMAS=0

[ -f "$CONFIG" ] && . "$CONFIG"
# Cómo se llega a pg_dump y psql: en el servidor o dentro del contenedor (revisar_pdf.sh también lo usa)
export COMO_POSTGRES="${COMO_POSTGRES:-sudo -u postgres}"
export BASE

# Avisa si el disco de la ruta pasó del umbral. No frena el respaldo
revisar_disco() {
    local nombre="$1" uso="$2"
    if ! [ "$uso" -ge 0 ] 2>/dev/null; then
        echo "ATENCIÓN: no se pudo leer el espacio del disco de $nombre." >&2
        PROBLEMAS=1
    elif [ "$uso" -ge "$UMBRAL_DISCO" ]; then
        echo "ATENCIÓN: el disco de $nombre está al $uso % (umbral: $UMBRAL_DISCO %)." >&2
        PROBLEMAS=1
    else
        echo "Disco de $nombre: $uso %."
    fi
}

mkdir -p "$CARPETA"
chmod 750 "$CARPETA"

# Por la salida estándar: sirve igual con pg_dump del servidor o del contenedor.
# El archivo lo escribe root, en esta carpeta; si pg_dump falla, pipefail detiene el respaldo
# Se escribe con otro nombre y toma el definitivo solo si terminó: un respaldo cortado no queda como bueno
DUMP="$CARPETA/base_$FECHA.dump"
trap 'rm -f "$DUMP.parcial"' EXIT
(cd / && $COMO_POSTGRES pg_dump -Fc -d "$BASE") > "$DUMP.parcial"
chmod 640 "$DUMP.parcial"
mv "$DUMP.parcial" "$DUMP"
# Un respaldo que no se puede leer no es un respaldo
(cd / && $COMO_POSTGRES pg_restore -l) < "$DUMP" > /dev/null

# Última huella de las bitácoras: viaja con el respaldo como anclaje externo
HUELLAS="$CARPETA/huellas_$FECHA.txt"
(cd / && $COMO_POSTGRES psql -X -tA -d "$BASE" -c "SELECT tabla, huella FROM bitacora_cadena ORDER BY tabla") > "$HUELLAS"

find "$CARPETA" -type f \( -name 'base_*.dump' -o -name 'huellas_*.txt' \) -mtime +"$DIAS" -delete
find "$CARPETA" -type f -name 'pdf_*.tar.gz' -mtime +"$DIAS_PDF_LOCAL" -delete

if [ -n "${RESPALDO_DESTINO:-}" ]; then
    EQUIPO="${RESPALDO_DESTINO%%:*}"
    CARPETA_DESTINO="${RESPALDO_DESTINO#*:}"

    rsync -a "$DUMP" "$HUELLAS" "$RESPALDO_DESTINO/"
    rsync -a --ignore-existing "$CARPETA_PDF/" "$RESPALDO_DESTINO/pdf/"
    echo "Copiado a $RESPALDO_DESTINO"

    # -maxdepth 1: la limpieza nunca entra en pdf/
    ssh -o BatchMode=yes "$EQUIPO" "find '$CARPETA_DESTINO' -maxdepth 1 -type f \\( -name 'base_*.dump' -o -name 'huellas_*.txt' -o -name 'pdf_*.tar.gz' \\) -mtime +$DIAS_DESTINO -delete"

    # Lo que falte en el destino se detecta aquí, no el día que haga falta restaurar
    EN_ORIGEN=$(find "$CARPETA_PDF" -type f | wc -l)
    EN_DESTINO=$(ssh -o BatchMode=yes "$EQUIPO" "find '$CARPETA_DESTINO/pdf' -type f | wc -l")
    echo "PDF: $EN_ORIGEN en este servidor, $EN_DESTINO en $EQUIPO."
    if [ "$EN_DESTINO" -lt "$EN_ORIGEN" ]; then
        echo "ATENCIÓN: faltan PDF en $EQUIPO." >&2
        PROBLEMAS=1
    fi

    revisar_disco "$EQUIPO" "$(ssh -o BatchMode=yes "$EQUIPO" "df -P '$CARPETA_DESTINO'" | awk 'NR==2 {print $5+0}')"
    ls -lh "$DUMP" "$HUELLAS"
else
    PDF="$CARPETA/pdf_$FECHA.tar.gz"
    tar -czf "$PDF" -C "$(dirname "$CARPETA_PDF")" "$(basename "$CARPETA_PDF")"
    tar -tzf "$PDF" > /dev/null
    echo "AVISO: RESPALDO_DESTINO no está definido: el respaldo quedó solo en este servidor." >&2
    ls -lh "$DUMP" "$PDF" "$HUELLAS"
fi

revisar_disco "este servidor" "$(df -P "$CARPETA" | awk 'NR==2 {print $5+0}')"

# Al final, para que un PDF faltante no impida guardar ni copiar el respaldo.
# Si falla, el servicio queda en error: el respaldo se hizo, pero hay algo que revisar.
if ! bash "$(dirname "$0")/revisar_pdf.sh"; then
    echo "ATENCIÓN: el respaldo se guardó, pero el control de PDF encontró problemas (detalle arriba)." >&2
    PROBLEMAS=1
fi

exit "$PROBLEMAS"
