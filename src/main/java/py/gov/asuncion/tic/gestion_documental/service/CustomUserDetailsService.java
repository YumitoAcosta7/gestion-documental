package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("🔍 [DIAGNÓSTICO LOGIN] Intentando autenticar al usuario: " + username);

        Usuario usuario = usuarioRepository.findByUsernameAndActivoTrue(username)
                .orElseThrow(() -> {
                    System.out.println("❌ [DIAGNÓSTICO LOGIN] No se encontró el usuario activo en la BD: " + username);
                    return new UsernameNotFoundException("Usuario no encontrado o inactivo: " + username);
                });

        System.out.println("✅ [DIAGNÓSTICO LOGIN] Usuario encontrado en BD: " + usuario.getUsername());
        System.out.println("👤 [DIAGNÓSTICO LOGIN] Rol asignado: " + usuario.getRol());

        String rolTexto = (usuario.getRol() != null) ? usuario.getRol().toString() : "ENCARGADO_UNIDAD";
        String rolSpring = rolTexto.startsWith("ROLE_") ? rolTexto : "ROLE_" + rolTexto;

        return new User(
                usuario.getUsername(),
                usuario.getPasswordHash(),
                usuario.getActivo(),
                true, true, true,
                List.of(new SimpleGrantedAuthority(rolSpring))
        );
    }
}