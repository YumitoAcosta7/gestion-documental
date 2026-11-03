package py.gov.asuncion.tic.gestion_documental.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import py.gov.asuncion.tic.gestion_documental.controller.AdministracionController;
import py.gov.asuncion.tic.gestion_documental.controller.HomeController;
import py.gov.asuncion.tic.gestion_documental.domain.Accion;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.*;
import py.gov.asuncion.tic.gestion_documental.service.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

// 🔐 Matriz de autorización (docs/auditoria-arquitectura-seguridad.md §3.1): para cada perfil × endpoint
// protegido, la cadena de seguridad real (SecurityConfig + ActualizacionPermisosFilter) responde 403 cuando
// el perfil no tiene la acción, y deja pasar cuando la tiene. Los servicios están simulados: lo que se prueba
// es la puerta de entrada. El acceso a cada expediente (propio o ajeno) lo cubren las pruebas de servicio
// (ControlAccesoExpedienteTest, UnidadDeExpedienteTest).
@WebMvcTest(controllers = {HomeController.class, AdministracionController.class})
@Import(SecurityConfig.class)
class MatrizAutorizacionTest {

    @Autowired MockMvc mvc;

    @MockitoBean PoliticaAcceso politicaAcceso;
    @MockitoBean UsuarioRepository usuarioRepository;
    @MockitoBean DocumentoService documentoService;
    @MockitoBean BandejaService bandejaService;
    @MockitoBean CanalService canalService;
    @MockitoBean AuditoriaService auditoriaService;
    @MockitoBean ArchivoService archivoService;
    @MockitoBean BiblioratoService biblioratoService;
    @MockitoBean AdministracionService administracionService;
    @MockitoBean DocumentoRepository documentoRepository;
    @MockitoBean AdjuntoDocumentoRepository adjuntoDocumentoRepository;

    // Perfiles típicos y las acciones que les da PoliticaAcceso
    private static final Map<String, Set<Accion>> PERFILES = Map.of(
            "consulta", EnumSet.noneOf(Accion.class),
            "operador", EnumSet.of(Accion.REGISTRAR_ACTUACION),
            "responsable", EnumSet.of(Accion.REGISTRAR_ACTUACION, Accion.DERIVAR, Accion.VER_AUDITORIA),
            "mesa", EnumSet.complementOf(EnumSet.of(Accion.ADMINISTRAR_SISTEMA)),
            "admin", EnumSet.of(Accion.ADMINISTRAR_SISTEMA));

    // Endpoint protegido -> acciones que lo habilitan (cualquiera de ellas)
    private record Puerta(HttpMethod metodo, String ruta, Set<Accion> requiere) {}

    private static final List<Puerta> PUERTAS = List.of(
            new Puerta(HttpMethod.GET, "/admin/usuarios", EnumSet.of(Accion.ADMINISTRAR_SISTEMA)),
            new Puerta(HttpMethod.POST, "/admin/asignaciones/1/aprobar", EnumSet.of(Accion.ADMINISTRAR_SISTEMA)),
            new Puerta(HttpMethod.POST, "/admin/pases/1/retirar", EnumSet.of(Accion.ADMINISTRAR_SISTEMA)),
            new Puerta(HttpMethod.POST, "/documentos/guardar", EnumSet.of(Accion.REGISTRAR_INGRESO)),
            new Puerta(HttpMethod.POST, "/documentos/salida", EnumSet.of(Accion.REGISTRAR_SALIDA)),
            new Puerta(HttpMethod.POST, "/documentos/salida-directa", EnumSet.of(Accion.REGISTRAR_SALIDA)),
            new Puerta(HttpMethod.POST, "/documentos/confirmar-acuse", EnumSet.of(Accion.REGISTRAR_SALIDA)),
            new Puerta(HttpMethod.POST, "/documentos/reemplazar-acuse", EnumSet.of(Accion.REGISTRAR_SALIDA)),
            new Puerta(HttpMethod.POST, "/documentos/ubicacion", EnumSet.of(Accion.GESTIONAR_ARCHIVO)),
            new Puerta(HttpMethod.POST, "/biblioratos/guardar", EnumSet.of(Accion.GESTIONAR_ARCHIVO)),
            new Puerta(HttpMethod.POST, "/documentos/forzar-cierre", EnumSet.of(Accion.FORZAR_CIERRE)),
            new Puerta(HttpMethod.GET, "/api/documentos/1/auditoria", EnumSet.of(Accion.VER_AUDITORIA)),
            new Puerta(HttpMethod.POST, "/documentos/derivar", EnumSet.of(Accion.DERIVAR, Accion.REGISTRAR_ACTUACION)),
            new Puerta(HttpMethod.POST, "/documentos/propio", EnumSet.of(Accion.REGISTRAR_ACTUACION)),
            new Puerta(HttpMethod.POST, "/documentos/devolver", EnumSet.of(Accion.REGISTRAR_ACTUACION)),
            new Puerta(HttpMethod.POST, "/documentos/actuaciones/guardar", EnumSet.of(Accion.REGISTRAR_ACTUACION)),
            new Puerta(HttpMethod.POST, "/documentos/actuaciones/anular-pase", EnumSet.of(Accion.REGISTRAR_ACTUACION)));

