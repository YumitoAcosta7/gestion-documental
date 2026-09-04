package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import py.gov.asuncion.tic.gestion_documental.domain.*;
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
            "127.0.0.1"
        );

        return docGuardado;
    }

    public List<Documento> obtenerDocumentosFinalizados() {
        return documentoRepository.findByEstadoActualOrderByFechaHoraSalidaDesc(EstadoDocumento.FINALIZADO);
    }

    @Transactional
    public Documento derivarDocumento(Long documentoId, Integer dependenciaDestinoId, String observacion) {
        Usuario usuarioDefault = usuarioRepository.findById(1)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return derivarDocumento(documentoId, List.of(dependenciaDestinoId), observacion, false, usuarioDefault);
    }

    @Transactional
    public Documento derivarDocumento(Long documentoId, Integer dependenciaDestinoId, String observacion, Usuario usuario) {
        return derivarDocumento(documentoId, List.of(dependenciaDestinoId), observacion, false, usuario);
    }

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
            "127.0.0.1"
        );

        return doc;
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
            "127.0.0.1"
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
            "127.0.0.1"
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
            "127.0.0.1"
        );

        // 🔄 EVALUACIÓN AUTOMÁTICA DE ESTADO GLOBAL
        verificarYActualizarEstadoGlobal(documentoId);

        return actuacionGuardada;
    }

    public List<ActuacionDocumento> obtenerActuacionesPorDocumento(Long documentoId) {
        return actuacionDocumentoRepository.findByDocumentoIdOrderByFechaHoraActuacionAsc(documentoId);
    }

    @Transactional
    public ActuacionDocumento confirmarRecepcionPaseInterno(Long actuacionId, 
                                                            MultipartFile archivoAcusePdf) {
        if (archivoAcusePdf == null || archivoAcusePdf.isEmpty()) {
            throw new IllegalArgumentException("Es OBLIGATORIO subir el PDF de la copia sellada y firmada para confirmar la recepción del pase.");
        }

        ActuacionDocumento actuacion = actuacionDocumentoRepository.findById(actuacionId)
                .orElseThrow(() -> new RuntimeException("Actuación no encontrada: " + actuacionId));

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
            "Recepción física de pase interno confirmada con PDF para " + actuacionGuardada.getDependenciaEmisora().getNombre(), 
            actuacionGuardada.getUsuario(), 
            "127.0.0.1"
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

        long pasesParaInforme = actuaciones.stream()
                .filter(a -> "PASE_INTERNO".equals(a.getTipoActuacion()) && a.getDescripcionResumen() != null && a.getDescripcionResumen().contains("PARA INFORME"))
                .count();

        long respuestasRecibidas = actuaciones.stream()
                .filter(a -> "INFORME_TECNICO".equals(a.getTipoActuacion()) || "MEMO".equals(a.getTipoActuacion()))
                .count();

        if (pasesParaInforme > 0 && respuestasRecibidas >= pasesParaInforme && doc.getEstadoActual() == EstadoDocumento.EN_ESPERA_INFORME) {
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
                "127.0.0.1"
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