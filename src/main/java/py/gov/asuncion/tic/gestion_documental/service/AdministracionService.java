package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.AsignacionRepository;
import py.gov.asuncion.tic.gestion_documental.repository.AuditoriaSistemaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;

// 👥 Administración de usuarios y asignaciones (docs/diseno-fase2-multiarea.md §6.1). Todo exige
// ADMINISTRAR_SISTEMA y queda en la auditoría del sistema. Las contraseñas temporales se muestran
// una sola vez al administrador y el usuario debe cambiarlas al entrar: nadie conoce la definitiva de otro.
@Service
public class AdministracionService {

    public record ContrasenaTemporal(Usuario usuario, String contrasena) {}

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int LARGO_TEMPORAL = 12;
    private static final SecureRandom AZAR = new SecureRandom();
    private static final java.time.format.DateTimeFormatter FECHA = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UsuarioRepository usuarioRepository;
    private final AsignacionRepository asignacionRepository;
    private final DependenciaRepository dependenciaRepository;
    private final AuditoriaSistemaRepository auditoriaRepository;
    private final PoliticaAcceso politicaAcceso;
    private final PasswordEncoder passwordEncoder;

    public AdministracionService(UsuarioRepository usuarioRepository, AsignacionRepository asignacionRepository,
                                 DependenciaRepository dependenciaRepository, AuditoriaSistemaRepository auditoriaRepository,
                                 PoliticaAcceso politicaAcceso, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.asignacionRepository = asignacionRepository;
        this.dependenciaRepository = dependenciaRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.politicaAcceso = politicaAcceso;
        this.passwordEncoder = passwordEncoder;
    }

    // =====================================================================
    // Consultas
    // =====================================================================

    public List<Usuario> buscarUsuarios(String texto, Usuario admin) {
        exigirAdministrador(admin);
        return usuarioRepository.buscar(texto == null ? "" : texto.trim());
    }

    public Usuario obtenerUsuario(Integer id, Usuario admin) {
        exigirAdministrador(admin);
        return usuarioRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
    }

    public List<Asignacion> asignacionesDe(Integer usuarioId, Usuario admin) {
        exigirAdministrador(admin);
        return asignacionRepository.findHistorial(usuarioId);
    }

    public List<AuditoriaSistema> auditoriaReciente(Usuario admin) {
        exigirAdministrador(admin);
        return auditoriaRepository.findRecientes(PageRequest.of(0, 100));
    }

    // =====================================================================
    // Usuarios
    // =====================================================================

    @Transactional
    public ContrasenaTemporal crearUsuario(String username, String nombreCompleto, String email, Integer dependenciaId, Usuario admin) {
        exigirAdministrador(admin);
        String usuarioLimpio = username == null ? "" : username.trim().toLowerCase();
        if (!usuarioLimpio.matches("^[a-z][a-z0-9._-]{2,49}$")) {
            throw new IllegalArgumentException("Usuario inválido: use minúsculas, números, punto, guion o guion bajo (3 a 50), empezando por una letra. Ej: juan.perez");
        }
        if (usuarioRepository.existsByUsernameIgnoreCase(usuarioLimpio)) {
            throw new IllegalArgumentException("Ya existe el usuario '" + usuarioLimpio + "' (puede estar dado de baja).");
        }
        Usuario u = new Usuario();
        u.setUsername(usuarioLimpio);
        u.setNombreCompleto(validarNombre(nombreCompleto));
        u.setEmail(validarEmail(email));
        u.setDependencia(dependenciaActiva(dependenciaId));
        u.setActivo(true);
        String temporal = generarContrasenaTemporal();
        u.setPasswordHash(passwordEncoder.encode(temporal));
        u.setDebeCambiarContrasena(true);
        Usuario guardado = usuarioRepository.save(u);
        auditar(admin, "ALTA_USUARIO", "USUARIO", guardado.getId().longValue(),
                "Alta de " + guardado.getUsername() + " (" + guardado.getNombreCompleto() + ") en " + guardado.getDependencia().getNombreVisible());
        return new ContrasenaTemporal(guardado, temporal);
    }