    @BeforeEach
    void preparar() {
        when(usuarioRepository.findByUsernameAndActivoTrue(any())).thenAnswer(inv -> {
            Usuario u = new Usuario();
            u.setId(PERFILES.keySet().stream().sorted().toList().indexOf((String) inv.getArgument(0)) + 1);
            u.setUsername(inv.getArgument(0));
            u.setActivo(true);
            return Optional.of(u);
        });
        when(politicaAcceso.autoridades(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            return PERFILES.getOrDefault(u.getUsername(), Set.of()).stream()
                    .map(a -> new SimpleGrantedAuthority(a.autoridad())).toList();
        });
    }

    private MockHttpServletRequestBuilder pedido(Puerta p, String perfil) {
        return request(p.metodo(), p.ruta()).with(user(perfil)).with(csrf());
    }

    // Con los servicios simulados, el controlador puede fallar: eso significa que la seguridad dejó pasar
    private int estado(MockHttpServletRequestBuilder pedido) {
        try {
            return mvc.perform(pedido).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            return 500;
        }
    }

    @Test
    void cadaPerfilEntraSoloDondeTienePermiso() throws Exception {
        List<String> errores = new ArrayList<>();
        for (Puerta p : PUERTAS) {
            for (var perfil : PERFILES.entrySet()) {
                boolean habilitado = perfil.getValue().stream().anyMatch(p.requiere()::contains);
                int estado = estado(pedido(p, perfil.getKey()));
                if (habilitado && estado == 403) {
                    errores.add(perfil.getKey() + " debería entrar a " + p.metodo() + " " + p.ruta() + " y recibió 403");
                }
                if (!habilitado && estado != 403) {
                    errores.add(perfil.getKey() + " NO debería entrar a " + p.metodo() + " " + p.ruta() + " y recibió " + estado);
                }
            }
        }
        assertTrue(errores.isEmpty(), String.join("\n", errores));
    }

    @Test
    void sinSesionSeVaAlInicioDeSesion() throws Exception {
        for (Puerta p : PUERTAS) {
            var r = mvc.perform(request(p.metodo(), p.ruta()).with(csrf())).andReturn().getResponse();
            assertEquals(302, r.getStatus(), p.ruta());
            assertTrue(String.valueOf(r.getRedirectedUrl()).endsWith("/login"), p.ruta());
        }
    }

    @Test
    void sinTokenCsrfNingunPostPasa() throws Exception {
        for (Puerta p : PUERTAS) {
            if (p.metodo() == HttpMethod.POST) {
                int estado = mvc.perform(request(p.metodo(), p.ruta()).with(user("mesa"))).andReturn().getResponse().getStatus();
                assertEquals(403, estado, "sin CSRF: " + p.ruta());
            }
        }
    }

    @Test
    void lasRespuestasLlevanLasCabecerasDeSeguridad() throws Exception {
        var r = mvc.perform(request(HttpMethod.GET, "/login")).andReturn().getResponse();
        String csp = r.getHeader("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("script-src 'self' https://cdn.jsdelivr.net"), csp);
        assertFalse(csp.contains("script-src 'self' 'unsafe-inline'"), "los scripts no admiten código en línea");
        assertTrue(csp.contains("object-src 'none'"));
        assertEquals("SAMEORIGIN", r.getHeader("X-Frame-Options"));
        assertEquals("nosniff", r.getHeader("X-Content-Type-Options"));
    }
}
