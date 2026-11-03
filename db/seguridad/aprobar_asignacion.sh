#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Aprobación de una asignación por el DBA (auditoría C2)
#
# Las asignaciones sensibles (administrador del sistema, y mesa de entrada o
# responsable de una Dirección o superior) quedan pendientes hasta que las
# apruebe un administrador distinto de quien las pidió. Con un solo
# administrador, nadie puede aprobar la del segundo: este script lo resuelve
# una vez, desde el servidor. Después, los administradores se aprueban entre
# ellos desde la pantalla.
#
# Se ejecuta en el servidor 192.168.129.73, como postgres, DESPUÉS de pedir la
# asignación en la pantalla de administración (queda "Pendiente de aprobación"):
#
#     sudo -u postgres bash aprobar_asignacion.sh <base>              # lista las pendientes
#     sudo -u postgres bash aprobar_asignacion.sh <base> <id>         # aprueba la indicada
#
# Solo aprueba asignaciones pendientes. Queda registrado en la auditoría de
# administración como APROBACION_ASIGNACION_DBA, con el rol de base que la aprobó.
# =====================================================================
set -euo pipefail

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Uso: sudo -u postgres bash $0 <base> [id de la asignación]" >&2
    exit 1
fi
BASE="$1"

if [ "$(id -un)" != "postgres" ]; then
    echo "Ejecútelo como postgres:  sudo -u postgres bash $0 $*" >&2
    exit 1
fi

if ! psql -X -tA -d "$BASE" -c "SELECT 1 FROM flyway_schema_history WHERE version = '17' AND success" | grep -q 1; then
    echo "La migración V17 todavía no se aplicó en $BASE: arranque la aplicación sobre esa base primero." >&2
    exit 1
fi

if [ $# -eq 1 ]; then
    psql -X -d "$BASE" -c "
        SELECT a.id, u.username AS usuario, a.rol, d.nombre AS ambito, a.vigente_desde, a.vigente_hasta,
               a.acto_administrativo AS acto, c.username AS pedida_por, a.fecha_hora_creacion
          FROM asignaciones a
          JOIN usuarios u ON u.id = a.usuario_id
          JOIN dependencias d ON d.id = a.dependencia_ambito_id
          LEFT JOIN usuarios c ON c.id = a.creada_por_id
         WHERE a.estado_aprobacion = 'PENDIENTE'
         ORDER BY a.fecha_hora_creacion"
    exit 0
fi

ID="$2"
if ! [[ "$ID" =~ ^[0-9]+$ ]]; then
    echo "El id de la asignación debe ser un número." >&2
    exit 1
fi

psql -X -v ON_ERROR_STOP=1 -q -d "$BASE" -v id="$ID" <<'SQL'
BEGIN;
SELECT EXISTS (SELECT 1 FROM asignaciones WHERE id = :id AND estado_aprobacion = 'PENDIENTE') AS hay \gset
\if :hay
    UPDATE asignaciones
       SET estado_aprobacion = 'APROBADA', activa = TRUE, aprobada_por_id = NULL, fecha_hora_aprobacion = now()
     WHERE id = :id;
    -- La auditoría exige un usuario: se registra a quien la pidió, aclarando que la aprobó el DBA
    INSERT INTO auditoria_sistema (usuario_id, accion, entidad, entidad_id, detalle)
    SELECT COALESCE(a.creada_por_id, a.usuario_id), 'APROBACION_ASIGNACION_DBA', 'ASIGNACION', a.id,
           'Aprobada por el DBA (rol ' || current_user || ' en el servidor): ' || u.username || ': ' || a.rol
           || ' en ' || d.nombre || '. Acto: ' || COALESCE(a.acto_administrativo, '')
      FROM asignaciones a JOIN usuarios u ON u.id = a.usuario_id JOIN dependencias d ON d.id = a.dependencia_ambito_id
     WHERE a.id = :id;
    COMMIT;
    \echo 'Asignación aprobada. El usuario la tiene desde su próxima petición.'
\else
    ROLLBACK;
    \echo 'No hay una asignación pendiente con ese id: no se cambió nada.'
\endif
SQL
