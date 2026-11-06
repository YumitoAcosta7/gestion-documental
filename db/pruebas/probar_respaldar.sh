#!/usr/bin/env bash
# Prueba de despliegue/respaldar.sh con comandos simulados: no toca servidores ni bases.
# Uso: bash db/pruebas/probar_respaldar.sh
set -u
RAIZ=$(mktemp -d)
SCRIPT="$(cd "$(dirname "$0")/../.." && pwd)/despliegue/respaldar.sh"
BIN=$RAIZ/bin; APP=$RAIZ/app; LOCAL=$RAIZ/local; PDF=$RAIZ/pdf; DEST=$RAIZ/dest
mkdir -p "$BIN" "$APP" "$PDF" "$DEST"
tr -d '\r' < "$SCRIPT" > "$APP/respaldar.sh"
printf '#!/usr/bin/env bash\n[ "${REVISAR_FALLA:-0}" = 1 ] && exit 1\nexit 0\n' > "$APP/revisar_pdf.sh"

cat > "$BIN/sudo" <<'E'
#!/usr/bin/env bash
shift 2; exec "$@"
E
cat > "$BIN/pg_dump" <<'E'
#!/usr/bin/env bash
while [ $# -gt 0 ]; do [ "$1" = -f ] && echo dump > "$2"; shift; done
E
printf '#!/usr/bin/env bash\necho lista\n' > "$BIN/pg_restore"
printf '#!/usr/bin/env bash\necho "tabla huella"\n' > "$BIN/psql"
printf '#!/usr/bin/env bash\nexit 0\n' > "$BIN/chown"
cat > "$BIN/rsync" <<'E'
#!/usr/bin/env bash
echo "rsync $*" >> "$RSYNC_LOG"
[ "${RSYNC_FALLA:-0}" = 1 ] && exit 23
ignorar=0; args=()
for a in "$@"; do case "$a" in -a) ;; --ignore-existing) ignorar=1 ;; *) args+=("$a") ;; esac; done
destino="${args[-1]#*:}"; unset 'args[-1]'
mkdir -p "$destino"
for o in "${args[@]}"; do
  if [ -d "$o" ]; then
    for f in "$o"*; do [ -e "$f" ] || continue; d="$destino$(basename "$f")"; if [ "$ignorar" = 1 ] && [ -e "$d" ]; then continue; fi; cp -p "$f" "$d"; done
  else cp -p "$o" "$destino"; fi
