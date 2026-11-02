package py.gov.asuncion.tic.gestion_documental.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;
import py.gov.asuncion.tic.gestion_documental.service.PoliticaAcceso;

import java.io.IOException;

// 🔄 Recalcula los permisos en cada petición a partir de las asignaciones vigentes,
// para que una asignación nueva o una suplencia vencida se aplique sin volver a entrar.
// Se registra solo dentro de la cadena de Spring Security (no es un @Component).
public class ActualizacionPermisosFilter extends OncePerRequestFilter {

    private final PoliticaAcceso politicaAcceso;
    private final UsuarioRepository usuarioRepository;

    public ActualizacionPermisosFilter(PoliticaAcceso politicaAcceso, UsuarioRepository usuarioRepository) {
        this.politicaAcceso = politicaAcceso;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getServletPath();
        return ruta.startsWith("/css/") || ruta.startsWith("/js/") || ruta.startsWith("/images/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        if (actual instanceof UsernamePasswordAuthenticationToken && actual.isAuthenticated()) {
            usuarioRepository.findByUsernameAndActivoTrue(actual.getName()).ifPresent(usuario -> {
                UsernamePasswordAuthenticationToken renovada = UsernamePasswordAuthenticationToken.authenticated(
                        actual.getPrincipal(), null, politicaAcceso.autoridades(usuario));
                renovada.setDetails(actual.getDetails());
                // Solo para esta petición: el contexto guardado en la sesión no se modifica
                SecurityContext contexto = SecurityContextHolder.createEmptyContext();
                contexto.setAuthentication(renovada);
                SecurityContextHolder.setContext(contexto);
            });
        }
        chain.doFilter(request, response);
    }
}
