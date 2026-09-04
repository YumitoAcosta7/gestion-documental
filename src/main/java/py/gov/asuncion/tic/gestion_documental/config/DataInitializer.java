package py.gov.asuncion.tic.gestion_documental.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.*;

// @Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DependenciaRepository dependenciaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        Dependencia depTic = dependenciaRepository.findByEsInternaTicTrueAndActivoTrueOrderByNombreAsc()
                .stream().findFirst().orElse(null);

        if (depTic == null) {
            return;
        }

        crearOActualizarUsuario("director_tic", "admin123", "Director de TIC", "director.tic@asuncion.gov.py", RolUsuario.DIRECTOR, depTic);
        crearOActualizarUsuario("secretaria_tic", "admin123", "Secretaría Mesa de Entrada", "secretaria.tic@asuncion.gov.py", RolUsuario.SECRETARIA_ADMIN, depTic);
        crearOActualizarUsuario("jefe_operativo", "admin123", "Jefe Dpto. Operativo de Informática", "jefe.operativo@asuncion.gov.py", RolUsuario.JEFE_DEPTO, depTic);
        crearOActualizarUsuario("jefe_soporte", "admin123", "Jefe Unidad de Soporte Técnico", "soporte.tec@asuncion.gov.py", RolUsuario.JEFE_UNIDAD, depTic);
    }

    private void crearOActualizarUsuario(String username, String rawPassword, String nombre, String email, RolUsuario rol, Dependencia dep) {
        Usuario usuario = usuarioRepository.findByUsernameAndActivoTrue(username)
                .orElseGet(() -> {
                    Usuario u = new Usuario();
                    u.setUsername(username);
                    return u;
                });

        usuario.setNombreCompleto(nombre);
        usuario.setEmail(email);
        usuario.setRol(rol);
        usuario.setDependencia(dep);
        usuario.setActivo(true);
        usuario.setPasswordHash(passwordEncoder.encode(rawPassword));

        usuarioRepository.save(usuario);
    }
}