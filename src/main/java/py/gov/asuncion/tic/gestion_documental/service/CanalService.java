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

    // Se arma en cada llamada: el organigrama tiene unas 600 dependencias y así refleja cualquier cambio
    public Organigrama organigrama() {
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

    // El usuario actúa en nombre de su dependencia y de las que abarcan sus asignaciones de trámite
    public boolean puedeActuarPor(Usuario usuario, Dependencia dependencia) {
        if (usuario == null || dependencia == null) {
            return false;
        }
        if (usuario.getDependencia() != null && usuario.getDependencia().getId().equals(dependencia.getId())) {
            return true;
        }
        for (Asignacion a : asignacionRepository.findVigentes(usuario.getId(), LocalDate.now())) {
            if (!ROLES_QUE_TRAMITAN.contains(a.getRol())) {
                continue;
            }
            Dependencia ambito = a.getDependenciaAmbito();
            if (ambito.getId().equals(dependencia.getId())
                    || (Boolean.TRUE.equals(a.getIncluyeSubordinadas()) && dependencia.getRuta() != null
                        && dependencia.getRuta().startsWith(ambito.getRuta()))) {
                return true;
            }
        }
        return false;
    }
}
