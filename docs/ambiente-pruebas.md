# Ambiente de pruebas

Una base aparte en el mismo servidor PostgreSQL (192.168.129.73), con su propio usuario y su carpeta de PDF. Sirve para probar versiones nuevas y capacitar sin tocar los expedientes reales.

| | Producción | Pruebas |
|---|---|---|
| Base | `gestion_documental` | `gestion_documental_pruebas` |
| Usuario de la base | `gestion_documental` | `gestion_documental_pruebas` |
| Contraseña | `application-local.properties` o `DB_PASSWORD` | variable `DB_PASSWORD_PRUEBAS` |
| PDF | `C:/gestion-documental-uploads/acuses` | `C:/gestion-documental-uploads-pruebas/acuses` |
| Configuración | `application.properties` | además `application-pruebas.properties` |

## 1. Crear la base (administrador de PostgreSQL, una sola vez)

```sql
CREATE ROLE gestion_documental_pruebas LOGIN PASSWORD '<contraseña nueva>';
CREATE DATABASE gestion_documental_pruebas OWNER gestion_documental_pruebas ENCODING 'UTF8';
```

## 2. Copiar el esquema y los catálogos desde producción (en el servidor)

Flyway arranca desde el esquema de la V6, que se creó a mano y no tiene script propio. Por eso se copia la estructura completa de producción junto con:
- los catálogos: niveles, organigrama y tipos de documento;
- el historial de Flyway, que le indica en qué versión está la base.

Los expedientes, los usuarios y sus asignaciones **no** se copian.

```bash
pg_dump -h localhost -U postgres -d gestion_documental --schema-only --no-owner -f esquema.sql
```

```bash
pg_dump -h localhost -U postgres -d gestion_documental --data-only --no-owner -t niveles_jerarquicos -t dependencias -t tipos_documentos -t flyway_schema_history -f catalogos.sql
```

```bash
psql -h localhost -U postgres -d gestion_documental_pruebas -f esquema.sql
```

```bash
psql -h localhost -U postgres -d gestion_documental_pruebas -f catalogos.sql
```

```sql
-- En gestion_documental_pruebas, al terminar
GRANT ALL ON ALL TABLES IN SCHEMA public TO gestion_documental_pruebas;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO gestion_documental_pruebas;
ALTER SCHEMA public OWNER TO gestion_documental_pruebas;
```

## 3. Usuarios de prueba

Se crean en `usuarios` de la base de pruebas, con contraseñas de prueba propias (nunca las reales), y cada uno recibe sus `asignaciones`. Por ejemplo:
- una mesa de entrada de la DTIC;
- un responsable de Hacienda, para probar que no ve los expedientes de TIC.

## 4. Levantar la aplicación con el perfil

```powershell
$env:DB_PASSWORD_PRUEBAS = '<contraseña de la base de pruebas>'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=pruebas"
```

Al arrancar, Flyway aplica sobre la base de pruebas las migraciones que falten. Conviene probar ahí cada versión nueva antes de llevarla a producción.
