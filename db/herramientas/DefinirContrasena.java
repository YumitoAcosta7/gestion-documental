import org.springframework.security.crypto.bcrypt.BCrypt;

import java.io.Console;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Properties;

// Define o cambia la contraseña de un usuario de Gestión Documental. La pide por teclado (sin mostrarla),
// la guarda como hash BCrypt y nunca la imprime. Sirve para el primer ingreso de un usuario creado sin
// contraseña (por ejemplo, el administrador de la migración V12) y para resetear una contraseña olvidada.
//
// Uso, desde la raíz del proyecto en PowerShell o CMD (no en Git Bash: no oculta lo que se escribe):
//   java -cp "$HOME\.m2\repository\org\postgresql\postgresql\42.7.11\postgresql-42.7.11.jar;$HOME\.m2\repository\org\springframework\security\spring-security-crypto\7.1.0\spring-security-crypto-7.1.0.jar" db\herramientas\DefinirContrasena.java <usuario> [pruebas]
//
// Sin "pruebas" actúa sobre producción (application.properties + application-local.properties o DB_PASSWORD).
// Con "pruebas", sobre la base de pruebas (DB_PASSWORD_PRUEBAS).
public class DefinirContrasena {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Indique el usuario: DefinirContrasena.java <usuario> [pruebas]");
            System.exit(1);
        }
        String usuario = args[0];
        boolean pruebas = args.length > 1 && "pruebas".equalsIgnoreCase(args[1]);

        Properties app = new Properties();
        try (FileReader r = new FileReader("src/main/resources/application.properties")) { app.load(r); }
        String url, usuarioBase, claveBase;
        if (pruebas) {
            Properties perfil = new Properties();
            try (FileReader r = new FileReader("src/main/resources/application-pruebas.properties")) { perfil.load(r); }
            url = perfil.getProperty("spring.datasource.url");
            usuarioBase = perfil.getProperty("spring.datasource.username");
            claveBase = System.getenv("DB_PASSWORD_PRUEBAS");
        } else {
            Properties local = new Properties();
            if (Files.exists(Path.of("application-local.properties"))) {
                try (FileReader r = new FileReader("application-local.properties")) { local.load(r); }
            }
            url = app.getProperty("spring.datasource.url");
            usuarioBase = app.getProperty("spring.datasource.username");
            claveBase = local.getProperty("spring.datasource.password", System.getenv("DB_PASSWORD"));
        }
        if (claveBase == null || claveBase.isBlank()) {
            System.err.println("Falta la contraseña de la base: " + (pruebas ? "defina DB_PASSWORD_PRUEBAS." : "application-local.properties o DB_PASSWORD."));
            System.exit(1);
        }

        Console consola = System.console();
        if (consola == null) {
            System.err.println("Ejecútelo en PowerShell o CMD: hace falta una consola que oculte la contraseña.");
            System.exit(1);
        }
        System.out.println("Base: " + (pruebas ? "PRUEBAS" : "PRODUCCIÓN") + " (" + url + ")");
        char[] clave = consola.readPassword("Contraseña nueva para %s: ", usuario);
        char[] repetida = consola.readPassword("Repita la contraseña: ");
        try {
            if (clave == null || !Arrays.equals(clave, repetida)) {
                System.err.println("Las contraseñas no coinciden. No se cambió nada.");
                System.exit(1);
            }
            String texto = new String(clave);
            if (texto.length() < 10 || !texto.matches(".*[A-Za-z].*") || !texto.matches(".*\\d.*")) {
                System.err.println("Use al menos 10 caracteres, con letras y números. No se cambió nada.");
                System.exit(1);
            }
            String hash = BCrypt.hashpw(texto, BCrypt.gensalt(10));
            try (Connection con = DriverManager.getConnection(url, usuarioBase, claveBase);
                 PreparedStatement ps = con.prepareStatement(
                         "UPDATE usuarios SET password_hash = ? WHERE username = ? AND activo")) {
                ps.setString(1, hash);
                ps.setString(2, usuario);
                int filas = ps.executeUpdate();
                if (filas == 0) {
                    System.err.println("No existe un usuario activo '" + usuario + "'. No se cambió nada.");
                    System.exit(1);
                }
            }
            System.out.println("Contraseña de " + usuario + " actualizada.");
        } finally {
            Arrays.fill(clave == null ? new char[0] : clave, ' ');
            Arrays.fill(repetida == null ? new char[0] : repetida, ' ');
        }
    }
}
