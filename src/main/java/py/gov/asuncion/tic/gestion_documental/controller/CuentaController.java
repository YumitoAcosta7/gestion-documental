package py.gov.asuncion.tic.gestion_documental.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;
import py.gov.asuncion.tic.gestion_documental.service.AdministracionService;
import py.gov.asuncion.tic.gestion_documental.service.ReglasContrasena;

// 🔑 Cuenta del propio usuario: cambio de contraseña (obligatorio tras un alta o un reseteo)
@Controller
public class CuentaController {

    private final UsuarioRepository usuarioRepository;
    private final AdministracionService administracionService;

    public CuentaController(UsuarioRepository usuarioRepository, AdministracionService administracionService) {
        this.usuarioRepository = usuarioRepository;
        this.administracionService = administracionService;
    }

    @GetMapping("/cuenta/contrasena")
    public String formulario(Authentication auth, Model model) {
        Usuario usuario = usuarioRepository.findByUsernameAndActivoTrue(auth.getName()).orElseThrow();
        model.addAttribute("usuario", usuario);
        model.addAttribute("reglas", ReglasContrasena.DESCRIPCION);
        return "cuenta-contrasena";
    }

    @PostMapping("/cuenta/contrasena")
    public String cambiar(@RequestParam("actual") String actual,
                          @RequestParam("nueva") String nueva,
                          @RequestParam("repetida") String repetida,
                          Authentication auth, RedirectAttributes redirectAttributes) {
        Usuario usuario = usuarioRepository.findByUsernameAndActivoTrue(auth.getName()).orElseThrow();
        try {
            administracionService.cambiarContrasenaPropia(usuario, actual, nueva, repetida);
            redirectAttributes.addFlashAttribute("mensajeExito", "Contraseña actualizada.");
            return "redirect:/";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/cuenta/contrasena";
        }
    }
}
