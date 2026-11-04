package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import py.gov.asuncion.tic.gestion_documental.domain.Accion;
import py.gov.asuncion.tic.gestion_documental.domain.Asignacion;
import py.gov.asuncion.tic.gestion_documental.domain.RolAsignacion;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.AsignacionRepository;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 🧭 Único punto que decide qué acciones puede hacer un usuario (docs/diseno-fase2-multiarea.md §3.2).
// Las acciones salen de sus asignaciones vigentes hoy: una suplencia que vence deja de habilitar
// sola, sin volver a iniciar sesión. Sobre qué expedientes se aplican lo decide el ámbito
// (DocumentoService.puedeAccederDocumento).
@Service
public class PoliticaAcceso {

    private static final Map<RolAsignacion, Set<Accion>> ACCIONES_POR_ROL = Map.of(
            // La mesa de entrada hace todo el trámite, pero no administra el sistema
            RolAsignacion.MESA_ENTRADA, EnumSet.complementOf(EnumSet.of(Accion.ADMINISTRAR_SISTEMA)),
            RolAsignacion.RESPONSABLE, EnumSet.of(Accion.DERIVAR, Accion.REGISTRAR_ACTUACION,
                    Accion.FORZAR_CIERRE, Accion.VER_AUDITORIA),
            RolAsignacion.OPERADOR, EnumSet.of(Accion.REGISTRAR_ACTUACION),
            // Administra usuarios y catálogos; no ve expedientes por esta asignación
            RolAsignacion.ADMIN_SISTEMA, EnumSet.of(Accion.ADMINISTRAR_SISTEMA),
            RolAsignacion.CONSULTA, EnumSet.noneOf(Accion.class));

    // Auditoría M12: sin asignación vigente no se trabaja. Toda asignación la aprueba otro administrador,
    // así que nadie crea una cuenta que tramite por su cuenta

    private final AsignacionRepository asignacionRepository;

    public PoliticaAcceso(AsignacionRepository asignacionRepository) {
        this.asignacionRepository = asignacionRepository;
    }

    public Set<Accion> acciones(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            return EnumSet.noneOf(Accion.class);
        }
        List<Asignacion> vigentes = asignacionRepository.findVigentes(usuario.getId(), LocalDate.now());
        // Auditoría M6: una cuenta de administración solo administra, aunque tenga otras asignaciones
        if (vigentes.stream().anyMatch(a -> a.getRol() == RolAsignacion.ADMIN_SISTEMA)) {
            return EnumSet.of(Accion.ADMINISTRAR_SISTEMA);
        }
        Set<Accion> acciones = EnumSet.noneOf(Accion.class);
        vigentes.forEach(a -> acciones.addAll(ACCIONES_POR_ROL.get(a.getRol())));
return acciones;
    }

    // Tiene una asignación vigente y no es una cuenta de administración: puede ver lo de su dependencia
    public boolean trabajaEnExpedientes(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            return false;
        }
        List<Asignacion> vigentes = asignacionRepository.findVigentes(usuario.getId(), LocalDate.now());
        return !vigentes.isEmpty() && vigentes.stream().noneMatch(a -> a.getRol() == RolAsignacion.ADMIN_SISTEMA);
    }

    public boolean puede(Usuario usuario, Accion accion) {
        return acciones(usuario).contains(accion);
    }

    public void exigir(Usuario usuario, Accion accion, String mensaje) {
        if (!puede(usuario, accion)) {
            throw new AccessDeniedException(mensaje);
        }
    }

    // Para mostrar junto al nombre del usuario: "Mesa de entrada · DTIC, Responsable · DTIC"
    public String descripcionRoles(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            return "";
        }
        List<Asignacion> vigentes = asignacionRepository.findVigentes(usuario.getId(), LocalDate.now());
        if (vigentes.isEmpty()) {
            return "Sin asignación vigente: consulte a un administrador";
        }
        return vigentes.stream()
                .map(a -> {
                    String rol = a.getRol().name().replace('_', ' ').toLowerCase();
                    String sigla = a.getDependenciaAmbito().getSigla();
                    return Character.toUpperCase(rol.charAt(0)) + rol.substring(1) + (sigla != null ? " · " + sigla : "");
                })
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    public List<GrantedAuthority> autoridades(Usuario usuario) {
        return acciones(usuario).stream().<GrantedAuthority>map(a -> new SimpleGrantedAuthority(a.autoridad())).toList();
    }
}
