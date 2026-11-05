#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Primer administrador de una base limpia (docs/plan-base-limpia.md)
#
# Una base limpia no tiene usuarios, y las asignaciones las aprueba otro administrador:
# el primero solo se puede crear desde el servidor. Queda en la auditoría. La contraseña
# temporal se pide por teclado, no se muestra y hay que cambiarla al entrar.
# Se niega si la base ya tiene usuarios.
#
#     sudo -u postgres bash crear_primer_admin.sh <base> <usuario> "<nombre completo>" <sigla de su dependencia> "<acto administrativo>"
# =====================================================================
set -euo pipefail

if [ $# -ne 5 ]; then
    echo "Uso: sudo -u postgres bash $0 <base> <usuario> \"<nombre completo>\" <sigla> \"<acto administrativo>\"" >&2
    exit 1
fi
BASE="$1"; USUARIO="$2"; NOMBRE="$3"; SIGLA="$4"; ACTO="$5"

if [ "$(id -un)" != "postgres" ]; then
    echo "Ejecútelo como postgres." >&2
    exit 1
fi
if [ ${#ACTO} -lt 5 ] || [ ${#ACTO} -gt 100 ]; then
    echo "El acto administrativo debe tener entre 5 y 100 caracteres." >&2
    exit 1
fi
USUARIOS=$(psql -X -tA -d "$BASE" -c "SELECT count(*) FROM usuarios")
if [ "$USUARIOS" != "0" ]; then
    echo "La base $BASE ya tiene $USUARIOS usuarios: no se modificó nada." >&2
    exit 1
fi

read -r -s -p "Contraseña TEMPORAL para $USUARIO (al menos 10 caracteres): " CLAVE; echo
read -r -s -p "Repítala: " CLAVE2; echo
if [ ${#CLAVE} -lt 10 ] || [ "$CLAVE" != "$CLAVE2" ]; then
    echo "Las contraseñas no coinciden o son muy cortas. No se creó nada." >&2
    exit 1
fi

# Los valores viajan como variables de psql, nunca armados dentro del SQL
psql -X -v ON_ERROR_STOP=1 -q -d "$BASE" -v clave="$CLAVE" -v usuario="$USUARIO" -v nombre="$NOMBRE" -v sigla="$SIGLA" -v acto="$ACTO" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pgcrypto;
BEGIN;
INSERT INTO usuarios (nombre_completo, username, password_hash, dependencia_id, rol, activo, debe_cambiar_contrasena)
SELECT :'nombre', :'usuario', crypt(:'clave', gen_salt('bf', 10)), d.id, 'JEFE_UNIDAD', TRUE, TRUE
  FROM dependencias d WHERE d.sigla = :'sigla';

INSERT INTO asignaciones (usuario_id, rol, dependencia_ambito_id, incluye_subordinadas, acto_administrativo, estado_aprobacion, activa, fecha_hora_aprobacion)
SELECT u.id, 'ADMIN_SISTEMA', d.id, TRUE, :'acto', 'APROBADA', TRUE, now()
  FROM usuarios u, dependencias d WHERE u.username = :'usuario' AND d.sigla = 'INT';

INSERT INTO auditoria_sistema (usuario_id, accion, entidad, entidad_id, detalle)
SELECT a.usuario_id, 'ALTA_PRIMER_ADMIN_DBA', 'ASIGNACION', a.id,
       'Primer administrador, creado por el DBA (rol ' || current_user || ' en el servidor): ' || :'usuario' || '. Acto: ' || :'acto'
  FROM asignaciones a JOIN usuarios u ON u.id = a.usuario_id WHERE u.username = :'usuario';

DO $$
BEGIN
    IF (SELECT count(*) FROM usuarios) <> 1 OR (SELECT count(*) FROM asignaciones) <> 1 THEN
        RAISE EXCEPTION 'No se pudo crear: revise la sigla de la dependencia.';
    END IF;
END
$$;
COMMIT;
SQL
unset CLAVE CLAVE2

psql -X -d "$BASE" -c "SELECT u.username, u.debe_cambiar_contrasena, a.rol, a.estado_aprobacion, a.acto_administrativo FROM usuarios u JOIN asignaciones a ON a.usuario_id = u.id;"
echo "Listo. Esa persona entra, cambia la contraseña y pide ADMIN_SISTEMA para el segundo administrador (aprobar_asignacion.sh)."
