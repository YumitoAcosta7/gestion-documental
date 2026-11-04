package py.gov.asuncion.tic.gestion_documental.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import py.gov.asuncion.tic.gestion_documental.domain.RolAsignacion;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;
import py.gov.asuncion.tic.gestion_documental.service.AdministracionService;
import py.gov.asuncion.tic.gestion_documental.service.DocumentoService;
import py.gov.asuncion.tic.gestion_documental.service.FeriadoService;

import java.time.LocalDate;

// 👥 Pantalla de administración (solo PERMISO_ADMINISTRAR_SISTEMA, ver SecurityConfig):
// usuarios, sus asignaciones y suplencias, y la auditoría del sistema.
@Controller
@RequestMapping("/admin")
public class AdministracionController {

    private final AdministracionService administracionService;
    private final DocumentoService documentoService;
    private final FeriadoService feriadoService;
    private final UsuarioRepository usuarioRepository;

    public AdministracionController(AdministracionService administracionService, DocumentoService documentoService,
                                    FeriadoService feriadoService, UsuarioRepository usuarioRepository) {
        this.administracionService = administracionService;
        this.documentoService = documentoService;
        this.feriadoService = feriadoService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/usuarios")
    public String usuarios(@RequestParam(value = "texto", required = false) String texto,
                           @RequestParam(value = "usuarioId", required = false) Integer usuarioId,
                           Authentication auth, Model model) {
        Usuario admin = actual(auth);
        model.addAttribute("admin", admin);
        model.addAttribute("texto", texto);
        model.addAttribute("usuarios", administracionService.buscarUsuarios(texto, admin));
        model.addAttribute("dependenciasAgrupadas", documentoService.obtenerDependenciasAgrupadas());
        model.addAttribute("roles", RolAsignacion.values());
        model.addAttribute("hoy", LocalDate.now());
        if (usuarioId != null) {
            model.addAttribute("seleccionado", administracionService.obtenerUsuario(usuarioId, admin));
            model.addAttribute("asignaciones", administracionService.asignacionesDe(usuarioId, admin));
            model.addAttribute("seleccionadoSensible", administracionService.esCuentaSensible(administracionService.obtenerUsuario(usuarioId, admin)));
        }
        model.addAttribute("pendientes", administracionService.pendientesDeAprobacion(admin));
        model.addAttribute("contrasenasPendientes", administracionService.contrasenasPendientes(admin));
        model.addAttribute("pasesTrabados", documentoService.pasesTrabados(admin));
        model.addAttribute("feriados", feriadoService.calendario(admin));
        model.addAttribute("auditoria", administracionService.auditoriaReciente(admin));
        return "admin";
    }

    @PostMapping("/usuarios")
    public String crear(@RequestParam("username") String username, @RequestParam("nombreCompleto") String nombreCompleto,
                        @RequestParam(value = "email", required = false) String email,
                        @RequestParam("dependenciaId") Integer dependenciaId,
                        Authentication auth, RedirectAttributes ra) {
        try {
            var r = administracionService.crearUsuario(username, nombreCompleto, email, dependenciaId, actual(auth));
            mostrarTemporal(ra, r, "Usuario " + r.usuario().getUsername() + " creado. Ahora asígnele sus roles.");
            return "redirect:/admin/usuarios?usuarioId=" + r.usuario().getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/usuarios";
        }
    }

    @PostMapping("/usuarios/{id}")
    public String actualizar(@PathVariable("id") Integer id, @RequestParam("nombreCompleto") String nombreCompleto,
                             @RequestParam(value = "email", required = false) String email,
                             @RequestParam("dependenciaId") Integer dependenciaId,
                             Authentication auth, RedirectAttributes ra) {
        try {
            administracionService.actualizarUsuario(id, nombreCompleto, email, dependenciaId, actual(auth));
            ra.addFlashAttribute("mensajeExito", "Datos del usuario actualizados.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios?usuarioId=" + id;
    }

    @PostMapping("/usuarios/{id}/estado")
    public String estado(@PathVariable("id") Integer id, @RequestParam("activo") boolean activo,
                         @RequestParam(value = "motivo", required = false) String motivo,
                         Authentication auth, RedirectAttributes ra) {
        try {
            administracionService.cambiarEstado(id, activo, motivo, actual(auth));
            ra.addFlashAttribute("mensajeExito", activo ? "Usuario reactivado." : "Usuario dado de baja.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios?usuarioId=" + id;
    }

    @PostMapping("/usuarios/{id}/contrasena")
    public String restablecer(@PathVariable("id") Integer id, @RequestParam(value = "motivo", required = false) String motivo,
                              Authentication auth, RedirectAttributes ra) {
        try {
            var r = administracionService.restablecerContrasena(id, motivo, actual(auth));
            if (r.isPresent()) {
                mostrarTemporal(ra, r.get(), "Contraseña restablecida.");
            } else {
                ra.addFlashAttribute("mensajeExito", "Solicitud registrada. Es una cuenta sensible: la contraseña se genera cuando la apruebe otro administrador.");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios?usuarioId=" + id;
    }

    // Auditoría A5: otro administrador aprueba el restablecimiento de una cuenta sensible, y recibe la contraseña temporal
    @PostMapping("/contrasenas/{id}/aprobar")
    public String aprobarContrasena(@PathVariable("id") Long id, Authentication auth, RedirectAttributes ra) {
        try {
            mostrarTemporal(ra, administracionService.aprobarContrasena(id, actual(auth)), "Restablecimiento aprobado.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/contrasenas/{id}/rechazar")
    public String rechazarContrasena(@PathVariable("id") Long id, @RequestParam("motivo") String motivo,
                                     Authentication auth, RedirectAttributes ra) {
        try {
            administracionService.rechazarContrasena(id, motivo, actual(auth));
            ra.addFlashAttribute("mensajeExito", "Solicitud de restablecimiento cerrada.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/{id}/asignaciones")
    public String asignar(@PathVariable("id") Integer id, @RequestParam("rol") RolAsignacion rol,
                          @RequestParam("ambitoId") Integer ambitoId,
                          @RequestParam(value = "incluyeSubordinadas", defaultValue = "false") boolean incluyeSubordinadas,
                          @RequestParam(value = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                          @RequestParam(value = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                          @RequestParam(value = "esSuplencia", defaultValue = "false") boolean esSuplencia,
                          @RequestParam(value = "titularId", required = false) Integer titularId,
                          @RequestParam("actoAdministrativo") String actoAdministrativo,
                          Authentication auth, RedirectAttributes ra) {
        try {
            var a = administracionService.crearAsignacion(id, rol, ambitoId, incluyeSubordinadas, desde, hasta, esSuplencia,
                    titularId, actoAdministrativo, actual(auth));
            ra.addFlashAttribute("mensajeExito", a.isPendiente()
                    ? "Solicitud registrada: no rige hasta que la apruebe otro administrador."
                    : esSuplencia ? "Suplencia registrada." : "Asignación registrada.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios?usuarioId=" + id;
    }

    @PostMapping("/asignaciones/{id}/baja")
    public String quitarAsignacion(@PathVariable("id") Long id, @RequestParam("usuarioId") Integer usuarioId,
                                   @RequestParam("motivo") String motivo, Authentication auth, RedirectAttributes ra) {
        try {
            administracionService.finalizarAsignacion(id, motivo, actual(auth));
            ra.addFlashAttribute("mensajeExito", "Asignación dada de baja.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios?usuarioId=" + usuarioId;
    }

    // Doble control (auditoría C2): otro administrador aprueba o rechaza las asignaciones sensibles
    @PostMapping("/asignaciones/{id}/aprobar")
    public String aprobar(@PathVariable("id") Long id, Authentication auth, RedirectAttributes ra) {
        try {
            var a = administracionService.aprobarAsignacion(id, actual(auth));
            ra.addFlashAttribute("mensajeExito", "Asignación aprobada: " + a.getUsuario().getUsername() + " ya tiene " + a.getRol() + ".");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/asignaciones/{id}/rechazar")
    public String rechazar(@PathVariable("id") Long id, @RequestParam("motivo") String motivo,
                           Authentication auth, RedirectAttributes ra) {
        try {
            administracionService.rechazarAsignacion(id, motivo, actual(auth));
            ra.addFlashAttribute("mensajeExito", "Solicitud rechazada.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    // 📅 Calendario de feriados (días que no cuentan para los plazos)
    @PostMapping("/feriados")
    public String agregarFeriado(@RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                 @RequestParam("descripcion") String descripcion, Authentication auth, RedirectAttributes ra) {
        try {
            feriadoService.agregar(fecha, descripcion, actual(auth));
            ra.addFlashAttribute("mensajeExito", "Feriado agregado.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios#feriados";
    }

    @PostMapping("/feriados/{fecha}/baja")
    public String quitarFeriado(@PathVariable("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                Authentication auth, RedirectAttributes ra) {
        try {
            feriadoService.quitar(fecha, actual(auth));
            ra.addFlashAttribute("mensajeExito", "Feriado quitado.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios#feriados";
    }

    // Auditoría M2: retiro de un pase que nadie puede recibir
    @PostMapping("/pases/{id}/retirar")
    public String retirarPase(@PathVariable("id") Long id, @RequestParam("motivo") String motivo,
                              Authentication auth, RedirectAttributes ra) {
        try {
            var pase = documentoService.retirarPaseTrabado(id, motivo, actual(auth));
            ra.addFlashAttribute("mensajeExito", "Pase retirado del expediente " + pase.getDocumento().getCodigoCuti()
                    + ". La dependencia emisora puede derivarlo de nuevo.");
        } catch (IllegalArgumentException | IllegalStateException | org.springframework.security.access.AccessDeniedException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    // La contraseña temporal se muestra una sola vez (flash): no queda en ningún registro
    private static void mostrarTemporal(RedirectAttributes ra, AdministracionService.ContrasenaTemporal r, String mensaje) {
        ra.addFlashAttribute("mensajeExito", mensaje);
        ra.addFlashAttribute("usuarioTemporal", r.usuario().getUsername());
        ra.addFlashAttribute("contrasenaTemporal", r.contrasena());
    }

    private Usuario actual(Authentication auth) {
        return usuarioRepository.findByUsernameAndActivoTrue(auth.getName()).orElseThrow();
    }
}
