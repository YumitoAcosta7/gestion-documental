package py.gov.asuncion.tic.gestion_documental.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import py.gov.asuncion.tic.gestion_documental.domain.ActuacionDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.AdjuntoDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.AuditoriaDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.Documento;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.dto.DevolucionRequestDTO;
import py.gov.asuncion.tic.gestion_documental.repository.AdjuntoDocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;
import py.gov.asuncion.tic.gestion_documental.service.AuditoriaService;
import py.gov.asuncion.tic.gestion_documental.service.DocumentoService;

import java.io.File;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    @Autowired(required = false)
    private DocumentoService documentoService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AdjuntoDocumentoRepository adjuntoDocumentoRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private DocumentoRepository documentoRepository;
    

    /**
     * 🏠 PANTALLA PRINCIPAL (DASHBOARD)
     */

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("nuevoDoc", new Documento());

        if (documentoService != null) {
            Usuario usuarioLogueado = getUsuarioAutenticado();
            model.addAttribute("usuarioLogueado", usuarioLogueado);

            model.addAttribute("documentos", documentoService.obtenerDocumentosActivosParaUsuario(usuarioLogueado));
            model.addAttribute("documentosFinalizados", documentoService.obtenerDocumentosFinalizadosParaUsuario(usuarioLogueado));
            
            // ==========================================
            // NUEVO: Cargar los documentos de salida propios
            // ==========================================
            if (usuarioLogueado != null && usuarioLogueado.getDependencia() != null) {
                model.addAttribute("documentosSalida", 
                    documentoRepository.findByDependenciaOrigenIdOrderByFechaHoraIngresoDesc(usuarioLogueado.getDependencia().getId())
                );
            } else {
                model.addAttribute("documentosSalida", new ArrayList<>());
            }
            // ==========================================

            if (usuarioLogueado != null) {
                model.addAttribute("dependenciasHijas", documentoService.obtenerDependenciasHijas(usuarioLogueado));
            } else {
                model.addAttribute("dependenciasHijas", new ArrayList<>());
            }

            model.addAttribute("metricas", documentoService.obtenerMetricasDashboard());
            model.addAttribute("dependencias", documentoService.obtenerDependenciasActivas());
            model.addAttribute("tiposDoc", documentoService.obtenerTiposDocumentoActivos());
            model.addAttribute("dependenciasInternas", documentoService.obtenerDependenciasInternasTic());
        } else {
            model.addAttribute("documentos", new ArrayList<>());
            model.addAttribute("documentosFinalizados", new ArrayList<>());
            model.addAttribute("documentosSalida", new ArrayList<>()); // <- Añadido para el bloque else
            Map<String, Long> metricasVacias = new HashMap<>();
            metricasVacias.put("ingresados", 0L);
            metricasVacias.put("enEsperaInforme", 0L);
            metricasVacias.put("pendienteSalida", 0L);
            metricasVacias.put("urgentes", 0L);
            model.addAttribute("metricas", metricasVacias);
            model.addAttribute("dependencias", new ArrayList<>());
            model.addAttribute("dependenciasInternas", new ArrayList<>());
            model.addAttribute("tiposDoc", new ArrayList<>());
        }

        return "index";
    }

  /*

   @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("nuevoDoc", new Documento());

        if (documentoService != null) {
            Usuario usuarioLogueado = getUsuarioAutenticado();
            model.addAttribute("usuarioLogueado", usuarioLogueado);

            model.addAttribute("documentos", documentoService.obtenerDocumentosActivosParaUsuario(usuarioLogueado));
            model.addAttribute("documentosFinalizados", documentoService.obtenerDocumentosFinalizadosParaUsuario(usuarioLogueado));
            
            if (usuarioLogueado != null) {
                model.addAttribute("dependenciasHijas", documentoService.obtenerDependenciasHijas(usuarioLogueado));
            } else {
                model.addAttribute("dependenciasHijas", new ArrayList<>());
            }

            model.addAttribute("metricas", documentoService.obtenerMetricasDashboard());
            model.addAttribute("dependencias", documentoService.obtenerDependenciasActivas());
            model.addAttribute("tiposDoc", documentoService.obtenerTiposDocumentoActivos());
            model.addAttribute("dependenciasInternas", documentoService.obtenerDependenciasInternasTic());
        } else {
            model.addAttribute("documentos", new ArrayList<>());
            model.addAttribute("documentosFinalizados", new ArrayList<>());
            Map<String, Long> metricasVacias = new HashMap<>();
            metricasVacias.put("ingresados", 0L);
            metricasVacias.put("enEsperaInforme", 0L);
            metricasVacias.put("pendienteSalida", 0L);
            metricasVacias.put("urgentes", 0L);
            model.addAttribute("metricas", metricasVacias);
            model.addAttribute("dependencias", new ArrayList<>());
            model.addAttribute("dependenciasInternas", new ArrayList<>());
            model.addAttribute("tiposDoc", new ArrayList<>());
        }

        return "index";
    }
  
  */  
   

    /**
     * ↩️ ENDPOINT DE DEVOLUCIÓN DE EXPEDIENTE
     *     @PostMapping("/documento/devolver")
            public String devolver(@RequestParam Long id, 
                           @RequestParam String observacion, 
                           RedirectAttributes redirectAttributes) {
            try {
                documentoService.devolverDocumento(id, observacion, getUsuarioAutenticado());
                redirectAttributes.addFlashAttribute("mensajeExito", "Documento devuelto correctamente.");
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("mensajeError", "Error al devolver: " + e.getMessage());
            }
            return "redirect:/";
            }
     * 
     * 
     */

    @PostMapping("/documentos/devolver")
    public String procesarDevolucion(@RequestParam("id") Long id,
                                    @RequestParam("observacion") String observacion,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            Usuario usuarioActual = usuarioRepository.findByUsernameAndActivoTrue(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Usuario no autenticado"));
                    
            documentoService.devolverDocumento(id, observacion, usuarioActual);
            redirectAttributes.addFlashAttribute("mensajeExito", "Expediente devuelto correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/"; // Redirige a la bandeja principal
    }   

    
    /**
     * 📥 GUARDAR NUEVO DOCUMENTO
     */
    @PostMapping("/documentos/guardar")
    public String guardarDocumento(@ModelAttribute("nuevoDoc") Documento documento,
                                   RedirectAttributes redirectAttributes) {
        try {
            Documento docGuardado = documentoService.guardarNuevoDocumento(documento, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "Documento registrado con CUTI " + docGuardado.getCodigoCuti() + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar el documento: " + e.getMessage());
        }
        return "redirect:/";
    }

    /**
     * ↗️ DERIVAR EXPEDIENTE
     */
    @PostMapping("/documentos/derivar")
    public String derivarDocumento(@RequestParam("documentoId") Long documentoId,
                                   @RequestParam("dependenciaDestinoId") List<Integer> dependenciasDestinoIds,
                                   @RequestParam("observacion") String observacion,
                                   @RequestParam(value = "requiereInforme", defaultValue = "false") Boolean requiereInforme,
                                   RedirectAttributes redirectAttributes) {
        try {
            documentoService.derivarDocumento(documentoId, dependenciasDestinoIds, observacion, requiereInforme, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "Expediente derivado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al derivar: " + e.getMessage());
        }
        return "redirect:/";
    }

    /**
     * 📤 REGISTRAR SALIDA EXTERIOR
     */
    @PostMapping("/documentos/salida")
    public String registrarSalida(@RequestParam("documentoId") Long documentoId,
                                  @RequestParam("nroNotaSalida") String nroNotaSalida,
                                  @RequestParam(value = "dependenciaDestinoId", required = false) Integer dependenciaDestinoId,
                                  @RequestParam("personaRetira") String personaRetira,
                                  @RequestParam("funcionarioEntrega") String funcionarioEntrega,
                                  @RequestParam(value = "archivoAcuse", required = false) MultipartFile archivoAcuse,
                                  RedirectAttributes redirectAttributes) {
        try {
            documentoService.registrarSalidaDocumento(documentoId, nroNotaSalida, dependenciaDestinoId,
                                                   personaRetira, funcionarioEntrega, archivoAcuse, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "Salida exterior registrada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar la salida: " + e.getMessage());
        }
        return "redirect:/";
    }

    /**
     * ✅ CONFIRMAR ACUSE FINAL
     */
    @PostMapping("/documentos/confirmar-acuse")
    public String confirmarAcuse(@RequestParam("documentoId") Long documentoId,
                                  @RequestParam(value = "archivoAcuse", required = false) MultipartFile archivoAcuse,
                                  RedirectAttributes redirectAttributes) {
        try {
            documentoService.confirmarEntregaYAcuse(documentoId, archivoAcuse, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "Acuse confirmado. El expediente quedó FINALIZADO.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al confirmar el acuse: " + e.getMessage());
        }
        return "redirect:/";
    }

    /**
     * 📄 VER PDF DE UNA ACTUACIÓN (copia sellada o acuse de pase interno)
     */
    @GetMapping("/documentos/actuaciones/{actuacionId}/pdf")
    public ResponseEntity<Resource> verPdfActuacion(@PathVariable Long actuacionId) {
        java.nio.file.Path ruta = documentoService.resolverPdfActuacion(actuacionId);
        if (ruta == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + ruta.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new FileSystemResource(ruta));
    }

    /**
     * 📄 DESCARGAR ACUSE PDF
     */
    @GetMapping("/documentos/descargar-acuse/{documentoId}")
    public ResponseEntity<Resource> descargarAcuse(@PathVariable Long documentoId) {
        List<AdjuntoDocumento> adjuntos = adjuntoDocumentoRepository.findByDocumentoId(documentoId);

        if (adjuntos.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AdjuntoDocumento adjunto = adjuntos.get(adjuntos.size() - 1);
        File archivoFisico = new File(adjunto.getRutaAlmacenamiento());

        if (!archivoFisico.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource recurso = new FileSystemResource(archivoFisico);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + adjunto.getNombreArchivo() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(recurso);
    }

    /**
     * 🔍 BUSCADOR MULTICRITERIO
     */
    @GetMapping("/documentos/buscar")
    public String buscarDocumentos(@RequestParam(value = "texto", required = false) String texto,
                                   @RequestParam(value = "dependenciaId", required = false) Integer dependenciaId,
                                   @RequestParam(value = "tipoDocId", required = false) Integer tipoDocId,
                                   @RequestParam(value = "estado", required = false) String estado,
                                   @RequestParam(value = "fechaDesde", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaDesde,
                                   @RequestParam(value = "fechaHasta", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaHasta,
                                   Model model) {

        model.addAttribute("nuevoDoc", new Documento());

        if (documentoService != null) {
            List<Documento> resultados = documentoService.buscarDocumentosMulticriterio(
                    texto, dependenciaId, tipoDocId, estado, fechaDesde, fechaHasta);

            model.addAttribute("documentosResultados", resultados);
            model.addAttribute("esBusqueda", true);
            model.addAttribute("paramTexto", texto);
            model.addAttribute("paramDependenciaId", dependenciaId);
            model.addAttribute("paramTipoDocId", tipoDocId);
            model.addAttribute("paramEstado", estado);
            model.addAttribute("paramFechaDesde", fechaDesde);
            model.addAttribute("paramFechaHasta", fechaHasta);

            Usuario usuarioLogueado = getUsuarioAutenticado();
            model.addAttribute("usuarioLogueado", usuarioLogueado);
            model.addAttribute("documentos", documentoService.obtenerDocumentosActivosParaUsuario(usuarioLogueado));
            model.addAttribute("documentosFinalizados", documentoService.obtenerDocumentosFinalizadosParaUsuario(usuarioLogueado));
            model.addAttribute("documentosSalida", usuarioLogueado != null && usuarioLogueado.getDependencia() != null
                    ? documentoRepository.findByDependenciaOrigenIdOrderByFechaHoraIngresoDesc(usuarioLogueado.getDependencia().getId())
                    : new ArrayList<>());
            model.addAttribute("dependenciasHijas", usuarioLogueado != null
                    ? documentoService.obtenerDependenciasHijas(usuarioLogueado)
                    : new ArrayList<>());
            model.addAttribute("metricas", documentoService.obtenerMetricasDashboard());
            model.addAttribute("dependencias", documentoService.obtenerDependenciasActivas());
            model.addAttribute("tiposDoc", documentoService.obtenerTiposDocumentoActivos());
            model.addAttribute("dependenciasInternas", documentoService.obtenerDependenciasInternasTic());
        }

        return "index";
    }

    /**
     * 📝 GUARDAR ACTUACIÓN / RESPUESTA INTERNA
     */
    @PostMapping("/documentos/actuaciones/guardar")
    public String guardarActuacion(@RequestParam("documentoId") Long documentoId,
                                   @RequestParam("tipoActuacion") String tipoActuacion,
                                   @RequestParam("numeroActuacion") String numeroActuacion,
                                   @RequestParam("descripcionResumen") String descripcionResumen,
                                   @RequestParam(value = "ubicacionBibliorato", required = false) String ubicacionBibliorato,
                                   @RequestParam(value = "archivoCopiaSellada", required = false) MultipartFile archivoCopiaSellada,
                                   RedirectAttributes redirectAttributes) {
        try {
            Usuario usuarioCarga = getUsuarioAutenticadoObligatorio();

            if (usuarioCarga.getDependencia() == null) {
                throw new IllegalStateException("El usuario actual no tiene una dependencia asignada para registrar actuaciones.");
            }
            Integer dependenciaEmisoraId = usuarioCarga.getDependencia().getId();

            documentoService.registrarActuacion(documentoId, dependenciaEmisoraId, tipoActuacion,
                       numeroActuacion, descripcionResumen, ubicacionBibliorato,
                       archivoCopiaSellada, usuarioCarga);
            redirectAttributes.addFlashAttribute("mensajeExito", "Actuación registrada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar la actuación: " + e.getMessage());
        }
        return "redirect:/";
    }

    /**
     * 📥 CONFIRMAR RECEPCIÓN DE PASE INTERNO
     */
    @PostMapping("/documentos/actuaciones/confirmar-pase")
    public String confirmarPaseInterno(@RequestParam("actuacionId") Long actuacionId,
                                       @RequestParam("archivoAcusePdf") MultipartFile archivoAcusePdf,
                                       RedirectAttributes redirectAttributes) {
        try {
            documentoService.confirmarRecepcionPaseInterno(actuacionId, archivoAcusePdf);
            redirectAttributes.addFlashAttribute("mensajeExito", "Recepción del pase confirmada.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al confirmar el pase: " + e.getMessage());
        }
        return "redirect:/";
    }

    /**
     * 🌐 API JSON: OBTENER ACTUACIONES (ÁRBOL DE TRAZABILIDAD)
     */
    @GetMapping("/api/documentos/{id}/actuaciones")
    @ResponseBody
    public List<Map<String, Object>> obtenerActuacionesJson(@PathVariable("id") Long id) {
        List<Map<String, Object>> listaJson = new ArrayList<>();
        if (documentoService != null) {
            List<ActuacionDocumento> actuaciones = documentoService.obtenerActuacionesPorDocumento(id);

            for (var act : actuaciones) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", act.getId());
                map.put("documentoId", id);
                map.put("tipoActuacion", act.getTipoActuacion() != null ? act.getTipoActuacion() : "PASE_INTERNO");
                map.put("numeroActuacion", act.getNumeroActuacion() != null ? act.getNumeroActuacion() : "S/N");
                map.put("dependencia", act.getDependenciaEmisora() != null ? act.getDependenciaEmisora().getNombre() : "Sin Dependencia");
                map.put("descripcion", act.getDescripcionResumen());
                map.put("bibliorato", act.getUbicacionBibliorato() != null ? act.getUbicacionBibliorato() : "No especificado");
                map.put("pdf", act.getRutaCopiaSelladaPdf());
                map.put("paseConfirmado", act.getPaseConfirmado() != null ? act.getPaseConfirmado() : true);
                map.put("fecha", act.getFechaHoraActuacion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                listaJson.add(map);
            }
        }
        return listaJson;
    }

    /**
     * 🌐 API JSON: INFORMES DISPONIBLES
     */
    @GetMapping("/api/documentos/{id}/informes-disponibles")
    @ResponseBody
    public List<Map<String, Object>> obtenerInformesDisponiblesJson(@PathVariable("id") Long id) {
        List<Map<String, Object>> listaJson = new ArrayList<>();
        if (documentoService != null) {
            List<ActuacionDocumento> actuaciones = documentoService.obtenerActuacionesPorDocumento(id);
            
            for (ActuacionDocumento act : actuaciones) {
                if ("INFORME_TECNICO".equals(act.getTipoActuacion()) || 
                    "MEMO".equals(act.getTipoActuacion()) || 
                    "PROVIDENCIA".equals(act.getTipoActuacion())) {
                    
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", act.getId());
                    map.put("tipo", act.getTipoActuacion());
                    map.put("numero", act.getNumeroActuacion() != null ? act.getNumeroActuacion() : "S/N");
                    map.put("dependencia", act.getDependenciaEmisora().getNombre());
                    map.put("resumen", act.getDescripcionResumen());
                    listaJson.add(map);
                }
            }
        }
        return listaJson;
    }

    @PostMapping("/documentos/salida-directa")
    public String registrarSalidaDirecta(@RequestParam("nroNotaSalida") String nroNotaSalida,
                                    @RequestParam(value = "tipoDocumentoId", required = false) Integer tipoDocumentoId,
                                    @RequestParam("asunto") String asunto,
                                    @RequestParam(value = "dependenciaDestinoId", required = false) Integer dependenciaDestinoId,
                                    @RequestParam(value = "personaRetira", required = false) String personaRetira,
                                    @RequestParam(value = "funcionarioEntrega", required = false) String funcionarioEntrega,
                                    @RequestParam(value = "archivoAcuse", required = false) MultipartFile archivoAcuse,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            Usuario usuarioSesion = usuarioRepository.findByUsernameAndActivoTrue(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Usuario no autenticado o inactivo"));

            documentoService.crearYRegistrarSalidaDirecta(
                nroNotaSalida, tipoDocumentoId, asunto, dependenciaDestinoId, 
                personaRetira, funcionarioEntrega, archivoAcuse, usuarioSesion
            );

            redirectAttributes.addFlashAttribute("mensajeExito", "Documento propio generado y salida registrada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar salida directa: " + e.getMessage());
        }
        return "redirect:/";
    }

    /**
     * 🛡️ API JSON: BITÁCORA DE AUDITORÍA INMUTABLE
     */
    @GetMapping("/api/documentos/{id}/auditoria")
    @ResponseBody
    public List<Map<String, Object>> obtenerAuditoriaJson(@PathVariable("id") Long id) {
        List<Map<String, Object>> listaJson = new ArrayList<>();
        List<AuditoriaDocumento> auditorias = auditoriaService.obtenerHistorialAuditoria(id);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        for (AuditoriaDocumento aud : auditorias) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", aud.getId());
            map.put("evento", aud.getTipoEvento());
            map.put("estadoAnterior", aud.getEstadoAnterior() != null ? aud.getEstadoAnterior() : "-");
            map.put("estadoNuevo", aud.getEstadoNuevo() != null ? aud.getEstadoNuevo() : "-");
            map.put("descripcion", aud.getDescripcionDetalle());
            map.put("usuario", aud.getUsuarioNombreCompleto() + " (" + aud.getUsuarioUsername() + ")");
            map.put("dependencia", aud.getDependenciaUsuario());
            map.put("ip", aud.getDireccionIp());
            map.put("fechaHora", aud.getFechaHora().format(fmt));
            listaJson.add(map);
        }
        return listaJson;
    }

    /**
     * 👤 MÉTODO AUXILIAR PRIVADO: OBTENER USUARIO AUTENTICADO
     */
    private Usuario getUsuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            String username = auth.getName();
            return usuarioRepository.findByUsernameAndActivoTrue(username).orElse(null);
        }
        return null;
    }

    private Usuario getUsuarioAutenticadoObligatorio() {
        Usuario usuario = getUsuarioAutenticado();
        if (usuario == null) {
            throw new IllegalStateException("No se pudo identificar al usuario de la sesión.");
        }
        return usuario;
    }
}