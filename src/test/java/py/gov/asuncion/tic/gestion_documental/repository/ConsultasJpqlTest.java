package py.gov.asuncion.tic.gestion_documental.repository;

import jakarta.persistence.Entity;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Las demás pruebas simulan los repositorios: una consulta inválida recién aparecería al arrancar la aplicación.
// Esta traduce todas las JPQL contra las entidades, sin base de datos. No revisa las consultas nativas.
class ConsultasJpqlTest {

    private static final String BASE = "py.gov.asuncion.tic.gestion_documental";

    private static StandardServiceRegistry registro;
    private static SessionFactory fabrica;

    @BeforeAll
    static void iniciarHibernate() throws Exception {
        registro = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        MetadataSources fuentes = new MetadataSources(registro);
        ClassPathScanningCandidateComponentProvider escaner = new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (BeanDefinition entidad : escaner.findCandidateComponents(BASE + ".domain")) {
            fuentes.addAnnotatedClass(Class.forName(entidad.getBeanClassName()));
        }
        fabrica = fuentes.buildMetadata().buildSessionFactory();
    }

    @AfterAll
    static void cerrarHibernate() {
        if (fabrica != null) fabrica.close();
        if (registro != null) StandardServiceRegistryBuilder.destroy(registro);
    }

    private static List<Class<?>> repositorios() throws Exception {
        ClassPathScanningCandidateComponentProvider escaner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definicion) {
                return definicion.getMetadata().isInterface();
            }
        };
        escaner.addIncludeFilter(new AssignableTypeFilter(JpaRepository.class));
        List<Class<?>> clases = new ArrayList<>();
        for (BeanDefinition repositorio : escaner.findCandidateComponents(BASE + ".repository")) {
            clases.add(Class.forName(repositorio.getBeanClassName()));
        }
        return clases;
    }

    @Test
    void todasLasConsultasJpqlCoincidenConLasEntidades() throws Exception {
        List<Class<?>> repositorios = repositorios();
        assertFalse(repositorios.isEmpty(), "no se encontraron repositorios");

        var motor = fabrica.unwrap(SessionFactoryImplementor.class).getQueryEngine().getHqlTranslator();
        List<String> invalidas = new ArrayList<>();
        int revisadas = 0;
        for (Class<?> repositorio : repositorios) {
            for (Method metodo : repositorio.getDeclaredMethods()) {
                Query consulta = metodo.getAnnotation(Query.class);
                if (consulta == null || consulta.nativeQuery()) continue;
                revisadas++;
                try {
                    motor.translate(consulta.value(), null);
                } catch (RuntimeException e) {
                    invalidas.add(repositorio.getSimpleName() + "." + metodo.getName() + ": " + e.getMessage());
                }
            }
        }
        assertTrue(revisadas > 0, "no se revisó ninguna consulta");
        assertTrue(invalidas.isEmpty(), () -> "Consultas inválidas:\n" + String.join("\n", invalidas));
    }
}
