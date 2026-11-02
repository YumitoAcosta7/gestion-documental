import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

// Lee el organigrama transcripto (sangría = jerarquía), genera códigos y el CSV del §12, valida
// y compara en SOLO LECTURA con las dependencias cargadas. No escribe nada en la base: con el
// quinto argumento genera la migración SQL de carga, que se revisa y se aplica aparte.
// Uso (desde la raíz del proyecto):
//   java -cp <driver.jar> db/organigrama/ImportarOrganigrama.java src/main/resources/application.properties
//        application-local.properties db/organigrama/organigrama_RI887_2026.txt
//        db/organigrama/organigrama_RI887_2026.csv [db/migraciones/V9__organigrama_RI887.sql]
public class ImportarOrganigrama {
    static final String RESOLUCION = "Res. N° 887/08-07-2026";
    static final Map<String, String> NIVEL = Map.of("I", "Intendencia", "DG", "Dirección General",
            "D", "Dirección", "DP", "Departamento", "U", "Unidad");
    static final Map<String, Integer> ORDEN = Map.of("I", 1, "DG", 2, "D", 4, "DP", 6, "U", 7);

    static class Dep {
        String codigo, nombre, nivel; Integer idActual; boolean mesa, revisar; Dep padre; int linea;
        List<Dep> hijos = new ArrayList<>(); int nD, nU;
    }

