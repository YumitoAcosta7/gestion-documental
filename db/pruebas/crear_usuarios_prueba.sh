#!/usr/bin/env bash
# =====================================================================
# Gestión Documental - Usuarios de prueba en la base de PRUEBAS
#
# Crea (o deja como están, si ya existen) tres usuarios con sus asignaciones:
#   - prueba_mesa_tic       Mesa de entrada de la DTIC
#   - prueba_mesa_dgaf      Mesa de entrada de la DGAF (paso obligado entre TIC y Hacienda)
#   - prueba_resp_hacienda  Responsable de la Dirección de Hacienda
# La contraseña ingresada se usa solo para los usuarios que se crean ahora.
#
# Se ejecuta en el servidor 192.168.129.73, como postgres:
#
#     cd /tmp && sudo -u postgres bash crear_usuarios_prueba.sh
#
# Solo toca la base gestion_documental_pruebas. La contraseña de prueba se pide
# por teclado y no queda guardada en ningún archivo.
# =====================================================================
set -euo pipefail

BASE_PRUEBAS="gestion_documental_pruebas"

psql_postgres() { psql -X -v ON_ERROR_STOP=1 -q "$@"; }

if [ "$(id -un)" != "postgres" ]; then
    echo "Ejecútelo como postgres:  sudo -u postgres bash $0" >&2
    exit 1
fi
if ! psql_postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$BASE_PRUEBAS'" | grep -q 1; then
    echo "No existe la base '$BASE_PRUEBAS'. Créela primero con crear_base_pruebas.sh." >&2
    exit 1
fi

read -r -s -p "Contraseña de PRUEBA para los usuarios que se creen ahora (mínimo 8 caracteres, no use una real): " CLAVE; echo
read -r -s -p "Repita la contraseña: " CLAVE_2; echo
if [ "$CLAVE" != "$CLAVE_2" ]; then
    echo "Las contraseñas no coinciden. No se creó nada." >&2
    exit 1
fi
if [ ${#CLAVE} -lt 8 ]; then
    echo "La contraseña debe tener al menos 8 caracteres. No se creó nada." >&2
    exit 1
fi

# La contraseña viaja como variable de psql (:'clave'). BCrypt con pgcrypto (prefijo 2a, que Spring Security acepta)
psql_postgres -d "$BASE_PRUEBAS" -v clave="$CLAVE" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO usuarios (nombre_completo, username, password_hash, email, dependencia_id, rol, activo)
SELECT 'Prueba Mesa de Entrada TIC', 'prueba_mesa_tic', crypt(:'clave', gen_salt('bf', 10)), NULL, d.id, 'SECRETARIA_ADMIN', TRUE
  FROM dependencias d
 WHERE d.sigla = 'DTIC-SA' AND NOT EXISTS (SELECT 1 FROM usuarios WHERE username = 'prueba_mesa_tic');

INSERT INTO usuarios (nombre_completo, username, password_hash, email, dependencia_id, rol, activo)
SELECT 'Prueba Mesa de Entrada DGAF', 'prueba_mesa_dgaf', crypt(:'clave', gen_salt('bf', 10)), NULL, d.id, 'SECRETARIA_ADMIN', TRUE
  FROM dependencias d
 WHERE d.sigla = 'DGAF-SA' AND NOT EXISTS (SELECT 1 FROM usuarios WHERE username = 'prueba_mesa_dgaf');

INSERT INTO usuarios (nombre_completo, username, password_hash, email, dependencia_id, rol, activo)
SELECT 'Prueba Responsable Hacienda', 'prueba_resp_hacienda', crypt(:'clave', gen_salt('bf', 10)), NULL, d.id, 'JEFE_DEPTO', TRUE
  FROM dependencias d
 WHERE d.sigla = 'DHAC' AND NOT EXISTS (SELECT 1 FROM usuarios WHERE username = 'prueba_resp_hacienda');

INSERT INTO asignaciones (usuario_id, rol, dependencia_ambito_id, incluye_subordinadas, acto_administrativo)
SELECT u.id, 'MESA_ENTRADA', d.id, TRUE, 'Usuario de prueba'
  FROM usuarios u, dependencias d
 WHERE u.username = 'prueba_mesa_tic' AND d.sigla = 'DTIC'
   AND NOT EXISTS (SELECT 1 FROM asignaciones a WHERE a.usuario_id = u.id);

INSERT INTO asignaciones (usuario_id, rol, dependencia_ambito_id, incluye_subordinadas, acto_administrativo)
SELECT u.id, 'MESA_ENTRADA', d.id, TRUE, 'Usuario de prueba'
  FROM usuarios u, dependencias d
 WHERE u.username = 'prueba_mesa_dgaf' AND d.sigla = 'DGAF'
   AND NOT EXISTS (SELECT 1 FROM asignaciones a WHERE a.usuario_id = u.id);

INSERT INTO asignaciones (usuario_id, rol, dependencia_ambito_id, incluye_subordinadas, acto_administrativo)
SELECT u.id, 'RESPONSABLE', d.id, TRUE, 'Usuario de prueba'
  FROM usuarios u, dependencias d
 WHERE u.username = 'prueba_resp_hacienda' AND d.sigla = 'DHAC'
   AND NOT EXISTS (SELECT 1 FROM asignaciones a WHERE a.usuario_id = u.id);

SELECT u.username, a.rol, d.sigla AS ambito
  FROM usuarios u JOIN asignaciones a ON a.usuario_id = u.id JOIN dependencias d ON d.id = a.dependencia_ambito_id
 ORDER BY u.username;
SQL
unset CLAVE CLAVE_2
echo "Listo: usuarios de prueba disponibles en $BASE_PRUEBAS."
