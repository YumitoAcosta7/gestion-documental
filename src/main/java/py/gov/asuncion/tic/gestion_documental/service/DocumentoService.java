package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityNotFoundException;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.dto.NuevoDocumentoDTO;
import py.gov.asuncion.tic.gestion_documental.repository.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

import java.time.LocalDate;
import java.time.LocalTime;

@Service
public class DocumentoService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private DependenciaRepository dependenciaRepository;

    @Autowired
    private TipoDocumentoRepository tipoDocumentoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AdjuntoDocumentoRepository adjuntoDocumentoRepository;

    @Autowired
    private ActuacionDocumentoRepository actuacionDocumentoRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private ArchivoService archivoService;

    @Autowired
    private BiblioratoService biblioratoService;

    @Autowired
    private AsignacionRepository asignacionRepository;

    @Autowired
    private PoliticaAcceso politicaAcceso;

    @Autowired
    private CanalService canalService;

    @Autowired
    private AuditoriaSistemaRepository auditoriaSistemaRepository;

    @Autowired
    private FeriadoService feriadoService;

    // Estados que ya no forman parte de las bandejas de trabajo
    private static final List<EstadoDocumento> ESTADOS_CERRADOS = List.of(EstadoDocumento.FINALIZADO, EstadoDocumento.ARCHIVADO);

    // ⏱️ Umbrales de demora (días hábiles sin movimiento), configurables en application.properties
    @Value("${app.sla.dias-advertencia:5}")
    private int slaDiasAdvertencia;

    @Value("${app.sla.dias-critico:10}")
    private int slaDiasCritico;

    @Value("${app.sla.urgente.dias-advertencia:2}")
    private int slaUrgenteDiasAdvertencia;

    @Value("${app.sla.urgente.dias-critico:4}")
    private int slaUrgenteDiasCritico;

    // 🏛️ Selectores de dependencias: con todo el organigrama hay muchos nombres repetidos
    // ("Unidad de Secretaría Administrativa", "Departamento Administrativo"...), así que se
    // agrupan por dirección, en orden jerárquico, y cada opción lleva la sigla de su dirección.
    public record OpcionDependencia(Integer id, String texto) {}

    public record GrupoDependencias(String etiqueta, List<OpcionDependencia> opciones) {}

    // Los grupos se arman una vez por versión del organigrama (auditoría B1), no en cada pantalla
    private record GruposEnCache(long version, List<GrupoDependencias> grupos) {}

    private volatile GruposEnCache gruposEnCache;

    public List<GrupoDependencias> obtenerDependenciasAgrupadas() {
        long version = canalService.versionOrganigrama();
        GruposEnCache actual = gruposEnCache;
        if (actual != null && actual.version() == version) {
            return actual.grupos();
        }
        List<GrupoDependencias> grupos = List.copyOf(armarDependenciasAgrupadas());
        gruposEnCache = new GruposEnCache(version, grupos);
        return grupos;
    }

    private List<GrupoDependencias> armarDependenciasAgrupadas() {
        Map<Integer, Integer> ordenPorNivel = new HashMap<>();
        dependenciaRepository.findNiveles().forEach(n -> ordenPorNivel.put(n.getId(), n.getOrdenNivel()));
        Map<Integer, Dependencia> porId = new HashMap<>();
        dependenciaRepository.findAll().forEach(d -> porId.put(d.getId(), d));

        // Clave de orden: nombres desde la raíz, así cada dependencia queda debajo de su superior
        Map<Integer, String> clave = new HashMap<>();
        java.util.function.Function<Dependencia, String> claveDe = new java.util.function.Function<>() {
            public String apply(Dependencia d) {
                String c = clave.get(d.getId());
                if (c == null) {
                    Dependencia padre = d.getDependenciaPadreId() == null ? null : porId.get(d.getDependenciaPadreId());
                    c = (padre == null ? "" : apply(padre) + "\u0001") + d.getNombre();
                    clave.put(d.getId(), c);
                }
                return c;
            }
        };

        // Dirección de referencia: la propia dependencia o su primer superior de nivel Dirección o mayor
        Map<Dependencia, List<Dependencia>> porDireccion = new java.util.TreeMap<>(java.util.Comparator.comparing(claveDe));
        for (Dependencia d : porId.values()) {
            if (!Boolean.TRUE.equals(d.getActivo())) {
                continue;
            }
            Dependencia dir = d;
            while (ordenPorNivel.getOrDefault(dir.getNivelJerarquicoId(), 99) > 4 && porId.get(dir.getDependenciaPadreId()) != null) {
                dir = porId.get(dir.getDependenciaPadreId());
            }
            porDireccion.computeIfAbsent(dir, k -> new ArrayList<>()).add(d);
        }

        List<GrupoDependencias> grupos = new ArrayList<>();
        porDireccion.forEach((dir, deps) -> {
            deps.sort(java.util.Comparator.comparing(claveDe));
            int profundidadDir = claveDe.apply(dir).split("\u0001").length;
            String sigla = dir.getSigla() != null ? dir.getSigla() : "";
            // Nombres que se repiten dentro de la misma dirección (las unidades de cada Centro Municipal):
            // se aclara con el nombre de su superior en lugar de la sigla
            Map<String, Long> repeticiones = deps.stream()
                    .collect(java.util.stream.Collectors.groupingBy(Dependencia::getNombre, java.util.stream.Collectors.counting()));
            List<OpcionDependencia> opciones = deps.stream().map(d -> {
                int nivel = claveDe.apply(d).split("\u0001").length - profundidadDir;
                String aclaracion = d == dir ? ""
                        : repeticiones.get(d.getNombre()) > 1 && porId.get(d.getDependenciaPadreId()) != null
                            ? " (" + porId.get(d.getDependenciaPadreId()).getNombre() + ")"
                            : sigla.isEmpty() ? "" : " (" + sigla + ")";
                return new OpcionDependencia(d.getId(), "   ".repeat(nivel) + d.getNombre() + aclaracion);
            }).toList();
            Dependencia superior = porId.get(dir.getDependenciaPadreId());
            String etiqueta = (sigla.isEmpty() ? "" : sigla + " · ") + dir.getNombre()
                    + (superior != null && superior.getDependenciaPadreId() != null && superior.getSigla() != null ? " (" + superior.getSigla() + ")" : "");
            grupos.add(new GrupoDependencias(etiqueta, opciones));
        });
        return grupos;
    }

    // Cada usuario accede a los expedientes de su ámbito (asignaciones vigentes, por organigrama)
    // y a los originados en su dependencia o en los que su dependencia emitió/recibió una actuación.
    public boolean puedeAccederDocumento(Usuario usuario, Long documentoId) {
        if (usuario == null || documentoId == null) {
            return false;
        }
        if (documentoRepository.existeEnAmbito(usuario.getId(), documentoId)) {
            return true;
        }
        if (usuario.getDependencia() == null) {
            return false;
        }

        Integer depId = usuario.getDependencia().getId();
        Documento doc = documentoRepository.findById(documentoId).orElse(null);
        if (doc == null) {
            return false;
        }
        if (doc.getDependenciaOrigen() != null && depId.equals(doc.getDependenciaOrigen().getId())) {
            return true;
        }
        return actuacionDocumentoRepository.existeParticipacionDependencia(documentoId, depId);
    }

    // Los expedientes de la lista que el usuario puede ver, conservando el orden. Misma regla que
    // puedeAccederDocumento, resuelta con una consulta por cada 1000 expedientes (auditoría B2)
    public List<Documento> filtrarAccesibles(Usuario usuario, List<Documento> documentos) {
        if (usuario == null || documentos.isEmpty()) {
            return List.of();
        }
        Set<Long> accesibles = new HashSet<>();
        List<Long> ids = documentos.stream().map(Documento::getId).distinct().toList();
        for (int i = 0; i < ids.size(); i += 1000) {
            accesibles.addAll(documentoRepository.idsAccesibles(usuario.getId(), ids.subList(i, Math.min(i + 1000, ids.size()))));
        }
        return documentos.stream().filter(d -> accesibles.contains(d.getId())).toList();
    }

    public void verificarAccesoDocumento(Usuario usuario, Long documentoId) {
        if (!puedeAccederDocumento(usuario, documentoId)) {
            throw new AccessDeniedException("Su dependencia no tiene acceso a este expediente.");
        }
    }

    public List<TipoDocumento> obtenerTiposDocumentoActivos() {
        return tipoDocumentoRepository.findByActivoTrueOrderByNombreAsc();
    }

    // Quién tiene el original. Los expedientes anteriores a la V11 que no la tengan cargada
    // quedan en la dependencia que los registró o, en último caso, en la de origen.
    private Dependencia custodiaDe(Documento doc) {
        if (doc.getDependenciaCustodia() != null) {
            return doc.getDependenciaCustodia();
        }
        return doc.getDependenciaRegistro() != null ? doc.getDependenciaRegistro() : doc.getDependenciaOrigen();
    }

    // 🛡️ Operaciones sobre el original (salida, acuse final, cierre forzado, ubicación, reemplazo del acuse):
    // además del permiso, el expediente tiene que estar en el ámbito del usuario y el usuario tiene que
    // actuar por quien tiene el original. Bloquea el expediente hasta el fin de la transacción.
    private Documento exigirCustodia(Long documentoId, Usuario usuario, String accion) {
        verificarAccesoDocumento(usuario, documentoId);
        Documento doc = documentoRepository.findByIdParaActualizar(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + documentoId));
        Dependencia custodia = custodiaDe(doc);
        if (!canalService.puedeActuarPor(usuario, custodia)) {
            throw new AccessDeniedException("Solo " + custodia.getNombreVisible() + ", que tiene el original, puede " + accion + " este expediente.");
        }
        return doc;
    }

    // 🛡️ Auditoría M1: mientras un pase de trámite espera acuse, el original está "en camino". Darle salida,
    // finalizarlo o devolverlo dejaría dos movimientos del original cruzados; primero se confirma o se retira el pase.
    private void exigirSinTramitePendiente(Documento doc, String accion) {
        if (actuacionDocumentoRepository.existePaseTramitePendiente(doc.getId())) {
            throw new IllegalStateException("No se puede " + accion + " mientras hay un pase de trámite esperando acuse: "
                    + "el original está en camino. Espere la confirmación o retire ese pase.");
        }
    }

    // 🛡️ La supervisión de pases (mesa de entrada) vale solo para los pases de su ámbito:
    // tiene que poder actuar por la dependencia emisora o por la receptora
    private boolean supervisaPase(Usuario usuario, ActuacionDocumento pase) {
        return politicaAcceso.puede(usuario, Accion.SUPERVISAR_PASES)
                && (canalService.puedeActuarPor(usuario, pase.getDependenciaEmisora())
                    || canalService.puedeActuarPor(usuario, pase.getDependenciaReceptoraPase()));
    }

    // 🧭 Lo que muestra el modal de derivación: desde dónde se deriva y a dónde permite el canal
    // conReceptor = false: nadie usa todavía el sistema en esa dependencia (el modal la muestra deshabilitada)
    public record DestinoPase(Integer id, String nombre, String sigla, String movimiento, boolean conReceptor) {}

    // soloElevar: el usuario no tiene el permiso de derivar; solo puede elevar el original a su superior
    public record OpcionesDerivacion(String custodia, boolean puedeDerivar, boolean soloElevar, List<DestinoPase> destinos) {}

    // Auditoría M2: no se deriva a dependencias sin nadie que reciba el pase (se puede desactivar mientras
    // se incorporan las direcciones, si se decide seguir enviando pases a áreas que todavía usan papel)
    @Value("${app.canal.exigir-receptor:true}")
    private boolean exigirReceptor;

    public OpcionesDerivacion obtenerOpcionesDerivacion(Long documentoId, Usuario usuario) {
        verificarAccesoDocumento(usuario, documentoId);
        Documento doc = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + documentoId));
        Dependencia custodia = custodiaDe(doc);
        boolean puede = canalService.puedeActuarPor(usuario, custodia);
        Organigrama organigrama = canalService.organigrama();
        // Un pase a otra dirección llega a su mesa de entrada: se muestra el nombre de la dirección,
        // no el de su "Unidad de Secretaría Administrativa", que se repite en todas
        boolean soloElevar = !politicaAcceso.puede(usuario, Accion.DERIVAR);
        List<Organigrama.Destino> canal = !puede ? List.of() : organigrama.destinos(custodia.getId()).stream()
                .filter(d -> !soloElevar || d.movimiento() == Organigrama.Movimiento.SUBIR)
                .toList();
        java.util.Set<Integer> conReceptor = canal.isEmpty() ? java.util.Set.of()
                : new java.util.HashSet<>(dependenciaRepository.findConReceptor(canal.stream().map(d -> d.nodo().id()).toList()));
        List<DestinoPase> destinos = canal.stream()
                .map(d -> {
                    Organigrama.Nodo efectivo = organigrama.nodoEfectivo(d.nodo());
                    boolean esMesa = !efectivo.id().equals(d.nodo().id());
                    return new DestinoPase(d.nodo().id(),
                            esMesa ? efectivo.nombre() + " (mesa de entrada)" : d.nodo().nombre(),
                            esMesa ? efectivo.sigla() : d.nodo().sigla(), d.movimiento().name(),
                            !exigirReceptor || conReceptor.contains(d.nodo().id()));
                })
                .toList();
        return new OpcionesDerivacion(custodia.getNombreVisible(), puede, soloElevar, destinos);
    }

    // 🔢 CUTI por cabecera (docs/diseno-fase2-multiarea.md §5): DTIC-2026-000001, DHAC-2026-000001...
    // El número sale de la serie de la cabecera (Dirección, Dirección General o Intendencia) de la
    // dependencia que registra el expediente. La fila de series_cuti queda bloqueada hasta el commit,
    // así dos registros simultáneos no obtienen el mismo número.
    private String generarCodigoCuti(Dependencia registro, int anio) {
        Organigrama organigrama = canalService.organigrama();
        Organigrama.Nodo cabecera = organigrama.cabecera(organigrama.nodo(registro.getId()));
        String sigla = cabecera.sigla() != null ? cabecera.sigla() : "MCA";
        String prefijo = String.format("%s-%d-", sigla, anio);
        int numero = documentoRepository.siguienteCorrelativoCuti(cabecera.id(), anio, prefijo + "%");
        return String.format("%s%06d", prefijo, numero);
    }

    private void validarNoFinalizado(Documento doc, String accion) {
        if (doc.getEstadoActual() == EstadoDocumento.FINALIZADO) {
            throw new IllegalStateException("No se puede " + accion + " un expediente FINALIZADO.");
        }
    }

    public static final String REGEX_NUMERO_DOC = "^(S/N|\\d+/\\d{4})$";

    private void validarFormatoNumeroDocumento(String numeroDoc) {
        if (numeroDoc == null || numeroDoc.isBlank()) {
            throw new IllegalArgumentException("El número de documento no puede estar vacío. Ingrese S/N si no posee número.");
        }
        
        String textoLimpio = numeroDoc.trim().toUpperCase();
        if (!textoLimpio.matches(REGEX_NUMERO_DOC)) {
            throw new IllegalArgumentException(
                "Formato no válido: '" + numeroDoc + "'. Debe utilizar el formato N°/AÑO (ej. 120/2026) o S/N."
            );
        }
    }

    @Transactional
    public Documento guardarNuevoDocumento(NuevoDocumentoDTO dto, Usuario usuarioSesion) {
        if (dto.getTipoDocumentoId() == null) {
            throw new IllegalArgumentException("Debe seleccionar el tipo de documento.");
        }
        if (dto.getDependenciaOrigenId() == null) {
            throw new IllegalArgumentException("Debe seleccionar la dependencia de origen.");
        }
        if (dto.getAsunto() == null || dto.getAsunto().isBlank()) {
            throw new IllegalArgumentException("Debe ingresar el asunto del documento.");
        }

        TipoDocumento tipo = tipoDocumentoRepository.findById(dto.getTipoDocumentoId())
                .orElseThrow(() -> new IllegalArgumentException("Tipo de documento no encontrado: " + dto.getTipoDocumentoId()));
        if (!Boolean.TRUE.equals(tipo.getActivo())) {
            throw new IllegalArgumentException("El tipo de documento '" + tipo.getNombre() + "' está dado de baja.");
        }
        Dependencia origen = dependenciaRepository.findById(dto.getDependenciaOrigenId())
                .orElseThrow(() -> new IllegalArgumentException("Dependencia no encontrada: " + dto.getDependenciaOrigenId()));
        if (Boolean.FALSE.equals(origen.getActivo())) {
            throw new IllegalArgumentException("La dependencia '" + origen.getNombre() + "' está inactiva.");
        }
        // Un documento de la propia dirección (una unidad que eleva un memo, por ejemplo) casi siempre pertenece a
        // un expediente que ya tiene CUTI: se agrega como actuación y se eleva, no se registra otro expediente
        if (esDeLaPropiaDireccion(origen, usuarioSesion) && !Boolean.TRUE.equals(dto.getAsuntoNuevo())) {
            throw new IllegalArgumentException("El documento viene de " + origen.getNombreVisible() + ", dentro de su propia dirección. "
                    + "Si pertenece a un expediente que ya tiene CUTI, regístrelo como actuación en ese expediente y elévelo con "
                    + "\"Derivar / Elevar\"; no cree otro expediente. Si es un asunto nuevo, marque \"Es un asunto nuevo\".");
        }

        Documento documento = new Documento();
        documento.setTipoDocumento(tipo);
        documento.setDependenciaOrigen(origen);
        documento.setPrioridad(dto.getPrioridad() != null ? dto.getPrioridad() : Prioridad.NORMAL);
        documento.setNumeroDocumentoOrigen(dto.getNumeroDocumentoOrigen());
        documento.setPersonaEntrega(dto.getPersonaEntrega());
        documento.setNroOrdenLibro(dto.getNroOrdenLibro());
        documento.setNroPaginaLibro(dto.getNroPaginaLibro());
        documento.setAsunto(dto.getAsunto().trim());

        // 📚 Ubicación física: obligatoria para los tipos que la requieren (ej. circulares)
        if (dto.getBiblioratoId() != null) {
            biblioratoService.asignarUbicacion(documento, dto.getBiblioratoId(), dto.getFojaDesde(), dto.getFojaHasta());
        } else if (Boolean.TRUE.equals(tipo.getRequiereBibliorato())) {
            throw new IllegalArgumentException("Para '" + tipo.getNombre() + "' es obligatorio indicar el bibliorato donde se archiva.");
        } else if (dto.getFojaDesde() != null || dto.getFojaHasta() != null) {
            throw new IllegalArgumentException("Indicó fojas pero no el bibliorato.");
        }

        Documento guardado = guardarNuevoDocumento(documento, usuarioSesion);

        // 🗄️ Los informativos no se tramitan: quedan archivados en el mismo acto
        if (tipo.isInformativo()) {
            guardado.setEstadoActual(EstadoDocumento.ARCHIVADO);
            guardado.setArchivado(true);
            guardado.setFechaHoraArchivado(LocalDateTime.now());
            documentoRepository.save(guardado);
            auditoriaService.registrarEvento(guardado, "ARCHIVO_INFORMATIVO", EstadoDocumento.INGRESADO, EstadoDocumento.ARCHIVADO,
                    "Documento informativo archivado en " + guardado.getUbicacionTexto(), usuarioSesion, null);
        }
        return guardado;
    }

    // 📚 Asigna o cambia la ubicación física (bibliorato + fojas) de un documento. Requiere GESTIONAR_ARCHIVO.
    @Transactional
    public Documento cambiarUbicacion(Long documentoId, Integer biblioratoId, Integer fojaDesde, Integer fojaHasta,
                                      String motivo, Usuario usuario) {
        politicaAcceso.exigir(usuario, Accion.GESTIONAR_ARCHIVO, "No está habilitado para registrar la ubicación física de los documentos.");
        if (biblioratoId == null) {
            throw new IllegalArgumentException("Debe seleccionar el bibliorato.");
        }
        Documento doc = exigirCustodia(documentoId, usuario, "registrar la ubicación física de");

        String anterior = doc.getUbicacionTexto();
        if (anterior != null) {
            validarMotivo(motivo, 10, "el motivo del cambio de ubicación");
        }
        biblioratoService.asignarUbicacion(doc, biblioratoId, fojaDesde, fojaHasta);

        // Un expediente concluido que se guarda en un bibliorato queda archivado físicamente
        if ((doc.getEstadoActual() == EstadoDocumento.FINALIZADO || doc.getEstadoActual() == EstadoDocumento.ARCHIVADO)
                && doc.getFechaHoraArchivado() == null) {
            doc.setArchivado(true);
            doc.setFechaHoraArchivado(LocalDateTime.now());
        }
        documentoRepository.save(doc);

        auditoriaService.registrarEvento(doc, "CAMBIO_UBICACION", doc.getEstadoActual(), doc.getEstadoActual(),
                (anterior == null ? "Ubicación física registrada: " : "Ubicación física cambiada de [" + anterior + "] a: ")
                        + doc.getUbicacionTexto() + (motivo != null && !motivo.isBlank() ? ". Motivo: " + motivo.trim() : ""),
                usuario, null);
        return doc;
    }

    // 📚 Archivo físico visible para el usuario: solo lo de su ámbito
    public List<Documento> obtenerDocumentosArchivoParaUsuario(Usuario usuario, Integer biblioratoId) {
        List<Documento> docs = documentoRepository.findDocumentosArchivo(biblioratoId);
        return filtrarAccesibles(usuario, docs);
    }

    // ¿El origen está dentro de la dirección (cabecera) donde registra el usuario?
    private boolean esDeLaPropiaDireccion(Dependencia origen, Usuario usuario) {
        Organigrama.Nodo cabecera = canalService.cabeceraDelUsuario(usuario);
        if (cabecera == null || origen.getRuta() == null) {
            return false;
        }
        return dependenciaRepository.findById(cabecera.id())
                .map(c -> c.getRuta() != null && origen.getRuta().startsWith(c.getRuta()))
                .orElse(false);
    }

    // ✍️ DOCUMENTO PROPIO (§4.3): cualquier dependencia (unidad, departamento, dirección) inicia un
    // expediente con su documento. Queda en su custodia, con el CUTI de su cabecera, y puede salir
    // en el mismo acto hacia su superior con un pase de trámite.
    @Transactional
    public Documento iniciarDocumentoPropio(Integer tipoDocumentoId, String numeroDocumento, String asunto,
                                           Prioridad prioridad, String observacion, boolean enviarAlSuperior,
                                           MultipartFile archivo, Usuario usuarioSesion) {
        if (usuarioSesion == null || usuarioSesion.getDependencia() == null) {
            throw new IllegalStateException("El usuario actual no tiene una dependencia asignada para emitir documentos.");
        }
        if (tipoDocumentoId == null) {
            throw new IllegalArgumentException("Debe seleccionar el tipo de documento.");
        }
        if (asunto == null || asunto.isBlank()) {
            throw new IllegalArgumentException("Debe ingresar el asunto del documento.");
        }
        TipoDocumento tipo = tipoDocumentoRepository.findById(tipoDocumentoId)
                .orElseThrow(() -> new IllegalArgumentException("Tipo de documento no encontrado: " + tipoDocumentoId));
        if (!Boolean.TRUE.equals(tipo.getActivo())) {
            throw new IllegalArgumentException("El tipo de documento '" + tipo.getNombre() + "' está dado de baja.");
        }
        if (archivo != null && !archivo.isEmpty()) {
            archivoService.validarPdf(archivo); // antes de grabar nada
        }

        // Emite la dependencia por la que actúa el usuario: su mesa si es mesa de entrada, si no la propia
        Dependencia emisora = canalService.dependenciaDeRegistro(usuarioSesion);
        Organigrama.Destino superior = null;
        if (enviarAlSuperior) {
            superior = canalService.organigrama().destinos(emisora.getId()).stream()
                    .filter(d -> d.movimiento() == Organigrama.Movimiento.SUBIR)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(emisora.getNombreVisible() + " no tiene una dependencia superior a la que dar salida."));
        }

        Documento doc = new Documento();
        doc.setTipoDocumento(tipo);
        doc.setAsunto(asunto.trim());
        doc.setPrioridad(prioridad != null ? prioridad : Prioridad.NORMAL);
        doc.setNumeroDocumentoOrigen(numeroDocumento);
        doc.setDependenciaOrigen(emisora);
        doc.setDependenciaRegistro(emisora);
        doc.setDependenciaCustodia(emisora);
        Documento guardado = guardarNuevoDocumento(doc, usuarioSesion);
        auditoriaService.registrarEvento(guardado, "DOCUMENTO_PROPIO", guardado.getEstadoActual(), guardado.getEstadoActual(),
                "Documento propio iniciado por " + emisora.getNombreVisible(), usuarioSesion, null);

        if (archivo != null && !archivo.isEmpty()) {
            registrarAdjunto(guardado, null, archivo, "DOCUMENTO", "DOCUMENTO_PROPIO", usuarioSesion);
        }
        if (superior != null) {
            String motivo = observacion != null && observacion.trim().length() >= 5 ? observacion.trim() : "Se eleva para su consideración";
            derivarDocumento(guardado.getId(), List.of(superior.nodo().id()), motivo, TipoPase.TRAMITE, usuarioSesion);
        }
        return guardado;
    }

    @Transactional
    public Documento guardarNuevoDocumento(Documento documento, Usuario usuarioSesion) {
        LocalDateTime ahora = LocalDateTime.now();
        int anioActual = ahora.getYear();
        
        if (documento.getNumeroDocumentoOrigen() == null || documento.getNumeroDocumentoOrigen().isBlank()) {
            documento.setNumeroDocumentoOrigen("S/N");
        } else {
            documento.setNumeroDocumentoOrigen(documento.getNumeroDocumentoOrigen().trim().toUpperCase());
            validarFormatoNumeroDocumento(documento.getNumeroDocumentoOrigen());
        }

        documento.setFechaHoraIngreso(ahora);
        documento.setEstadoActual(EstadoDocumento.INGRESADO);
        documento.setAnio(anioActual);

        documento.setFechaHoraCargaIngreso(ahora);
        documento.setUsuarioCargaIngreso(usuarioSesion);

        // El original queda en la mesa (o dependencia) que lo registra, y el CUTI sale de la serie de su cabecera
        if (documento.getDependenciaRegistro() == null) {
            documento.setDependenciaRegistro(canalService.dependenciaDeRegistro(usuarioSesion));
        }
        if (documento.getCodigoCuti() == null || documento.getCodigoCuti().isBlank()) {
            documento.setCodigoCuti(generarCodigoCuti(documento.getDependenciaRegistro(), anioActual));
        }
        if (documento.getDependenciaCustodia() == null) {
            documento.setDependenciaCustodia(documento.getDependenciaRegistro());
        }

        if (documento.getFuncionarioRecibeFisico() == null || documento.getFuncionarioRecibeFisico().isBlank()) {
            documento.setFuncionarioRecibeFisico(usuarioSesion.getNombreCompleto());
        }

        Integer idUsuario = (documento.getUsuarioRecepcion() != null && documento.getUsuarioRecepcion().getId() != null)
                ? documento.getUsuarioRecepcion().getId()
                : usuarioSesion.getId();

        Usuario usuarioPersistido = usuarioRepository.findById(idUsuario)
                .orElse(usuarioSesion);
        
        documento.setUsuarioRecepcion(usuarioPersistido);

        Documento docGuardado = documentoRepository.save(documento);

        // 🛡️ REGISTRO DE AUDITORÍA
        auditoriaService.registrarEvento(
            docGuardado, 
            "REGISTRO_INGRESO", 
            null, 
            docGuardado.getEstadoActual(), 
            "Ingreso inicial asentar en libro con N° Origen " + docGuardado.getNumeroDocumentoOrigen(), 
            usuarioSesion, 
            null
        );

        return docGuardado;
    }


    @Transactional
    public void devolverDocumento(Long documentoId, String observacion, Usuario usuarioActual) {
        // 1. Buscar el documento
        Documento documento = documentoRepository.findByIdParaActualizar(documentoId)
                .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado"));

        // 2. Validar estado operativo con getEstadoActual()
        if (documento.getEstadoActual() == EstadoDocumento.FINALIZADO ||
            documento.getEstadoActual() == EstadoDocumento.PENDIENTE_SALIDA ||
            documento.getEstadoActual() == EstadoDocumento.ARCHIVADO) {
            throw new IllegalStateException("No se puede devolver un documento finalizado, pendiente de salida o archivado.");
        }

        if (usuarioActual == null || usuarioActual.getDependencia() == null) {
            throw new IllegalStateException("El usuario actual no tiene una dependencia asignada para devolver expedientes.");
        }

        if (observacion == null || observacion.trim().length() < 5) {
            throw new IllegalArgumentException("Debe ingresar el motivo de la devolución (mínimo 5 caracteres).");
        }

        // 3. Localizar el último pase recibido: por quien tiene el original si el usuario actúa por ella
        //    (la mesa de su dirección, por ejemplo); si no, por la dependencia del usuario
        Dependencia custodia = custodiaDe(documento);
        Dependencia depActual = canalService.puedeActuarPor(usuarioActual, custodia) ? custodia : usuarioActual.getDependencia();
        // Devolver el original mientras hay un trámite en curso dejaría dos entregas del original cruzadas
        if (depActual.getId().equals(custodia.getId())) {
            exigirSinTramitePendiente(documento, "devolver");
        }
        ActuacionDocumento ultimoPase = actuacionDocumentoRepository
                .findTopByDocumentoIdAndDependenciaReceptoraPaseIdAndAnuladaFalseOrderByFechaHoraActuacionDesc(documentoId, depActual.getId());

        if (ultimoPase == null || ultimoPase.getDependenciaEmisora() == null) {
            throw new IllegalStateException("Su dependencia no recibió ningún pase de este expediente, no hay a quién devolverlo.");
        }

        Dependencia depDestino = ultimoPase.getDependenciaEmisora();
        if (depDestino.getId().equals(depActual.getId())) {
            throw new IllegalStateException("El último pase recibido fue emitido por su propia dependencia, no se puede devolver.");
        }

        // Evita devolver dos veces el mismo pase
        ActuacionDocumento ultimaDevolucion = actuacionDocumentoRepository
                .findTopByDocumentoIdAndDependenciaEmisoraIdAndTipoActuacionOrderByFechaHoraActuacionDesc(
                        documentoId, depActual.getId(), "DEVOLUCION");
        if (ultimaDevolucion != null && ultimaDevolucion.getFechaHoraActuacion() != null
                && ultimoPase.getFechaHoraActuacion() != null
                && !ultimaDevolucion.getFechaHoraActuacion().isBefore(ultimoPase.getFechaHoraActuacion())) {
            throw new IllegalStateException("Su dependencia ya devolvió este expediente a " + depDestino.getNombreVisible() + ".");
        }

        EstadoDocumento estadoAnterior = documento.getEstadoActual();

        // 4. Actualizar estado del documento con setEstadoActual()
        documento.setEstadoActual(EstadoDocumento.EN_REVISION);
        // Si quien devuelve tenía el original, el original vuelve a quien lo envió
        if (depActual.getId().equals(custodia.getId())) {
            documento.setDependenciaCustodia(depDestino);
        }
        documentoRepository.save(documento);

        // 5. Registrar la nueva actuación de devolución (retorna a quien nos envió)
        ActuacionDocumento devolucion = ActuacionDocumento.builder()
                .documento(documento)
                .dependenciaEmisora(depActual)
                .dependenciaReceptoraPase(depDestino)
                .tipoActuacion("DEVOLUCION")
                .numeroActuacion("S/N")
                .descripcionResumen("DEVOLUCIÓN ➔ " + depDestino.getNombreVisible() + ": " + observacion.trim())
                .ubicacionBibliorato(documento.getUbicacionBibliorato())
                .fechaHoraActuacion(LocalDateTime.now())
                .paseConfirmado(true) // Se asume confirmada al ser una devolución inmediata
                .usuario(usuarioActual)
                .build();

        actuacionDocumentoRepository.save(devolucion);

        // 6. Auditoría inmutable automática usando registrarEvento(...)
        auditoriaService.registrarEvento(
            documento,
            "DEVOLUCION",
            estadoAnterior,
            documento.getEstadoActual(),
            "Expediente devuelto por " + depActual.getNombreVisible() + " a " + depDestino.getNombreVisible() + ": " + observacion.trim(),
            usuarioActual,
            null
        );
    }
    

    @Transactional
    public Documento derivarDocumento(Long documentoId,
                                      List<Integer> dependenciasDestinoIds,
                                      String observacion,
                                      TipoPase tipoPase,
                                      Usuario usuario) {

        if (dependenciasDestinoIds == null || dependenciasDestinoIds.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una dependencia destino para derivar.");
        }
        if (tipoPase == null) {
            throw new IllegalArgumentException("Debe indicar el tipo de pase: trámite, informe o conocimiento.");
        }

        if (observacion == null || observacion.trim().length() < 5) {
            throw new IllegalArgumentException("Debe ingresar una observación o motivo válido (mínimo 5 caracteres) para derivar el documento.");
        }

        verificarAccesoDocumento(usuario, documentoId);

        // Un mismo destino repetido en la petición genera un solo pase
        List<Integer> destinos = new ArrayList<>(new java.util.LinkedHashSet<>(dependenciasDestinoIds));
        destinos.remove(null);
        if (tipoPase == TipoPase.TRAMITE && destinos.size() != 1) {
            throw new IllegalArgumentException("Un pase de trámite entrega el original: va a una sola dependencia. Para varias, use informe o conocimiento.");
        }

        // Bloquea el expediente: dos derivaciones simultáneas se ejecutan una después de la otra
        Documento doc = documentoRepository.findByIdParaActualizar(documentoId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));
        validarNoFinalizado(doc, "derivar");

        // 🧭 Canal jerárquico: se deriva desde quien tiene el original y solo a los destinos que permite el organigrama
        Dependencia custodia = custodiaDe(doc);
        if (!canalService.puedeActuarPor(usuario, custodia)) {
            throw new AccessDeniedException("El expediente está en " + custodia.getNombreVisible() + ": solo esa dependencia puede derivarlo.");
        }
        Organigrama organigrama = canalService.organigrama();
        for (Integer depId : destinos) {
            if (!organigrama.puedePasar(custodia.getId(), depId)) {
                throw new AccessDeniedException("El canal no permite pasar de " + custodia.getNombreVisible() + " a la dependencia seleccionada (ID "
                        + depId + "): el expediente sube por su dependencia superior y pasa entre direcciones por sus mesas de entrada.");
            }
        }
        // Quien no tiene el permiso de derivar (un operador de una unidad) igual puede ELEVAR el mismo
        // expediente a su superior con un pase de trámite: así no se crea otro expediente con otro CUTI
        if (!politicaAcceso.puede(usuario, Accion.DERIVAR)) {
            java.util.Set<Integer> superiores = organigrama.destinos(custodia.getId()).stream()
                    .filter(d -> d.movimiento() == Organigrama.Movimiento.SUBIR)
                    .map(d -> d.nodo().id()).collect(java.util.stream.Collectors.toSet());
            if (tipoPase != TipoPase.TRAMITE || !superiores.containsAll(destinos)) {
                throw new AccessDeniedException("Sin el permiso de derivar solo se puede elevar el expediente a la dependencia superior, con un pase de trámite.");
            }
        }
        // Auditoría M2: sin nadie que lo reciba, el pase quedaría pendiente para siempre
        if (exigirReceptor) {
            java.util.Set<Integer> conReceptor = new java.util.HashSet<>(dependenciaRepository.findConReceptor(destinos));
            List<String> sinReceptor = destinos.stream().filter(id -> !conReceptor.contains(id))
                    .map(id -> organigrama.nodo(id).nombre()).toList();
            if (!sinReceptor.isEmpty()) {
                throw new IllegalStateException("Nadie usa todavía el sistema en: " + String.join(", ", sinReceptor)
                        + ". El pase quedaría sin recibir; envíelo en papel o pida que se habiliten usuarios.");
            }
        }
        // Con un trámite esperando acuse, el original está en camino: nadie más puede pasarlo, ni en trámite
        // ni para informe o conocimiento, porque la dependencia emisora ya no lo tiene
        if (actuacionDocumentoRepository.existePaseTramitePendiente(documentoId)) {
            throw new IllegalStateException("Ya hay un pase de trámite esperando acuse: el original está en camino y no puede volver a pasarse. Espere la confirmación o retire ese pase.");
        }

        boolean esInformeRequerido = tipoPase == TipoPase.INFORME;
        boolean esInformativo = doc.getTipoDocumento() != null && doc.getTipoDocumento().isInformativo();
        if (tipoPase != TipoPase.CONOCIMIENTO && (esInformativo || doc.getEstadoActual() == EstadoDocumento.ARCHIVADO)) {
            throw new IllegalArgumentException("Un documento informativo no se tramita ni se deriva para informe: solo puede difundirse para conocimiento.");
        }

        // No se duplica un pase que todavía espera el acuse de la misma dependencia
        List<String> conPasePendiente = new ArrayList<>();
        for (Integer depId : destinos) {
            if (actuacionDocumentoRepository.existePasePendiente(documentoId, depId)) {
                conPasePendiente.add(dependenciaRepository.findById(depId).map(Dependencia::getNombre).orElse("ID " + depId));
            }
        }
        if (!conPasePendiente.isEmpty()) {
            throw new IllegalStateException("Ya existe un pase pendiente de acuse para: " + String.join(", ", conPasePendiente)
                    + ". Espere la confirmación o retire ese pase antes de volver a derivar.");
        }

        EstadoDocumento estadoAnterior = doc.getEstadoActual();

        if (estadoAnterior == EstadoDocumento.ARCHIVADO) {
            // Difusión de un informativo archivado: los pases son de conocimiento y el estado no cambia
            doc.setEstadoActual(EstadoDocumento.ARCHIVADO);
        } else if (esInformeRequerido) {
            doc.setEstadoActual(EstadoDocumento.EN_ESPERA_INFORME);
        } else if (estadoAnterior == EstadoDocumento.EN_ESPERA_INFORME && quedanPasesParaInformeVigentes(documentoId)) {
            // Una derivación de conocimiento no interrumpe los informes que siguen pendientes
            doc.setEstadoActual(EstadoDocumento.EN_ESPERA_INFORME);
        } else {
            doc.setEstadoActual(EstadoDocumento.EN_REVISION);
        }
        documentoRepository.save(doc);

        String tipoPaseStr = switch (tipoPase) {
            case TRAMITE -> "PASE DE TRÁMITE";
            case INFORME -> "PASE PARA INFORME";
            case CONOCIMIENTO -> "PASE PARA CONOCIMIENTO";
        };
        String detalleObservacion = observacion.trim();

        for (Integer depId : destinos) {
            Dependencia destino = dependenciaRepository.findById(depId)
                    .orElseThrow(() -> new RuntimeException("Dependencia no encontrada: " + depId));

            // Emite el pase la dependencia que tiene el original, aunque lo cargue un usuario de su ámbito
            ActuacionDocumento paseInterno = ActuacionDocumento.builder()
                    .documento(doc)
                    .dependenciaEmisora(custodia)
                    .dependenciaReceptoraPase(destino)
                    .tipoActuacion("PASE_INTERNO")
                    .numeroActuacion("S/N")
                    .descripcionResumen(tipoPaseStr + " ➔ " + destino.getNombreVisible() + ": " + detalleObservacion)
                    .ubicacionBibliorato(doc.getUbicacionBibliorato())
                    .fechaHoraActuacion(LocalDateTime.now())
                    .paseConfirmado(false)
                    .requiereInforme(esInformeRequerido)
                    .tipoPase(tipoPase)
                    .usuario(usuario)
                    .build();

            actuacionDocumentoRepository.save(paseInterno);
        }
        // 🛡️ REGISTRO DE AUDITORÍA
        auditoriaService.registrarEvento(
            doc,
            "DERIVACION_MULTIPLE",
            estadoAnterior,
            doc.getEstadoActual(),
            "Pase de " + tipoPase.name().toLowerCase() + " desde " + custodia.getNombreVisible() + " a " + destinos.size()
                    + (destinos.size() == 1 ? " dependencia" : " dependencias") + ": " + observacion,
            usuario, 
            null
        );

        return doc;
    }

 @Transactional
    public Documento crearYRegistrarSalidaDirecta(String nroNotaSalida, 
                                                  Integer tipoDocumentoId,
                                                  String asunto,
                                                  Integer dependenciaDestinoId, 
                                                  String personaRetira, 
                                                  String funcionarioEntrega, 
                                                  MultipartFile archivoAcuse, 
                                                  Usuario usuarioSesion) {
        LocalDateTime ahora = LocalDateTime.now();
        int anioActual = ahora.getYear();

        if (usuarioSesion == null || usuarioSesion.getDependencia() == null) {
            throw new IllegalStateException("El usuario actual no tiene una dependencia asignada para emitir documentos.");
        }
        if (asunto == null || asunto.isBlank()) {
            throw new IllegalArgumentException("Debe ingresar el asunto del documento de salida.");
        }
        if (nroNotaSalida == null || nroNotaSalida.isBlank()) {
            throw new IllegalArgumentException("Debe ingresar el N° de nota de salida.");
        }
        if (tipoDocumentoId == null) {
            throw new IllegalArgumentException("Debe seleccionar el tipo de documento de salida.");
        }
        TipoDocumento tipoDoc = tipoDocumentoRepository.findById(tipoDocumentoId)
                .orElseThrow(() -> new IllegalArgumentException("Tipo de documento no encontrado: " + tipoDocumentoId));

        // 1. Crear el documento nativo de la dependencia
        Documento doc = new Documento();

        // Generación oficial del CUTI, de la serie de la cabecera que registra
        Dependencia registro = canalService.dependenciaDeRegistro(usuarioSesion);
        doc.setAnio(anioActual);
        doc.setCodigoCuti(generarCodigoCuti(registro, anioActual));

        doc.setAsunto(asunto.trim());
        doc.setTipoDocumento(tipoDoc);
        doc.setPrioridad(Prioridad.NORMAL);
        doc.setNumeroDocumentoOrigen("S/N");
        doc.setDependenciaOrigen(usuarioSesion.getDependencia());
        doc.setDependenciaRegistro(registro);
        doc.setDependenciaCustodia(doc.getDependenciaRegistro());
        doc.setEstadoActual(EstadoDocumento.PENDIENTE_SALIDA);
        doc.setFechaHoraIngreso(ahora);
        doc.setUsuarioCargaIngreso(usuarioSesion);
        doc.setUsuarioRecepcion(usuarioSesion); // Columna NOT NULL en la BD
        doc.setFechaHoraCargaIngreso(ahora);
        doc.setFuncionarioRecibeFisico(usuarioSesion.getNombreCompleto());

        // 2. Setear los datos de salida inmediata
        doc.setNroNotaSalida(nroNotaSalida);
        doc.setFechaHoraSalida(ahora);
        doc.setPersonaRetiraSalida(personaRetira);
        doc.setFuncionarioEntregaFisico(funcionarioEntrega);
        doc.setUsuarioCargaSalida(usuarioSesion);
        doc.setFechaHoraCargaSalida(ahora);

        if (dependenciaDestinoId != null) {
            Dependencia destino = dependenciaRepository.findById(dependenciaDestinoId).orElse(null);
            doc.setDependenciaDestinoSalida(destino);
        }

        Documento docGuardado = documentoRepository.save(doc);

        // 3. Manejo del archivo PDF de acuse (opcional)
        if (archivoAcuse != null && !archivoAcuse.isEmpty()) {
            registrarAdjunto(docGuardado, null, archivoAcuse, "SALIDA", "ACUSE_SALIDA", usuarioSesion);
        }

        // 4. Auditoría
        auditoriaService.registrarEvento(
            docGuardado, 
            "CREACION_Y_SALIDA_DIRECTA", 
            null, 
            docGuardado.getEstadoActual(), 
            "Documento generado y despachado directamente con N° Remisión " + nroNotaSalida, 
            usuarioSesion, 
            null
        );

        return docGuardado;
    }



    @Transactional
    public Documento registrarSalidaDocumento(Long documentoId, 
                                               String nroNotaSalida, 
                                               Integer dependenciaDestinoId, 
                                               String personaRetira, 
                                               String funcionarioEntrega, 
                                               MultipartFile archivoAcuse, 
                                               Usuario usuarioSesion) {
        Documento doc = exigirCustodia(documentoId, usuarioSesion, "registrar la salida de");
        exigirSinTramitePendiente(doc, "registrar la salida");
        validarNoFinalizado(doc, "registrar la salida de");
        if (doc.getEstadoActual() == EstadoDocumento.ARCHIVADO) {
            throw new IllegalStateException("Un documento informativo archivado no tiene salida exterior.");
        }
        if (nroNotaSalida == null || nroNotaSalida.isBlank()) {
            throw new IllegalArgumentException("Debe ingresar el N° de nota o remisión de salida.");
        }

        EstadoDocumento estadoAnterior = doc.getEstadoActual();
        LocalDateTime ahora = LocalDateTime.now();

        doc.setNroNotaSalida(nroNotaSalida);
        doc.setFechaHoraSalida(ahora);
        doc.setPersonaRetiraSalida(personaRetira);
        doc.setFuncionarioEntregaFisico(funcionarioEntrega);
        doc.setUsuarioCargaSalida(usuarioSesion);
        doc.setFechaHoraCargaSalida(ahora);
        doc.setEstadoActual(EstadoDocumento.PENDIENTE_SALIDA);

        if (dependenciaDestinoId != null) {
            Dependencia destino = dependenciaRepository.findById(dependenciaDestinoId).orElse(null);
            doc.setDependenciaDestinoSalida(destino);
        }

        Documento docGuardado = documentoRepository.save(doc);

        if (archivoAcuse != null && !archivoAcuse.isEmpty()) {
            registrarAdjunto(docGuardado, null, archivoAcuse, "SALIDA", "ACUSE_SALIDA", usuarioSesion);
        }

        // 🛡️ REGISTRO DE AUDITORÍA
        auditoriaService.registrarEvento(
            docGuardado, 
            "SALIDA_EXTERNA", 
            estadoAnterior, 
            docGuardado.getEstadoActual(), 
            "Salida externa con N° Remisión " + nroNotaSalida + " entregada a " + personaRetira, 
            usuarioSesion, 
            null
        );

        return docGuardado;
    }

    @Transactional
    public Documento confirmarEntregaYAcuse(Long documentoId, MultipartFile archivoAcuse, Usuario usuarioSesion) {
        Documento doc = exigirCustodia(documentoId, usuarioSesion, "confirmar el acuse final de");
        exigirSinTramitePendiente(doc, "confirmar el acuse final");

        if (doc.getEstadoActual() != EstadoDocumento.PENDIENTE_SALIDA || doc.getFechaHoraSalida() == null) {
            throw new IllegalStateException("Solo se puede confirmar el acuse de un expediente en PENDIENTE_SALIDA con la salida física ya registrada.");
        }
        if (archivoAcuse == null || archivoAcuse.isEmpty()) {
            throw new IllegalArgumentException("Es obligatorio adjuntar el PDF del acuse firmado para finalizar el expediente.");
        }

        EstadoDocumento estadoAnterior = doc.getEstadoActual();

        registrarAdjunto(doc, null, archivoAcuse, "ACUSE_CONFIRMADO", "ACUSE_ENTREGA", usuarioSesion);

        doc.setEstadoActual(EstadoDocumento.FINALIZADO);

        Documento docFinalizado = documentoRepository.save(doc);

        // 🛡️ REGISTRO DE AUDITORÍA
        auditoriaService.registrarEvento(
            docFinalizado, 
            "CONFIRMACION_ACUSE_FINAL", 
            estadoAnterior, 
            EstadoDocumento.FINALIZADO, 
            "Acuse exterior firmado y adjunto. Expediente concluido.", 
            usuarioSesion, 
            null
        );

        return docFinalizado;
    }

    // Solo devuelve expedientes que el usuario puede ver (el filtro de ámbito va en la consulta)
    public List<Documento> buscarDocumentosMulticriterio(Usuario usuario,
                                                        String texto,
                                                        Integer dependenciaId, 
                                                        Integer tipoDocId, 
                                                        String estadoStr,
                                                        Integer biblioratoId,
                                                        LocalDate fechaDesde, 
                                                        LocalDate fechaHasta) {

        if (usuario == null) {
            return List.of();
        }
        EstadoDocumento estadoEnum = null;
        if (estadoStr != null && !estadoStr.isBlank()) {
            try {
                estadoEnum = EstadoDocumento.valueOf(estadoStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        LocalDateTime desde = (fechaDesde != null) ? fechaDesde.atStartOfDay() : null;
        LocalDateTime hasta = (fechaHasta != null) ? fechaHasta.atTime(LocalTime.MAX) : null;

        String textoLimpio = (texto != null && !texto.isBlank()) ? texto.trim() : null;

        return documentoRepository.buscarMulticriterio(usuario.getId(), textoLimpio, dependenciaId, tipoDocId,
                estadoEnum != null ? estadoEnum.name() : null, biblioratoId, desde, hasta);
    }

    @Transactional
    public ActuacionDocumento registrarActuacion(Long documentoId, 
                                                Integer dependenciaEmisoraId, 
                                                String tipoActuacion, 
                                                String numeroActuacion, 
                                                String descripcionResumen, 
                                                String ubicacionBibliorato,
                                                MultipartFile archivoCopiaSellada,
                                                Usuario usuarioSesion) {
        verificarAccesoDocumento(usuarioSesion, documentoId);

        Documento doc = documentoRepository.findByIdParaActualizar(documentoId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));
        validarNoFinalizado(doc, "registrar actuaciones en");

        String nroLimpio = (numeroActuacion == null || numeroActuacion.isBlank()) 
                ? "S/N" : numeroActuacion.trim().toUpperCase();
                
        validarFormatoNumeroDocumento(nroLimpio);

        Dependencia depEmisora = dependenciaRepository.findById(dependenciaEmisoraId)
                .orElseThrow(() -> new RuntimeException("Dependencia no encontrada: " + dependenciaEmisoraId));

        boolean tienePdf = archivoCopiaSellada != null && !archivoCopiaSellada.isEmpty();
        if (tienePdf) {
            archivoService.validarPdf(archivoCopiaSellada); // antes de grabar nada
        }

        ActuacionDocumento actuacion = ActuacionDocumento.builder()
                .documento(doc)
                .dependenciaEmisora(depEmisora)
                .tipoActuacion(tipoActuacion)
                .numeroActuacion(nroLimpio)
                .descripcionResumen(descripcionResumen)
                .ubicacionBibliorato(ubicacionBibliorato)
                .fechaHoraActuacion(LocalDateTime.now())
                .usuario(usuarioSesion)
                .build();

        if (ubicacionBibliorato != null && !ubicacionBibliorato.isBlank()) {
            doc.setUbicacionBibliorato(ubicacionBibliorato);
            documentoRepository.save(doc);
        }

        ActuacionDocumento actuacionGuardada = actuacionDocumentoRepository.save(actuacion);

        if (tienePdf) {
            AdjuntoDocumento adjunto = registrarAdjunto(doc, actuacionGuardada, archivoCopiaSellada, "ACTUACION", "COPIA_SELLADA", usuarioSesion);
            actuacionGuardada.setRutaCopiaSelladaPdf(adjunto.getRutaAlmacenamiento());
        }

        // 🛡️ REGISTRO DE AUDITORÍA
        auditoriaService.registrarEvento(
            doc, 
            "REGISTRO_ACTUACION", 
            doc.getEstadoActual(), 
            doc.getEstadoActual(), 
            "Nueva actuación cargada: " + tipoActuacion + " N° " + nroLimpio + " por " + depEmisora.getNombreVisible(), 
            usuarioSesion, 
            null
        );

        // 🔄 EVALUACIÓN AUTOMÁTICA DE ESTADO GLOBAL
        verificarYActualizarEstadoGlobal(documentoId);

        return actuacionGuardada;
    }

    // Ubica el PDF de una actuación (copia sellada o acuse de pase). Se busca en la carpeta
    // relativa histórica "uploads/acuses" y en app.upload.dir.
    public Path resolverPdfActuacion(Long actuacionId, Usuario usuario) {
        ActuacionDocumento actuacion = actuacionDocumentoRepository.findById(actuacionId).orElse(null);
        if (actuacion == null || actuacion.getRutaCopiaSelladaPdf() == null || actuacion.getRutaCopiaSelladaPdf().isBlank()) {
            return null;
        }
        verificarAccesoDocumento(usuario, actuacion.getDocumento().getId());
        return archivoService.resolver(actuacion.getRutaCopiaSelladaPdf());
    }

    public List<ActuacionDocumento> obtenerActuacionesPorDocumento(Long documentoId) {
        return actuacionDocumentoRepository.findByDocumentoIdOrderByFechaHoraActuacionAsc(documentoId);
    }

    @Transactional
    public ActuacionDocumento confirmarRecepcionPaseInterno(Long actuacionId,
                                                            MultipartFile archivoAcusePdf,
                                                            Usuario usuarioSesion) {
        if (archivoAcusePdf == null || archivoAcusePdf.isEmpty()) {
            throw new IllegalArgumentException("Es OBLIGATORIO subir el PDF de la copia sellada y firmada para confirmar la recepción del pase.");
        }

        ActuacionDocumento actuacion = actuacionDocumentoRepository.findByIdParaActualizar(actuacionId)
                .orElseThrow(() -> new RuntimeException("Actuación no encontrada: " + actuacionId));
        verificarAccesoDocumento(usuarioSesion, actuacion.getDocumento().getId());
        documentoRepository.findByIdParaActualizar(actuacion.getDocumento().getId()); // serializa con derivar/anular

        if (!"PASE_INTERNO".equals(actuacion.getTipoActuacion()) || Boolean.TRUE.equals(actuacion.getPaseConfirmado())
                || Boolean.TRUE.equals(actuacion.getAnulada())) {
            throw new IllegalStateException("La actuación no es un pase interno pendiente de confirmación.");
        }

        // Solo confirma la dependencia receptora (o quien supervisa los pases del ámbito)
        Dependencia receptora = actuacion.getDependenciaReceptoraPase();
        Dependencia depUsuario = usuarioSesion != null ? usuarioSesion.getDependencia() : null;
        boolean esReceptora = depUsuario != null && receptora != null && receptora.getId().equals(depUsuario.getId());
        // La mesa de una dirección recibe en su nombre: cuenta como receptora quien actúa por ella
        esReceptora = esReceptora || (receptora != null && canalService.puedeActuarPor(usuarioSesion, receptora));
        if (!esReceptora && !supervisaPase(usuarioSesion, actuacion)) {
            throw new AccessDeniedException("Solo la dependencia receptora del pase puede confirmar su recepción.");
        }

        AdjuntoDocumento adjunto = registrarAdjunto(actuacion.getDocumento(), actuacion, archivoAcusePdf,
                "ACUSE_PASE", "ACUSE_PASE", usuarioSesion);
        actuacion.setRutaCopiaSelladaPdf(adjunto.getRutaAlmacenamiento());
        actuacion.setPaseConfirmado(true);
        actuacion.setTipoActuacion("PASE_CONFIRMADO");

        ActuacionDocumento actuacionGuardada = actuacionDocumentoRepository.save(actuacion);

        // 📦 Un pase de trámite confirmado entrega el original: la custodia pasa al receptor
        if (actuacion.getTipoPase() == TipoPase.TRAMITE && receptora != null) {
            Documento doc = actuacionGuardada.getDocumento();
            String anterior = custodiaDe(doc).getNombreVisible();
            doc.setDependenciaCustodia(receptora);
            documentoRepository.save(doc);
            auditoriaService.registrarEvento(doc, "CAMBIO_CUSTODIA", doc.getEstadoActual(), doc.getEstadoActual(),
                    "El original pasa de " + anterior + " a " + receptora.getNombreVisible(), usuarioSesion, null);
        }

        // 🛡️ REGISTRO DE AUDITORÍA
        auditoriaService.registrarEvento(
            actuacionGuardada.getDocumento(), 
            "CONFIRMACION_PASE_INTERNO", 
            actuacionGuardada.getDocumento().getEstadoActual(), 
            actuacionGuardada.getDocumento().getEstadoActual(), 
            "Recepción física de pase interno confirmada con PDF por "
                + (receptora != null ? receptora.getNombreVisible() : "dependencia receptora")
                + " (pase emitido por " + actuacionGuardada.getDependenciaEmisora().getNombreVisible() + ")",
            usuarioSesion,
            null
        );

        // 🔄 EVALUACIÓN AUTOMÁTICA DE ESTADO GLOBAL
        verificarYActualizarEstadoGlobal(actuacionGuardada.getDocumento().getId());

        return actuacionGuardada;
    }

    // Área que recibe un pedido de informe: si llegó a la mesa de entrada de una dirección, la dirección
    // completa (la mesa lo redistribuye); si no, la dependencia misma con sus subordinadas
    static Dependencia areaReceptora(Dependencia receptora) {
        if (Boolean.TRUE.equals(receptora.getEsMesaEntrada()) && receptora.getDependenciaPadre() != null) {
            return receptora.getDependenciaPadre();
        }
        return receptora;
    }

    // Auditoría A4: un pedido de informe está respondido si el área que lo recibió emitió un informe o memo
    // DESPUÉS del pedido. Antes bastaba cualquier respuesta de esa dependencia, aun de un pedido anterior.
    static boolean informeRespondido(ActuacionDocumento pase, List<ActuacionDocumento> respuestas) {
        Dependencia area = areaReceptora(pase.getDependenciaReceptoraPase());
        return respuestas.stream().anyMatch(r -> {
            Dependencia emisora = r.getDependenciaEmisora();
            boolean delArea = emisora != null && (emisora.getId().equals(area.getId())
                    || (emisora.getRuta() != null && area.getRuta() != null && emisora.getRuta().startsWith(area.getRuta())));
            boolean posterior = r.getFechaHoraActuacion() != null && pase.getFechaHoraActuacion() != null
                    && !r.getFechaHoraActuacion().isBefore(pase.getFechaHoraActuacion());
            return delArea && posterior;
        });
    }

    @Transactional
    public void verificarYActualizarEstadoGlobal(Long documentoId) {
        Documento doc = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));

        // Los pases retirados o rechazados no cuentan
        List<ActuacionDocumento> actuaciones = actuacionDocumentoRepository.findByDocumentoIdOrderByFechaHoraActuacionAsc(documentoId)
                .stream().filter(a -> !Boolean.TRUE.equals(a.getAnulada())).toList();

        // Al confirmar la recepción, un PASE_INTERNO pasa a PASE_CONFIRMADO: ambos cuentan como pase emitido
        List<ActuacionDocumento> pasesInforme = actuaciones.stream()
                .filter(a -> ("PASE_INTERNO".equals(a.getTipoActuacion()) || "PASE_CONFIRMADO".equals(a.getTipoActuacion()))
                        && Boolean.TRUE.equals(a.getRequiereInforme()))
                .toList();

        List<ActuacionDocumento> respuestas = actuaciones.stream()
                .filter(a -> "INFORME_TECNICO".equals(a.getTipoActuacion()) || "MEMO".equals(a.getTipoActuacion()))
                .toList();

        long pasesParaInforme = pasesInforme.size();
        long respuestasRecibidas = respuestas.size();

        boolean todasRespondieron;
        boolean pasesConReceptora = pasesInforme.stream().allMatch(a -> a.getDependenciaReceptoraPase() != null);
        if (pasesConReceptora) {
            // Cada pedido de informe necesita su propia respuesta: del área que lo recibió y posterior al pedido
            pasesParaInforme = pasesInforme.size();
            respuestasRecibidas = pasesInforme.stream().filter(p -> informeRespondido(p, respuestas)).count();
            todasRespondieron = respuestasRecibidas == pasesParaInforme;
        } else {
            // Pases antiguos sin dependencia receptora: se mantiene el conteo simple
            todasRespondieron = respuestasRecibidas >= pasesParaInforme;
        }

        if (pasesParaInforme > 0 && todasRespondieron && doc.getEstadoActual() == EstadoDocumento.EN_ESPERA_INFORME) {
            EstadoDocumento estadoAnterior = doc.getEstadoActual();
            doc.setEstadoActual(EstadoDocumento.PENDIENTE_SALIDA);
            Documento docActualizado = documentoRepository.save(doc);

            // 🛡️ REGISTRO DE AUDITORÍA (Cambio Automático)
            auditoriaService.registrarEvento(
                docActualizado, 
                "CAMBIO_ESTADO_AUTOMATICO", 
                estadoAnterior, 
                docActualizado.getEstadoActual(), 
                "Todas las dependencias respondieron (" + respuestasRecibidas + "/" + pasesParaInforme + "). Trámite pasa automáticamente a PENDIENTE_SALIDA.", 
                null, 
                null
            );
        }
    }

    // =====================================================================
    // 📎 ADJUNTOS: cada PDF es una versión; nunca se sobrescribe ni se borra
    // =====================================================================

    private AdjuntoDocumento registrarAdjunto(Documento doc, ActuacionDocumento actuacion, MultipartFile archivo,
                                              String prefijo, String tipoAdjunto, Usuario usuario) {
        ArchivoService.ArchivoGuardado guardado = archivoService.guardarPdf(archivo, prefijo, doc.getCodigoCuti());
        return adjuntoDocumentoRepository.save(AdjuntoDocumento.builder()
                .documento(doc)
                .actuacion(actuacion)
                .nombreArchivo(archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : guardado.ruta().getFileName().toString())
                .rutaAlmacenamiento(guardado.ruta().toString())
                .tipoAdjunto(tipoAdjunto)
                .tamanioBytes(guardado.tamanio())
                .hashSha256(guardado.sha256())
                .tipoMime(guardado.tipoMime())
                .fechaHoraSubida(LocalDateTime.now())
                .usuarioSubida(usuario)
                .vigente(true)
                .build());
    }

    // Dependencia responsable del PDF de una actuación: la receptora si es el acuse de un pase,
    // la emisora en el resto (informes, memos, actas).
    private Integer dependenciaResponsablePdf(ActuacionDocumento act) {
        if ("PASE_CONFIRMADO".equals(act.getTipoActuacion()) && act.getDependenciaReceptoraPase() != null) {
            return act.getDependenciaReceptoraPase().getId();
        }
        return act.getDependenciaEmisora() != null ? act.getDependenciaEmisora().getId() : null;
    }

    public boolean puedeReemplazarPdf(ActuacionDocumento act, Usuario usuario) {
        if (act == null || usuario == null || act.getRutaCopiaSelladaPdf() == null || Boolean.TRUE.equals(act.getAnulada())) {
            return false;
        }
        if (supervisaPase(usuario, act)) {
            return true;
        }
        Integer responsable = dependenciaResponsablePdf(act);
        return responsable != null && usuario.getDependencia() != null && responsable.equals(usuario.getDependencia().getId());
    }

    // Reemplaza el PDF de una actuación: la versión anterior queda como "reemplazada" con su motivo
    @Transactional
    public AdjuntoDocumento reemplazarPdfActuacion(Long actuacionId, MultipartFile archivo, String motivo, Usuario usuario) {
        ActuacionDocumento act = actuacionDocumentoRepository.findByIdParaActualizar(actuacionId)
                .orElseThrow(() -> new IllegalArgumentException("Actuación no encontrada: " + actuacionId));
        Documento doc = documentoRepository.findByIdParaActualizar(act.getDocumento().getId()).orElseThrow();
        verificarAccesoDocumento(usuario, doc.getId());
        validarMotivo(motivo, 10, "el motivo del reemplazo");

        if (act.getRutaCopiaSelladaPdf() == null) {
            throw new IllegalStateException("La actuación no tiene un PDF cargado para reemplazar.");
        }
        if (!puedeReemplazarPdf(act, usuario)) {
            throw new AccessDeniedException("Solo la dependencia que cargó el PDF (o quien supervisa los pases del ámbito) puede reemplazarlo.");
        }

        AdjuntoDocumento previo = adjuntoDocumentoRepository.findByActuacionIdOrderByFechaHoraSubidaAsc(actuacionId).stream()
                .filter(a -> Boolean.TRUE.equals(a.getVigente()))
                .reduce((a, b) -> b)
                .orElseGet(() -> registrarVersionHistorica(act)); // PDF anterior a esta versión del sistema

        AdjuntoDocumento nuevo = registrarAdjunto(doc, act, archivo, "ACTUACION_V", previo.getTipoAdjunto(), usuario);
        marcarReemplazado(previo, nuevo, motivo);

        act.setRutaCopiaSelladaPdf(nuevo.getRutaAlmacenamiento());
        actuacionDocumentoRepository.save(act);

        auditoriaService.registrarEvento(doc, "REEMPLAZO_PDF", doc.getEstadoActual(), doc.getEstadoActual(),
                "PDF de la actuación " + act.getTipoActuacion() + " (" + act.getId() + ") reemplazado. Archivo anterior: "
                        + previo.getNombreArchivo() + ". Motivo: " + motivo.trim(),
                usuario, null);
        return nuevo;
    }

    // Reemplaza el acuse/adjunto principal del documento (salida o acuse final). Requiere REGISTRAR_SALIDA.
    @Transactional
    public AdjuntoDocumento reemplazarAdjuntoDocumento(Long documentoId, MultipartFile archivo, String motivo, Usuario usuario) {
        politicaAcceso.exigir(usuario, Accion.REGISTRAR_SALIDA, "No está habilitado para reemplazar el acuse del expediente.");
        validarMotivo(motivo, 10, "el motivo del reemplazo");
        Documento doc = exigirCustodia(documentoId, usuario, "reemplazar el acuse de");

        AdjuntoDocumento previo = obtenerAdjuntoVigenteDocumento(documentoId);
        if (previo == null) {
            throw new IllegalStateException("El expediente no tiene un acuse cargado para reemplazar.");
        }

        AdjuntoDocumento nuevo = registrarAdjunto(doc, null, archivo, "ACUSE_V", previo.getTipoAdjunto(), usuario);
        marcarReemplazado(previo, nuevo, motivo);

        auditoriaService.registrarEvento(doc, "REEMPLAZO_ACUSE", doc.getEstadoActual(), doc.getEstadoActual(),
                "Acuse " + previo.getTipoAdjunto() + " reemplazado. Archivo anterior: " + previo.getNombreArchivo()
                        + ". Motivo: " + motivo.trim(),
                usuario, null);
        return nuevo;
    }

    private void marcarReemplazado(AdjuntoDocumento previo, AdjuntoDocumento nuevo, String motivo) {
        previo.setVigente(false);
        previo.setReemplazadoPor(nuevo);
        previo.setMotivoReemplazo(motivo.trim());
        adjuntoDocumentoRepository.save(previo);
    }

    // Crea el registro de un PDF que existía solo como ruta en la actuación (antes del versionado)
    private AdjuntoDocumento registrarVersionHistorica(ActuacionDocumento act) {
        Path ruta = archivoService.resolver(act.getRutaCopiaSelladaPdf());
        long tamanio = 0;
        try {
            if (ruta != null) tamanio = Files.size(ruta);
        } catch (IOException ignored) {}
        return adjuntoDocumentoRepository.save(AdjuntoDocumento.builder()
                .documento(act.getDocumento())
                .actuacion(act)
                .nombreArchivo(Paths.get(act.getRutaCopiaSelladaPdf()).getFileName().toString())
                .rutaAlmacenamiento(ruta != null ? ruta.toString() : act.getRutaCopiaSelladaPdf())
                .tipoAdjunto("PASE_CONFIRMADO".equals(act.getTipoActuacion()) ? "ACUSE_PASE" : "COPIA_SELLADA")
                .tamanioBytes(tamanio)
                .fechaHoraSubida(act.getFechaHoraActuacion() != null ? act.getFechaHoraActuacion() : LocalDateTime.now())
                .usuarioSubida(act.getUsuario())
                .vigente(true)
                .build());
    }

    // Acuse vigente del documento: prioriza el acuse final de entrega sobre el de salida
    public AdjuntoDocumento obtenerAdjuntoVigenteDocumento(Long documentoId) {
        List<AdjuntoDocumento> vigentes = adjuntoDocumentoRepository.findByDocumentoIdAndActuacionIsNullOrderByFechaHoraSubidaAsc(documentoId)
                .stream().filter(a -> Boolean.TRUE.equals(a.getVigente())).toList();
        return vigentes.stream().filter(a -> "ACUSE_ENTREGA".equals(a.getTipoAdjunto())).reduce((a, b) -> b)
                .orElse(vigentes.isEmpty() ? null : vigentes.get(vigentes.size() - 1));
    }

    public List<AdjuntoDocumento> obtenerAdjuntosDocumento(Long documentoId) {
        return adjuntoDocumentoRepository.findByDocumentoIdAndActuacionIsNullOrderByFechaHoraSubidaAsc(documentoId);
    }

    public List<AdjuntoDocumento> obtenerVersionesPdfActuaciones(List<Long> actuacionIds) {
        return actuacionIds.isEmpty() ? List.of() : adjuntoDocumentoRepository.findByActuacionIdInOrderByFechaHoraSubidaAsc(actuacionIds);
    }

    // 🔐 Verificación de integridad: compara la huella registrada con el archivo actual en disco
    public record ResultadoIntegridad(String estado, String detalle) {
        public static final String INTEGRO = "INTEGRO";
        public static final String ALTERADO = "ALTERADO";
        public static final String FALTANTE = "FALTANTE";
        public static final String SIN_HUELLA = "SIN_HUELLA";
    }

    public ResultadoIntegridad verificarIntegridadAdjunto(Long adjuntoId, Usuario usuario) {
        AdjuntoDocumento adj = adjuntoDocumentoRepository.findById(adjuntoId)
                .orElseThrow(() -> new IllegalArgumentException("Adjunto no encontrado: " + adjuntoId));
        verificarAccesoDocumento(usuario, adj.getDocumento().getId());

        Path ruta = archivoService.resolver(adj.getRutaAlmacenamiento());
        if (ruta == null) {
            return new ResultadoIntegridad(ResultadoIntegridad.FALTANTE, "El archivo no está en el servidor.");
        }
        if (adj.getHashSha256() == null) {
            return new ResultadoIntegridad(ResultadoIntegridad.SIN_HUELLA, "Adjunto anterior al registro de huellas: no se puede verificar.");
        }
        String actual = archivoService.calcularSha256(ruta);
        return actual.equals(adj.getHashSha256())
                ? new ResultadoIntegridad(ResultadoIntegridad.INTEGRO, "El archivo coincide con el original (SHA-256 " + actual.substring(0, 12) + "…).")
                : new ResultadoIntegridad(ResultadoIntegridad.ALTERADO, "¡El archivo en disco NO coincide con el que se subió!");
    }

    // Descarga de cualquier versión (vigente o reemplazada) con control de acceso
    public Path resolverAdjunto(Long adjuntoId, Usuario usuario) {
        AdjuntoDocumento adj = adjuntoDocumentoRepository.findById(adjuntoId).orElse(null);
        if (adj == null) {
            return null;
        }
        verificarAccesoDocumento(usuario, adj.getDocumento().getId());
        return archivoService.resolver(adj.getRutaAlmacenamiento());
    }

    // =====================================================================
    // ↩️ RETIRAR / RECHAZAR PASES (antes de la confirmación con copia sellada)
    // =====================================================================

    public static final String ANULACION_RETIRADO = "RETIRADO";
    public static final String ANULACION_RECHAZADO = "RECHAZADO";
    public static final String ANULACION_CIERRE_EXCEPCIONAL = "CIERRE_EXCEPCIONAL";

    private boolean esPasePendiente(ActuacionDocumento act) {
        return act != null && "PASE_INTERNO".equals(act.getTipoActuacion())
                && !Boolean.TRUE.equals(act.getPaseConfirmado()) && !Boolean.TRUE.equals(act.getAnulada());
    }

    // Retira el pase la dependencia que lo emitió (o quien supervisa los pases del ámbito)
    public boolean puedeRetirarPase(ActuacionDocumento act, Usuario usuario) {
        if (!esPasePendiente(act) || usuario == null) {
            return false;
        }
        // Retira quien actúa por la emisora (su mesa, su responsable) o quien supervisa los pases del ámbito
        return canalService.puedeActuarPor(usuario, act.getDependenciaEmisora()) || supervisaPase(usuario, act);
    }

    // Rechaza la recepción únicamente la dependencia receptora
    public boolean puedeRechazarPase(ActuacionDocumento act, Usuario usuario) {
        if (!esPasePendiente(act) || usuario == null || usuario.getDependencia() == null) {
            return false;
        }
        Dependencia receptora = act.getDependenciaReceptoraPase();
        return receptora != null && canalService.puedeActuarPor(usuario, receptora);
    }

    @Transactional
    public ActuacionDocumento anularPase(Long actuacionId, String accion, String motivo, Usuario usuario) {
        ActuacionDocumento pase = actuacionDocumentoRepository.findByIdParaActualizar(actuacionId)
                .orElseThrow(() -> new IllegalArgumentException("Pase no encontrado: " + actuacionId));
        verificarAccesoDocumento(usuario, pase.getDocumento().getId());
        Documento doc = documentoRepository.findByIdParaActualizar(pase.getDocumento().getId()).orElseThrow();
        validarNoFinalizado(doc, "modificar pases de");
        validarMotivo(motivo, 10, "el motivo");

        if (!esPasePendiente(pase)) {
            throw new IllegalStateException("Solo se puede retirar o rechazar un pase pendiente: este ya fue confirmado con copia sellada o anulado.");
        }

        String tipoAnulacion;
        if ("RETIRAR".equals(accion)) {
            if (!puedeRetirarPase(pase, usuario)) {
                throw new AccessDeniedException("Solo la dependencia que emitió el pase (o quien supervisa los pases del ámbito) puede retirarlo.");
            }
            tipoAnulacion = ANULACION_RETIRADO;
        } else if ("RECHAZAR".equals(accion)) {
            if (!puedeRechazarPase(pase, usuario)) {
                throw new AccessDeniedException("Solo la dependencia receptora puede rechazar la recepción del pase.");
            }
            tipoAnulacion = ANULACION_RECHAZADO;
        } else {
            throw new IllegalArgumentException("Acción no válida: " + accion);
        }
        return aplicarAnulacion(pase, doc, tipoAnulacion, motivo, usuario);
    }

    // =====================================================================
    // ⏳ PASES DEMORADOS Y TRABADOS (auditoría M2)
    // =====================================================================

    // Un pase esperando acuse fuera de plazo (días hábiles >= crítico) o hacia una dependencia en la que
    // nadie puede recibirlo (sinReceptor)
    public record PaseDemorado(Long actuacionId, Long documentoId, String cuti, String emisora, String receptora,
                               TipoPase tipoPase, LocalDateTime fecha, long diasHabiles, boolean sinReceptor) {}

    // Cuántos pases demorados hay en total y los más viejos, que son los que se muestran
    public record AvisoPases(long total, List<PaseDemorado> pases) {
        public static final AvisoPases NINGUNO = new AvisoPases(0, List.of());
    }

    static final int PASES_EN_AVISO = 10;
    static final int PASES_EN_PANEL = 200;

    // Aviso para la mesa que emitió: sus pases fuera de plazo o trabados
    public AvisoPases pasesEmitidosDemorados(Usuario usuario) {
        if (usuario == null) {
            return AvisoPases.NINGUNO;
        }
        return demorados(usuario, false, PASES_EN_AVISO);
    }

    // Panel del administrador: los pases trabados o fuera de plazo de toda la institución.
    // Solo datos de circulación (CUTI, dependencias, fecha): no el contenido del expediente.
    public AvisoPases pasesTrabados(Usuario admin) {
        politicaAcceso.exigir(admin, Accion.ADMINISTRAR_SISTEMA, "Solo un administrador del sistema puede ver los pases trabados.");
        return demorados(admin, true, PASES_EN_PANEL);
    }

    // La base filtra y cuenta: con volumen real hay miles de pases pendientes (medido con 200.000 expedientes)
    private AvisoPases demorados(Usuario usuario, boolean todos, int maximo) {
        List<Integer> receptoras = actuacionDocumentoRepository.receptorasDePasesPendientes(usuario.getId(), todos);
        if (receptoras.isEmpty()) {
            return AvisoPases.NINGUNO;
        }
        Set<Integer> conReceptor = new HashSet<>(dependenciaRepository.findConReceptor(receptoras));
        List<Integer> sinReceptor = new ArrayList<>(receptoras.stream().filter(id -> !conReceptor.contains(id)).toList());
        if (sinReceptor.isEmpty()) {
            sinReceptor.add(-1); // IN () no es SQL válido
        }
        LocalDate hoy = LocalDate.now();
        LocalDateTime limite = inicioDelPlazoVencido(hoy).plusDays(1).atStartOfDay();

        long total = actuacionDocumentoRepository.contarPasesDemorados(usuario.getId(), todos, limite, sinReceptor);
        if (total == 0) {
            return AvisoPases.NINGUNO;
        }
        List<ActuacionDocumento> pases = actuacionDocumentoRepository.pasesDemorados(usuario.getId(), todos, limite, sinReceptor, maximo);
        Set<LocalDate> feriados = feriadoService.feriadosEntre(pases.get(0).getFechaHoraActuacion().toLocalDate(), hoy);
        List<PaseDemorado> resultado = new ArrayList<>();
        for (ActuacionDocumento a : pases) {
            Dependencia receptora = a.getDependenciaReceptoraPase();
            resultado.add(new PaseDemorado(a.getId(), a.getDocumento().getId(), a.getDocumento().getCodigoCuti(),
                    a.getDependenciaEmisora().getNombreVisible(), receptora != null ? receptora.getNombreVisible() : "(sin receptora)",
                    a.getTipoPase(), a.getFechaHoraActuacion(),
                    FeriadoService.diasHabilesEntre(a.getFechaHoraActuacion().toLocalDate(), hoy, feriados),
                    receptora == null || !conReceptor.contains(receptora.getId())));
        }
        return new AvisoPases(total, resultado);
    }

    // Último día en que pudo emitirse un pase para que hoy ya lleve app.sla.dias-critico días hábiles
    // sin acuse: los emitidos ese día o antes están fuera de plazo
    LocalDate inicioDelPlazoVencido(LocalDate hoy) {
        Set<LocalDate> feriados = feriadoService.feriadosEntre(hoy.minusDays(slaDiasCritico * 3L + 30), hoy);
        LocalDate desde = hoy;
        int contados = 0;
        while (contados < slaDiasCritico) {
            if (FeriadoService.diasHabilesEntre(desde.minusDays(1), desde, feriados) == 1) {
                contados++;
            }
            desde = desde.minusDays(1);
        }
        return desde;
    }

    // El administrador retira un pase que nadie puede recibir (la receptora no tiene usuarios ni asignaciones
    // vigentes, o está dada de baja). Queda en la bitácora del expediente y en la de administración.
    @Transactional
    public ActuacionDocumento retirarPaseTrabado(Long actuacionId, String motivo, Usuario admin) {
        politicaAcceso.exigir(admin, Accion.ADMINISTRAR_SISTEMA, "Solo un administrador del sistema puede retirar pases trabados.");
        validarMotivo(motivo, 10, "el motivo");
        ActuacionDocumento pase = actuacionDocumentoRepository.findByIdParaActualizar(actuacionId)
                .orElseThrow(() -> new IllegalArgumentException("Pase no encontrado: " + actuacionId));
        if (!esPasePendiente(pase)) {
            throw new IllegalStateException("El pase ya fue confirmado o anulado.");
        }
        Dependencia receptora = pase.getDependenciaReceptoraPase();
        boolean sinReceptor = receptora == null || Boolean.FALSE.equals(receptora.getActivo())
                || dependenciaRepository.findConReceptor(List.of(receptora.getId())).isEmpty();
        if (!sinReceptor) {
            throw new IllegalStateException("La dependencia receptora tiene quién reciba el pase: lo confirma o rechaza ella, o lo retira la emisora.");
        }
        Documento doc = documentoRepository.findByIdParaActualizar(pase.getDocumento().getId()).orElseThrow();
        validarNoFinalizado(doc, "modificar pases de");
        String motivoCompleto = "Retirado por el administrador del sistema (receptora sin usuarios): " + motivo.trim();
        aplicarAnulacion(pase, doc, ANULACION_RETIRADO, motivoCompleto, admin);
        auditoriaSistemaRepository.save(AuditoriaSistema.builder()
                .usuario(admin)
                .accion("RETIRO_PASE_TRABADO")
                .entidad("PASE")
                .entidadId(pase.getId())
                .detalle(doc.getCodigoCuti() + ": " + pase.getDependenciaEmisora().getNombreVisible() + " ➔ "
                        + (receptora != null ? receptora.getNombreVisible() : "(sin receptora)") + ". Motivo: " + motivo.trim())
                .build());
        return pase;
    }

    private ActuacionDocumento aplicarAnulacion(ActuacionDocumento pase, Documento doc, String tipoAnulacion,
                                                String motivo, Usuario usuario) {
        pase.setAnulada(true);
        pase.setTipoAnulacion(tipoAnulacion);
        pase.setMotivoAnulacion(motivo.trim());
        pase.setFechaHoraAnulacion(LocalDateTime.now());
        pase.setUsuarioAnulacion(usuario);
        actuacionDocumentoRepository.save(pase);

        // Si ya no queda ningún pase vigente pidiendo informe, el expediente deja de esperar informes
        EstadoDocumento estadoAnterior = doc.getEstadoActual();
        if (doc.getEstadoActual() == EstadoDocumento.EN_ESPERA_INFORME && !quedanPasesParaInformeVigentes(doc.getId())) {
            doc.setEstadoActual(EstadoDocumento.EN_REVISION);
            documentoRepository.save(doc);
        }

        String receptora = pase.getDependenciaReceptoraPase() != null ? pase.getDependenciaReceptoraPase().getNombreVisible() : "dependencia receptora";
        auditoriaService.registrarEvento(doc,
                ANULACION_RETIRADO.equals(tipoAnulacion) ? "PASE_RETIRADO" : "PASE_RECHAZADO",
                estadoAnterior, doc.getEstadoActual(),
                (ANULACION_RETIRADO.equals(tipoAnulacion) ? "Pase retirado" : "Recepción rechazada")
                        + " (" + pase.getDependenciaEmisora().getNombreVisible() + " ➔ " + receptora + "). Motivo: " + motivo.trim(),
                usuario, null);

        // Las demás dependencias pueden haber respondido ya todos los informes vigentes
        verificarYActualizarEstadoGlobal(doc.getId());
        return pase;
    }

    private boolean quedanPasesParaInformeVigentes(Long documentoId) {
        return actuacionDocumentoRepository.findByDocumentoIdOrderByFechaHoraActuacionAsc(documentoId).stream()
                .anyMatch(a -> !Boolean.TRUE.equals(a.getAnulada())
                        && ("PASE_INTERNO".equals(a.getTipoActuacion()) || "PASE_CONFIRMADO".equals(a.getTipoActuacion()))
                        && Boolean.TRUE.equals(a.getRequiereInforme()));
    }

    // =====================================================================
    // ⚠️ CIERRE FORZADO / SALIDA EXCEPCIONAL (requiere FORZAR_CIERRE)
    // =====================================================================

    @Transactional
    public Documento forzarCierreInformes(Long documentoId, String justificacion, Usuario usuario) {
        politicaAcceso.exigir(usuario, Accion.FORZAR_CIERRE, "No está habilitado para forzar el cierre de informes.");
        validarMotivo(justificacion, 20, "la justificación");

        Documento doc = exigirCustodia(documentoId, usuario, "forzar el cierre de informes de");
        EstadoDocumento estadoAnterior = doc.getEstadoActual();
        if (estadoAnterior == EstadoDocumento.PENDIENTE_SALIDA || estadoAnterior == EstadoDocumento.FINALIZADO
                || estadoAnterior == EstadoDocumento.ARCHIVADO) {
            throw new IllegalStateException("El expediente ya está en " + estadoAnterior + ": no corresponde forzar el cierre.");
        }

        // Dependencias a las que se pidió informe y todavía no respondieron (queda constancia)
        List<ActuacionDocumento> vigentes = actuacionDocumentoRepository.findByDocumentoIdOrderByFechaHoraActuacionAsc(documentoId)
                .stream().filter(a -> !Boolean.TRUE.equals(a.getAnulada())).toList();
        List<ActuacionDocumento> respuestas = vigentes.stream()
                .filter(a -> "INFORME_TECNICO".equals(a.getTipoActuacion()) || "MEMO".equals(a.getTipoActuacion()))
                .toList();
        List<ActuacionDocumento> pedidosSinRespuesta = vigentes.stream()
                .filter(a -> ("PASE_INTERNO".equals(a.getTipoActuacion()) || "PASE_CONFIRMADO".equals(a.getTipoActuacion()))
                        && Boolean.TRUE.equals(a.getRequiereInforme())
                        && a.getDependenciaReceptoraPase() != null
                        && !informeRespondido(a, respuestas))
                .toList();
        List<String> sinRespuesta = pedidosSinRespuesta.stream()
                .map(a -> a.getDependenciaReceptoraPase().getNombreVisible())
                .distinct().toList();

        // Auditoría A4: los pedidos que todavía esperan acuse se anulan; si no, seguirían en "Por recibir"
        // de la receptora, que podría confirmarlos y contestar sobre un expediente ya cerrado
        LocalDateTime ahora = LocalDateTime.now();
        for (ActuacionDocumento pedido : pedidosSinRespuesta) {
            if ("PASE_INTERNO".equals(pedido.getTipoActuacion()) && !Boolean.TRUE.equals(pedido.getPaseConfirmado())) {
                pedido.setAnulada(true);
                pedido.setTipoAnulacion(ANULACION_CIERRE_EXCEPCIONAL);
                pedido.setMotivoAnulacion("Cierre excepcional de informes: " + justificacion.trim());
                pedido.setFechaHoraAnulacion(ahora);
                pedido.setUsuarioAnulacion(usuario);
                actuacionDocumentoRepository.save(pedido);
            }
        }

        doc.setEstadoActual(EstadoDocumento.PENDIENTE_SALIDA);
        documentoRepository.save(doc);

        String detalle = "CIERRE EXCEPCIONAL DE INFORMES: " + justificacion.trim()
                + (sinRespuesta.isEmpty() ? "" : " | Sin respuesta de: " + String.join(", ", sinRespuesta));

        // Queda visible en el árbol de trazabilidad, no solo en la bitácora. Lo emite quien tiene el original.
        actuacionDocumentoRepository.save(ActuacionDocumento.builder()
                .documento(doc)
                .dependenciaEmisora(custodiaDe(doc))
                .tipoActuacion("CIERRE_EXCEPCIONAL")
                .numeroActuacion("S/N")
                .descripcionResumen(detalle)
                .ubicacionBibliorato(doc.getUbicacionBibliorato())
                .fechaHoraActuacion(LocalDateTime.now())
                .usuario(usuario)
                .build());

        auditoriaService.registrarEvento(doc, "CIERRE_FORZADO", estadoAnterior, doc.getEstadoActual(), detalle, usuario, null);
        return doc;
    }

    private void validarMotivo(String motivo, int minimo, String nombre) {
        if (motivo == null || motivo.trim().length() < minimo) {
            throw new IllegalArgumentException("Debe ingresar " + nombre + " (mínimo " + minimo + " caracteres).");
        }
    }

    // =====================================================================
    // ⏱️ SLA: días hábiles sin movimiento y nivel de alerta por expediente
    // =====================================================================

    public record IndicadorSla(long diasHabiles, String nivel) {
        public static final String OK = "OK";
        public static final String ADVERTENCIA = "ADVERTENCIA";
        public static final String CRITICO = "CRITICO";
    }

    // Una sola consulta para toda la bandeja. "Movimiento" = última actuación vigente o el ingreso.
    public Map<Long, IndicadorSla> calcularSla(List<Documento> documentos) {
        Map<Long, IndicadorSla> resultado = new HashMap<>();
        List<Documento> activos = documentos.stream()
                .filter(d -> !ESTADOS_CERRADOS.contains(d.getEstadoActual())).toList();
        if (activos.isEmpty()) {
            return resultado;
        }

        Map<Long, LocalDateTime> ultimoMovimiento = new HashMap<>();
        for (Object[] fila : actuacionDocumentoRepository.obtenerUltimoMovimientoPorDocumento(
                activos.stream().map(Documento::getId).toList())) {
            ultimoMovimiento.put((Long) fila[0], (LocalDateTime) fila[1]);
        }

        LocalDate hoy = LocalDate.now();
        Map<Long, LocalDate> bases = new HashMap<>();
        for (Documento d : activos) {
            LocalDateTime base = d.getFechaHoraIngreso();
            LocalDateTime mov = ultimoMovimiento.get(d.getId());
            if (mov != null && (base == null || mov.isAfter(base))) {
                base = mov;
            }
            if (base != null) {
                bases.put(d.getId(), base.toLocalDate());
            }
        }
        // Los feriados de todo el período en una sola consulta
        Set<LocalDate> feriados = bases.values().stream().min(LocalDate::compareTo)
                .map(desde -> feriadoService.feriadosEntre(desde, hoy)).orElse(Set.of());

        for (Documento d : activos) {
            LocalDate base = bases.get(d.getId());
            long dias = base != null ? FeriadoService.diasHabilesEntre(base, hoy, feriados) : 0;

            boolean urgente = d.getPrioridad() == Prioridad.URGENTE;
            int advertencia = urgente ? slaUrgenteDiasAdvertencia : slaDiasAdvertencia;
            int critico = urgente ? slaUrgenteDiasCritico : slaDiasCritico;
            String nivel = dias >= critico ? IndicadorSla.CRITICO : dias >= advertencia ? IndicadorSla.ADVERTENCIA : IndicadorSla.OK;
            resultado.put(d.getId(), new IndicadorSla(dias, nivel));
        }
        return resultado;
    }

    // Días hábiles (lunes a viernes) transcurridos, sin feriados. El cálculo con feriados está en FeriadoService.
    static long diasHabilesEntre(LocalDate desde, LocalDate hasta) {
        return FeriadoService.diasHabilesEntre(desde, hasta, Set.of());
    }

    // 🏆 Carga segmentada de Documentos Finalizados por Rol/Dependencia
    public List<Documento> obtenerDocumentosFinalizadosParaUsuario(Usuario usuario) {
        if (usuario == null) {
            return new ArrayList<>();
        }

        // 🧭 Los finalizados más recientes que puede ver; los anteriores se encuentran con el buscador
        return documentoRepository.findFinalizadosAccesibles(usuario.getId());
    }
}