package py.gov.asuncion.tic.gestion_documental.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsernameAndActivoTrue(username)
                .orElseThrow(() -> {
                    log.info("Intento de acceso con usuario inexistente o inactivo: {}", username);
                    return new UsernameNotFoundException("Usuario no encontrado o inactivo: " + username);
                });

        // El rol es obligatorio en la BD (chk_usuarios_rol): DIRECTOR, SECRETARIA_ADMIN, JEFE_DEPTO, JEFE_UNIDAD
        log.debug("Usuario {} autenticándose con rol {}", usuario.getUsername(), usuario.getRol());

        return new User(
                usuario.getUsername(),
                usuario.getPasswordHash(),
                usuario.getActivo(),
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))
        );
    }
}
