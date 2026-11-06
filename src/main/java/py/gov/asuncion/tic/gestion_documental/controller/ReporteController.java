package py.gov.asuncion.tic.gestion_documental.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;
import py.gov.asuncion.tic.gestion_documental.service.DocumentoService;
import py.gov.asuncion.tic.gestion_documental.service.ReporteCsv;
import py.gov.asuncion.tic.gestion_documental.service.ReporteService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

// Pantalla de reportes y su exportación. El permiso lo exige SecurityConfig; el alcance, ReporteService.
@Controller
public class ReporteController {

    @Autowired private ReporteService reporteService;
    @Autowired private DocumentoService documentoService;
    @Autowired private UsuarioRepository usuarioRepository;

    @GetMapping("/reportes")
    public String reportes(@RequestParam(required = false) Integer dependenciaId,
                           @RequestParam(required = false) Integer tipoId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                           Authentication auth, Model model) {
        Usuario usuario = actual(auth);
        ReporteService.Filtro filtro = ReporteService.Filtro.de(dependenciaId, tipoId, desde, hasta, LocalDate.now());
        model.addAttribute("usuario", usuario);
        model.addAttribute("filtro", filtro);
        model.addAttribute("datos", reporteService.generar(usuario, filtro));
        model.addAttribute("dependencias", reporteService.dependenciasDelAlcance(usuario));
        model.addAttribute("tipos", documentoService.obtenerTiposDocumentoActivos());
        return "reportes";
    }

    @GetMapping("/reportes/exportar/{reporte}")
    public ResponseEntity<byte[]> exportar(@PathVariable String reporte,
                                           @RequestParam(required = false) Integer dependenciaId,
                                           @RequestParam(required = false) Integer tipoId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                           Authentication auth) {
        if (!ReporteCsv.REPORTES.contains(reporte)) {
            return ResponseEntity.notFound().build();
        }
        ReporteService.Filtro filtro = ReporteService.Filtro.de(dependenciaId, tipoId, desde, hasta, LocalDate.now());
        byte[] csv = ReporteCsv.generar(reporte, reporteService.generar(actual(auth), filtro));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-" + reporte + "-" + LocalDate.now() + ".csv\"")
                .body(csv);
    }

    private Usuario actual(Authentication auth) {
        return usuarioRepository.findByUsernameAndActivoTrue(auth.getName()).orElseThrow();
    }
}
