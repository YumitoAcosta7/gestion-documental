package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.AsignacionRepository;
import py.gov.asuncion.tic.gestion_documental.repository.AuditoriaSistemaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdministracionServiceTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AsignacionRepository asignaciones = mock(AsignacionRepository.class);
    private final DependenciaRepository dependencias = mock(DependenciaRepository.class);
    private final AuditoriaSistemaRepository auditoria = mock(AuditoriaSistemaRepository.class);
    private final PoliticaAcceso politica = mock(PoliticaAcceso.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final org.springframework.security.core.session.SessionRegistry sesiones =
            new org.springframework.security.core.session.SessionRegistryImpl();
    private final AdministracionService servicio =
            new AdministracionService(usuarios, asignaciones, dependencias, auditoria, politica, encoder, sesiones);

    private final Usuario admin = usuario(1, "admin.tic");
    private final Dependencia baseDatos = Dependencia.builder().id(50).nombre("Departamento de Base de Datos").ruta("/1/10/50/").activo(true).build();
    private final Dependencia dataCenter = Dependencia.builder().id(77).nombre("Unidad de Data Center").ruta("/1/10/50/77/").activo(true).build();

    private static Usuario usuario(int id, String username) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setNombreCompleto("Usuario " + username);
        u.setActivo(true);
        return u;
    }

    @BeforeEach
    void preparar() {
        when(dependencias.findById(77)).thenReturn(Optional.of(dataCenter));
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(50);
            }
            return u;
        });
        when(asignaciones.save(any(Asignacion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sinPermisoDeAdministradorNoHaceNada() {
        doThrow(new AccessDeniedException("no")).when(politica).exigir(eq(admin), eq(Accion.ADMINISTRAR_SISTEMA), anyString());
        assertThrows(AccessDeniedException.class, () -> servicio.crearUsuario("ana.lopez", "Ana López", null, 77, admin));
        verify(usuarios, never()).save(any());
    }

    @Test
    void elAltaGeneraUnaContrasenaTemporalQueDebeCambiarse() {
        var r = servicio.crearUsuario("  Ana.Lopez ", "Ana   López", "ANA@municipio.gov.py", 77, admin);
        assertEquals("ana.lopez", r.usuario().getUsername());
        assertEquals("Ana López", r.usuario().getNombreCompleto());
        assertEquals("ana@municipio.gov.py", r.usuario().getEmail());
        assertTrue(r.usuario().getDebeCambiarContrasena());
        assertTrue(ReglasContrasena.cumple(r.contrasena()));
        assertTrue(encoder.matches(r.contrasena(), r.usuario().getPasswordHash()), "se guarda solo el hash");
        assertNotEquals(r.contrasena(), r.usuario().getPasswordHash());
        verify(auditoria).save(argThat(a -> "ALTA_USUARIO".equals(a.getAccion()) && !a.getDetalle().contains(r.contrasena())));
    }

    @Test
    void noSeRepiteNiSeAceptaUnUsuarioInvalido() {
        when(usuarios.existsByUsernameIgnoreCase("ana.lopez")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> servicio.crearUsuario("ana.lopez", "Ana López", null, 77, admin));
        assertThrows(IllegalArgumentException.class, () -> servicio.crearUsuario("1ana", "Ana López", null, 77, admin));
        assertThrows(IllegalArgumentException.class, () -> servicio.crearUsuario("ana lopez", "Ana López", null, 77, admin));
    }

    @Test
    void noSePuedeDarDeBajaAlUnicoAdministrador() {
        Usuario otro = usuario(2, "otro.admin");
        when(usuarios.findById(2)).thenReturn(Optional.of(otro));
        when(asignaciones.findVigentes(eq(2), any())).thenReturn(List.of(Asignacion.builder().rol(RolAsignacion.ADMIN_SISTEMA).build()));
        when(asignaciones.contarAdministradoresVigentes(any())).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> servicio.cambiarEstado(2, false, "Fin de contrato", admin));
        assertTrue(otro.getActivo());
    }

    @Test
    void elAdministradorNoSeAsignaRolesDeTramiteNiSeMudaDeDependencia() {
        when(usuarios.findById(1)).thenReturn(Optional.of(admin));
        admin.setDependencia(Dependencia.builder().id(10).nombre("DTIC").activo(true).build());
        for (RolAsignacion rol : new RolAsignacion[]{RolAsignacion.MESA_ENTRADA, RolAsignacion.RESPONSABLE,
                RolAsignacion.OPERADOR, RolAsignacion.CONSULTA}) {
            assertThrows(IllegalStateException.class, () -> servicio.crearAsignacion(1, rol, 77, true,
                    LocalDate.now(), null, false, null, "Res. prueba", admin), "autoasignación de " + rol);
        }
        assertThrows(IllegalStateException.class, () -> servicio.actualizarUsuario(1, admin.getNombreCompleto(), null, 77, admin));
        verify(asignaciones, never()).save(any());
    }

    @Test
    void nadieSeDaDeBajaASiMismo() {
        when(usuarios.findById(1)).thenReturn(Optional.of(admin));
        assertThrows(IllegalStateException.class, () -> servicio.cambiarEstado(1, false, "Prueba de baja", admin));
    }

    @Test
    void unaSuplenciaNecesitaFechaDeFinYTitular() {
        Usuario suplente = usuario(3, "suplente");
        when(usuarios.findById(3)).thenReturn(Optional.of(suplente));
        LocalDate hoy = LocalDate.now();
        assertThrows(IllegalArgumentException.class, () -> servicio.crearAsignacion(3, RolAsignacion.RESPONSABLE, 77, true,
                hoy, null, true, 1, "Res. 1/2026", admin), "sin fecha de fin");
        assertThrows(IllegalArgumentException.class, () -> servicio.crearAsignacion(3, RolAsignacion.RESPONSABLE, 77, true,
                hoy, hoy.plusDays(10), true, null, "Res. 1/2026", admin), "sin titular");
        when(usuarios.findById(1)).thenReturn(Optional.of(admin));
        // El titular es RESPONSABLE del Departamento de Base de Datos, con sus subordinadas y sin fecha de fin
        when(asignaciones.findHistorial(1)).thenReturn(List.of(Asignacion.builder().usuario(admin)
                .rol(RolAsignacion.RESPONSABLE).dependenciaAmbito(baseDatos).incluyeSubordinadas(true)
                .vigenteDesde(hoy.minusYears(1)).build()));
        Asignacion a = servicio.crearAsignacion(3, RolAsignacion.RESPONSABLE, 77, true, hoy, hoy.plusDays(10), true, 1, "Res. 1/2026", admin);
        assertTrue(a.getEsSuplencia());
        assertEquals(admin, a.getTitular());
        assertEquals(admin, a.getCreadaPor());
    }

    @Test
    void laSuplenciaNoDaMasDeLoQueTieneElTitular() {
        Usuario suplente = usuario(3, "suplente");
        when(usuarios.findById(3)).thenReturn(Optional.of(suplente));
        when(usuarios.findById(1)).thenReturn(Optional.of(admin));
        when(dependencias.findById(50)).thenReturn(Optional.of(baseDatos));
        LocalDate hoy = LocalDate.now();
        // El titular solo es RESPONSABLE de la Unidad de Data Center, hasta dentro de 5 días
        when(asignaciones.findHistorial(1)).thenReturn(List.of(Asignacion.builder().usuario(admin)
                .rol(RolAsignacion.RESPONSABLE).dependenciaAmbito(dataCenter).incluyeSubordinadas(true)
                .vigenteDesde(hoy.minusYears(1)).vigenteHasta(hoy.plusDays(5)).build()));

        assertThrows(IllegalStateException.class, () -> servicio.crearAsignacion(3, RolAsignacion.MESA_ENTRADA, 77, true,
                hoy, hoy.plusDays(3), true, 1, "Res. 1/2026", admin), "otro rol");
        assertThrows(IllegalStateException.class, () -> servicio.crearAsignacion(3, RolAsignacion.RESPONSABLE, 50, true,
                hoy, hoy.plusDays(3), true, 1, "Res. 1/2026", admin), "ámbito más amplio (el departamento)");
        assertThrows(IllegalStateException.class, () -> servicio.crearAsignacion(3, RolAsignacion.RESPONSABLE, 77, true,
                hoy, hoy.plusDays(10), true, 1, "Res. 1/2026", admin), "más allá de la vigencia del titular");
        assertDoesNotThrow(() -> servicio.crearAsignacion(3, RolAsignacion.RESPONSABLE, 77, true,
                hoy, hoy.plusDays(5), true, 1, "Res. 1/2026", admin));
    }

    @Test
    void noSeDuplicanAsignacionesNiSeCarganRetroactivas() {
        Usuario u = usuario(3, "operador");
        when(usuarios.findById(3)).thenReturn(Optional.of(u));
        LocalDate hoy = LocalDate.now();
        when(asignaciones.findHistorial(3)).thenReturn(List.of(Asignacion.builder().usuario(u)
                .rol(RolAsignacion.OPERADOR).dependenciaAmbito(dataCenter).vigenteDesde(hoy).vigenteHasta(hoy.plusDays(30)).build()));
        assertThrows(IllegalStateException.class, () -> servicio.crearAsignacion(3, RolAsignacion.OPERADOR, 77, false,
                hoy.plusDays(10), null, false, null, "Memo 2/2026", admin), "se superpone");
        assertThrows(IllegalArgumentException.class, () -> servicio.crearAsignacion(3, RolAsignacion.CONSULTA, 77, false,
                hoy.minusDays(1), null, false, null, "Memo 2/2026", admin), "retroactiva");
        assertDoesNotThrow(() -> servicio.crearAsignacion(3, RolAsignacion.OPERADOR, 77, false,
                hoy.plusDays(31), null, false, null, "Memo 3/2026", admin), "empieza cuando termina la anterior");
    }

    @Test
    void resetearLaContrasenaCierraLasSesionesAbiertas() {
        Usuario ana = usuario(6, "ana");
        when(usuarios.findById(6)).thenReturn(Optional.of(ana));
        var principal = org.springframework.security.core.userdetails.User.withUsername("ana").password("x").authorities(List.of()).build();
        sesiones.registerNewSession("sesion-de-ana", principal);
        servicio.restablecerContrasena(6, admin);
        assertTrue(sesiones.getSessionInformation("sesion-de-ana").isExpired());
    }

    @Test
    void noSeQuitaLaUltimaAsignacionDeAdministrador() {
        Asignacion unica = Asignacion.builder().id(9L).rol(RolAsignacion.ADMIN_SISTEMA).usuario(admin).dependenciaAmbito(dataCenter).build();
        when(asignaciones.findById(9L)).thenReturn(Optional.of(unica));
        when(asignaciones.contarAdministradoresVigentes(any())).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> servicio.finalizarAsignacion(9L, "Ya no corresponde", admin));
        assertTrue(unica.getActiva());
    }

    @Test
    void cambioDeContrasenaPropio() {
        Usuario u = usuario(4, "maria");
        u.setPasswordHash(encoder.encode("Temporal123x"));
        u.setDebeCambiarContrasena(true);
        when(usuarios.findById(4)).thenReturn(Optional.of(u));

        assertThrows(IllegalArgumentException.class, () -> servicio.cambiarContrasenaPropia(u, "incorrecta", "NuevaClave2026", "NuevaClave2026"));
        assertThrows(IllegalArgumentException.class, () -> servicio.cambiarContrasenaPropia(u, "Temporal123x", "corta1", "corta1"));
        assertThrows(IllegalArgumentException.class, () -> servicio.cambiarContrasenaPropia(u, "Temporal123x", "NuevaClave2026", "OtraClave2026"));

        servicio.cambiarContrasenaPropia(u, "Temporal123x", "NuevaClave2026", "NuevaClave2026");
        assertTrue(encoder.matches("NuevaClave2026", u.getPasswordHash()));
        assertFalse(u.getDebeCambiarContrasena());
    }

    @Test
    void reglasDeContrasena() {
        assertTrue(ReglasContrasena.cumple("abcdefghi1"));
        assertFalse(ReglasContrasena.cumple("abcdefghij"), "sin números");
        assertFalse(ReglasContrasena.cumple("1234567890"), "sin letras");
        assertFalse(ReglasContrasena.cumple("abc123"), "corta");
        for (int i = 0; i < 50; i++) {
            assertTrue(ReglasContrasena.cumple(AdministracionService.generarContrasenaTemporal()));
        }
    }
}
