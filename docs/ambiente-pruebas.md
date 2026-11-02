# Ambiente de pruebas

Una base aparte en el mismo servidor PostgreSQL (192.168.129.73), con su propio usuario y su carpeta de PDF. Sirve para probar versiones nuevas y capacitar sin tocar los expedientes reales.

| | Producción | Pruebas |
|---|---|---|
| Base | `gestion_documental` | `gestion_documental_pruebas` |
| Usuario de la base | `gestion_documental` | `gestion_documental_pruebas` |
| Contraseña | `application-local.properties` o `DB_PASSWORD` | variable `DB_PASSWORD_PRUEBAS` |
| PDF | `C:/gestion-documental-uploads/acuses` | `C:/gestion-documental-uploads-pruebas/acuses` |
| Configuración | `application.properties` | además `application-pruebas.properties` |

## 1. Crear la base en el servidor (una sola vez)

El script `db/pruebas/crear_base_pruebas.sh` hace todo. Se copia al servidor y se ejecuta como `postgres`:

```bash
sudo -u postgres bash crear_base_pruebas.sh
```

Pasos que sigue:
1. **Crea el rol y la base** `gestion_documental_pruebas`. La contraseña del rol se pide por teclado y no queda guardada.
2. **Copia desde producción:**
   - la estructura completa;
   - los catálogos: niveles, organigrama, tipos de documento y atajos del canal;
   - el historial de Flyway, que le indica en qué versión está la base.

   Flyway arranca desde el esquema de la V6, que se creó a mano y no tiene script propio; por eso se copia la estructura. **No** se copian expedientes, actuaciones, adjuntos, usuarios ni asignaciones. Producción solo se lee.
3. **Deja todos los objetos a nombre del rol de pruebas.**
4. **Opcional: crea tres usuarios de prueba**, con una contraseña de prueba que se pide por teclado:
   - `prueba_mesa_tic`, mesa de entrada de la DTIC;
   - `prueba_mesa_dgaf`, mesa de entrada de la DGAF, paso obligado entre TIC y Hacienda;
   - `prueba_resp_hacienda`, responsable de Hacienda, para comprobar que no ve los expedientes de TIC.
5. **Muestra un resumen** con la cantidad de dependencias, expedientes y usuarios, y la versión de Flyway.

Si la base de pruebas ya existe, el script se detiene sin tocar nada.

Los usuarios de prueba también se pueden crear después, sin rehacer la base. El script `db/pruebas/crear_usuarios_prueba.sh` no duplica los que ya existen:

```bash
sudo -u postgres bash crear_usuarios_prueba.sh
```

## 2. Permitir la conexión desde la máquina de la aplicación

Si la aplicación corre en otra máquina, el rol de pruebas necesita una línea en `pg_hba.conf` como la del usuario de producción:

```
host  gestion_documental_pruebas  gestion_documental_pruebas  <red de la aplicación>/24  scram-sha-256
```

Después se recarga la configuración:

```bash
sudo -u postgres psql -c "SELECT pg_reload_conf();"
```

## 3. Levantar la aplicación con el perfil

```powershell
$env:DB_PASSWORD_PRUEBAS = '<contraseña de la base de pruebas>'
```

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=pruebas"
```

Al arrancar, Flyway aplica sobre la base de pruebas las migraciones que falten. Conviene probar ahí cada versión nueva antes de llevarla a producción.