done
E
cat > "$BIN/ssh" <<'E'
#!/usr/bin/env bash
shift 3
eval "$1"
E
cat > "$BIN/df" <<'E'
#!/usr/bin/env bash
echo "Filesystem 1024-blocks Used Available Capacity Mounted"
echo "/dev/x 100 50 50 ${USO_DISCO:-40}% /"
E
chmod +x "$BIN"/*

export PATH="$BIN:$PATH" CARPETA="$LOCAL" CARPETA_PDF="$PDF" CONFIG="$RAIZ/conf" RSYNC_LOG="$RAIZ/rsync.log" LC_ALL=C.UTF-8
: > "$RSYNC_LOG"
OK=0; MAL=0
comprobar() { if eval "$2"; then OK=$((OK+1)); echo "  ok   $1"; else MAL=$((MAL+1)); echo "  MAL  $1"; fi; }
correr() { bash "$APP/respaldar.sh" > "$RAIZ/salida" 2>&1; CODIGO=$?; }

echo "respaldo@equipo:$DEST" | sed 's/^/RESPALDO_DESTINO=/' > "$RAIZ/conf"
echo uno > "$PDF/a.pdf"; echo dos > "$PDF/b.pdf"

echo "1. Primer respaldo con destino"
correr
comprobar "termina bien" '[ $CODIGO = 0 ]'
comprobar "base en el servidor" 'ls "$LOCAL"/base_*.dump >/dev/null 2>&1'
comprobar "sin .tar.gz local" '! ls "$LOCAL"/pdf_*.tar.gz >/dev/null 2>&1'
comprobar "base y huellas en destino" 'ls "$DEST"/base_*.dump "$DEST"/huellas_*.txt >/dev/null 2>&1'
comprobar "PDF en destino" '[ -f "$DEST/pdf/a.pdf" ] && [ -f "$DEST/pdf/b.pdf" ]'
comprobar "copia los PDF con --ignore-existing" 'grep -q -- "--ignore-existing .*pdf/" "$RSYNC_LOG"'
comprobar "nunca usa --delete" '! grep -q -- "--delete" "$RSYNC_LOG"'
comprobar "informa el conteo" 'grep -q "PDF: 2 en este servidor, 2 en" "$RAIZ/salida"'

echo "2. Un PDF dañado y otro borrado en el servidor no afectan la copia"
echo ALTERADO > "$PDF/a.pdf"; rm "$PDF/b.pdf"; echo tres > "$PDF/c.pdf"
correr
comprobar "termina bien" '[ $CODIGO = 0 ]'
comprobar "el alterado sigue sano en destino" '[ "$(cat "$DEST/pdf/a.pdf")" = uno ]'
comprobar "el borrado sigue en destino" '[ "$(cat "$DEST/pdf/b.pdf")" = dos ]'
comprobar "el nuevo llegó" '[ "$(cat "$DEST/pdf/c.pdf")" = tres ]'

echo "3. Limpieza"
touch -d '40 days ago' "$LOCAL/base_viejo.dump" "$LOCAL/huellas_viejo.txt"
touch -d '10 days ago' "$LOCAL/base_reciente.dump" "$LOCAL/pdf_viejo.tar.gz"
touch -d '100 days ago' "$DEST/base_viejo.dump" "$DEST/huellas_viejo.txt" "$DEST/pdf_viejo.tar.gz" "$DEST/pdf/antiguo.pdf"
touch -d '60 days ago' "$DEST/base_reciente.dump"
correr
comprobar "borra la base local de más de 30 días" '[ ! -f "$LOCAL/base_viejo.dump" ] && [ ! -f "$LOCAL/huellas_viejo.txt" ]'
comprobar "conserva la base local reciente" '[ -f "$LOCAL/base_reciente.dump" ]'
comprobar "borra el .tar.gz local de más de 7 días" '[ ! -f "$LOCAL/pdf_viejo.tar.gz" ]'
comprobar "borra en destino lo de más de 90 días" '[ ! -f "$DEST/base_viejo.dump" ] && [ ! -f "$DEST/huellas_viejo.txt" ] && [ ! -f "$DEST/pdf_viejo.tar.gz" ]'
comprobar "conserva en destino lo reciente" '[ -f "$DEST/base_reciente.dump" ]'
comprobar "nunca borra un PDF del destino" '[ -f "$DEST/pdf/antiguo.pdf" ]'

echo "4. Avisos"
USO_DISCO=85 correr
comprobar "disco al 85 %: termina con error" '[ $CODIGO = 1 ]'
comprobar "disco al 85 %: lo dice" 'grep -q "disco de este servidor está al 85" "$RAIZ/salida"'
comprobar "disco al 85 %: igual copió" 'grep -q "Copiado a" "$RAIZ/salida"'
REVISAR_FALLA=1 correr
comprobar "control de PDF con problemas: termina con error" '[ $CODIGO = 1 ] && grep -q "control de PDF" "$RAIZ/salida"'
mv "$DEST/pdf/c.pdf" "$RAIZ/c.pdf"; rm -f "$DEST/pdf/a.pdf" "$DEST/pdf/b.pdf" "$DEST/pdf/antiguo.pdf"
RSYNC_FALLA=1 correr
comprobar "falla rsync: termina con error" '[ $CODIGO != 0 ]'
cat > "$BIN/rsync.bak" < "$BIN/rsync"; printf '#!/usr/bin/env bash\necho "rsync $*" >> "$RSYNC_LOG"\nexit 0\n' > "$BIN/rsync"
correr
comprobar "faltan PDF en destino: lo detecta" '[ $CODIGO = 1 ] && grep -q "faltan PDF" "$RAIZ/salida"'
cat "$BIN/rsync.bak" > "$BIN/rsync"

echo "5. Sin destino"
: > "$RAIZ/conf"
correr
comprobar "termina bien" '[ $CODIGO = 0 ]'
comprobar "guarda los PDF en .tar.gz local" 'ls "$LOCAL"/pdf_*.tar.gz >/dev/null 2>&1'
comprobar "avisa" 'grep -q "RESPALDO_DESTINO no está definido" "$RAIZ/salida"'

echo; echo "Resultado: $OK bien, $MAL mal"
rm -rf "$RAIZ"
[ "$MAL" = 0 ]
