package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.AsignacionRepository;
import py.gov.asuncion.tic.gestion_documental.repository.AuditoriaSistemaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.SolicitudContrasenaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
    private final SessionRegistry sessionRegistry;
    private final SolicitudContrasenaRepository solicitudContrasenaRepository;

    public AdministracionService(UsuarioRepository usuarioRepository, AsignacionRepository asignacionRepository,
                                 DependenciaRepository dependenciaRepository, AuditoriaSistemaRepository auditoriaRepository,
                                 PoliticaAcceso politicaAcceso, PasswordEncoder passwordEncoder, SessionRegistry sessionRegistry,
                                 SolicitudContrasenaRepository solicitudContrasenaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.asignacionRepository = asignacionRepository;
        this.dependenciaRepository = dependenciaRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.politicaAcceso = politicaAcceso;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
        this.solicitudContrasenaRepository = solicitudContrasenaRepository;
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
        if (!activo) {
            expirarSesiones(u.getUsername());
        }
        auditar(admin, activo ? "REACTIVACION_USUARIO" : "BAJA_USUARIO", "USUARIO", id.longValue(),
                u.getUsername() + (activo ? " reactivado" : " dado de baja. Motivo: " + motivo.trim()));
        return guardado;
    }

    // Quien recibe la contraseña temporal puede entrar en nombre de esa cuenta (auditoría A5). En una cuenta sensible,
    // la pide un administrador y la aprueba otro: el resultado vacío significa que quedó pendiente. Con un solo
    // administrador no hay quién apruebe: se hace igual, y queda dicho en la auditoría.
    @Transactional
    public Optional<ContrasenaTemporal> restablecerContrasena(Integer id, String motivo, Usuario admin) {
        exigirAdministrador(admin);
        Usuario u = obtenerUsuario(id, admin);
        if (!Boolean.TRUE.equals(u.getActivo())) {
            throw new IllegalStateException("El usuario está dado de baja: reactívelo antes de restablecer su contraseña.");
        }
        if (!esCuentaSensible(u)) {
            return Optional.of(generarTemporal(u, admin, "Contraseña temporal generada para " + u.getUsername() + " (debe cambiarla al entrar)"));
        }
        if (motivo == null || motivo.trim().length() < 10) {
            throw new IllegalArgumentException("Es una cuenta sensible: indique el motivo (mínimo 10 caracteres).");
        }
        if (asignacionRepository.contarAdministradoresVigentes(LocalDate.now()) <= 1) {
            return Optional.of(generarTemporal(u, admin, "Contraseña temporal generada para " + u.getUsername()
                    + ", cuenta sensible, sin segunda firma porque hay un solo administrador. Motivo: " + motivo.trim()));
        }
        if (solicitudContrasenaRepository.existePendiente(u.getId())) {
            throw new IllegalStateException(u.getUsername() + " ya tiene una solicitud de restablecimiento pendiente.");
        }
        SolicitudContrasena solicitud = solicitudContrasenaRepository.save(SolicitudContrasena.builder()
                .usuario(u).motivo(motivo.trim()).solicitadaPor(admin).build());
        auditar(admin, "SOLICITUD_CONTRASENA", "USUARIO", id.longValue(), "Restablecimiento pedido para " + u.getUsername()
                + " (solicitud " + solicitud.getId() + "). Motivo: " + motivo.trim());
        return Optional.empty();
    }

    // Administrador, o mesa de entrada o responsable de una cabecera o de una mesa
    public boolean esCuentaSensible(Usuario u) {
        return asignacionRepository.findVigentes(u.getId(), LocalDate.now()).stream()
                .anyMatch(a -> requiereDobleControl(a.getRol(), a.getDependenciaAmbito()));
    }

    private ContrasenaTemporal generarTemporal(Usuario u, Usuario admin, String detalle) {
        String temporal = generarContrasenaTemporal();
        u.setPasswordHash(passwordEncoder.encode(temporal));
        u.setDebeCambiarContrasena(true);
        usuarioRepository.save(u);
        expirarSesiones(u.getUsername()); // si alguien usaba la cuenta, queda afuera
        auditar(admin, "RESETEO_CONTRASENA", "USUARIO", u.getId().longValue(), detalle);
        return new ContrasenaTemporal(u, temporal);
    }

    private SolicitudContrasena solicitudPendiente(Long solicitudId) {
        SolicitudContrasena s = solicitudContrasenaRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada: " + solicitudId));
        if (!SolicitudContrasena.PENDIENTE.equals(s.getEstado())) {
            throw new IllegalStateException("La solicitud ya fue resuelta.");
        }
        return s;
    }

    // La temporal la recibe quien aprueba, se muestra una sola vez y no queda en ningún registro
    @Transactional
    public ContrasenaTemporal aprobarContrasena(Long solicitudId, Usuario admin) {
        exigirAdministrador(admin);
        SolicitudContrasena s = solicitudPendiente(solicitudId);
        if (s.getUsuario().getId().equals(admin.getId())) {
            throw new IllegalStateException("No puede aprobar el restablecimiento de su propia cuenta: debe hacerlo otro administrador.");
        }
        if (s.getSolicitadaPor().getId().equals(admin.getId())) {
            throw new IllegalStateException("No puede aprobar un restablecimiento que pidió usted: debe hacerlo otro administrador.");
        }
        if (!Boolean.TRUE.equals(s.getUsuario().getActivo())) {
            throw new IllegalStateException(s.getUsuario().getUsername() + " está dado de baja: rechace la solicitud.");
        }
        s.setEstado(SolicitudContrasena.APROBADA);
        s.setResueltaPor(admin);
        s.setFechaResolucion(java.time.LocalDateTime.now());
        solicitudContrasenaRepository.save(s);
        return generarTemporal(s.getUsuario(), admin, "Contraseña temporal generada para " + s.getUsuario().getUsername()
                + "; la pidió " + s.getSolicitadaPor().getUsername() + ". Motivo: " + s.getMotivo());
    }

    // Quien la pidió también puede retirarla; el dueño de la cuenta no la resuelve
    @Transactional
    public void rechazarContrasena(Long solicitudId, String motivo, Usuario admin) {
        exigirAdministrador(admin);
        SolicitudContrasena s = solicitudPendiente(solicitudId);
        if (s.getUsuario().getId().equals(admin.getId())) {
            throw new IllegalStateException("No puede resolver el restablecimiento de su propia cuenta.");
        }
        if (motivo == null || motivo.trim().length() < 5) {
            throw new IllegalArgumentException("Indique el motivo (mínimo 5 caracteres).");
        }
        s.setEstado(SolicitudContrasena.RECHAZADA);
        s.setResueltaPor(admin);
        s.setFechaResolucion(java.time.LocalDateTime.now());
        s.setMotivoRechazo(motivo.trim());
        solicitudContrasenaRepository.save(s);
        auditar(admin, "RECHAZO_CONTRASENA", "USUARIO", s.getUsuario().getId().longValue(),
                "Restablecimiento rechazado para " + s.getUsuario().getUsername() + ". Motivo: " + motivo.trim());
    }

    public List<SolicitudContrasena> contrasenasPendientes(Usuario admin) {
        exigirAdministrador(admin);
        return solicitudContrasenaRepository.findPendientes();
    }

    @Transactional
    public void cambiarContrasenaPropia(Usuario usuario, String actual, String nueva, String repetida) {
        cambiarContrasenaPropia(usuario, actual, nueva, repetida, null);
    }

    // sesionActual: la sesión desde la que se cambia, que sigue abierta. Las demás del usuario se cierran:
    // si alguien conocía la contraseña anterior y había entrado, queda afuera.
    @Transactional
    public void cambiarContrasenaPropia(Usuario usuario, String actual, String nueva, String repetida, String sesionActual) {
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
        int cerradas = expirarSesiones(u.getUsername(), sesionActual);
        auditar(u, "CAMBIO_CONTRASENA", "USUARIO", u.getId().longValue(), u.getUsername() + " cambió su contraseña"
                + (cerradas > 0 ? "; se cerraron " + cerradas + " sesiones abiertas en otros equipos" : ""));
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
        // El registro tiene que reflejar cuándo se dio el permiso: no se cargan asignaciones retroactivas
        if (inicio.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La asignación no puede empezar antes de hoy.");
        }
        // Una asignación igual (mismo rol y ámbito) con fechas superpuestas sería un duplicado,
        // también si la otra todavía espera aprobación
        for (Asignacion existente : asignacionRepository.findHistorial(u.getId())) {
            if ((Boolean.TRUE.equals(existente.getActiva()) || existente.isPendiente()) && existente.getRol() == rol
                    && existente.getDependenciaAmbito().getId().equals(ambito.getId())
                    && seSuperponen(existente.getVigenteDesde(), existente.getVigenteHasta(), inicio, hasta)) {
                throw new IllegalStateException("El usuario ya tiene " + rol + " en " + ambito.getNombreVisible()
                        + " en esas fechas: quite esa asignación o ajuste el período.");
            }
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
            // Auditoría A3: la suplencia no da más de lo que tiene el titular (mismo rol, ámbito y período cubiertos)
            if (!titularCubre(titular, rol, ambito, incluyeSubordinadas, inicio, hasta)) {
                throw new IllegalStateException(titular.getUsername() + " no tiene " + rol + " en " + ambito.getNombreVisible()
                        + (incluyeSubordinadas ? " (con sus subordinadas)" : "")
                        + " durante todo el período: la suplencia no puede dar más de lo que tiene el titular.");
            }
        }
        String acto = actoAdministrativo == null ? "" : actoAdministrativo.trim();
        if (acto.length() < 3) {
            throw new IllegalArgumentException("Indique el acto administrativo que respalda la asignación (resolución, memo...).");
        }
        // Auditoría C2: las sensibles quedan pendientes (inactivas) hasta que las apruebe otro administrador
        boolean sensible = requiereDobleControl(rol, ambito);
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
                .activa(!sensible)
                .estadoAprobacion(sensible ? Asignacion.PENDIENTE : Asignacion.APROBADA)
                .build());
        String accion = sensible ? "SOLICITUD_ASIGNACION" : esSuplencia ? "ALTA_SUPLENCIA" : "ALTA_ASIGNACION";
        auditar(admin, accion, "ASIGNACION", a.getId(), describir(a)
                + (sensible ? ". Queda pendiente: la debe aprobar otro administrador" : ""));
        return a;
    }

    // ADMIN_SISTEMA siempre; MESA_ENTRADA y RESPONSABLE cuando el ámbito es una Dirección o superior,
    // porque dan acceso a todo lo que pasa por esa cabecera. Cualquier rol de trámite en una mesa de entrada
    // también, aunque sea una unidad: por ella pasa el original de toda su dirección (auditoría A5)
    boolean requiereDobleControl(RolAsignacion rol, Dependencia ambito) {
        if (rol == RolAsignacion.ADMIN_SISTEMA) {
            return true;
        }
        if (Boolean.TRUE.equals(ambito.getEsMesaEntrada()) && rol != RolAsignacion.CONSULTA) {
            return true;
        }
        if (rol != RolAsignacion.MESA_ENTRADA && rol != RolAsignacion.RESPONSABLE) {
            return false;
        }
        int orden = dependenciaRepository.findNiveles().stream()
                .filter(n -> n.getId().equals(ambito.getNivelJerarquicoId()))
                .map(n -> n.getOrdenNivel())
                .findFirst().orElse(99);
        return orden <= Organigrama.ORDEN_DIRECCION;
    }

    @Transactional
    public Asignacion aprobarAsignacion(Long asignacionId, Usuario admin) {
        exigirAdministrador(admin);
        Asignacion a = pendiente(asignacionId);
        if (a.getUsuario().getId().equals(admin.getId())) {
            throw new IllegalStateException("No puede aprobar una asignación para usted mismo: debe hacerlo otro administrador.");
        }
        if (a.getCreadaPor() != null && a.getCreadaPor().getId().equals(admin.getId())) {
            throw new IllegalStateException("No puede aprobar una asignación que pidió usted: debe hacerlo otro administrador.");
        }
        if (!Boolean.TRUE.equals(a.getUsuario().getActivo())) {
            throw new IllegalStateException(a.getUsuario().getUsername() + " está dado de baja: rechace la solicitud.");
        }
        if (a.getVigenteHasta() != null && a.getVigenteHasta().isBefore(LocalDate.now())) {
            throw new IllegalStateException("El período de la asignación ya terminó: rechácela y pida una nueva.");
        }
        a.setEstadoAprobacion(Asignacion.APROBADA);
        a.setActiva(true);
        a.setAprobadaPor(admin);
        a.setFechaHoraAprobacion(java.time.LocalDateTime.now());
        Asignacion guardada = asignacionRepository.save(a);
        auditar(admin, "APROBACION_ASIGNACION", "ASIGNACION", a.getId(), "Aprobada: " + describir(a)
                + (a.getCreadaPor() != null ? ". Pedida por " + a.getCreadaPor().getUsername() : ""));
        return guardada;
    }

    // Rechaza una solicitud pendiente. Quien la pidió también puede retirarla; el beneficiario no.
    @Transactional
    public Asignacion rechazarAsignacion(Long asignacionId, String motivo, Usuario admin) {
        exigirAdministrador(admin);
        Asignacion a = pendiente(asignacionId);
        if (a.getUsuario().getId().equals(admin.getId())) {
            throw new IllegalStateException("No puede resolver una asignación para usted mismo.");
        }
        if (motivo == null || motivo.trim().length() < 5) {
            throw new IllegalArgumentException("Indique el motivo (mínimo 5 caracteres).");
        }
        a.setEstadoAprobacion(Asignacion.RECHAZADA);
        a.setActiva(false);
        Asignacion guardada = asignacionRepository.save(a);
        auditar(admin, "RECHAZO_ASIGNACION", "ASIGNACION", a.getId(), "Rechazada: " + describir(a) + ". Motivo: " + motivo.trim());
        return guardada;
    }

    public List<Asignacion> pendientesDeAprobacion(Usuario admin) {
        exigirAdministrador(admin);
        return asignacionRepository.findPendientesDeAprobacion();
    }

    private Asignacion pendiente(Long asignacionId) {
        Asignacion a = asignacionRepository.findById(asignacionId)
                .orElseThrow(() -> new IllegalArgumentException("Asignación no encontrada: " + asignacionId));
        if (!a.isPendiente()) {
            throw new IllegalStateException("La asignación ya fue resuelta.");
        }
        return a;
    }

    private static String describir(Asignacion a) {
        return a.getUsuario().getUsername() + ": " + a.getRol() + " en " + a.getDependenciaAmbito().getNombreVisible()
                + (Boolean.TRUE.equals(a.getIncluyeSubordinadas()) ? " y subordinadas" : "")
                + " desde " + a.getVigenteDesde().format(FECHA)
                + (a.getVigenteHasta() != null ? " hasta " + a.getVigenteHasta().format(FECHA) : " sin fecha de fin")
                + (a.getTitular() != null ? ", en suplencia de " + a.getTitular().getUsername() : "")
                + ". Acto: " + a.getActoAdministrativo();
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

    // ¿Alguna asignación activa del titular (no suplencia) cubre el rol, el ámbito y todo el período?
    private boolean titularCubre(Usuario titular, RolAsignacion rol, Dependencia ambito, boolean incluyeSubordinadas,
                                 LocalDate desde, LocalDate hasta) {
        for (Asignacion t : asignacionRepository.findHistorial(titular.getId())) {
            if (!Boolean.TRUE.equals(t.getActiva()) || t.getRol() != rol || Boolean.TRUE.equals(t.getEsSuplencia())) {
                continue;
            }
            Dependencia suyo = t.getDependenciaAmbito();
            boolean mismoAmbito = suyo.getId().equals(ambito.getId());
            boolean dentroDelSuyo = Boolean.TRUE.equals(t.getIncluyeSubordinadas()) && suyo.getRuta() != null
                    && ambito.getRuta() != null && ambito.getRuta().startsWith(suyo.getRuta());
            boolean subordinadasCubiertas = !incluyeSubordinadas || Boolean.TRUE.equals(t.getIncluyeSubordinadas());
            boolean periodoCubierto = !t.getVigenteDesde().isAfter(desde)
                    && (t.getVigenteHasta() == null || (hasta != null && !t.getVigenteHasta().isBefore(hasta)));
            if ((mismoAmbito || dentroDelSuyo) && subordinadasCubiertas && periodoCubierto) {
                return true;
            }
        }
        return false;
    }

    // Períodos [desde, hasta] con hasta = null como "sin fin"
    static boolean seSuperponen(LocalDate desde1, LocalDate hasta1, LocalDate desde2, LocalDate hasta2) {
        boolean primeroTerminaAntes = hasta1 != null && hasta1.isBefore(desde2);
        boolean segundoTerminaAntes = hasta2 != null && hasta2.isBefore(desde1);
        return !primeroTerminaAntes && !segundoTerminaAntes;
    }

    // Cierra todas las sesiones abiertas del usuario (auditoría A2): la siguiente petición vuelve al inicio de sesión
    private void expirarSesiones(String username) {
        expirarSesiones(username, null);
    }

    // Cierra las sesiones del usuario salvo la indicada (null: todas). Devuelve cuántas cerró.
    private int expirarSesiones(String username, String salvoSesion) {
        int cerradas = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UserDetails detalle && detalle.getUsername().equals(username)) {
                for (var sesion : sessionRegistry.getAllSessions(principal, false)) {
                    if (!sesion.getSessionId().equals(salvoSesion)) {
                        sesion.expireNow();
                        cerradas++;
                    }
                }
            }
        }
        return cerradas;
    }

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
        auditoriaRepository.marcarIp(AuditoriaService.ipDeLaPeticion());
        auditoriaRepository.save(AuditoriaSistema.builder()
                .usuario(admin)
                .accion(accion)
                .entidad(entidad)
                .entidadId(entidadId)
                .detalle(detalle)
                .build());
    }
}
