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
import java.util.List;
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

    // ⏱️ Umbrales de demora (días hábiles sin movimiento), configurables en application.properties
    @Value("${app.sla.dias-advertencia:5}")
    private int slaDiasAdvertencia;

    @Value("${app.sla.dias-critico:10}")
    private int slaDiasCritico;

    @Value("${app.sla.urgente.dias-advertencia:2}")
    private int slaUrgenteDiasAdvertencia;

    @Value("${app.sla.urgente.dias-critico:4}")
    private int slaUrgenteDiasCritico;

    public List<Documento> obtenerDocumentosActivos() {
        return documentoRepository.findByEstadoActualNotOrderByFechaHoraIngresoDesc(EstadoDocumento.FINALIZADO);
    }

    public List<Dependencia> obtenerDependenciasActivas() {
        return dependenciaRepository.findByActivoTrueOrderByNombreAsc();
    }

    // Método auxiliar para encontrar la Dirección raíz o el nodo superior inmediato de un usuario
    public Dependencia obtenerDireccionRaiz(Dependencia dependenciaActual) {
        Dependencia actual = dependenciaActual;
        
        // Subimos por el padre mientras tenga uno y no sea la Intendencia Municipal (ID 1)
        while (actual != null && actual.getDependenciaPadreId() != null) {
            Dependencia padre = dependenciaRepository.findById(actual.getDependenciaPadreId()).orElse(null);
            
            // Si el padre es la Intendencia (ID 1) o llegamos al nivel de Dirección (ej. nivel jerárquico 4), 
            // entonces 'actual' es nuestra Dirección raíz.
            if (padre == null || padre.getId() == 1 || (actual.getNivelJerarquicoId() != null && actual.getNivelJerarquicoId() == 4)) {
                break;
            }
            actual = padre;
        }
        return actual;
    }


    // 🛡️ CONTROL DE ACCESO POR DEPENDENCIA
    public boolean esRolAdministrativo(Usuario usuario) {
        return usuario != null
                && (usuario.getRol() == RolUsuario.DIRECTOR || usuario.getRol() == RolUsuario.SECRETARIA_ADMIN);
    }

    // Dirección y Secretaría acceden a todo; el resto solo a los documentos originados
    // en su dependencia o en los que su dependencia emitió/recibió una actuación.
    public boolean puedeAccederDocumento(Usuario usuario, Long documentoId) {
        if (usuario == null || documentoId == null) {
            return false;
        }
        if (esRolAdministrativo(usuario)) {
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

    public void verificarAccesoDocumento(Usuario usuario, Long documentoId) {
        if (!puedeAccederDocumento(usuario, documentoId)) {
            throw new AccessDeniedException("Su dependencia no tiene acceso a este expediente.");
        }
    }

    public List<TipoDocumento> obtenerTiposDocumentoActivos() {
        return tipoDocumentoRepository.findByActivoTrueOrderByNombreAsc();
    }

    public List<Dependencia> obtenerDependenciasHijas(Usuario usuario) {
        if (usuario == null || usuario.getDependencia() == null) {
            return new ArrayList<>();
        }

        Dependencia depActual = usuario.getDependencia();

        // Si es la Secretaría Administrativa (ID 11) o Director, 
        // les devolvemos las dependencias que cuelgan directamente de la Dirección de TIC (ej. Padre ID 10)
        if (depActual.getId() == 11 || usuario.getRol() == RolUsuario.SECRETARIA_ADMIN || usuario.getRol() == RolUsuario.DIRECTOR) {
            return dependenciaRepository.findByDependenciaPadreIdAndActivoTrueOrderByNombreAsc(10);
        }

        // Para cualquier otro Jefe de Departamento, muestra únicamente las unidades hijas directas de su área
        return dependenciaRepository.findByDependenciaPadreIdAndActivoTrueOrderByNombreAsc(depActual.getId());
    }

    public List<Dependencia> obtenerDependenciasInternasTic() {
        return dependenciaRepository.findByEsInternaTicTrueAndActivoTrueOrderByNombreAsc();
    }

    public Map<String, Long> obtenerMetricasDashboard() {
        Map<String, Long> metricas = new HashMap<>();
        metricas.put("ingresados", documentoRepository.countByEstadoActual(EstadoDocumento.INGRESADO) 
                                         + documentoRepository.countByEstadoActual(EstadoDocumento.EN_REVISION));
        metricas.put("enEsperaInforme", documentoRepository.countByEstadoActual(EstadoDocumento.EN_ESPERA_INFORME));
        metricas.put("pendienteSalida", documentoRepository.countByEstadoActual(EstadoDocumento.PENDIENTE_SALIDA));
        metricas.put("urgentes", documentoRepository.countByPrioridadAndEstadoActualNot(Prioridad.URGENTE, EstadoDocumento.FINALIZADO));
        return metricas;
    }

    // Clave base del bloqueo de PostgreSQL para el correlativo del CUTI (se le suma el año)
    private static final long CLAVE_BLOQUEO_CUTI = 7_384_000_000L;

    // Debe llamarse dentro de una transacción: el bloqueo dura hasta el commit,
    // así dos registros simultáneos no obtienen el mismo número.
    private String generarCodigoCuti(int anio) {
        documentoRepository.bloquearCorrelativoCuti(CLAVE_BLOQUEO_CUTI + anio);
        String prefijo = String.format("TIC-%d-", anio);
        int ultimo = documentoRepository.obtenerUltimoCorrelativoCuti(prefijo + "%");
        return String.format("%s%06d", prefijo, ultimo + 1);
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

        Documento documento = new Documento();
        documento.setTipoDocumento(tipoDocumentoRepository.findById(dto.getTipoDocumentoId())
                .orElseThrow(() -> new IllegalArgumentException("Tipo de documento no encontrado: " + dto.getTipoDocumentoId())));
        documento.setDependenciaOrigen(dependenciaRepository.findById(dto.getDependenciaOrigenId())
                .orElseThrow(() -> new IllegalArgumentException("Dependencia no encontrada: " + dto.getDependenciaOrigenId())));
        documento.setPrioridad(dto.getPrioridad() != null ? dto.getPrioridad() : Prioridad.NORMAL);
        documento.setNumeroDocumentoOrigen(dto.getNumeroDocumentoOrigen());
        documento.setPersonaEntrega(dto.getPersonaEntrega());
        documento.setNroOrdenLibro(dto.getNroOrdenLibro());
        documento.setNroPaginaLibro(dto.getNroPaginaLibro());
        documento.setAsunto(dto.getAsunto().trim());

        return guardarNuevoDocumento(documento, usuarioSesion);
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

        if (documento.getCodigoCuti() == null || documento.getCodigoCuti().isBlank()) {
            documento.setCodigoCuti(generarCodigoCuti(anioActual));
        }

        documento.setFechaHoraCargaIngreso(ahora);
        documento.setUsuarioCargaIngreso(usuarioSesion);

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

    public List<Documento> obtenerDocumentosFinalizados() {
        return documentoRepository.findByEstadoActualOrderByFechaHoraSalidaDesc(EstadoDocumento.FINALIZADO);
    }

    @Transactional
    public Documento derivarDocumento(Long documentoId, Integer dependenciaDestinoId, String observacion, Usuario usuario) {
        return derivarDocumento(documentoId, List.of(dependenciaDestinoId), observacion, false, usuario);
    }


    @Transactional
    public void devolverDocumento(Long documentoId, String observacion, Usuario usuarioActual) {
        // 1. Buscar el documento
        Documento documento = documentoRepository.findByIdParaActualizar(documentoId)
                .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado"));

        // 2. Validar estado operativo con getEstadoActual()
        if (documento.getEstadoActual() == EstadoDocumento.FINALIZADO || 
            documento.getEstadoActual() == EstadoDocumento.PENDIENTE_SALIDA) {
            throw new IllegalStateException("No se puede devolver un documento que ya se encuentra finalizado o pendiente de salida.");
        }

        if (usuarioActual == null || usuarioActual.getDependencia() == null) {
            throw new IllegalStateException("El usuario actual no tiene una dependencia asignada para devolver expedientes.");
        }

        if (observacion == null || observacion.trim().length() < 5) {
            throw new IllegalArgumentException("Debe ingresar el motivo de la devolución (mínimo 5 caracteres).");
        }

        // 3. Localizar el último pase recibido por la dependencia del usuario
        Dependencia depActual = usuarioActual.getDependencia();
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
            throw new IllegalStateException("Su dependencia ya devolvió este expediente a " + depDestino.getNombre() + ".");
        }

        EstadoDocumento estadoAnterior = documento.getEstadoActual();

        // 4. Actualizar estado del documento con setEstadoActual()
        documento.setEstadoActual(EstadoDocumento.EN_REVISION);
        documentoRepository.save(documento);

        // 5. Registrar la nueva actuación de devolución (retorna a quien nos envió)
        ActuacionDocumento devolucion = ActuacionDocumento.builder()
                .documento(documento)
                .dependenciaEmisora(depActual)
                .dependenciaReceptoraPase(depDestino)
                .tipoActuacion("DEVOLUCION")
                .numeroActuacion("S/N")
                .descripcionResumen("DEVOLUCIÓN ➔ " + depDestino.getNombre() + ": " + observacion.trim())
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
            "Expediente devuelto por " + depActual.getNombre() + " a " + depDestino.getNombre() + ": " + observacion.trim(),
            usuarioActual,
            null
        );
    }
    

    @Transactional
    public Documento derivarDocumento(Long documentoId, 
                                      List<Integer> dependenciasDestinoIds, 
                                      String observacion, 
                                      Boolean requiereInforme, 
                                      Usuario usuario) {
        
        if (dependenciasDestinoIds == null || dependenciasDestinoIds.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una dependencia destino para derivar.");
        }

        if (observacion == null || observacion.trim().length() < 5) {
            throw new IllegalArgumentException("Debe ingresar una observación o motivo válido (mínimo 5 caracteres) para derivar el documento.");
        }

        verificarAccesoDocumento(usuario, documentoId);

        // Un mismo destino repetido en la petición genera un solo pase
        List<Integer> destinos = new ArrayList<>(new java.util.LinkedHashSet<>(dependenciasDestinoIds));
        destinos.remove(null);

        // Solo se puede derivar a las dependencias que el usuario tiene habilitadas
        java.util.Set<Integer> destinosPermitidos = obtenerDependenciasHijas(usuario).stream()
                .map(Dependencia::getId)
                .collect(java.util.stream.Collectors.toSet());
        for (Integer depId : destinos) {
            if (!destinosPermitidos.contains(depId)) {
                throw new AccessDeniedException("No está habilitado para derivar a la dependencia seleccionada (ID " + depId + ").");
            }
        }

        // Bloquea el expediente: dos derivaciones simultáneas se ejecutan una después de la otra
        Documento doc = documentoRepository.findByIdParaActualizar(documentoId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));
        validarNoFinalizado(doc, "derivar");

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
        boolean esInformeRequerido = (requiereInforme != null && requiereInforme);

        if (esInformeRequerido) {
            doc.setEstadoActual(EstadoDocumento.EN_ESPERA_INFORME);
        } else if (estadoAnterior == EstadoDocumento.EN_ESPERA_INFORME && quedanPasesParaInformeVigentes(documentoId)) {
            // Una derivación de conocimiento no interrumpe los informes que siguen pendientes
            doc.setEstadoActual(EstadoDocumento.EN_ESPERA_INFORME);
        } else {
            doc.setEstadoActual(EstadoDocumento.EN_REVISION);
        }
        documentoRepository.save(doc);

        String tipoPaseStr = esInformeRequerido ? "PASE MÚLTIPLE PARA INFORME" : "PASE MÚLTIPLE DE CONOCIMIENTO/TRÁMITE";
        String detalleObservacion = observacion.trim();

        // Identifica la dependencia del usuario que emite el pase
        Dependencia dependenciaEmisoraReal = (usuario != null && usuario.getDependencia() != null) 
                ? usuario.getDependencia() 
                : null;

        for (Integer depId : destinos) {
            Dependencia destino = dependenciaRepository.findById(depId)
                    .orElseThrow(() -> new RuntimeException("Dependencia no encontrada: " + depId));

            ActuacionDocumento paseInterno = ActuacionDocumento.builder()
                    .documento(doc)
                    .dependenciaEmisora(dependenciaEmisoraReal != null ? dependenciaEmisoraReal : destino)
                    .dependenciaReceptoraPase(destino) // 🎯 ESTA ES LA LÍNEA CLAVE QUE FALTABA
                    .tipoActuacion("PASE_INTERNO")
                    .numeroActuacion("S/N")
                    .descripcionResumen(tipoPaseStr + " ➔ " + destino.getNombre() + ": " + detalleObservacion)
                    .ubicacionBibliorato(doc.getUbicacionBibliorato())
                    .fechaHoraActuacion(LocalDateTime.now())
                    .paseConfirmado(false)
                    .requiereInforme(esInformeRequerido)
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
            "Derivado a " + destinos.size() + " áreas" + (esInformeRequerido ? " para informe" : " para conocimiento") + ": " + observacion,
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

        // Generación oficial del CUTI
        doc.setAnio(anioActual);
        doc.setCodigoCuti(generarCodigoCuti(anioActual));

        doc.setAsunto(asunto.trim());
        doc.setTipoDocumento(tipoDoc);
        doc.setPrioridad(Prioridad.NORMAL);
        doc.setNumeroDocumentoOrigen("S/N");
        doc.setDependenciaOrigen(usuarioSesion.getDependencia());
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
        Documento doc = documentoRepository.findByIdParaActualizar(documentoId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));
        validarNoFinalizado(doc, "registrar la salida de");
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
        Documento doc = documentoRepository.findByIdParaActualizar(documentoId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));

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

    public List<Documento> buscarDocumentosMulticriterio(String texto, 
                                                        Integer dependenciaId, 
                                                        Integer tipoDocId, 
                                                        String estadoStr, 
                                                        LocalDate fechaDesde, 
                                                        LocalDate fechaHasta) {

        EstadoDocumento estadoEnum = null;
        if (estadoStr != null && !estadoStr.isBlank()) {
            try {
                estadoEnum = EstadoDocumento.valueOf(estadoStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        LocalDateTime desde = (fechaDesde != null) ? fechaDesde.atStartOfDay() : null;
        LocalDateTime hasta = (fechaHasta != null) ? fechaHasta.atTime(LocalTime.MAX) : null;

        String textoLimpio = (texto != null && !texto.isBlank()) ? texto.trim() : null;

        return documentoRepository.buscarMulticriterio(textoLimpio, dependenciaId, tipoDocId, estadoEnum, desde, hasta);
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
            "Nueva actuación cargada: " + tipoActuacion + " N° " + nroLimpio + " por " + depEmisora.getNombre(), 
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
        documentoRepository.findByIdParaActualizar(actuacion.getDocumento().getId()); // serializa con derivar/anular

        if (!"PASE_INTERNO".equals(actuacion.getTipoActuacion()) || Boolean.TRUE.equals(actuacion.getPaseConfirmado())
                || Boolean.TRUE.equals(actuacion.getAnulada())) {
            throw new IllegalStateException("La actuación no es un pase interno pendiente de confirmación.");
        }

        // Solo confirma la dependencia receptora (o Dirección/Secretaría)
        Dependencia receptora = actuacion.getDependenciaReceptoraPase();
        Dependencia depUsuario = usuarioSesion != null ? usuarioSesion.getDependencia() : null;
        boolean esReceptora = depUsuario != null && (receptora != null
                ? receptora.getId().equals(depUsuario.getId())
                // Pases antiguos sin receptora: se identifica por la descripción "➔ <dependencia>"
                : actuacion.getDescripcionResumen() != null
                    && actuacion.getDescripcionResumen().contains("➔ " + depUsuario.getNombre() + ":"));
        if (!esReceptora && !esRolAdministrativo(usuarioSesion)) {
            throw new AccessDeniedException("Solo la dependencia receptora del pase puede confirmar su recepción.");
        }

        AdjuntoDocumento adjunto = registrarAdjunto(actuacion.getDocumento(), actuacion, archivoAcusePdf,
                "ACUSE_PASE", "ACUSE_PASE", usuarioSesion);
        actuacion.setRutaCopiaSelladaPdf(adjunto.getRutaAlmacenamiento());
        actuacion.setPaseConfirmado(true);
        actuacion.setTipoActuacion("PASE_CONFIRMADO");

        ActuacionDocumento actuacionGuardada = actuacionDocumentoRepository.save(actuacion);

        // 🛡️ REGISTRO DE AUDITORÍA
        auditoriaService.registrarEvento(
            actuacionGuardada.getDocumento(), 
            "CONFIRMACION_PASE_INTERNO", 
            actuacionGuardada.getDocumento().getEstadoActual(), 
            actuacionGuardada.getDocumento().getEstadoActual(), 
            "Recepción física de pase interno confirmada con PDF por "
                + (receptora != null ? receptora.getNombre() : "dependencia receptora")
                + " (pase emitido por " + actuacionGuardada.getDependenciaEmisora().getNombre() + ")",
            usuarioSesion,
            null
        );

        // 🔄 EVALUACIÓN AUTOMÁTICA DE ESTADO GLOBAL
        verificarYActualizarEstadoGlobal(actuacionGuardada.getDocumento().getId());

        return actuacionGuardada;
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
            // Cada dependencia a la que se pidió informe debe haber emitido al menos una respuesta
            java.util.Set<Integer> dependenciasPendientes = pasesInforme.stream()
                    .map(a -> a.getDependenciaReceptoraPase().getId())
                    .collect(java.util.stream.Collectors.toSet());
            java.util.Set<Integer> dependenciasQueRespondieron = respuestas.stream()
                    .filter(a -> a.getDependenciaEmisora() != null)
                    .map(a -> a.getDependenciaEmisora().getId())
                    .collect(java.util.stream.Collectors.toSet());
            todasRespondieron = dependenciasQueRespondieron.containsAll(dependenciasPendientes);
            pasesParaInforme = dependenciasPendientes.size();
            respuestasRecibidas = dependenciasPendientes.stream().filter(dependenciasQueRespondieron::contains).count();
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

    public List<Documento> obtenerDocumentosActivosParaUsuario(Usuario usuario) {
        if (usuario == null) {
            return new ArrayList<>();
        }

        // 👑 Dirección de TIC y Secretaría Ven Todo
        if (usuario.getRol() == RolUsuario.DIRECTOR || usuario.getRol() == RolUsuario.SECRETARIA_ADMIN) {
            return documentoRepository.findByEstadoActualNotOrderByFechaHoraIngresoDesc(EstadoDocumento.FINALIZADO);
        }

        // 🏢 Jefes de Departamento y Unidades Ven Solo lo derivado a su Dependencia
        if (usuario.getDependencia() != null) {
            return documentoRepository.findDocumentosActivosPorDependencia(usuario.getDependencia().getId());
        }

        return new ArrayList<>();
    }

    // =====================================================================
    // 📎 ADJUNTOS: cada PDF es una versión; nunca se sobrescribe ni se borra
    // =====================================================================

    private AdjuntoDocumento registrarAdjunto(Documento doc, ActuacionDocumento actuacion, MultipartFile archivo,
                                              String prefijo, String tipoAdjunto, Usuario usuario) {
        Path ruta = archivoService.guardarPdf(archivo, prefijo, doc.getCodigoCuti());
        return adjuntoDocumentoRepository.save(AdjuntoDocumento.builder()
                .documento(doc)
                .actuacion(actuacion)
                .nombreArchivo(archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : ruta.getFileName().toString())
                .rutaAlmacenamiento(ruta.toString())
                .tipoAdjunto(tipoAdjunto)
                .tamanioBytes(archivo.getSize())
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
        if (esRolAdministrativo(usuario)) {
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
            throw new AccessDeniedException("Solo la dependencia que cargó el PDF (o Dirección/Secretaría) puede reemplazarlo.");
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

    // Reemplaza el acuse/adjunto principal del documento (salida o acuse final). Solo Dirección/Secretaría.
    @Transactional
    public AdjuntoDocumento reemplazarAdjuntoDocumento(Long documentoId, MultipartFile archivo, String motivo, Usuario usuario) {
        if (!esRolAdministrativo(usuario)) {
            throw new AccessDeniedException("Solo Dirección o Secretaría pueden reemplazar el acuse del expediente.");
        }
        validarMotivo(motivo, 10, "el motivo del reemplazo");
        Documento doc = documentoRepository.findByIdParaActualizar(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + documentoId));

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

    private boolean esPasePendiente(ActuacionDocumento act) {
        return act != null && "PASE_INTERNO".equals(act.getTipoActuacion())
                && !Boolean.TRUE.equals(act.getPaseConfirmado()) && !Boolean.TRUE.equals(act.getAnulada());
    }

    // Retira el pase la dependencia que lo emitió (o Dirección/Secretaría)
    public boolean puedeRetirarPase(ActuacionDocumento act, Usuario usuario) {
        if (!esPasePendiente(act) || usuario == null) {
            return false;
        }
        if (esRolAdministrativo(usuario)) {
            return true;
        }
        return usuario.getDependencia() != null && act.getDependenciaEmisora() != null
                && act.getDependenciaEmisora().getId().equals(usuario.getDependencia().getId());
    }

    // Rechaza la recepción únicamente la dependencia receptora
    public boolean puedeRechazarPase(ActuacionDocumento act, Usuario usuario) {
        if (!esPasePendiente(act) || usuario == null || usuario.getDependencia() == null) {
            return false;
        }
        Dependencia receptora = act.getDependenciaReceptoraPase();
        return receptora != null
                ? receptora.getId().equals(usuario.getDependencia().getId())
                : act.getDescripcionResumen() != null
                    && act.getDescripcionResumen().contains("➔ " + usuario.getDependencia().getNombre() + ":");
    }

    @Transactional
    public ActuacionDocumento anularPase(Long actuacionId, String accion, String motivo, Usuario usuario) {
        ActuacionDocumento pase = actuacionDocumentoRepository.findByIdParaActualizar(actuacionId)
                .orElseThrow(() -> new IllegalArgumentException("Pase no encontrado: " + actuacionId));
        Documento doc = documentoRepository.findByIdParaActualizar(pase.getDocumento().getId()).orElseThrow();
        validarNoFinalizado(doc, "modificar pases de");
        validarMotivo(motivo, 10, "el motivo");

        if (!esPasePendiente(pase)) {
            throw new IllegalStateException("Solo se puede retirar o rechazar un pase pendiente: este ya fue confirmado con copia sellada o anulado.");
        }

        String tipoAnulacion;
        if ("RETIRAR".equals(accion)) {
            if (!puedeRetirarPase(pase, usuario)) {
                throw new AccessDeniedException("Solo la dependencia que emitió el pase (o Dirección/Secretaría) puede retirarlo.");
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

        String receptora = pase.getDependenciaReceptoraPase() != null ? pase.getDependenciaReceptoraPase().getNombre() : "dependencia receptora";
        auditoriaService.registrarEvento(doc,
                ANULACION_RETIRADO.equals(tipoAnulacion) ? "PASE_RETIRADO" : "PASE_RECHAZADO",
                estadoAnterior, doc.getEstadoActual(),
                (ANULACION_RETIRADO.equals(tipoAnulacion) ? "Pase retirado" : "Recepción rechazada")
                        + " (" + pase.getDependenciaEmisora().getNombre() + " ➔ " + receptora + "). Motivo: " + motivo.trim(),
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
    // ⚠️ CIERRE FORZADO / SALIDA EXCEPCIONAL (solo Dirección y Secretaría)
    // =====================================================================

    @Transactional
    public Documento forzarCierreInformes(Long documentoId, String justificacion, Usuario usuario) {
        if (!esRolAdministrativo(usuario)) {
            throw new AccessDeniedException("Solo Dirección o Secretaría pueden forzar el cierre de informes.");
        }
        validarMotivo(justificacion, 20, "la justificación");

        Documento doc = documentoRepository.findByIdParaActualizar(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + documentoId));
        EstadoDocumento estadoAnterior = doc.getEstadoActual();
        if (estadoAnterior == EstadoDocumento.PENDIENTE_SALIDA || estadoAnterior == EstadoDocumento.FINALIZADO) {
            throw new IllegalStateException("El expediente ya está en " + estadoAnterior + ": no corresponde forzar el cierre.");
        }

        // Dependencias a las que se pidió informe y todavía no respondieron (queda constancia)
        List<ActuacionDocumento> vigentes = actuacionDocumentoRepository.findByDocumentoIdOrderByFechaHoraActuacionAsc(documentoId)
                .stream().filter(a -> !Boolean.TRUE.equals(a.getAnulada())).toList();
        java.util.Set<Integer> respondieron = vigentes.stream()
                .filter(a -> "INFORME_TECNICO".equals(a.getTipoActuacion()) || "MEMO".equals(a.getTipoActuacion()))
                .filter(a -> a.getDependenciaEmisora() != null)
                .map(a -> a.getDependenciaEmisora().getId())
                .collect(java.util.stream.Collectors.toSet());
        List<String> sinRespuesta = vigentes.stream()
                .filter(a -> ("PASE_INTERNO".equals(a.getTipoActuacion()) || "PASE_CONFIRMADO".equals(a.getTipoActuacion()))
                        && Boolean.TRUE.equals(a.getRequiereInforme())
                        && a.getDependenciaReceptoraPase() != null
                        && !respondieron.contains(a.getDependenciaReceptoraPase().getId()))
                .map(a -> a.getDependenciaReceptoraPase().getNombre())
                .distinct().toList();

        doc.setEstadoActual(EstadoDocumento.PENDIENTE_SALIDA);
        documentoRepository.save(doc);

        String detalle = "CIERRE EXCEPCIONAL DE INFORMES: " + justificacion.trim()
                + (sinRespuesta.isEmpty() ? "" : " | Sin respuesta de: " + String.join(", ", sinRespuesta));

        // Queda visible en el árbol de trazabilidad, no solo en la bitácora
        actuacionDocumentoRepository.save(ActuacionDocumento.builder()
                .documento(doc)
                .dependenciaEmisora(usuario.getDependencia())
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
                .filter(d -> d.getEstadoActual() != EstadoDocumento.FINALIZADO).toList();
        if (activos.isEmpty()) {
            return resultado;
        }

        Map<Long, LocalDateTime> ultimoMovimiento = new HashMap<>();
        for (Object[] fila : actuacionDocumentoRepository.obtenerUltimoMovimientoPorDocumento(
                activos.stream().map(Documento::getId).toList())) {
            ultimoMovimiento.put((Long) fila[0], (LocalDateTime) fila[1]);
        }

        LocalDate hoy = LocalDate.now();
        for (Documento d : activos) {
            LocalDateTime base = d.getFechaHoraIngreso();
            LocalDateTime mov = ultimoMovimiento.get(d.getId());
            if (mov != null && (base == null || mov.isAfter(base))) {
                base = mov;
            }
            long dias = base != null ? diasHabilesEntre(base.toLocalDate(), hoy) : 0;

            boolean urgente = d.getPrioridad() == Prioridad.URGENTE;
            int advertencia = urgente ? slaUrgenteDiasAdvertencia : slaDiasAdvertencia;
            int critico = urgente ? slaUrgenteDiasCritico : slaDiasCritico;
            String nivel = dias >= critico ? IndicadorSla.CRITICO : dias >= advertencia ? IndicadorSla.ADVERTENCIA : IndicadorSla.OK;
            resultado.put(d.getId(), new IndicadorSla(dias, nivel));
        }
        return resultado;
    }

    // Días hábiles (lunes a viernes) transcurridos. No contempla feriados.
    static long diasHabilesEntre(LocalDate desde, LocalDate hasta) {
        long dias = 0;
        for (LocalDate f = desde.plusDays(1); !f.isAfter(hasta); f = f.plusDays(1)) {
            if (f.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && f.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                dias++;
            }
        }
        return dias;
    }

    // 🏆 Carga segmentada de Documentos Finalizados por Rol/Dependencia
    public List<Documento> obtenerDocumentosFinalizadosParaUsuario(Usuario usuario) {
        if (usuario == null) {
            return new ArrayList<>();
        }

        // 👑 Dirección y Secretaría ven todo el archivo finalizado
        if (usuario.getRol() == RolUsuario.DIRECTOR || usuario.getRol() == RolUsuario.SECRETARIA_ADMIN) {
            return documentoRepository.findByEstadoActualOrderByFechaHoraSalidaDesc(EstadoDocumento.FINALIZADO);
        }

        // 🏢 Departamentos y Unidades ven solo los finalizados en los que participaron
        if (usuario.getDependencia() != null) {
            return documentoRepository.findDocumentosFinalizadosPorDependencia(usuario.getDependencia().getId());
        }

        return new ArrayList<>();
    }
}