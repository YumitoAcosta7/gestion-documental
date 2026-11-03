package py.gov.asuncion.tic.gestion_documental.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Las consultas nativas se arman concatenando bloques de texto ("""), y Java quita los espacios al final de cada
// línea: "AND """ + condicion" queda pegado ("ANDd.id IN ..."), un error que recién aparece al ejecutar la consulta.
class ConsultasNativasTest {

    private static final Pattern PALABRA_PEGADA = Pattern.compile("\\b(AND|OR|WHERE|ON|SELECT|FROM|LIMIT)(?=[a-z0-9_:])");

    @Test
    void ningunaConsultaTienePalabrasClavePegadas() {
        List<String> errores = new ArrayList<>();
        for (Class<?> repositorio : List.of(DocumentoRepository.class, ActuacionDocumentoRepository.class,
                DependenciaRepository.class, AsignacionRepository.class, FeriadoRepository.class)) {
            for (var metodo : repositorio.getDeclaredMethods()) {
                Query q = metodo.getAnnotation(Query.class);
                if (q != null && PALABRA_PEGADA.matcher(q.value()).find()) {
                    errores.add(repositorio.getSimpleName() + "." + metodo.getName());
                }
            }
        }
        assertTrue(errores.isEmpty(), "Consultas con una palabra clave pegada a lo que sigue: " + errores);
    }
}
