#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Control de los PDF contra la base
#
# Contar archivos no alcanza: un respaldo puede tener 26 PDF y aun así no
# tener el que un expediente necesita. Este control compara lo que la base
# pide con lo que hay en disco. Es de SOLO LECTURA: no borra ni cambia nada.
#
#     bash /opt/gestion-documental/revisar_pdf.sh
#     bash /opt/gestion-documental/revisar_pdf.sh --sobrantes     (además los nombra)
#
# Se ejecuta como root, igual que el respaldo, que lo corre en cada pasada.
#
# Informa:
#   FALTA     la base pide el archivo y no está en disco
#   ANTERIOR  falta el archivo de una versión ya reemplazada (solo aviso: el vigente es otro)
#   ALTERADO  el archivo no coincide con la huella SHA-256 guardada al subirlo
#   sobrantes archivos que la base no menciona (solo aviso, nunca se borran)
#
# Termina con 0 si todo está bien, 1 si falta o cambió algún archivo, y 2 si no
# pudo hacer el control.
# =====================================================================
set -uo pipefail

BASE="gestion_documental"
CARPETA_PDF="${CARPETA_PDF:-/var/lib/gestion-documental/pdf}"
# Con la base en Docker, /etc/gestion-documental/respaldo define COMO_POSTGRES (ver respaldar.sh)
if [ -z "${COMO_POSTGRES:-}" ] && [ -f /etc/gestion-documental/respaldo ]; then
    COMO_POSTGRES="$(. /etc/gestion-documental/respaldo; printf '%s' "${COMO_POSTGRES:-}")"
fi
COMO_POSTGRES="${COMO_POSTGRES:-sudo -u postgres}"
NOMBRAR_SOBRANTES=0
[ "${1:-}" = "--sobrantes" ] && NOMBRAR_SOBRANTES=1

if [ ! -d "$CARPETA_PDF" ] || [ ! -r "$CARPETA_PDF" ]; then
    echo "No se puede leer $CARPETA_PDF (¿se ejecutó como root?)." >&2
    exit 2
fi

# Una fila por referencia: expediente, origen, ruta y huella ('-' si no tiene)
CONSULTA="
SELECT d.codigo_cuti, 'actuación ' || a.tipo_actuacion, a.ruta_copia_sellada_pdf, '-'
  FROM actuaciones_documento a JOIN documentos d ON d.id = a.documento_id
 WHERE a.ruta_copia_sellada_pdf <> ''
UNION ALL
SELECT d.codigo_cuti,
       'adjunto ' || COALESCE(x.tipo_adjunto, 'sin tipo') || CASE WHEN x.vigente THEN '' ELSE ' (versión anterior)' END,
       x.ruta_almacenamiento, COALESCE(NULLIF(x.hash_sha256, ''), '-')
  FROM adjuntos_documento x JOIN documentos d ON d.id = x.documento_id
 ORDER BY 1, 3"

# Se lee primero: si la consulta falla, no se confunde con "la base no pide nada"
if ! FILAS=$(cd / && $COMO_POSTGRES psql -X -tA -F $'\t' -v ON_ERROR_STOP=1 -d "$BASE" -c "$CONSULTA"); then
    echo "No se pudo consultar la base $BASE." >&2
    exit 2
fi

declare -A PEDIDOS=()
REFERENCIAS=0
FALTAN=0
ANTERIORES=0
ALTERADOS=0

while IFS=$'\t' read -r CUTI ORIGEN RUTA HUELLA; do
    [ -n "$CUTI" ] || continue
    REFERENCIAS=$((REFERENCIAS + 1))
    # La base puede tener rutas de Windows: se aceptan los dos separadores, igual que ArchivoService
    NOMBRE="${RUTA##*[/\\]}"
    PEDIDOS["$NOMBRE"]=1
    ARCHIVO="$CARPETA_PDF/$NOMBRE"
    if [ ! -f "$ARCHIVO" ]; then
        case "$ORIGEN" in
            # Ya no es el documento vigente: se informa, pero no deja el control en error para siempre
            *"(versión anterior)"*)
                echo "ANTERIOR  $CUTI  $ORIGEN  $NOMBRE"
                ANTERIORES=$((ANTERIORES + 1))
                ;;
            *)
                echo "FALTA     $CUTI  $ORIGEN  $NOMBRE"
                FALTAN=$((FALTAN + 1))
                ;;
        esac
    elif [ "$HUELLA" != "-" ]; then
        # Por la entrada estándar: con un nombre que tenga "\", sha256sum antepone "\" a la huella
        ACTUAL=$(sha256sum < "$ARCHIVO" | cut -d' ' -f1)
        if [ "$ACTUAL" != "$HUELLA" ]; then
            echo "ALTERADO  $CUTI  $ORIGEN  $NOMBRE"
            ALTERADOS=$((ALTERADOS + 1))
        fi
    fi
done <<< "$FILAS"

EN_DISCO=0
SOBRANTES=0
for ARCHIVO in "$CARPETA_PDF"/*; do
    [ -f "$ARCHIVO" ] || continue
    EN_DISCO=$((EN_DISCO + 1))
    NOMBRE="${ARCHIVO##*/}"
    if [ -z "${PEDIDOS[$NOMBRE]:-}" ]; then
        SOBRANTES=$((SOBRANTES + 1))
        [ "$NOMBRAR_SOBRANTES" -eq 1 ] && echo "sobrante  $NOMBRE"
    fi
done

echo "Control de PDF: $REFERENCIAS referencias en la base, $EN_DISCO archivos en disco; faltan $FALTAN, versiones anteriores sin archivo $ANTERIORES, alterados $ALTERADOS, sobrantes $SOBRANTES."

[ "$FALTAN" -eq 0 ] && [ "$ALTERADOS" -eq 0 ]
