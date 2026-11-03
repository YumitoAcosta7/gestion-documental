package py.gov.asuncion.tic.gestion_documental.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import py.gov.asuncion.tic.gestion_documental.domain.Accion;
import py.gov.asuncion.tic.gestion_documental.domain.ActuacionDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.AdjuntoDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.AuditoriaDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.Documento;
import py.gov.asuncion.tic.gestion_documental.domain.Prioridad;
import py.gov.asuncion.tic.gestion_documental.domain.TipoPase;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.dto.NuevoDocumentoDTO;
import py.gov.asuncion.tic.gestion_documental.repository.AdjuntoDocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;
import py.gov.asuncion.tic.gestion_documental.service.ArchivoService;
import py.gov.asuncion.tic.gestion_documental.service.BandejaService;
import py.gov.asuncion.tic.gestion_documental.service.CanalService;
import py.gov.asuncion.tic.gestion_documental.service.AuditoriaService;
import py.gov.asuncion.tic.gestion_documental.service.BiblioratoService;
import py.gov.asuncion.tic.gestion_documental.service.DocumentoService;
import py.gov.asuncion.tic.gestion_documental.service.PoliticaAcceso;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    @Autowired
    private DocumentoService documentoService;

    @Autowired
    private PoliticaAcceso politicaAcceso;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AdjuntoDocumentoRepository adjuntoDocumentoRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private ArchivoService archivoService;

    @Autowired
    private BiblioratoService biblioratoService;

    @Autowired
    private BandejaService bandejaService;

    @Autowired
    private CanalService canalService;
    

    /**
     * 🏠 PANTALLA PRINCIPAL (DASHBOARD)
     */

    @GetMapping("/")
    public String index(@RequestParam(value = "bandeja", defaultValue = "TODOS") BandejaService.Bandeja bandeja,
                        @RequestParam(value = "pagina", defaultValue = "0") int pagina,
                        Model model) {
        cargarPantallaPrincipal(model, getUsuarioAutenticado(), List.of(), bandeja, pagina);
        return "index";
    }

    // ⏱️ Diagnóstico de rendimiento (solo si app.diagnostico.server-timing=true, perfil de pruebas):
    // la respuesta lleva la cabecera estándar Server-Timing con lo que tardó cada parte de la pantalla
    @org.springframework.beans.factory.annotation.Value("${app.diagnostico.server-timing:false}")
    private boolean serverTiming;

    private static final class Cronometro {
        private final Map<String, Long> tiempos = new java.util.LinkedHashMap<>();

        <T> T medir(String nombre, java.util.function.Supplier<T> paso) {
            long inicio = System.nanoTime();
            try {
                return paso.get();
            } finally {
                tiempos.merge(nombre, (System.nanoTime() - inicio) / 1_000_000, Long::sum);
            }
        }

        void medir(String nombre, Runnable paso) {
            medir(nombre, () -> { paso.run(); return null; });
        }

        String cabecera() {
            return tiempos.entrySet().stream().map(e -> e.getKey() + ";dur=" + e.getValue())
                    .collect(java.util.stream.Collectors.joining(", "));
        }
    }

    // Datos comunes de la pantalla principal (bandejas, KPI, catálogos), para el inicio y la búsqueda
    private void cargarPantallaPrincipal(Model model, Usuario usuario, List<Documento> resultadosBusqueda,
                                         BandejaService.Bandeja bandeja, int pagina) {
        Cronometro c = new Cronometro();
        model.addAttribute("nuevoDoc", new NuevoDocumentoDTO());
        model.addAttribute("usuarioLogueado", usuario);
        model.addAttribute("rolesUsuario", c.medir("roles", () -> politicaAcceso.descripcionRoles(usuario)));
        model.addAttribute("areaUsuario", c.medir("area", () -> usuario != null ? canalService.cabeceraDelUsuario(usuario) : null));

        // 📥 Bandejas paginadas por ámbito, "Todos" incluida (auditoría B2)
        List<Documento> activos = List.of();
        BandejaService.Resumen resumen = null;
        if (usuario != null) {
            resumen = c.medir("resumen", () -> bandejaService.resumen(usuario));
            long total = resumen.bandejas().getOrDefault(bandeja.name(), 0L);
            Page<Documento> paginaBandeja = c.medir("bandeja", () -> bandejaService.listar(usuario, bandeja, pagina, total));
            activos = paginaBandeja.getContent();
            model.addAttribute("paginaBandeja", paginaBandeja);
        }
        model.addAttribute("bandejas", BandejaService.Bandeja.values());
        model.addAttribute("bandejaActual", bandeja);
        model.addAttribute("contadoresBandejas", resumen != null ? resumen.bandejas() : java.util.Map.of());
        // Indicadores del panel: del ámbito del usuario, no de toda la institución
        model.addAttribute("metricas", resumen != null ? resumen.indicadores() : java.util.Map.of());
        // ⏳ Pases que emitió su ámbito y siguen sin acuse fuera de plazo, o que nadie puede recibir (auditoría M2)
        model.addAttribute("pasesDemorados", c.medir("demorados", () -> documentoService.pasesEmitidosDemorados(usuario)));
        model.addAttribute("documentos", activos);
        List<Documento> paraSla = activos;
        c.medir("sla", () -> agregarIndicadoresSla(model, paraSla, resultadosBusqueda));
        c.medir("archivo", () -> agregarDatosArchivo(model, usuario));
        model.addAttribute("documentosFinalizados", c.medir("finalizados", () -> documentoService.obtenerDocumentosFinalizadosParaUsuario(usuario)));

        boolean conDependencia = usuario != null && usuario.getDependencia() != null;
        model.addAttribute("documentosSalida", c.medir("salidas", () -> conDependencia
                ? documentoRepository.findTop50ByDependenciaOrigenIdOrderByFechaHoraIngresoDesc(usuario.getDependencia().getId())
                : List.<Documento>of()));

        model.addAttribute("dependenciasAgrupadas", c.medir("dependencias", () -> documentoService.obtenerDependenciasAgrupadas()));
        model.addAttribute("tiposDoc", c.medir("tipos", () -> documentoService.obtenerTiposDocumentoActivos()));

        if (serverTiming && org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes atributos
                && atributos.getResponse() != null) {
            atributos.getResponse().setHeader("Server-Timing", c.cabecera());
        }
    }

    /**
     * ↩️ DEVOLUCIÓN RÁPIDA DEL EXPEDIENTE AL EMISOR DEL ÚLTIMO PASE RECIBIDO
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
            redirectAttributes.addFlashAttribute("error", mensajeError(e));
        }
        return "redirect:/"; // Redirige a la bandeja principal
    }   

    
    /**
     * 📥 GUARDAR NUEVO DOCUMENTO
     */
    @PostMapping("/documentos/guardar")
    public String guardarDocumento(@ModelAttribute("nuevoDoc") NuevoDocumentoDTO documento,
                                   RedirectAttributes redirectAttributes) {
        try {
            Documento docGuardado = documentoService.guardarNuevoDocumento(documento, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "Documento registrado con CUTI " + docGuardado.getCodigoCuti() + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar el documento: " + mensajeError(e));
        }
        return "redirect:/";
    }

    /**
     * ✍️ DOCUMENTO PROPIO: lo inicia cualquier dependencia y puede salir hacia su superior
     */
    @PostMapping("/documentos/propio")
    public String iniciarDocumentoPropio(@RequestParam("tipoDocumentoId") Integer tipoDocumentoId,
                                         @RequestParam(value = "numeroDocumento", required = false) String numeroDocumento,
                                         @RequestParam("asunto") String asunto,
                                         @RequestParam(value = "prioridad", required = false) Prioridad prioridad,
                                         @RequestParam(value = "observacion", required = false) String observacion,
                                         @RequestParam(value = "enviarAlSuperior", defaultValue = "false") boolean enviarAlSuperior,
                                         @RequestParam(value = "asuntoNuevo", defaultValue = "false") boolean asuntoNuevo,
                                         @RequestParam(value = "archivo", required = false) MultipartFile archivo,
                                         RedirectAttributes redirectAttributes) {
        try {
            // Un documento que pertenece a un expediente existente se agrega como actuación y se eleva (mismo CUTI)
            if (!asuntoNuevo) {
                throw new IllegalArgumentException("Confirme que es un asunto nuevo. Si el documento pertenece a un expediente existente, "
                        + "regístrelo como actuación en ese expediente y elévelo con \"Derivar / Elevar\".");
            }
            Documento doc = documentoService.iniciarDocumentoPropio(tipoDocumentoId, numeroDocumento, asunto, prioridad,
                    observacion, enviarAlSuperior, archivo, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "Documento " + doc.getCodigoCuti()
                    + (enviarAlSuperior ? " creado y enviado a su dependencia superior." : " creado."));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al crear el documento: " + mensajeError(e));
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
                                   @RequestParam("tipoPase") TipoPase tipoPase,
                                   RedirectAttributes redirectAttributes) {
        try {
            documentoService.derivarDocumento(documentoId, dependenciasDestinoIds, observacion, tipoPase, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "Expediente derivado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al derivar: " + mensajeError(e));
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
                                  @RequestParam(value = "tipoRemision", required = false) String tipoRemision,
                                  @RequestParam(value = "informeActuacionId", required = false) List<Long> informes,
                                  RedirectAttributes redirectAttributes) {
        try {
            documentoService.registrarSalidaDocumento(documentoId, nroNotaSalida, dependenciaDestinoId,
                                                   personaRetira, funcionarioEntrega, archivoAcuse,
                                                   tipoRemision, informes, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "Salida exterior registrada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar la salida: " + mensajeError(e));
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
            redirectAttributes.addFlashAttribute("error", "Error al confirmar el acuse: " + mensajeError(e));
        }
        return "redirect:/";
    }

    /**
     * 📄 VER PDF DE UNA ACTUACIÓN (copia sellada o acuse de pase interno)
     */
    @GetMapping("/documentos/actuaciones/{actuacionId}/pdf")
    public ResponseEntity<Resource> verPdfActuacion(@PathVariable Long actuacionId) {
        java.nio.file.Path ruta;
        try {
            ruta = documentoService.resolverPdfActuacion(actuacionId, getUsuarioAutenticado());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return responderPdf(ruta, null);
    }

    /**
     * 📄 DESCARGAR ACUSE PDF
     */
    @GetMapping("/documentos/descargar-acuse/{documentoId}")
    public ResponseEntity<Resource> descargarAcuse(@PathVariable Long documentoId) {
        if (!documentoService.puedeAccederDocumento(getUsuarioAutenticado(), documentoId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Solo la versión vigente (las reemplazadas se consultan desde el historial)
        AdjuntoDocumento adjunto = documentoService.obtenerAdjuntoVigenteDocumento(documentoId);
        if (adjunto == null) {
            return ResponseEntity.notFound().build();
        }
        java.nio.file.Path ruta = archivoService.resolver(adjunto.getRutaAlmacenamiento());
        return responderPdf(ruta, adjunto.getNombreArchivo());
    }

    /**
     * 📄 DESCARGAR UNA VERSIÓN CONCRETA DE UN ADJUNTO (vigente o reemplazada)
     */
    @GetMapping("/documentos/adjuntos/{adjuntoId}/pdf")
    public ResponseEntity<Resource> descargarAdjunto(@PathVariable Long adjuntoId) {
        try {
            java.nio.file.Path ruta = documentoService.resolverAdjunto(adjuntoId, getUsuarioAutenticado());
            return responderPdf(ruta, ruta != null ? ruta.getFileName().toString() : null);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // Auditoría B3: los PDF no llevan la CSP general (object-src 'none' impediría el visor del navegador)
    // ni "sandbox" (Chrome y Edge no muestran un PDF aislado). La protección está en la carga:
    // ArchivoService rechaza los PDF con JavaScript o acciones que abren programas. Esta política solo
    // impide que otro sitio muestre el PDF dentro de la suya.
    static final String POLITICA_PDF = "frame-ancestors 'self'";

    private ResponseEntity<Resource> responderPdf(java.nio.file.Path ruta, String nombre) {
        if (ruta == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("Content-Security-Policy", POLITICA_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(nombre != null ? nombre : ruta.getFileName().toString(), StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(new FileSystemResource(ruta));
    }

    /**
     * ↩️ RETIRAR (emisor) O RECHAZAR (receptor) UN PASE PENDIENTE DE CONFIRMACIÓN
     */
    @PostMapping("/documentos/actuaciones/anular-pase")
    public String anularPase(@RequestParam("actuacionId") Long actuacionId,
                             @RequestParam("accion") String accion,
                             @RequestParam("motivo") String motivo,
                             RedirectAttributes redirectAttributes) {
        try {
            documentoService.anularPase(actuacionId, accion, motivo, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "RECHAZAR".equals(accion) ? "Recepción del pase rechazada." : "Pase retirado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo anular el pase: " + mensajeError(e));
        }
        return "redirect:/";
    }

    /**
     * ⚠️ CIERRE FORZADO DE INFORMES / SALIDA EXCEPCIONAL
     */
    @PostMapping("/documentos/forzar-cierre")
    public String forzarCierre(@RequestParam("documentoId") Long documentoId,
                               @RequestParam("justificacion") String justificacion,
                               RedirectAttributes redirectAttributes) {
        try {
            Documento doc = documentoService.forzarCierreInformes(documentoId, justificacion, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Cierre excepcional registrado. " + doc.getCodigoCuti() + " pasó a PENDIENTE_SALIDA.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo forzar el cierre: " + mensajeError(e));
        }
        return "redirect:/";
    }

    /**
     * 🔁 REEMPLAZAR EL PDF DE UNA ACTUACIÓN (la versión anterior queda en el historial)
     */
    @PostMapping("/documentos/actuaciones/reemplazar-pdf")
    public String reemplazarPdfActuacion(@RequestParam("actuacionId") Long actuacionId,
                                         @RequestParam("archivo") MultipartFile archivo,
                                         @RequestParam("motivo") String motivo,
                                         RedirectAttributes redirectAttributes) {
        try {
            documentoService.reemplazarPdfActuacion(actuacionId, archivo, motivo, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "PDF reemplazado. La versión anterior quedó en el historial.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo reemplazar el PDF: " + mensajeError(e));
        }
        return "redirect:/";
    }

    /**
     * 🔁 REEMPLAZAR EL ACUSE DEL EXPEDIENTE (salida / acuse final)
     */
    @PostMapping("/documentos/reemplazar-acuse")
    public String reemplazarAcuse(@RequestParam("documentoId") Long documentoId,
                                  @RequestParam("archivo") MultipartFile archivo,
                                  @RequestParam("motivo") String motivo,
                                  RedirectAttributes redirectAttributes) {
        try {
            documentoService.reemplazarAdjuntoDocumento(documentoId, archivo, motivo, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "Acuse reemplazado. La versión anterior quedó en el historial.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo reemplazar el acuse: " + mensajeError(e));
        }
        return "redirect:/";
    }

    /**
     * 🌐 API JSON: VERSIONES DE LOS ADJUNTOS DEL EXPEDIENTE (salida / acuse final)
     */
    @GetMapping("/api/documentos/{id}/adjuntos")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> obtenerAdjuntosJson(@PathVariable("id") Long id) {
        if (!documentoService.puedeAccederDocumento(getUsuarioAutenticado(), id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(documentoService.obtenerAdjuntosDocumento(id).stream().map(this::adjuntoJson).toList());
    }

    /**
     * 🔐 API JSON: VERIFICAR LA INTEGRIDAD DE UN ADJUNTO (huella SHA-256 vs. archivo en disco)
     */
    @GetMapping("/api/adjuntos/{adjuntoId}/integridad")
    @ResponseBody
    public ResponseEntity<Map<String, String>> verificarIntegridad(@PathVariable Long adjuntoId) {
        try {
            var r = documentoService.verificarIntegridadAdjunto(adjuntoId, getUsuarioAutenticado());
            return ResponseEntity.ok(Map.of("estado", r.estado(), "detalle", r.detalle()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private Map<String, Object> adjuntoJson(AdjuntoDocumento a) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", a.getId());
        map.put("tieneHuella", a.getHashSha256() != null);
        map.put("nombre", a.getNombreArchivo());
        map.put("tipo", a.getTipoAdjunto());
        map.put("vigente", Boolean.TRUE.equals(a.getVigente()));
        map.put("motivoReemplazo", a.getMotivoReemplazo());
        map.put("usuario", a.getUsuarioSubida() != null ? a.getUsuarioSubida().getNombreCompleto() : "-");
        map.put("fecha", a.getFechaHoraSubida() != null
                ? a.getFechaHoraSubida().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "-");
        return map;
    }

    /**
     * 📚 REGISTRAR O CAMBIAR LA UBICACIÓN FÍSICA (bibliorato + fojas)
     */
    @PostMapping("/documentos/ubicacion")
    public String cambiarUbicacion(@RequestParam("documentoId") Long documentoId,
                                   @RequestParam("biblioratoId") Integer biblioratoId,
                                   @RequestParam(value = "fojaDesde", required = false) Integer fojaDesde,
                                   @RequestParam(value = "fojaHasta", required = false) Integer fojaHasta,
                                   @RequestParam(value = "motivo", required = false) String motivo,
                                   RedirectAttributes redirectAttributes) {
        try {
            Documento doc = documentoService.cambiarUbicacion(documentoId, biblioratoId, fojaDesde, fojaHasta, motivo,
                    getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", doc.getCodigoCuti() + " guardado en " + doc.getUbicacionTexto() + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo registrar la ubicación: " + mensajeError(e));
        }
        return "redirect:/";
    }

    /**
     * 📚 CATÁLOGO DE BIBLIORATOS: alta
     */
    @PostMapping("/biblioratos/guardar")
    public String guardarBibliorato(@RequestParam("codigo") String codigo,
                                    @RequestParam("descripcion") String descripcion,
                                    @RequestParam("dependenciaId") Integer dependenciaId,
                                    @RequestParam("anio") Integer anio,
                                    @RequestParam("ubicacionFisica") String ubicacionFisica,
                                    @RequestParam(value = "capacidadFojas", required = false) Integer capacidadFojas,
                                    RedirectAttributes redirectAttributes) {
        try {
            var b = biblioratoService.crear(codigo, descripcion, dependenciaId, anio, ubicacionFisica, capacidadFojas,
                    getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "Bibliorato " + b.getCodigo() + " creado.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo crear el bibliorato: " + mensajeError(e));
        }
        return "redirect:/";
    }

    /**
     * 📚 CATÁLOGO DE BIBLIORATOS: activar / cerrar
     */
    @PostMapping("/biblioratos/{id}/estado")
    public String cambiarEstadoBibliorato(@PathVariable Integer id,
                                          @RequestParam("activo") boolean activo,
                                          RedirectAttributes redirectAttributes) {
        try {
            var b = biblioratoService.cambiarEstado(id, activo, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "Bibliorato " + b.getCodigo() + (activo ? " reabierto." : " cerrado."));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo actualizar el bibliorato: " + mensajeError(e));
        }
        return "redirect:/";
    }

    // 📚 Datos de la pestaña "Archivo físico" y del catálogo de biblioratos
    private void agregarDatosArchivo(Model model, Usuario usuario) {
        model.addAttribute("biblioratos", biblioratoService.listarActivos());
        model.addAttribute("documentosArchivo", documentoService.obtenerDocumentosArchivoParaUsuario(usuario, null));
        if (politicaAcceso.puede(usuario, Accion.GESTIONAR_ARCHIVO)) {
            model.addAttribute("biblioratosTodos", biblioratoService.listarTodos());
            model.addAttribute("usoBiblioratos", biblioratoService.obtenerUso());
        }
    }

    // ⏱️ Indicadores de demora para la bandeja de activos y los resultados de búsqueda
    private void agregarIndicadoresSla(Model model, List<Documento> activos, List<Documento> resultados) {
        List<Documento> todos = new ArrayList<>(activos);
        todos.addAll(resultados);
        Map<Long, DocumentoService.IndicadorSla> sla = documentoService.calcularSla(todos);
        model.addAttribute("sla", sla);
        model.addAttribute("slaCriticos", activos.stream()
                .map(d -> sla.get(d.getId()))
                .filter(i -> i != null && DocumentoService.IndicadorSla.CRITICO.equals(i.nivel()))
                .count());
    }

    /**
     * 🔍 BUSCADOR MULTICRITERIO
     */
    @GetMapping("/documentos/buscar")
    public String buscarDocumentos(@RequestParam(value = "texto", required = false) String texto,
                                   @RequestParam(value = "dependenciaId", required = false) Integer dependenciaId,
                                   @RequestParam(value = "tipoDocId", required = false) Integer tipoDocId,
                                   @RequestParam(value = "estado", required = false) String estado,
                                   @RequestParam(value = "biblioratoId", required = false) Integer biblioratoId,
                                   @RequestParam(value = "fechaDesde", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaDesde,
                                   @RequestParam(value = "fechaHasta", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaHasta,
                                   Model model) {

        Usuario usuarioLogueado = getUsuarioAutenticado();
        // 🛡️ La búsqueda muestra solo los expedientes del ámbito del usuario (filtrado en la consulta)
        List<Documento> resultados = documentoService.buscarDocumentosMulticriterio(usuarioLogueado,
                texto, dependenciaId, tipoDocId, estado, biblioratoId, fechaDesde, fechaHasta);

        model.addAttribute("documentosResultados", resultados);
        model.addAttribute("busquedaRecortada", resultados.size() >= DocumentoRepository.MAXIMO_RESULTADOS_BUSQUEDA);
        model.addAttribute("esBusqueda", true);
        model.addAttribute("paramTexto", texto);
        model.addAttribute("paramDependenciaId", dependenciaId);
        model.addAttribute("paramTipoDocId", tipoDocId);
        model.addAttribute("paramEstado", estado);
        model.addAttribute("paramBiblioratoId", biblioratoId);
        model.addAttribute("paramFechaDesde", fechaDesde);
        model.addAttribute("paramFechaHasta", fechaHasta);

        cargarPantallaPrincipal(model, usuarioLogueado, resultados, BandejaService.Bandeja.TODOS, 0);
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
            redirectAttributes.addFlashAttribute("error", "Error al registrar la actuación: " + mensajeError(e));
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
            documentoService.confirmarRecepcionPaseInterno(actuacionId, archivoAcusePdf, getUsuarioAutenticadoObligatorio());
            redirectAttributes.addFlashAttribute("mensajeExito", "Recepción del pase confirmada.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al confirmar el pase: " + mensajeError(e));
        }
        return "redirect:/";
    }

    /**
     * 🌐 API JSON: OBTENER ACTUACIONES (ÁRBOL DE TRAZABILIDAD)
     */
    static final String JUSTIFICACION_RESERVADA =
            "Cierre excepcional registrado. La justificación la ven quienes pueden forzar cierres o auditar el expediente.";

    @GetMapping("/api/documentos/{id}/actuaciones")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> obtenerActuacionesJson(@PathVariable("id") Long id) {
        Usuario usuario = getUsuarioAutenticado();
        if (!documentoService.puedeAccederDocumento(usuario, id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<Map<String, Object>> listaJson = new ArrayList<>();
        List<ActuacionDocumento> actuaciones = documentoService.obtenerActuacionesPorDocumento(id);

        // Versiones de PDF de todas las actuaciones en una sola consulta
        Map<Long, List<Map<String, Object>>> versiones = new HashMap<>();
        for (AdjuntoDocumento adj : documentoService.obtenerVersionesPdfActuaciones(
                actuaciones.stream().map(ActuacionDocumento::getId).toList())) {
            versiones.computeIfAbsent(adj.getActuacion().getId(), k -> new ArrayList<>()).add(adjuntoJson(adj));
        }

        // 🛡️ La justificación de un cierre excepcional la ven solo quienes pueden forzar cierres o auditar;
        // el resto ve que hubo un cierre excepcional, cuándo y quién, pero no el texto
        boolean veJustificacionCierre = politicaAcceso.puede(usuario, Accion.FORZAR_CIERRE)
                || politicaAcceso.puede(usuario, Accion.VER_AUDITORIA);
        Map<Long, String> legajo = documentoService.legajoDeSalida(id);

        for (var act : actuaciones) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", act.getId());
            map.put("documentoId", id);
            // Unidad de expediente: a qué pedido de informe responde y si integra el legajo de la salida
            map.put("respondeA", act.getActuacionPadre() != null ? act.getActuacionPadre().getId() : null);
            map.put("respondeADependencia", act.getActuacionPadre() != null && act.getActuacionPadre().getDependenciaEmisora() != null
                    ? act.getActuacionPadre().getDependenciaEmisora().getNombreVisible() : null);
            map.put("enLegajo", legajo.get(act.getId()));
            map.put("tipoActuacion", act.getTipoActuacion() != null ? act.getTipoActuacion() : "PASE_INTERNO");
            map.put("numeroActuacion", act.getNumeroActuacion() != null ? act.getNumeroActuacion() : "S/N");
            map.put("dependencia", act.getDependenciaEmisora() != null ? act.getDependenciaEmisora().getNombreVisible() : "Sin Dependencia");
            map.put("dependenciaEmisoraId", act.getDependenciaEmisora() != null ? act.getDependenciaEmisora().getId() : null);
            map.put("dependenciaReceptoraId", act.getDependenciaReceptoraPase() != null ? act.getDependenciaReceptoraPase().getId() : null);
            map.put("dependenciaReceptora", act.getDependenciaReceptoraPase() != null ? act.getDependenciaReceptoraPase().getNombreVisible() : null);
            boolean reservada = !veJustificacionCierre && "CIERRE_EXCEPCIONAL".equals(act.getTipoActuacion());
            map.put("descripcion", reservada ? JUSTIFICACION_RESERVADA : act.getDescripcionResumen());
            map.put("bibliorato", act.getUbicacionBibliorato() != null && !act.getUbicacionBibliorato().isBlank()
                    ? act.getUbicacionBibliorato() : "No especificado");
            // Solo indica si hay PDF: la ruta interna del servidor no se expone
            map.put("pdf", act.getRutaCopiaSelladaPdf() != null && !act.getRutaCopiaSelladaPdf().isBlank());
            map.put("paseConfirmado", act.getPaseConfirmado() != null ? act.getPaseConfirmado() : true);
            map.put("fecha", act.getFechaHoraActuacion() != null
                    ? act.getFechaHoraActuacion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "-");

            // Anulación lógica (pases retirados / rechazados)
            map.put("anulada", Boolean.TRUE.equals(act.getAnulada()));
            map.put("tipoAnulacion", act.getTipoAnulacion());
            map.put("motivoAnulacion", !veJustificacionCierre && "CIERRE_EXCEPCIONAL".equals(act.getTipoAnulacion())
                    ? JUSTIFICACION_RESERVADA : act.getMotivoAnulacion());
            map.put("fechaAnulacion", act.getFechaHoraAnulacion() != null
                    ? act.getFechaHoraAnulacion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : null);

            // Acciones que el usuario actual puede realizar sobre esta actuación
            map.put("puedeRetirar", documentoService.puedeRetirarPase(act, usuario));
            map.put("puedeRechazar", documentoService.puedeRechazarPase(act, usuario));
            map.put("puedeReemplazarPdf", documentoService.puedeReemplazarPdf(act, usuario));
            map.put("versiones", versiones.getOrDefault(act.getId(), List.of()));
            listaJson.add(map);
        }
        return ResponseEntity.ok(listaJson);
    }

    /**
     * 🧭 API JSON: DESTINOS QUE PERMITE EL CANAL DESDE QUIEN TIENE EL ORIGINAL
     */
    @GetMapping("/api/documentos/{id}/destinos")
    @ResponseBody
    public ResponseEntity<DocumentoService.OpcionesDerivacion> obtenerDestinosJson(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(documentoService.obtenerOpcionesDerivacion(id, getUsuarioAutenticadoObligatorio()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * 🌐 API JSON: INFORMES DISPONIBLES
     */
    @GetMapping("/api/documentos/{id}/informes-disponibles")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> obtenerInformesDisponiblesJson(@PathVariable("id") Long id) {
        if (!documentoService.puedeAccederDocumento(getUsuarioAutenticado(), id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<Map<String, Object>> listaJson = new ArrayList<>();
        List<ActuacionDocumento> actuaciones = documentoService.obtenerActuacionesPorDocumento(id);
        
        for (ActuacionDocumento act : actuaciones) {
            if ("INFORME_TECNICO".equals(act.getTipoActuacion()) || 
                "MEMO".equals(act.getTipoActuacion()) || 
                "PROVIDENCIA".equals(act.getTipoActuacion())) {
                
                Map<String, Object> map = new HashMap<>();
                map.put("id", act.getId());
                map.put("tipo", act.getTipoActuacion());
                map.put("numero", act.getNumeroActuacion() != null ? act.getNumeroActuacion() : "S/N");
                map.put("dependencia", act.getDependenciaEmisora().getNombreVisible());
                map.put("resumen", act.getDescripcionResumen());
                listaJson.add(map);
            }
        }
        return ResponseEntity.ok(listaJson);
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

    // Traduce errores técnicos (restricciones de la BD, bloqueos) a mensajes para el usuario
    private String mensajeError(Exception e) {
        if (e instanceof DataIntegrityViolationException integridad) {
            String detalle = String.valueOf(integridad.getMostSpecificCause().getMessage());
            if (detalle.contains("uq_pase_pendiente_por_receptora")) {
                return "Ya existe un pase pendiente de acuse para esa dependencia. Espere la confirmación o retire el pase anterior.";
            }
            if (detalle.contains("chk_")) {
                return "El dato no está dentro de los valores permitidos por el sistema.";
            }
            return "La operación viola una regla de integridad de la base de datos y no se guardó.";
        }
        if (e instanceof PessimisticLockingFailureException) {
            return "El expediente está siendo modificado por otro usuario. Intente nuevamente en unos segundos.";
        }
        return e.getMessage();
    }

    private Usuario getUsuarioAutenticadoObligatorio() {
        Usuario usuario = getUsuarioAutenticado();
        if (usuario == null) {
            throw new IllegalStateException("No se pudo identificar al usuario de la sesión.");
        }
        return usuario;
    }
}