    public static void main(String[] args) throws Exception {
        List<Dep> todas = new ArrayList<>();
        Deque<Dep> pila = new ArrayDeque<>(); Deque<Integer> sangrias = new ArrayDeque<>();
        List<String> errores = new ArrayList<>();
        int nro = 0;
        for (String l : Files.readAllLines(Path.of(args[2]), StandardCharsets.UTF_8)) {
            nro++;
            if (l.isBlank() || l.strip().startsWith("#")) continue;
            int s = l.length() - l.stripLeading().length();
            String[] c = l.strip().split("\\|", -1);
            Dep d = new Dep(); d.linea = nro;
            if (c[0].equals("SA")) {
                d.nombre = "Unidad de Secretaría Administrativa"; d.nivel = "U"; d.mesa = true;
                if (c.length > 1 && !c[1].isBlank()) d.idActual = Integer.valueOf(c[1]);
            } else {
                d.codigo = c[0].isBlank() ? null : c[0]; d.nombre = c[1]; d.nivel = c[2];
                for (int i = 3; i < c.length; i++) {
                    if (c[i].equals("!")) d.revisar = true; else if (!c[i].isBlank()) d.idActual = Integer.valueOf(c[i]);
                }
                if (d.nombre.contains("Secretaría Administrativa") || d.nombre.contains("Mesa Central de Entradas")) d.mesa = true;
            }
            if (!NIVEL.containsKey(d.nivel)) errores.add("línea " + nro + ": nivel desconocido " + d.nivel);
            while (!sangrias.isEmpty() && sangrias.peek() >= s) { sangrias.pop(); pila.pop(); }
            d.padre = pila.peek();
            if (d.padre != null) d.padre.hijos.add(d);
            else if (!todas.isEmpty()) errores.add("línea " + nro + ": más de una raíz");
            pila.push(d); sangrias.push(s);
            todas.add(d);
        }
        // Códigos generados a partir del superior
        for (Dep d : todas) {
            if (d.codigo != null) continue;
            if (d.padre == null) { errores.add("línea " + d.linea + ": raíz sin código"); continue; }
            if (d.mesa && d.nombre.startsWith("Unidad de Secretaría") || d.nombre.equals("Secretaría Administrativa")) d.codigo = d.padre.codigo + "-SA";
            else if (d.nivel.equals("DP")) d.codigo = String.format("%s-D%02d", d.padre.codigo, ++d.padre.nD);
            else d.codigo = String.format("%s-U%02d", d.padre.codigo, ++d.padre.nU);
        }
        // Validaciones
        Map<String, Dep> porCodigo = new HashMap<>();
        Set<Integer> ids = new HashSet<>();
        for (Dep d : todas) {
            if (porCodigo.put(d.codigo, d) != null) errores.add("código duplicado " + d.codigo);
            if (d.idActual != null && !ids.add(d.idActual)) errores.add("id_actual duplicado " + d.idActual);
            if (d.padre != null && ORDEN.get(d.nivel) <= ORDEN.get(d.padre.nivel))
                errores.add("nivel incoherente: " + d.codigo + " (" + d.nivel + ") bajo " + d.padre.codigo + " (" + d.padre.nivel + ")");
            if (!d.codigo.matches("^[A-Z0-9][A-Z0-9-]{1,19}$")) errores.add("sigla inválida (mayúsculas, números y guiones, hasta 20) " + d.codigo);
            Set<String> hermanos = new HashSet<>();
            for (Dep h : d.hijos) if (!hermanos.add(h.nombre)) errores.add("nombre repetido entre hermanos bajo " + d.codigo + ": " + h.nombre);
        }
        // Ciclos: con sangría no puede haberlos, pero se verifica igual recorriendo hacia arriba
        for (Dep d : todas) { int k = 0; for (Dep p = d.padre; p != null; p = p.padre) if (++k > 20) { errores.add("ciclo en " + d.codigo); break; } }

        // CSV del §12
        StringBuilder csv = new StringBuilder("codigo;nombre;nivel;codigo_superior;es_mesa_entrada;responsable;correo_responsable;activa;resolucion;id_actual;revisar\n");
        for (Dep d : todas) csv.append(String.join(";", d.codigo, d.nombre, NIVEL.get(d.nivel),
                d.padre == null ? "" : d.padre.codigo, d.mesa ? "S" : "N", "", "", "S", RESOLUCION,
                d.idActual == null ? "" : d.idActual.toString(), d.revisar ? "S" : "")).append('\n');
        Files.writeString(Path.of(args[3]), "﻿" + csv, StandardCharsets.UTF_8);
        if (args.length > 4 && errores.isEmpty()) Files.writeString(Path.of(args[4]), generarSql(todas), StandardCharsets.UTF_8);

        System.out.println("=== RESUMEN DEL ORGANIGRAMA");
        Map<String, Integer> cuenta = new LinkedHashMap<>();
        for (String n : List.of("I", "DG", "D", "DP", "U")) cuenta.put(NIVEL.get(n), 0);
        for (Dep d : todas) cuenta.merge(NIVEL.get(d.nivel), 1, Integer::sum);
        System.out.println("total " + todas.size() + " " + cuenta);
        System.out.println("mesas de entrada: " + todas.stream().filter(d -> d.mesa).count());
        System.out.println("vinculadas a dependencias existentes: " + ids.size());
        System.out.println("a verificar (!): " + todas.stream().filter(d -> d.revisar).map(d -> d.codigo + " " + d.nombre).toList());
        System.out.println("\n=== VALIDACIONES: " + (errores.isEmpty() ? "OK, sin errores" : errores.size() + " errores"));
        errores.forEach(e -> System.out.println("  " + e));

        // Comparación de solo lectura con la base
        Properties app = new Properties(); try (FileReader r = new FileReader(args[0])) { app.load(r); }
        Properties local = new Properties();
        if (Files.exists(Path.of(args[1]))) { try (FileReader r = new FileReader(args[1])) { local.load(r); } }
        String clave = local.getProperty("spring.datasource.password", System.getenv("DB_PASSWORD"));
        record Fila(int id, String nombre, Integer padre, String nivel, boolean activo, int usuarios, int documentos) {}
        Map<Integer, Fila> base = new TreeMap<>();
        try (Connection con = DriverManager.getConnection(app.getProperty("spring.datasource.url"),
                app.getProperty("spring.datasource.username"), clave)) {
            con.setReadOnly(true); con.setAutoCommit(false);
            try (Statement st = con.createStatement()) {
                st.execute("SET TRANSACTION READ ONLY");
                ResultSet rs = st.executeQuery("""
                    SELECT d.id, d.nombre, d.dependencia_padre_id, n.nombre, d.activo,
                      (SELECT COUNT(*) FROM usuarios u WHERE u.dependencia_id = d.id),
                      (SELECT COUNT(*) FROM documentos x WHERE x.dependencia_origen_id = d.id) + (SELECT COUNT(*) FROM actuaciones_documento a WHERE a.dependencia_emisora_id = d.id OR a.dependencia_receptora_pase_id = d.id)
                    FROM dependencias d JOIN niveles_jerarquicos n ON n.id = d.nivel_jerarquico_id""");
                while (rs.next()) base.put(rs.getInt(1), new Fila(rs.getInt(1), rs.getString(2),
                        (Integer) rs.getObject(3), rs.getString(4), rs.getBoolean(5), rs.getInt(6), rs.getInt(7)));
            }
            con.rollback();
        }
        Map<Integer, Dep> porId = new HashMap<>();
        for (Dep d : todas) if (d.idActual != null) porId.put(d.idActual, d);
        System.out.println("\n=== COMPARACIÓN CON LA BASE (" + base.size() + " dependencias cargadas)");
        List<String> renombres = new ArrayList<>(), niveles = new ArrayList<>(), padres = new ArrayList<>(), noEstan = new ArrayList<>();
        for (Dep d : todas) {
            if (d.idActual == null) continue;
            Fila f = base.get(d.idActual);
            if (f == null) { errores.add("id_actual inexistente " + d.idActual); System.out.println("  ERROR id inexistente " + d.idActual); continue; }
            if (!f.nombre().equals(d.nombre)) renombres.add(f.id() + ": \"" + f.nombre() + "\" -> \"" + d.nombre + "\"");
            if (!f.nivel().equals(NIVEL.get(d.nivel))) niveles.add(f.id() + " " + d.nombre + ": " + f.nivel() + " -> " + NIVEL.get(d.nivel));
            Integer padreNuevo = d.padre == null ? null : d.padre.idActual;
            if (!Objects.equals(f.padre(), padreNuevo)) padres.add(f.id() + " " + d.nombre + ": superior " + f.padre() + " -> " + padreNuevo);
        }
        for (Fila f : base.values()) if (!porId.containsKey(f.id()))
            noEstan.add(f.id() + " " + f.nombre() + " (" + f.nivel() + ", superior " + f.padre() + ", usuarios " + f.usuarios() + ", documentos " + f.documentos() + ")");
        System.out.println("-- Cambios de nombre (" + renombres.size() + ")"); renombres.forEach(x -> System.out.println("  " + x));
        System.out.println("-- Cambios de nivel (" + niveles.size() + ")"); niveles.forEach(x -> System.out.println("  " + x));
        System.out.println("-- Cambios de superior (" + padres.size() + ")"); padres.forEach(x -> System.out.println("  " + x));
        System.out.println("-- En la base pero no en el organigrama (" + noEstan.size() + ")"); noEstan.forEach(x -> System.out.println("  " + x));
        System.out.println("-- Altas nuevas: " + (todas.size() - porId.size()));
    }

