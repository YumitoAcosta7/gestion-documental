package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityNotFoundException;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.dto.DevolucionRequestDTO;
import py.gov.asuncion.tic.gestion_documental.dto.NuevoDocumentoDTO;
import py.gov.asuncion.tic.gestion_documental.dto.SalidaDocumentoDTO;
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
            long correlativoAnual = documentoRepository.countByAnio(anioActual) + 1;
            documento.setCodigoCuti(String.format("TIC-%d-%06d", anioActual, correlativoAnual));
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

/*
    @Transactional
    public Documento devolverDocumento(Long documentoId, String motivo, Usuario usuario) {
        Documento doc = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        // Buscamos el último pase interno que recibió nuestra dependencia
        ActuacionDocumento ultimoPase = actuacionDocumentoRepository
                .findTopByDocumentoIdAndDependenciaReceptoraPaseIdOrderByFechaHoraActuacionDesc(
                    documentoId, usuario.getDependencia().getId());

        if (ultimoPase == null) {
            throw new IllegalStateException("No se encontró un pase previo para devolver.");
        }

        // Creamos la actuación de devolución
        ActuacionDocumento devolucion = ActuacionDocumento.builder()
                .documento(doc)
                .dependenciaEmisora(usuario.getDependencia())
                .dependenciaReceptoraPase(ultimoPase.getDependenciaEmisora()) // Retorna a quien nos envió
                .tipoActuacion("DEVOLUCION")
                .numeroActuacion("S/N")
                .descripcionResumen("DEVOLUCIÓN A " + ultimoPase.getDependenciaEmisora().getNombre() + ": " + motivo)
                .fechaHoraActuacion(LocalDateTime.now())
                .usuario(usuario)
                .paseConfirmado(true) // Se asume confirmada al ser una devolución inmediata
                .build();

        actuacionDocumentoRepository.save(devolucion);
        
        // El documento vuelve a estado EN_REVISION para que el emisor original lo vea en su bandeja
        doc.setEstadoActual(EstadoDocumento.EN_REVISION);
        
        return documentoRepository.save(doc);
    }

*/

    @Transactional
    public void devolverDocumento(Long documentoId, String observacion, Usuario usuarioActual) {
        // 1. Buscar el documento
        Documento documento = documentoRepository.findById(documentoId)
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
                .findTopByDocumentoIdAndDependenciaReceptoraPaseIdOrderByFechaHoraActuacionDesc(documentoId, depActual.getId());

        if (ultimoPase == null || ultimoPase.getDependenciaEmisora() == null) {
            throw new IllegalStateException("Su dependencia no recibió ningún pase de este expediente, no hay a quién devolverlo.");
        }

        Dependencia depDestino = ultimoPase.getDependenciaEmisora();
        if (depDestino.getId().equals(depActual.getId())) {
            throw new IllegalStateException("El último pase recibido fue emitido por su propia dependencia, no se puede devolver.");
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

        // Solo se puede derivar a las dependencias que el usuario tiene habilitadas
        java.util.Set<Integer> destinosPermitidos = obtenerDependenciasHijas(usuario).stream()
                .map(Dependencia::getId)
                .collect(java.util.stream.Collectors.toSet());
        for (Integer depId : dependenciasDestinoIds) {
            if (!destinosPermitidos.contains(depId)) {
                throw new AccessDeniedException("No está habilitado para derivar a la dependencia seleccionada (ID " + depId + ").");
            }
        }

        Documento doc = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));

        EstadoDocumento estadoAnterior = doc.getEstadoActual();
        boolean esInformeRequerido = (requiereInforme != null && requiereInforme);
        
        if (esInformeRequerido) {
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

       for (Integer depId : dependenciasDestinoIds) {
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
            "Derivado a " + dependenciasDestinoIds.size() + " áreas: " + observacion, 
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

        // Generación oficial del CUTI (el año debe quedar grabado para que el correlativo no se repita)
        doc.setAnio(anioActual);
        long correlativoAnual = documentoRepository.countByAnio(anioActual) + 1;
        doc.setCodigoCuti(String.format("TIC-%d-%06d", anioActual, correlativoAnual));

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

        // 3. Manejo del archivo PDF de acuse
        if (archivoAcuse != null && !archivoAcuse.isEmpty()) {
            try {
                File carpeta = new File(uploadDir);
                if (!carpeta.exists()) carpeta.mkdirs();

                String nombreOriginal = archivoAcuse.getOriginalFilename();
                String extension = nombreOriginal != null && nombreOriginal.contains(".") 
                        ? nombreOriginal.substring(nombreOriginal.lastIndexOf(".")) : ".pdf";
                String nombreServidor = doc.getCodigoCuti() + "_SALIDA_" + System.currentTimeMillis() + extension;

                Path rutaFinal = Paths.get(uploadDir, nombreServidor);
                Files.copy(archivoAcuse.getInputStream(), rutaFinal, StandardCopyOption.REPLACE_EXISTING);

                AdjuntoDocumento adjunto = AdjuntoDocumento.builder()
                        .documento(docGuardado)
                        .nombreArchivo(nombreOriginal)
                        .rutaAlmacenamiento(rutaFinal.toString())
                        .tipoAdjunto("ACUSE_SALIDA")
                        .tamanioBytes(archivoAcuse.getSize())
                        .fechaHoraSubida(ahora)
                        .usuarioSubida(usuarioSesion)
                        .build();

                adjuntoDocumentoRepository.save(adjunto);
            } catch (IOException e) {
                throw new RuntimeException("Error al guardar el archivo PDF: " + e.getMessage(), e);
            }
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
        Documento doc = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));

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
            try {
                File carpeta = new File(uploadDir);
                if (!carpeta.exists()) {
                    carpeta.mkdirs();
                }

                String nombreOriginal = archivoAcuse.getOriginalFilename();
                String extension = nombreOriginal != null && nombreOriginal.contains(".") 
                        ? nombreOriginal.substring(nombreOriginal.lastIndexOf(".")) : ".pdf";
                String nombreServidor = doc.getCodigoCuti() + "_ACUSE_" + System.currentTimeMillis() + extension;

                Path rutaFinal = Paths.get(uploadDir, nombreServidor);
                Files.copy(archivoAcuse.getInputStream(), rutaFinal, StandardCopyOption.REPLACE_EXISTING);

                AdjuntoDocumento adjunto = AdjuntoDocumento.builder()
                        .documento(docGuardado)
                        .nombreArchivo(nombreOriginal)
                        .rutaAlmacenamiento(rutaFinal.toString())
                        .tipoAdjunto("ACUSE_SALIDA")
                        .tamanioBytes(archivoAcuse.getSize())
                        .fechaHoraSubida(ahora)
                        .usuarioSubida(usuarioSesion)
                        .build();

                adjuntoDocumentoRepository.save(adjunto);

            } catch (IOException e) {
                throw new RuntimeException("Error al guardar el archivo acuse PDF: " + e.getMessage(), e);
            }
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
        Documento doc = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));

        EstadoDocumento estadoAnterior = doc.getEstadoActual();
        LocalDateTime ahora = LocalDateTime.now();

        if (archivoAcuse != null && !archivoAcuse.isEmpty()) {
            try {
                File carpeta = new File(uploadDir);
                if (!carpeta.exists()) {
                    carpeta.mkdirs();
                }

                String nombreOriginal = archivoAcuse.getOriginalFilename();
                String extension = nombreOriginal != null && nombreOriginal.contains(".") 
                        ? nombreOriginal.substring(nombreOriginal.lastIndexOf(".")) : ".pdf";
                String nombreServidor = doc.getCodigoCuti() + "_ACUSE_CONFIRMADO_" + System.currentTimeMillis() + extension;

                Path rutaFinal = Paths.get(uploadDir, nombreServidor);
                Files.copy(archivoAcuse.getInputStream(), rutaFinal, StandardCopyOption.REPLACE_EXISTING);

                AdjuntoDocumento adjunto = AdjuntoDocumento.builder()
                        .documento(doc)
                        .nombreArchivo(nombreOriginal)
                        .rutaAlmacenamiento(rutaFinal.toString())
                        .tipoAdjunto("ACUSE_ENTREGA")
                        .tamanioBytes(archivoAcuse.getSize())
                        .fechaHoraSubida(ahora)
                        .usuarioSubida(usuarioSesion)
                        .build();

                adjuntoDocumentoRepository.save(adjunto);

            } catch (IOException e) {
                throw new RuntimeException("Error al guardar el archivo acuse PDF: " + e.getMessage(), e);
            }
        }

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

        Documento doc = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));

        String nroLimpio = (numeroActuacion == null || numeroActuacion.isBlank()) 
                ? "S/N" : numeroActuacion.trim().toUpperCase();
                
        validarFormatoNumeroDocumento(nroLimpio);

        Dependencia depEmisora = dependenciaRepository.findById(dependenciaEmisoraId)
                .orElseThrow(() -> new RuntimeException("Dependencia no encontrada: " + dependenciaEmisoraId));

        String nombreArchivoPdf = null;
        if (archivoCopiaSellada != null && !archivoCopiaSellada.isEmpty()) {
            try {
                String extension = ".pdf";
                nombreArchivoPdf = "ACTUACION_" + doc.getCodigoCuti() + "_" + System.currentTimeMillis() + extension;
                Path rutaDirectorio = Paths.get("uploads/acuses");
                if (!Files.exists(rutaDirectorio)) {
                    Files.createDirectories(rutaDirectorio);
                }
                Files.copy(archivoCopiaSellada.getInputStream(), rutaDirectorio.resolve(nombreArchivoPdf));
            } catch (Exception e) {
                throw new RuntimeException("Error al guardar la copia sellada en PDF: " + e.getMessage());
            }
        }

        ActuacionDocumento actuacion = ActuacionDocumento.builder()
                .documento(doc)
                .dependenciaEmisora(depEmisora)
                .tipoActuacion(tipoActuacion)
                .numeroActuacion(nroLimpio)
                .descripcionResumen(descripcionResumen)
                .ubicacionBibliorato(ubicacionBibliorato)
                .rutaCopiaSelladaPdf(nombreArchivoPdf)
                .fechaHoraActuacion(LocalDateTime.now())
                .usuario(usuarioSesion)
                .build();

        if (ubicacionBibliorato != null && !ubicacionBibliorato.isBlank()) {
            doc.setUbicacionBibliorato(ubicacionBibliorato);
            documentoRepository.save(doc);
        }

        ActuacionDocumento actuacionGuardada = actuacionDocumentoRepository.save(actuacion);

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

        // Solo el nombre del archivo, para evitar rutas manipuladas
        Path nombre = Paths.get(actuacion.getRutaCopiaSelladaPdf()).getFileName();
        for (Path candidato : List.of(Paths.get("uploads/acuses").resolve(nombre), Paths.get(uploadDir).resolve(nombre))) {
            if (Files.isRegularFile(candidato)) {
                return candidato;
            }
        }
        return null;
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

        ActuacionDocumento actuacion = actuacionDocumentoRepository.findById(actuacionId)
                .orElseThrow(() -> new RuntimeException("Actuación no encontrada: " + actuacionId));

        if (!"PASE_INTERNO".equals(actuacion.getTipoActuacion()) || Boolean.TRUE.equals(actuacion.getPaseConfirmado())) {
            throw new IllegalStateException("La actuación no es un pase interno pendiente de confirmación.");
        }

        // Solo confirma la dependencia receptora (o Dirección/Secretaría)
        Dependencia receptora = actuacion.getDependenciaReceptoraPase();
        Dependencia depUsuario = usuarioSesion != null ? usuarioSesion.getDependencia() : null;
        boolean esReceptora = depUsuario != null && (receptora != null
                ? receptora.getId().equals(depUsuario.getId())
                // Pases antiguos sin receptora: se identifica por la descripción "➔ <dependencia>"
                : actuacion.getDescripcionResumen() != null
                    && actuacion.getDescripcionResumen().contains("➔ " + depUsuario.getNombre()));
        if (!esReceptora && !esRolAdministrativo(usuarioSesion)) {
            throw new AccessDeniedException("Solo la dependencia receptora del pase puede confirmar su recepción.");
        }

        try {
            String extension = ".pdf";
            String nombreArchivoPdf = "ACUSE_PASE_" + actuacion.getDocumento().getCodigoCuti() + "_" + System.currentTimeMillis() + extension;
            Path rutaDirectorio = Paths.get("uploads/acuses");
            if (!Files.exists(rutaDirectorio)) {
                Files.createDirectories(rutaDirectorio);
            }
            Files.copy(archivoAcusePdf.getInputStream(), rutaDirectorio.resolve(nombreArchivoPdf));

            actuacion.setRutaCopiaSelladaPdf(nombreArchivoPdf);
            actuacion.setPaseConfirmado(true);
            actuacion.setTipoActuacion("PASE_CONFIRMADO");

        } catch (Exception e) {
            throw new RuntimeException("Error al guardar la copia sellada del pase: " + e.getMessage());
        }

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

        List<ActuacionDocumento> actuaciones = actuacionDocumentoRepository.findByDocumentoIdOrderByFechaHoraActuacionAsc(documentoId);

        // Al confirmar la recepción, un PASE_INTERNO pasa a PASE_CONFIRMADO: ambos cuentan como pase emitido
        List<ActuacionDocumento> pasesInforme = actuaciones.stream()
                .filter(a -> ("PASE_INTERNO".equals(a.getTipoActuacion()) || "PASE_CONFIRMADO".equals(a.getTipoActuacion()))
                        && a.getDescripcionResumen() != null && a.getDescripcionResumen().contains("PARA INFORME"))
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