package py.gov.asuncion.tic.gestion_documental.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 🔒 Límite de intentos de inicio de sesión (auditoría A2): tras varios intentos fallidos seguidos, el
// usuario queda bloqueado unos minutos. El conteo vive en memoria: se reinicia al reiniciar la aplicación,
// suficiente para frenar la prueba de contraseñas por fuerza bruta. Bloquea por nombre de usuario,
// exista o no, para no revelar qué usuarios existen.
@Service
public class LimiteIntentosService {

    private static final Logger log = LoggerFactory.getLogger(LimiteIntentosService.class);

    private record Registro(int fallidos, Instant bloqueadoHasta) {}

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final int maximoIntentos;
    private final Duration bloqueo;
    private final Clock reloj;

    // Con dos constructores, Spring necesita saber cuál usar: este (el otro es para las pruebas)
    @Autowired
    public LimiteIntentosService(@Value("${app.login.intentos-maximos:5}") int maximoIntentos,
                                 @Value("${app.login.minutos-bloqueo:15}") int minutosBloqueo) {
        this(maximoIntentos, Duration.ofMinutes(minutosBloqueo), Clock.systemUTC());
    }

    LimiteIntentosService(int maximoIntentos, Duration bloqueo, Clock reloj) {
        this.maximoIntentos = maximoIntentos;
        this.bloqueo = bloqueo;
        this.reloj = reloj;
    }

    public boolean estaBloqueado(String usuario) {
        Registro r = registros.get(clave(usuario));
        return r != null && r.bloqueadoHasta() != null && reloj.instant().isBefore(r.bloqueadoHasta());
    }

    @EventListener
    public void alFallar(AuthenticationFailureBadCredentialsEvent evento) {
        registrarFallo(String.valueOf(evento.getAuthentication().getName()));
    }

    @EventListener
    public void alEntrar(AuthenticationSuccessEvent evento) {
        registros.remove(clave(evento.getAuthentication().getName()));
    }

    void registrarFallo(String usuario) {
        Registro nuevo = registros.compute(clave(usuario), (k, r) -> {
            // Si ya venció un bloqueo anterior, se empieza a contar de nuevo
            int previos = (r == null || (r.bloqueadoHasta() != null && !reloj.instant().isBefore(r.bloqueadoHasta()))) ? 0 : r.fallidos();
            int fallidos = previos + 1;
            return new Registro(fallidos, fallidos >= maximoIntentos ? reloj.instant().plus(bloqueo) : null);
        });
        if (nuevo.bloqueadoHasta() != null && nuevo.fallidos() == maximoIntentos) {
            log.warn("Usuario {} bloqueado {} minutos tras {} intentos fallidos de inicio de sesión",
                    usuario, bloqueo.toMinutes(), maximoIntentos);
        }
    }

    private static String clave(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    }
}
