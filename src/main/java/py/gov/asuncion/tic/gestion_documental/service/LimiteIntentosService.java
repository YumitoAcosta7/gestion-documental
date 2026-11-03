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

    // Por dirección IP: fallos dentro de una ventana de tiempo, sumando todos los usuarios. Frena a quien prueba
    // una misma contraseña con muchos usuarios distintos (el bloqueo por usuario no lo detecta).
    private record RegistroIp(int fallidos, Instant inicioVentana, Instant bloqueadoHasta) {}

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final Map<String, RegistroIp> registrosIp = new ConcurrentHashMap<>();
    private final int maximoIntentos;
    private final int maximoIntentosIp;
    private final Duration bloqueo;
    private final Clock reloj;

    // Con varios constructores, Spring necesita saber cuál usar: este (los otros son para las pruebas)
    @Autowired
    public LimiteIntentosService(@Value("${app.login.intentos-maximos:5}") int maximoIntentos,
                                 @Value("${app.login.intentos-maximos-ip:20}") int maximoIntentosIp,
                                 @Value("${app.login.minutos-bloqueo:15}") int minutosBloqueo) {
        this(maximoIntentos, maximoIntentosIp, Duration.ofMinutes(minutosBloqueo), Clock.systemUTC());
    }

    LimiteIntentosService(int maximoIntentos, Duration bloqueo, Clock reloj) {
        this(maximoIntentos, 20, bloqueo, reloj);
    }

    LimiteIntentosService(int maximoIntentos, int maximoIntentosIp, Duration bloqueo, Clock reloj) {
        this.maximoIntentos = maximoIntentos;
        this.maximoIntentosIp = maximoIntentosIp;
        this.bloqueo = bloqueo;
        this.reloj = reloj;
    }

    public boolean estaBloqueado(String usuario) {
        Registro r = registros.get(clave(usuario));
        return r != null && r.bloqueadoHasta() != null && reloj.instant().isBefore(r.bloqueadoHasta());
    }

    public boolean ipBloqueada(String ip) {
        RegistroIp r = ip == null ? null : registrosIp.get(ip);
        return r != null && r.bloqueadoHasta() != null && reloj.instant().isBefore(r.bloqueadoHasta());
    }

    @EventListener
    public void alFallar(AuthenticationFailureBadCredentialsEvent evento) {
        registrarFallo(String.valueOf(evento.getAuthentication().getName()));
        if (evento.getAuthentication().getDetails() instanceof org.springframework.security.web.authentication.WebAuthenticationDetails d) {
            registrarFalloIp(d.getRemoteAddress());
        }
    }

    // Un inicio de sesión correcto no borra el conteo de la IP: si no, con una cuenta propia válida se
    // podría seguir probando contraseñas ajenas desde la misma dirección
    void registrarFalloIp(String ip) {
        if (ip == null) {
            return;
        }
        Instant ahora = reloj.instant();
        RegistroIp nuevo = registrosIp.compute(ip, (k, r) -> {
            boolean ventanaVencida = r == null || !ahora.isBefore(r.inicioVentana().plus(bloqueo))
                    || (r.bloqueadoHasta() != null && !ahora.isBefore(r.bloqueadoHasta()));
            int fallidos = ventanaVencida ? 1 : r.fallidos() + 1;
            Instant inicio = ventanaVencida ? ahora : r.inicioVentana();
            return new RegistroIp(fallidos, inicio, fallidos >= maximoIntentosIp ? ahora.plus(bloqueo) : null);
        });
        if (nuevo.bloqueadoHasta() != null && nuevo.fallidos() == maximoIntentosIp) {
            log.warn("Dirección {} bloqueada {} minutos tras {} intentos fallidos de inicio de sesión (varios usuarios)",
                    ip, bloqueo.toMinutes(), maximoIntentosIp);
        }
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
