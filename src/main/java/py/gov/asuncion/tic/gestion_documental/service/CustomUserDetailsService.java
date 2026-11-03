package py.gov.asuncion.tic.gestion_documental.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PoliticaAcceso politicaAcceso;

    @Autowired
    private LimiteIntentosService limiteIntentos;

    // Dirección del cliente. Detrás de un proxy inverso, con server.forward-headers-strategy=native
    // Tomcat la toma de X-Forwarded-For.
    private static String ipDeLaPeticion() {
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes atributos) {
            return atributos.getRequest().getRemoteAddr();
        }
        return null;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Bloqueado por intentos fallidos: cuenta "bloqueada", exista o no el usuario (no se revela cuál existe).
        // Spring rechaza las cuentas bloqueadas antes de comprobar la contraseña.
        // Lo mismo si la dirección IP de la petición acumuló demasiados fallos con cualquier usuario.
        if (limiteIntentos.estaBloqueado(username) || limiteIntentos.ipBloqueada(ipDeLaPeticion())) {
            return User.withUsername(username).password("!bloqueado").authorities(java.util.List.of()).accountLocked(true).build();
        }
        Usuario usuario = usuarioRepository.findByUsernameAndActivoTrue(username)
                .orElseThrow(() -> {
                    log.info("Intento de acceso con usuario inexistente o inactivo: {}", username);
                    return new UsernameNotFoundException("Usuario no encontrado o inactivo: " + username);
                });

        // Los permisos salen de las asignaciones vigentes; se recalculan además en cada petición
        // (ActualizacionPermisosFilter), así que estos solo rigen hasta la primera.
        var autoridades = politicaAcceso.autoridades(usuario);
        log.debug("Usuario {} autenticándose con permisos {}", usuario.getUsername(), autoridades);

        return new User(
                usuario.getUsername(),
                usuario.getPasswordHash(),
                usuario.getActivo(),
                true, true, true,
                autoridades
        );
    }
}
