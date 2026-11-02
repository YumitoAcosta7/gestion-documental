package py.gov.asuncion.tic.gestion_documental.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;
import py.gov.asuncion.tic.gestion_documental.service.PoliticaAcceso;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Auditoría A1: un usuario dado de baja con la sesión abierta la pierde en la petición siguiente
class ActualizacionPermisosFilterTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PoliticaAcceso politica = mock(PoliticaAcceso.class);
    private final ActualizacionPermisosFilter filtro = new ActualizacionPermisosFilter(politica, usuarios);

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void sesionIniciadaCon(String... permisos) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "ana", null, java.util.Arrays.stream(permisos).map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void usuarioDadoDeBajaPierdeLaSesion() throws Exception {
        sesionIniciadaCon("PERMISO_REGISTRAR_SALIDA");
        when(usuarios.findByUsernameAndActivoTrue("ana")).thenReturn(Optional.empty());
        MockHttpSession sesion = new MockHttpSession();
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/");
        pedido.setSession(sesion);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(pedido, respuesta, cadena);

        assertTrue(sesion.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("/login?baja", respuesta.getRedirectedUrl());
        assertNull(cadena.getRequest(), "la petición no llega a la aplicación");
    }

    @Test
    void usuarioActivoRecibeLosPermisosVigentes() throws Exception {
        sesionIniciadaCon("PERMISO_VIEJO");
        Usuario ana = new Usuario();
        ana.setUsername("ana");
        when(usuarios.findByUsernameAndActivoTrue("ana")).thenReturn(Optional.of(ana));
        when(politica.autoridades(ana)).thenReturn(List.of(new SimpleGrantedAuthority("PERMISO_DERIVAR")));
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse(), cadena);

        assertNotNull(cadena.getRequest());
        assertEquals(List.of("PERMISO_DERIVAR"), SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().map(Object::toString).toList());
    }
}
