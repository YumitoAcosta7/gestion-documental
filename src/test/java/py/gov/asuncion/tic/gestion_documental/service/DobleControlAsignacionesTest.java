package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

// Auditoría C2: las asignaciones sensibles no rigen hasta que las apruebe un segundo administrador,
// distinto de quien las pidió y del beneficiario.
class DobleControlAsignacionesTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AsignacionRepository asignaciones = mock(AsignacionRepository.class);
    private final DependenciaRepository dependencias = mock(DependenciaRepository.class);
    private final AuditoriaSistemaRepository auditoria = mock(AuditoriaSistemaRepository.class);
    private final AdministracionService servicio = new AdministracionService(usuarios, asignaciones, dependencias, auditoria,
            mock(PoliticaAcceso.class), new BCryptPasswordEncoder(4),
            new org.springframework.security.core.session.SessionRegistryImpl());

    private final Usuario adminA = usuario(1, "admin.a");
    private final Usuario adminB = usuario(2, "admin.b");
    private final Usuario ana = usuario(3, "ana.lopez");

    // Nivel 4 = Dirección (cabecera); nivel 7 = Unidad
    private final Dependencia dtic = Dependencia.builder().id(10).nombre("Dirección de TIC").ruta("/1/10/").nivelJerarquicoId(4).activo(true).build();
    private final Dependencia redes = Dependencia.builder().id(75).nombre("Unidad de Redes").ruta("/1/10/40/75/").nivelJerarquicoId(7).activo(true).build();

    private static Usuario usuario(int id, String username) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setNombreCompleto("Usuario " + username);
        u.setActivo(true);
        return u;
    }

    private static NivelJerarquico nivel(int id, int orden) {
        NivelJerarquico n = new NivelJerarquico();
        n.setId(id);
        n.setOrdenNivel(orden);
        return n;
    }

    @BeforeEach
    void preparar() {
        when(usuarios.findById(3)).thenReturn(Optional.of(ana));
        when(usuarios.findById(1)).thenReturn(Optional.of(adminA));
        when(usuarios.findById(2)).thenReturn(Optional.of(adminB));
        when(dependencias.findById(10)).thenReturn(Optional.of(dtic));
        when(dependencias.findById(75)).thenReturn(Optional.of(redes));
        when(dependencias.findNiveles()).thenReturn(List.of(nivel(4, 4), nivel(7, 7)));
        when(asignaciones.save(any(Asignacion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Asignacion pedir(RolAsignacion rol, Dependencia ambito, Usuario beneficiario, Usuario quien) {
        return servicio.crearAsignacion(beneficiario.getId(), rol, ambito.getId(), true, LocalDate.now(), null,
                false, null, "Res. N° 1/2026", quien);
    }

    @Test
    void lasSensiblesQuedanPendientesEInactivas() {
        Asignacion mesaDireccion = pedir(RolAsignacion.MESA_ENTRADA, dtic, ana, adminA);
        assertTrue(mesaDireccion.isPendiente());
        assertFalse(mesaDireccion.getActiva(), "pendiente: no da permisos");

        Asignacion admin = pedir(RolAsignacion.ADMIN_SISTEMA, redes, ana, adminA);
        assertTrue(admin.isPendiente(), "ADMIN_SISTEMA siempre, aunque el ámbito sea una unidad");

        verify(auditoria, times(2)).save(argThat(e -> "SOLICITUD_ASIGNACION".equals(e.getAccion())));
    }

    @Test
    void lasComunesRigenEnElActo() {
        Asignacion operador = pedir(RolAsignacion.OPERADOR, dtic, ana, adminA);
        assertFalse(operador.isPendiente());
        assertTrue(operador.getActiva());
        Asignacion mesaUnidad = pedir(RolAsignacion.RESPONSABLE, redes, ana, adminA);
        assertTrue(mesaUnidad.getActiva(), "responsable de una unidad: no es sensible");
    }

    @Test
    void quienLaPidioNoLaApruebaPeroOtroAdministradorSi() {
        Asignacion a = pedir(RolAsignacion.MESA_ENTRADA, dtic, ana, adminA);
        a.setId(40L);
        when(asignaciones.findById(40L)).thenReturn(Optional.of(a));

        assertThrows(IllegalStateException.class, () -> servicio.aprobarAsignacion(40L, adminA));
        assertTrue(a.isPendiente());

        servicio.aprobarAsignacion(40L, adminB);
        assertEquals(Asignacion.APROBADA, a.getEstadoAprobacion());
        assertTrue(a.getActiva());
        assertSame(adminB, a.getAprobadaPor());
        assertThrows(IllegalStateException.class, () -> servicio.aprobarAsignacion(40L, adminB), "ya resuelta");
    }

    @Test
    void nadieApruebaLoQueLoBeneficia() {
        // adminB pide que adminA sea mesa de la DTIC: adminA no puede aprobárselo a sí mismo
        Asignacion a = pedir(RolAsignacion.MESA_ENTRADA, dtic, adminA, adminB);
        a.setId(41L);
        when(asignaciones.findById(41L)).thenReturn(Optional.of(a));
        assertThrows(IllegalStateException.class, () -> servicio.aprobarAsignacion(41L, adminA));
        assertThrows(IllegalStateException.class, () -> servicio.rechazarAsignacion(41L, "No corresponde", adminA));
        assertTrue(a.isPendiente());
    }

    @Test
    void unaSolicitudSeRechazaConMotivoYNoDaPermisos() {
        Asignacion a = pedir(RolAsignacion.ADMIN_SISTEMA, dtic, ana, adminA);
        a.setId(42L);
        when(asignaciones.findById(42L)).thenReturn(Optional.of(a));
        assertThrows(IllegalArgumentException.class, () -> servicio.rechazarAsignacion(42L, "no", adminB));
        servicio.rechazarAsignacion(42L, "Sin resolución firmada", adminB);
        assertEquals(Asignacion.RECHAZADA, a.getEstadoAprobacion());
        assertFalse(a.getActiva());
        verify(auditoria).save(argThat(e -> "RECHAZO_ASIGNACION".equals(e.getAccion()) && e.getDetalle().contains("Sin resolución firmada")));
    }

    @Test
    void unaPendienteCuentaComoDuplicado() {
        Asignacion pendiente = pedir(RolAsignacion.MESA_ENTRADA, dtic, ana, adminA);
        when(asignaciones.findHistorial(3)).thenReturn(List.of(pendiente));
        assertThrows(IllegalStateException.class, () -> pedir(RolAsignacion.MESA_ENTRADA, dtic, ana, adminB));
    }
}
