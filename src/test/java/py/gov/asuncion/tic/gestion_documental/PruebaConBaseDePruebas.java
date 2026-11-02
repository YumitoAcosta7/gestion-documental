package py.gov.asuncion.tic.gestion_documental;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

/**
 * Para toda prueba que levanta Spring y usa la base de datos.
 *
 * Corre con el perfil "pruebas" (base gestion_documental_pruebas, docs/ambiente-pruebas.md) y solo si
 * está definida la variable DB_PASSWORD_PRUEBAS; si no, la prueba se saltea. Así una prueba nunca se
 * conecta a producción ni le aplica migraciones por descuido.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@ActiveProfiles("pruebas")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD_PRUEBAS", matches = ".+",
        disabledReason = "Defina DB_PASSWORD_PRUEBAS para correr las pruebas contra la base de pruebas")
public @interface PruebaConBaseDePruebas {
}