    static String q(String s) { return s == null ? "NULL" : "'" + s.replace("'", "''") + "'"; }

    // Migración de datos: actualiza por id las existentes y da de alta las nuevas (idempotente)
    static String generarSql(List<Dep> todas) {
        StringBuilder sb = new StringBuilder("""
            -- =====================================================================
            -- Gestión Documental - Migración V9: carga del organigrama completo
            --
            -- Res. N° 887/08-07-2026. GENERADA por db/organigrama/ImportarOrganigrama.java
            -- desde organigrama_RI887_2026.txt: no editar a mano; se corrige el .txt
            -- y se regenera. Requiere V8. Actualiza por id las dependencias ya
            -- cargadas (conservan su historial) y da de alta las nuevas, ubicando
            -- a cada superior por su sigla. Idempotente.
            -- =====================================================================

            BEGIN;

            -- Los ids actuales se cargaron a mano (1131, 1455...): la secuencia queda detrás del máximo
            SELECT setval('dependencias_id_seq', (SELECT MAX(id) FROM dependencias));

            -- Dependencias ya cargadas
            """);
        for (Dep d : todas) if (d.idActual != null)
            sb.append(String.format("UPDATE dependencias SET nombre = %s, sigla = %s, nivel_jerarquico_id = (SELECT id FROM niveles_jerarquicos WHERE nombre = %s), es_mesa_entrada = %s, recibe_externos = %s, resolucion = %s WHERE id = %d;%n",
                    q(d.nombre), q(d.codigo), q(NIVEL.get(d.nivel)), d.mesa, recibeExternos(d), q(RESOLUCION), d.idActual));
        sb.append("""

            -- Coordinación de TIC: no figura en la Res. 887; se conserva como dependencia interna de la DTIC
            UPDATE dependencias SET sigla = 'DTIC-COORD' WHERE id = 12 AND sigla IS NULL;

            -- Altas, en orden jerárquico (cada superior se crea antes que sus subordinadas)
            """);
        for (Dep d : todas) if (d.idActual == null)
            sb.append(String.format("INSERT INTO dependencias (nombre, sigla, nivel_jerarquico_id, dependencia_padre_id, es_interna_tic, activo, es_mesa_entrada, recibe_externos, resolucion)%n"
                    + "    SELECT %s, %s, (SELECT id FROM niveles_jerarquicos WHERE nombre = %s), (SELECT id FROM dependencias WHERE sigla = %s), FALSE, TRUE, %s, %s, %s%n"
                    + "    WHERE NOT EXISTS (SELECT 1 FROM dependencias WHERE sigla = %s);%n",
                    q(d.nombre), q(d.codigo), q(NIVEL.get(d.nivel)), q(d.padre.codigo), d.mesa, recibeExternos(d), q(RESOLUCION), q(d.codigo)));
        sb.append(String.format("""

            DO $$
            BEGIN
                IF (SELECT COUNT(*) FROM dependencias WHERE resolucion = %s) <> %d THEN
                    RAISE EXCEPTION 'La carga no coincide con el organigrama: se esperaban %d dependencias de la resolución';
                END IF;
                IF EXISTS (SELECT 1 FROM dependencias WHERE sigla IS NULL) THEN
                    RAISE EXCEPTION 'Quedaron dependencias sin sigla';
                END IF;
            END $$;

            COMMIT;
            """, q(RESOLUCION), todas.size(), todas.size()));
        return sb.toString();
    }

    // La Mesa Central de Entradas de Secretaría General recibe lo externo y lo deriva a cada mesa (§5)
    static boolean recibeExternos(Dep d) { return d.nombre.contains("Mesa Central de Entradas"); }
}
