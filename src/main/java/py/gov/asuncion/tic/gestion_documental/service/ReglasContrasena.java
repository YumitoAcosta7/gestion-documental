package py.gov.asuncion.tic.gestion_documental.service;

// 🔑 Política mínima de contraseñas: al menos 10 caracteres, con letras y números.
// La misma regla aplica la herramienta db/herramientas/DefinirContrasena.java.
public final class ReglasContrasena {

    public static final int LARGO_MINIMO = 10;
    public static final String DESCRIPCION = "al menos " + LARGO_MINIMO + " caracteres, con letras y números";

    private ReglasContrasena() {
    }

    public static boolean cumple(String contrasena) {
        return contrasena != null && contrasena.length() >= LARGO_MINIMO
                && contrasena.chars().anyMatch(Character::isLetter)
                && contrasena.chars().anyMatch(Character::isDigit);
    }
}
