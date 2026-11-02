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

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
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
