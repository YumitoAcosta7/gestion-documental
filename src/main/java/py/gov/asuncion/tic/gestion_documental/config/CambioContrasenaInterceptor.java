package py.gov.asuncion.tic.gestion_documental.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;

// 🔑 Quien entra con una contraseña temporal (alta o reseteo) solo puede cambiarla: cualquier otra
// página lo lleva a /cuenta/contrasena hasta que lo haga. Las rutas libres se excluyen en WebConfig.
@Component
public class CambioContrasenaInterceptor implements HandlerInterceptor {

    private final UsuarioRepository usuarioRepository;

    public CambioContrasenaInterceptor(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof UsernamePasswordAuthenticationToken) || !auth.isAuthenticated()) {
            return true;
        }
        boolean debeCambiar = usuarioRepository.findByUsernameAndActivoTrue(auth.getName())
                .map(u -> Boolean.TRUE.equals(u.getDebeCambiarContrasena()))
                .orElse(false);
        if (debeCambiar) {
            response.sendRedirect(request.getContextPath() + "/cuenta/contrasena");
            return false;
        }
        return true;
    }
}