    @Transactional
    public Usuario actualizarUsuario(Integer id, String nombreCompleto, String email, Integer dependenciaId, Usuario admin) {
        exigirAdministrador(admin);
        Usuario u = obtenerUsuario(id, admin);
        StringBuilder cambios = new StringBuilder();
        String nombre = validarNombre(nombreCompleto);
        if (!nombre.equals(u.getNombreCompleto())) {
            cambios.append("nombre: ").append(u.getNombreCompleto()).append(" → ").append(nombre).append("; ");
            u.setNombreCompleto(nombre);
        }
        String correo = validarEmail(email);
        if (!java.util.Objects.equals(correo, u.getEmail())) {
            cambios.append("correo: ").append(u.getEmail()).append(" → ").append(correo).append("; ");
            u.setEmail(correo);
        }
        Dependencia dep = dependenciaActiva(dependenciaId);
        if (!dep.getId().equals(u.getDependencia().getId())) {
            // La propia dependencia define lo que se ve sin asignaciones de trámite: no se la cambia uno mismo
            if (u.getId().equals(admin.getId())) {
                throw new IllegalStateException("No puede cambiar su propia dependencia: debe hacerlo otro administrador.");
            }
            cambios.append("dependencia: ").append(u.getDependencia().getNombreVisible()).append(" → ").append(dep.getNombreVisible()).append("; ");
            u.setDependencia(dep);
        }
        if (cambios.isEmpty()) {
            return u;
        }
        Usuario guardado = usuarioRepository.save(u);
        auditar(admin, "MODIFICACION_USUARIO", "USUARIO", id.longValue(), u.getUsername() + ": " + cambios.toString().trim());
        return guardado;
    }

    // Baja lógica: el usuario no puede entrar, pero su historial (expedientes, actuaciones, auditoría) se conserva
    @Transactional
    public Usuario cambiarEstado(Integer id, boolean activo, String motivo, Usuario admin) {
        exigirAdministrador(admin);
        Usuario u = obtenerUsuario(id, admin);
        if (Boolean.valueOf(activo).equals(u.getActivo())) {
            return u;
        }
        if (!activo) {
            if (u.getId().equals(admin.getId())) {
                throw new IllegalStateException("No puede dar de baja su propio usuario.");
            }
            if (motivo == null || motivo.trim().length() < 5) {
                throw new IllegalArgumentException("Indique el motivo de la baja (mínimo 5 caracteres).");
            }
            if (esAdministradorVigente(u) && asignacionRepository.contarAdministradoresVigentes(LocalDate.now()) <= 1) {
                throw new IllegalStateException("Es el único administrador del sistema: asigne otro antes de darlo de baja.");
            }
        }
        u.setActivo(activo);
        Usuario guardado = usuarioRepository.save(u);
        auditar(admin, activo ? "REACTIVACION_USUARIO" : "BAJA_USUARIO", "USUARIO", id.longValue(),
                u.getUsername() + (activo ? " reactivado" : " dado de baja. Motivo: " + motivo.trim()));
        return guardado;
    }

    @Transactional
    public ContrasenaTemporal restablecerContrasena(Integer id, Usuario admin) {
        exigirAdministrador(admin);
        Usuario u = obtenerUsuario(id, admin);
        if (!Boolean.TRUE.equals(u.getActivo())) {
            throw new IllegalStateException("El usuario está dado de baja: reactívelo antes de restablecer su contraseña.");
        }
        String temporal = generarContrasenaTemporal();
        u.setPasswordHash(passwordEncoder.encode(temporal));
        u.setDebeCambiarContrasena(true);
        usuarioRepository.save(u);
        auditar(admin, "RESETEO_CONTRASENA", "USUARIO", id.longValue(),
                "Contraseña temporal generada para " + u.getUsername() + " (debe cambiarla al entrar)");
        return new ContrasenaTemporal(u, temporal);
    }

