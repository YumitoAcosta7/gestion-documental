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
            RolAsignacion.MESA_ENTRADA, EnumSet.allOf(Accion.class),
            RolAsignacion.RESPONSABLE, EnumSet.of(Accion.DERIVAR, Accion.REGISTRAR_ACTUACION,
                    Accion.FORZAR_CIERRE, Accion.VER_AUDITORIA),
            RolAsignacion.OPERADOR, EnumSet.of(Accion.REGISTRAR_ACTUACION),
            RolAsignacion.ADMIN_SISTEMA, EnumSet.of(Accion.GESTIONAR_ARCHIVO, Accion.VER_AUDITORIA),
            RolAsignacion.CONSULTA, EnumSet.noneOf(Accion.class));

    // Sin asignaciones vigentes trabaja como operador de su propia dependencia
    private static final Set<Accion> SIN_ASIGNACIONES = EnumSet.of(Accion.REGISTRAR_ACTUACION);

    private final AsignacionRepository asignacionRepository;

    public PoliticaAcceso(AsignacionRepository asignacionRepository) {
        this.asignacionRepository = asignacionRepository;
    }

    public Set<Accion> acciones(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            return EnumSet.noneOf(Accion.class);
        }
        List<Asignacion> vigentes = asignacionRepository.findVigentes(usuario.getId(), LocalDate.now());
        if (vigentes.isEmpty()) {
            return EnumSet.copyOf(SIN_ASIGNACIONES);
        }
        Set<Accion> acciones = EnumSet.noneOf(Accion.class);
        vigentes.forEach(a -> acciones.addAll(ACCIONES_POR_ROL.get(a.getRol())));
        return acciones;
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
            return "Operador";
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
