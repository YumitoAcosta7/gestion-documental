package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

// Auditoría A2: bloqueo temporal tras intentos fallidos de inicio de sesión
class LimiteIntentosServiceTest {

    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-02T12:00:00Z"));
    private final Clock reloj = new Clock() {
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zona) { return this; }
        public Instant instant() { return ahora.get(); }
    };
    private final LimiteIntentosService limite = new LimiteIntentosService(5, Duration.ofMinutes(15), reloj);

    @Test
    void seBloqueaAlQuintoIntentoFallidoYSeLiberaAlVencer() {
        for (int i = 0; i < 4; i++) {
            limite.registrarFallo("ana");
        }
        assertFalse(limite.estaBloqueado("ana"));
        limite.registrarFallo("ANA ");  // mismo usuario, sin importar mayúsculas ni espacios
        assertTrue(limite.estaBloqueado("ana"));

        ahora.set(ahora.get().plus(Duration.ofMinutes(16)));
        assertFalse(limite.estaBloqueado("ana"));
        limite.registrarFallo("ana");
        assertFalse(limite.estaBloqueado("ana"), "tras el bloqueo el conteo empieza de nuevo");
    }

    @Test
    void unIngresoCorrectoReiniciaElConteo() {
        for (int i = 0; i < 4; i++) {
            limite.registrarFallo("ana");
        }
        limite.alEntrar(new AuthenticationSuccessEvent(UsernamePasswordAuthenticationToken.authenticated("ana", null, List.of())));
        limite.registrarFallo("ana");
        assertFalse(limite.estaBloqueado("ana"));
    }

    @Test
    void springPuedeCrearloComoEnLaAplicacion() {
        // Tiene dos constructores: sin @Autowired en el público, la aplicación no arrancaba
        try (var ctx = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("prueba",
                    java.util.Map.of("app.login.intentos-maximos", "2", "app.login.minutos-bloqueo", "1")));
            ctx.register(LimiteIntentosService.class);
            ctx.refresh();
            LimiteIntentosService desdeSpring = ctx.getBean(LimiteIntentosService.class);
            desdeSpring.registrarFallo("ana");
            desdeSpring.registrarFallo("ana");
            assertTrue(desdeSpring.estaBloqueado("ana"), "toma la configuración app.login.*");
        }
    }

    @Test
    void tambienSeBloqueanUsuariosInexistentes() {
        // Así el mensaje "bloqueado" no revela qué nombres de usuario existen
        for (int i = 0; i < 5; i++) {
            limite.registrarFallo("no.existe");
        }
        assertTrue(limite.estaBloqueado("no.existe"));
        assertFalse(limite.estaBloqueado("ana"));
    }
}
