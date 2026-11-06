package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import py.gov.asuncion.tic.gestion_documental.domain.Accion;
import py.gov.asuncion.tic.gestion_documental.domain.Bibliorato;
import py.gov.asuncion.tic.gestion_documental.domain.Dependencia;
import py.gov.asuncion.tic.gestion_documental.domain.Documento;
import py.gov.asuncion.tic.gestion_documental.domain.UbicacionDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.BiblioratoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.UbicacionDocumentoRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// 📚 Cada dependencia tiene sus biblioratos y registra dónde guarda su papel de cada documento.
// Una mesa o un responsable actúan también por sus subordinadas (CanalService.alcance).
@Service
public class BiblioratoService {

    @Autowired
    private BiblioratoRepository biblioratoRepository;

    @Autowired
    private DependenciaRepository dependenciaRepository;

    @Autowired
    private UbicacionDocumentoRepository ubicacionRepository;

    @Autowired
    private PoliticaAcceso politicaAcceso;

    @Autowired
    private CanalService canalService;

    // Las carpetas abiertas que el usuario puede usar
    public List<Bibliorato> listarActivos(Usuario usuario) {
        return delAlcance(usuario, biblioratoRepository.findByActivoTrueOrderByAnioDescCodigoAsc());
    }

    // También las cerradas: siguen guardando sus documentos
    public List<Bibliorato> listarTodos(Usuario usuario) {
        return delAlcance(usuario, biblioratoRepository.findAllByOrderByAnioDescCodigoAsc());
    }

    private List<Bibliorato> delAlcance(Usuario usuario, List<Bibliorato> biblioratos) {
        Predicate<Dependencia> alcance = canalService.alcance(usuario);
        return biblioratos.stream().filter(b -> alcance.test(b.getDependencia())).toList();
    }

    // Las dependencias para las que el usuario puede crear una carpeta
    public List<Dependencia> dependenciasDelAlcance(Usuario usuario) {
        Predicate<Dependencia> alcance = canalService.alcance(usuario);
        return dependenciaRepository.findAll().stream()
                .filter(d -> !Boolean.FALSE.equals(d.getActivo()))
                .filter(alcance)
                .sorted(Comparator.comparing(Dependencia::getNombreVisible))
                .toList();
    }

    // Uso de cada bibliorato: id -> [cantidad de documentos, última foja ocupada]
    public Map<Integer, long[]> obtenerUso() {
        Map<Integer, long[]> uso = new HashMap<>();
        for (Object[] fila : ubicacionRepository.contarPorBibliorato()) {
            uso.put((Integer) fila[0], new long[] { (Long) fila[1], fila[2] != null ? ((Number) fila[2]).longValue() : 0 });
        }
        return uso;
    }

    public List<UbicacionDocumento> ubicacionesEn(Collection<Integer> biblioratoIds) {
        return biblioratoIds.isEmpty() ? List.of() : ubicacionRepository.findDeBiblioratos(biblioratoIds);
    }

    // Dónde está cada documento de una lista: documento -> ubicaciones
    public Map<Long, List<UbicacionDocumento>> ubicacionesDe(Collection<Long> documentoIds) {
        if (documentoIds.isEmpty()) {
            return Map.of();
        }
        return ubicacionRepository.findDeDocumentos(documentoIds).stream()
                .collect(Collectors.groupingBy(u -> u.getDocumento().getId()));
    }

    public boolean tieneUbicacion(Long documentoId) {
        return ubicacionRepository.existsByDocumentoId(documentoId);
    }

    @Transactional
    public Bibliorato crear(String codigo, String descripcion, Integer dependenciaId, Integer anio,
                            String ubicacionFisica, Integer capacidadFojas, Usuario usuario) {
        exigirAdministrativo(usuario);
        String codigoLimpio = codigo == null ? "" : codigo.trim().toUpperCase();
        if (!codigoLimpio.matches("^[A-Z0-9][A-Z0-9-]{2,29}$")) {
            throw new IllegalArgumentException("Código inválido: use letras, números y guiones (3 a 30 caracteres). Ej: BIB-DTIC-2026-001");
        }
        if (biblioratoRepository.existsByCodigoIgnoreCase(codigoLimpio)) {
            throw new IllegalArgumentException("Ya existe un bibliorato con el código " + codigoLimpio + ".");
        }
        if (descripcion == null || descripcion.isBlank() || ubicacionFisica == null || ubicacionFisica.isBlank()) {
            throw new IllegalArgumentException("La descripción y la ubicación física son obligatorias.");
        }
        if (anio == null || anio < 2000 || anio > 2100) {
            throw new IllegalArgumentException("Año inválido.");
        }
        if (capacidadFojas != null && capacidadFojas <= 0) {
            throw new IllegalArgumentException("La capacidad de fojas debe ser mayor que cero.");
        }
        Dependencia dep = dependenciaRepository.findById(dependenciaId == null ? -1 : dependenciaId)
                .orElseThrow(() -> new IllegalArgumentException("Dependencia no encontrada."));
        exigirActuarPor(usuario, dep, "crear un bibliorato de " + dep.getNombreVisible());

        return biblioratoRepository.save(Bibliorato.builder()
                .codigo(codigoLimpio)
                .descripcion(descripcion.trim())
                .dependencia(dep)
                .anio(anio)
                .ubicacionFisica(ubicacionFisica.trim())
                .capacidadFojas(capacidadFojas)
                .activo(true)
                .fechaHoraAlta(LocalDateTime.now())
                .build());
    }