    // Cualquier usuario cambia su propia contraseña (obligatorio tras un alta o un reseteo)
    @Transactional
    public void cambiarContrasenaPropia(Usuario usuario, String actual, String nueva, String repetida) {
        Usuario u = usuarioRepository.findById(usuario.getId())
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado."));
        if (actual == null || !passwordEncoder.matches(actual, u.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta.");
        }
        if (nueva == null || !nueva.equals(repetida)) {
            throw new IllegalArgumentException("La contraseña nueva y su repetición no coinciden.");
        }
        if (!ReglasContrasena.cumple(nueva)) {
            throw new IllegalArgumentException("La contraseña nueva debe tener " + ReglasContrasena.DESCRIPCION + ".");
        }
        if (passwordEncoder.matches(nueva, u.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña nueva debe ser distinta de la actual.");
        }
        u.setPasswordHash(passwordEncoder.encode(nueva));
        u.setDebeCambiarContrasena(false);
        usuarioRepository.save(u);
        auditar(u, "CAMBIO_CONTRASENA", "USUARIO", u.getId().longValue(), u.getUsername() + " cambió su contraseña");
    }

    // =====================================================================
    // Asignaciones (rol × ámbito × vigencia) y suplencias
    // =====================================================================

    @Transactional
    public Asignacion crearAsignacion(Integer usuarioId, RolAsignacion rol, Integer ambitoId, boolean incluyeSubordinadas,
                                      LocalDate desde, LocalDate hasta, boolean esSuplencia, Integer titularId,
                                      String actoAdministrativo, Usuario admin) {
        exigirAdministrador(admin);
        Usuario u = obtenerUsuario(usuarioId, admin);
        if (rol == null) {
            throw new IllegalArgumentException("Seleccione el rol.");
        }
        // Separación de funciones: administrar usuarios no da acceso a expedientes, y un administrador
        // no puede dárselo a sí mismo. Sus roles de trámite los asigna otro administrador.
        if (u.getId().equals(admin.getId()) && rol != RolAsignacion.ADMIN_SISTEMA) {
            throw new IllegalStateException("Un administrador no puede asignarse roles de trámite a sí mismo: debe hacerlo otro administrador.");
        }
        Dependencia ambito = dependenciaActiva(ambitoId);
        LocalDate inicio = desde != null ? desde : LocalDate.now();
        if (hasta != null && hasta.isBefore(inicio)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio.");
        }
        Usuario titular = null;
        if (esSuplencia) {
            if (hasta == null) {
                throw new IllegalArgumentException("Una suplencia necesita fecha de fin: vence sola ese día.");
            }
            if (titularId == null) {
                throw new IllegalArgumentException("Indique a quién suple.");
            }
            titular = usuarioRepository.findById(titularId)
                    .orElseThrow(() -> new IllegalArgumentException("Titular no encontrado: " + titularId));
            if (titular.getId().equals(u.getId())) {
                throw new IllegalArgumentException("Un usuario no puede suplirse a sí mismo.");
            }
        }
        String acto = actoAdministrativo == null ? "" : actoAdministrativo.trim();
        if (acto.length() < 3) {
            throw new IllegalArgumentException("Indique el acto administrativo que respalda la asignación (resolución, memo...).");
        }
        Asignacion a = asignacionRepository.save(Asignacion.builder()
                .usuario(u)
                .rol(rol)
                .dependenciaAmbito(ambito)
                .incluyeSubordinadas(incluyeSubordinadas)
                .vigenteDesde(inicio)
                .vigenteHasta(hasta)
                .esSuplencia(esSuplencia)
                .titular(titular)
                .actoAdministrativo(acto.length() > 100 ? acto.substring(0, 100) : acto)
                .creadaPor(admin)
                .build());
        auditar(admin, esSuplencia ? "ALTA_SUPLENCIA" : "ALTA_ASIGNACION", "ASIGNACION", a.getId(),
                u.getUsername() + ": " + rol + " en " + ambito.getNombreVisible()
                        + (incluyeSubordinadas ? " y subordinadas" : "")
                        + " desde " + inicio.format(FECHA) + (hasta != null ? " hasta " + hasta.format(FECHA) : " sin fecha de fin")
                        + (titular != null ? ", en suplencia de " + titular.getUsername() : "")
                        + ". Acto: " + acto);
        return a;
    }

    // Da de baja una asignación (no se borra: queda en el historial del usuario y en la auditoría)
    @Transactional
    public Asignacion finalizarAsignacion(Long asignacionId, String motivo, Usuario admin) {
        exigirAdministrador(admin);
        Asignacion a = asignacionRepository.findById(asignacionId)
                .orElseThrow(() -> new IllegalArgumentException("Asignación no encontrada: " + asignacionId));
        if (!Boolean.TRUE.equals(a.getActiva())) {
            return a;
        }
        if (motivo == null || motivo.trim().length() < 5) {
            throw new IllegalArgumentException("Indique el motivo (mínimo 5 caracteres).");
        }
        if (a.getRol() == RolAsignacion.ADMIN_SISTEMA && asignacionRepository.contarAdministradoresVigentes(LocalDate.now()) <= 1) {
            throw new IllegalStateException("Es la única asignación de administrador vigente: cree otra antes de quitar esta.");
        }
        a.setActiva(false);
        Asignacion guardada = asignacionRepository.save(a);
        auditar(admin, "BAJA_ASIGNACION", "ASIGNACION", a.getId(),
                a.getUsuario().getUsername() + ": se quita " + a.getRol() + " en " + a.getDependenciaAmbito().getNombreVisible()
                        + ". Motivo: " + motivo.trim());
        return guardada;
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private void exigirAdministrador(Usuario admin) {
        politicaAcceso.exigir(admin, Accion.ADMINISTRAR_SISTEMA, "Solo un administrador del sistema puede hacer esta operación.");
    }

    private boolean esAdministradorVigente(Usuario u) {
        return asignacionRepository.findVigentes(u.getId(), LocalDate.now()).stream()
                .anyMatch(a -> a.getRol() == RolAsignacion.ADMIN_SISTEMA);
    }

    private Dependencia dependenciaActiva(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("Seleccione la dependencia.");
        }
        Dependencia d = dependenciaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dependencia no encontrada: " + id));
        if (Boolean.FALSE.equals(d.getActivo())) {
            throw new IllegalArgumentException("La dependencia '" + d.getNombre() + "' está dada de baja.");
        }
        return d;
    }

    private static String validarNombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
        if (limpio.length() < 5 || limpio.length() > 150) {
            throw new IllegalArgumentException("Indique el nombre completo (5 a 150 caracteres).");
        }
        return limpio;
    }

    private static String validarEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String limpio = email.trim().toLowerCase();
        if (!limpio.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || limpio.length() > 255) {
            throw new IllegalArgumentException("Correo inválido: " + email);
        }
        return limpio;
    }

    static String generarContrasenaTemporal() {
        StringBuilder sb;
        do {
            sb = new StringBuilder();
            for (int i = 0; i < LARGO_TEMPORAL; i++) {
                sb.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
            }
        } while (!ReglasContrasena.cumple(sb.toString()));
        return sb.toString();
    }

    private void auditar(Usuario admin, String accion, String entidad, Long entidadId, String detalle) {
        auditoriaRepository.save(AuditoriaSistema.builder()
                .usuario(admin)
                .accion(accion)
                .entidad(entidad)
                .entidadId(entidadId)
                .detalle(detalle)
                .build());
    }
}
