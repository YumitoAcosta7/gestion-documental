package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.stereotype.Service;
import py.gov.asuncion.tic.gestion_documental.domain.Asignacion;
import py.gov.asuncion.tic.gestion_documental.domain.Dependencia;
import py.gov.asuncion.tic.gestion_documental.domain.RolAsignacion;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.AsignacionRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;

import java.time.LocalDate;
import java.util.*;

// 🧭 Canal jerárquico con los datos de la base: arma el Organigrama y responde
// a dónde puede pasar un expediente y en nombre de qué dependencia actúa un usuario.
@Service
public class CanalService {

    private static final Set<RolAsignacion> ROLES_QUE_TRAMITAN =
            EnumSet.of(RolAsignacion.MESA_ENTRADA, RolAsignacion.RESPONSABLE, RolAsignacion.OPERADOR);

    private final DependenciaRepository dependenciaRepository;
    private final AsignacionRepository asignacionRepository;

    public CanalService(DependenciaRepository dependenciaRepository, AsignacionRepository asignacionRepository) {
        this.dependenciaRepository = dependenciaRepository;
        this.asignacionRepository = asignacionRepository;
    }

    // Organigrama en memoria con la versión de la base con que se armó (auditoría B1).
    // Organigrama no se modifica después de construido, así que se comparte entre peticiones.
    private record Cache(long version, Organigrama organigrama) {}

    private volatile Cache cache;

    // Versión del organigrama: la incrementan los triggers de V16 en cada cambio de dependencias,
    // niveles o atajos. Leerla es una consulta de una sola fila.
    public long versionOrganigrama() {
        return dependenciaRepository.versionOrganigrama();
    }

    // Se rearma solo cuando cambió la versión: el organigrama tiene unas 600 dependencias
    public Organigrama organigrama() {
        long version = versionOrganigrama();
        Cache actual = cache;
        if (actual != null && actual.version() == version) {
            return actual.organigrama();
        }
        Organigrama nuevo = armarOrganigrama();
        cache = new Cache(version, nuevo);
        return nuevo;
    }

    private Organigrama armarOrganigrama() {
        Map<Integer, Integer> ordenPorNivel = new HashMap<>();
        dependenciaRepository.findNiveles().forEach(n -> ordenPorNivel.put(n.getId(), n.getOrdenNivel()));
        List<Organigrama.Nodo> nodos = dependenciaRepository.findAll().stream()
                .map(d -> new Organigrama.Nodo(d.getId(), d.getNombre(), d.getSigla(), d.getDependenciaPadreId(),
                        ordenPorNivel.getOrDefault(d.getNivelJerarquicoId(), 99),
                        !Boolean.FALSE.equals(d.getActivo()), Boolean.TRUE.equals(d.getEsMesaEntrada())))
                .toList();
        Map<Integer, Set<Integer>> atajos = new HashMap<>();
        for (Object[] fila : dependenciaRepository.findAtajosActivos()) {
            atajos.computeIfAbsent(((Number) fila[0]).intValue(), k -> new HashSet<>()).add(((Number) fila[1]).intValue());
        }
        return new Organigrama(nodos, atajos);
    }

    // Dónde queda registrado y en custodia un expediente que carga el usuario:
    // la mesa de su asignación MESA_ENTRADA, o su propia dependencia
    public Dependencia dependenciaDeRegistro(Usuario usuario) {
        Organigrama org = organigrama();
        for (Asignacion a : asignacionRepository.findVigentes(usuario.getId(), LocalDate.now())) {
            if (a.getRol() == RolAsignacion.MESA_ENTRADA) {
                Organigrama.Nodo mesa = org.puntoDeRecepcion(org.cabecera(org.nodo(a.getDependenciaAmbito().getId())));
                return dependenciaRepository.findById(mesa.id()).orElse(usuario.getDependencia());
            }
        }
        return usuario.getDependencia();
    }

    // Dirección (o cabecera) en la que trabaja el usuario: la de su mesa o la de su dependencia.
    // Es la que se muestra como título de la pantalla.
    public Organigrama.Nodo cabeceraDelUsuario(Usuario usuario) {
        Dependencia trabajo = dependenciaDeRegistro(usuario);
        if (trabajo == null) {
            return null;
        }
        Organigrama org = organigrama();
        return org.cabecera(org.nodo(trabajo.getId()));
    }

    // El usuario actúa en nombre de su dependencia y de las que abarcan sus asignaciones de trámite
    public boolean puedeActuarPor(Usuario usuario, Dependencia dependencia) {
        if (usuario == null || dependencia == null) {
            return false;
        }
        return actuaPor(usuario, asignacionRepository.findVigentes(usuario.getId(), LocalDate.now()), dependencia);
    }

    // La misma regla para muchas dependencias, con una sola consulta de asignaciones (una página de la bandeja)
    public java.util.function.Predicate<Dependencia> alcance(Usuario usuario) {
        List<Asignacion> vigentes = asignacionRepository.findVigentes(usuario.getId(), LocalDate.now());
        return dependencia -> dependencia != null && actuaPor(usuario, vigentes, dependencia);
    }

    private static boolean actuaPor(Usuario usuario, List<Asignacion> vigentes, Dependencia dependencia) {
        // Auditoría A5: por una mesa de entrada pasa el original de toda su dirección. Pertenecer a ella no alcanza:
        // hace falta una asignación, que requiere la aprobación de otro administrador
        // Auditoría M6 y M12: sin asignación vigente no se actúa, y una cuenta de administración no actúa por ninguna dependencia
        if (vigentes.isEmpty() || vigentes.stream().anyMatch(a -> a.getRol() == RolAsignacion.ADMIN_SISTEMA)) {
            return false;
        }
        if (usuario.getDependencia() != null && usuario.getDependencia().getId().equals(dependencia.getId())
                && !Boolean.TRUE.equals(dependencia.getEsMesaEntrada())) {
            return true;
        }
        for (Asignacion a : vigentes) {
            if (!ROLES_QUE_TRAMITAN.contains(a.getRol())) {
                continue;
            }
            Dependencia ambito = a.getDependenciaAmbito();
            // Auditoría M5: el OPERADOR trabaja en su dependencia; recibir o devolver por las subordinadas
            // es de la mesa de entrada o del responsable
            boolean abarcaSubordinadas = Boolean.TRUE.equals(a.getIncluyeSubordinadas()) && a.getRol() != RolAsignacion.OPERADOR;
            if (ambito.getId().equals(dependencia.getId())
                    || (abarcaSubordinadas && dependencia.getRuta() != null
                        && dependencia.getRuta().startsWith(ambito.getRuta()))) {
                return true;
            }
        }
        return false;
    }
}