    // Un bibliorato inactivo no recibe documentos nuevos, pero conserva los que ya tiene
    @Transactional
    public Bibliorato cambiarEstado(Integer id, boolean activo, Usuario usuario) {
        exigirAdministrativo(usuario);
        Bibliorato b = biblioratoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bibliorato no encontrado: " + id));
        exigirActuarPor(usuario, b.getDependencia(), "cambiar el estado de un bibliorato de " + b.getDependencia().getNombreVisible());
        b.setActivo(activo);
        return biblioratoRepository.save(b);
    }

    // La carpeta donde se va a guardar: del alcance del usuario y abierta
    public Bibliorato biblioratoParaGuardar(Integer biblioratoId, Usuario usuario) {
        Bibliorato b = biblioratoRepository.findById(biblioratoId)
                .orElseThrow(() -> new IllegalArgumentException("Bibliorato no encontrado: " + biblioratoId));
        exigirActuarPor(usuario, b.getDependencia(), "guardar en un bibliorato de " + b.getDependencia().getNombreVisible());
        if (!Boolean.TRUE.equals(b.getActivo())) {
            throw new IllegalArgumentException("El bibliorato " + b.getCodigo() + " está inactivo (cerrado): elija otro.");
        }
        return b;
    }

    // Lo que esa dependencia ya tiene registrado de este documento
    public Optional<UbicacionDocumento> ubicacionActual(Long documentoId, Bibliorato bibliorato) {
        return ubicacionRepository.findDeDocumentoYDependencia(documentoId, bibliorato.getDependencia().getId());
    }

    // Registra o cambia la ubicación del documento en la dependencia dueña de la carpeta
    @Transactional
    public UbicacionDocumento guardarUbicacion(Documento doc, Bibliorato b, Integer fojaDesde, Integer fojaHasta, Usuario usuario) {
        validarFojas(fojaDesde, fojaHasta);
        if (fojaDesde != null) {
            if (b.getCapacidadFojas() != null && fojaHasta > b.getCapacidadFojas()) {
                throw new IllegalArgumentException("La foja " + fojaHasta + " supera la capacidad del bibliorato ("
                        + b.getCapacidadFojas() + " fojas).");
            }
            List<UbicacionDocumento> superpuestos = ubicacionRepository.findFojasSuperpuestas(b.getId(), fojaDesde, fojaHasta, doc.getId());
            if (!superpuestos.isEmpty()) {
                throw new IllegalArgumentException("Las fojas " + fojaDesde + "–" + fojaHasta + " ya están ocupadas en "
                        + b.getCodigo() + " por: " + superpuestos.stream()
                        .map(u -> u.getDocumento().getCodigoCuti() + " (fojas " + u.getFojaDesde() + "–" + u.getFojaHasta() + ")")
                        .collect(Collectors.joining(", ")));
            }
        }
        UbicacionDocumento ubicacion = ubicacionActual(doc.getId(), b).orElseGet(() -> UbicacionDocumento.builder()
                .documento(doc).dependencia(b.getDependencia()).build());
        ubicacion.setBibliorato(b);
        ubicacion.setFojaDesde(fojaDesde);
        ubicacion.setFojaHasta(fojaHasta);
        ubicacion.setUsuario(usuario);
        ubicacion.setFechaHora(LocalDateTime.now());
        return ubicacionRepository.save(ubicacion);
    }

    // Un registro anulado no sigue reservando lugar en las carpetas. Devuelve lo que liberó, para la bitácora
    @Transactional
    public String liberarUbicaciones(Long documentoId) {
        List<UbicacionDocumento> ubicaciones = ubicacionRepository.findByDocumentoId(documentoId);
        if (ubicaciones.isEmpty()) {
            return null;
        }
        String texto = ubicaciones.stream().map(UbicacionDocumento::getTexto).collect(Collectors.joining("; "));
        ubicacionRepository.deleteAll(ubicaciones);
        return texto;
    }

    // Fojas opcionales, pero si se indica una deben indicarse ambas, positivas y en orden
    static void validarFojas(Integer desde, Integer hasta) {
        if (desde == null && hasta == null) {
            return;
        }
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Indique la foja inicial y la final (pueden ser iguales si es una sola).");
        }
        if (desde <= 0 || hasta <= 0) {
            throw new IllegalArgumentException("Las fojas deben ser números mayores que cero.");
        }
        if (hasta < desde) {
            throw new IllegalArgumentException("La foja final (" + hasta + ") no puede ser menor que la inicial (" + desde + ").");
        }
    }

    private void exigirAdministrativo(Usuario usuario) {
        politicaAcceso.exigir(usuario, Accion.GESTIONAR_ARCHIVO, "No está habilitado para administrar el catálogo de biblioratos.");
    }

    private void exigirActuarPor(Usuario usuario, Dependencia dependencia, String accion) {
        if (!canalService.puedeActuarPor(usuario, dependencia)) {
            throw new AccessDeniedException("No puede " + accion + ": cada dependencia administra sus propios biblioratos.");
        }
    }
}
