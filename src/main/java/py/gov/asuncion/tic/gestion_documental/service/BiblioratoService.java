package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import py.gov.asuncion.tic.gestion_documental.domain.Bibliorato;
import py.gov.asuncion.tic.gestion_documental.domain.Dependencia;
import py.gov.asuncion.tic.gestion_documental.domain.Documento;
import py.gov.asuncion.tic.gestion_documental.domain.Accion;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.BiblioratoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 📚 Catálogo de biblioratos y ubicación física de los documentos (bibliorato + fojas)
@Service
public class BiblioratoService {

    @Autowired
    private BiblioratoRepository biblioratoRepository;

    @Autowired
    private DependenciaRepository dependenciaRepository;

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private PoliticaAcceso politicaAcceso;

    public List<Bibliorato> listarActivos() {
        return biblioratoRepository.findByActivoTrueOrderByAnioDescCodigoAsc();
    }

    public List<Bibliorato> listarTodos() {
        return biblioratoRepository.findAllByOrderByAnioDescCodigoAsc();
    }

    // Uso de cada bibliorato: id -> [cantidad de documentos, última foja ocupada]
    public Map<Integer, long[]> obtenerUso() {
        Map<Integer, long[]> uso = new HashMap<>();
        for (Object[] fila : biblioratoRepository.contarDocumentosPorBibliorato()) {
            uso.put((Integer) fila[0], new long[] { (Long) fila[1], fila[2] != null ? ((Number) fila[2]).longValue() : 0 });
        }
        return uso;
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
        b.setActivo(activo);
        return biblioratoRepository.save(b);
    }

    // Valida y asigna la ubicación física a un documento (sin guardarlo).
    public Bibliorato asignarUbicacion(Documento doc, Integer biblioratoId, Integer fojaDesde, Integer fojaHasta) {
        Bibliorato b = biblioratoRepository.findById(biblioratoId)
                .orElseThrow(() -> new IllegalArgumentException("Bibliorato no encontrado: " + biblioratoId));
        if (!Boolean.TRUE.equals(b.getActivo())) {
            throw new IllegalArgumentException("El bibliorato " + b.getCodigo() + " está inactivo (cerrado): elija otro.");
        }

        validarFojas(fojaDesde, fojaHasta);
        if (fojaDesde != null) {
            if (b.getCapacidadFojas() != null && fojaHasta > b.getCapacidadFojas()) {
                throw new IllegalArgumentException("La foja " + fojaHasta + " supera la capacidad del bibliorato ("
                        + b.getCapacidadFojas() + " fojas).");
            }
            List<Documento> superpuestos = documentoRepository.findFojasSuperpuestas(b.getId(), fojaDesde, fojaHasta, doc.getId());
            if (!superpuestos.isEmpty()) {
                throw new IllegalArgumentException("Las fojas " + fojaDesde + "–" + fojaHasta + " ya están ocupadas en "
                        + b.getCodigo() + " por: " + superpuestos.stream()
                        .map(d -> d.getCodigoCuti() + " (fojas " + d.getFojaDesde() + "–" + d.getFojaHasta() + ")")
                        .collect(Collectors.joining(", ")));
            }
        }

        doc.setBibliorato(b);
        doc.setFojaDesde(fojaDesde);
        doc.setFojaHasta(fojaHasta);
        return b;
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
}
