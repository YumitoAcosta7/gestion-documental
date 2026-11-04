package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Auditoría M7: el original de una dependencia sin nadie se traslada con motivo, acto y un segundo administrador
class TrasladoCustodiaTest {

    private final SolicitudTrasladoCustodiaRepository solicitudes = mock(SolicitudTrasladoCustodiaRepository.class);
    private final DocumentoRepository documentos = mock(DocumentoRepository.class);
    private final DependenciaRepository dependencias = mock(DependenciaRepository.class);
    private final ActuacionDocumentoRepository actuaciones = mock(ActuacionDocumentoRepository.class);
    private final PoliticaAcceso politica = mock(PoliticaAcceso.class);
    private final AuditoriaService auditoriaDocumento = mock(AuditoriaService.class);
    private final AuditoriaSistemaRepository auditoriaSistema = mock(AuditoriaSistemaRepository.class);
    private final TrasladoCustodiaService servicio = new TrasladoCustodiaService(solicitudes, documentos, dependencias, actuaciones,
            politica, auditoriaDocumento, auditoriaSistema);

    private static final String MOTIVO = "La unidad se disolvió por reorganización";
    private static final String ACTO = "Res. N° 12/2026";

    private final Dependencia disuelta = Dependencia.builder().id(90).nombre("Unidad disuelta").activo(false).build();
    private final Dependencia destino = Dependencia.builder().id(11).nombre("Mesa DTIC").activo(true).build();
    private final Usuario adminA = usuario(1, "admin.a");
    private final Usuario adminB = usuario(2, "admin.b");
    private Documento expediente;

    private static Usuario usuario(int id, String nombre) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(nombre);
        return u;
    }

    @BeforeEach
    void preparar() {
        expediente = new Documento();
        expediente.setId(300L);
        expediente.setCodigoCuti("DTIC-2026-000300");
        expediente.setEstadoActual(EstadoDocumento.EN_REVISION);
        expediente.setDependenciaCustodia(disuelta);
        expediente.setFechaHoraIngreso(LocalDateTime.now());
        when(documentos.findByIdParaActualizar(300L)).thenReturn(Optional.of(expediente));
        when(dependencias.findById(11)).thenReturn(Optional.of(destino));
        when(dependencias.findById(90)).thenReturn(Optional.of(disuelta));
        when(dependencias.findConReceptor(anyCollection())).thenAnswer(inv ->
                ((java.util.Collection<Integer>) inv.getArgument(0)).stream().filter(id -> id != 90).toList());
        when(solicitudes.save(any())).thenAnswer(inv -> {
            SolicitudTrasladoCustodia s = inv.getArgument(0);
            s.setId(40L);
            when(solicitudes.findById(40L)).thenReturn(Optional.of(s));
            when(solicitudes.documentoIdDe(40L)).thenReturn(Optional.of(300L));
            return s;
        });
    }

    private SolicitudTrasladoCustodia pedir() {
        return servicio.solicitar(300L, 11, MOTIVO, ACTO, adminA);
    }

    @Test
    void pedirNoMueveElOriginal() {
        SolicitudTrasladoCustodia s = pedir();
        assertEquals(SolicitudTrasladoCustodia.PENDIENTE, s.getEstado());
        assertSame(disuelta, s.getDependenciaOrigen());
        assertSame(disuelta, expediente.getDependenciaCustodia());
        verify(auditoriaDocumento).registrarEvento(same(expediente), eq("SOLICITUD_TRASLADO_CUSTODIA"), any(), any(), contains(ACTO), same(adminA), isNull());
        verify(auditoriaSistema).save(argThat(e -> "SOLICITUD_TRASLADO_CUSTODIA".equals(e.getAccion())));
    }

    @Test
    void otroAdministradorApruebaYElOriginalCambiaDeManos() {
        SolicitudTrasladoCustodia s = pedir();
        servicio.aprobar(s.getId(), adminB);
        assertEquals(SolicitudTrasladoCustodia.APROBADA, s.getEstado());
        assertSame(adminB, s.getResueltaPor());
        assertSame(destino, expediente.getDependenciaCustodia());
        verify(auditoriaDocumento).registrarEvento(same(expediente), eq("CAMBIO_CUSTODIA"), any(), any(), contains("Traslado administrativo"), same(adminB), isNull());
        verify(auditoriaSistema).save(argThat(e -> "APROBACION_TRASLADO_CUSTODIA".equals(e.getAccion())));
    }

    @Test
    void quienLoPidioNoLoAprueba() {
        SolicitudTrasladoCustodia s = pedir();
        assertThrows(IllegalStateException.class, () -> servicio.aprobar(s.getId(), adminA));
        assertSame(disuelta, expediente.getDependenciaCustodia());
        assertEquals(SolicitudTrasladoCustodia.PENDIENTE, s.getEstado());
    }

    @Test
    void unaSolicitudResueltaNoSeVuelveAResolver() {
        SolicitudTrasladoCustodia s = pedir();
        servicio.aprobar(s.getId(), adminB);
        assertThrows(IllegalStateException.class, () -> servicio.aprobar(s.getId(), adminB));
        assertThrows(IllegalStateException.class, () -> servicio.rechazar(s.getId(), "Ya no corresponde", adminB));
    }

    @Test
    void siLaDependenciaVolvioATenerPersonalYaNoSeAprueba() {
        SolicitudTrasladoCustodia s = pedir();
        when(dependencias.findConReceptor(anyCollection())).thenAnswer(inv -> List.copyOf((java.util.Collection<Integer>) inv.getArgument(0)));
        assertThrows(IllegalStateException.class, () -> servicio.aprobar(s.getId(), adminB));
        assertSame(disuelta, expediente.getDependenciaCustodia());
    }

    @Test
    void unaDependenciaQueFuncionaMueveLoSuyoConUnPase() {
        expediente.setDependenciaCustodia(destino);
        Dependencia otra = Dependencia.builder().id(12).nombre("Otra").activo(true).build();
        when(dependencias.findById(12)).thenReturn(Optional.of(otra));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> servicio.solicitar(300L, 12, MOTIVO, ACTO, adminA));
        assertTrue(e.getMessage().contains("pase"));
    }

    @Test
    void elDestinoDebeTenerQuienSeHagaCargo() {
        when(dependencias.findById(91)).thenReturn(Optional.of(Dependencia.builder().id(91).nombre("Sin nadie").build()));
        when(dependencias.findConReceptor(anyCollection())).thenReturn(List.of());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> servicio.solicitar(300L, 91, MOTIVO, ACTO, adminA));
        assertTrue(e.getMessage().contains("Nadie puede hacerse cargo"));
    }

    @Test
    void conElOriginalEnCaminoNoSeTraslada() {
        when(actuaciones.conOriginalEnCamino(anyCollection())).thenReturn(List.of(300L));
        IllegalStateException e = assertThrows(IllegalStateException.class, this::pedir);
        assertTrue(e.getMessage().contains("en camino"));
    }

    @Test
    void unExpedienteAnuladoNoTieneOriginalQueTrasladar() {
        expediente.setEstadoActual(EstadoDocumento.ANULADO);
        assertThrows(IllegalStateException.class, this::pedir);
    }

    @Test
    void unaSolaSolicitudPendientePorExpediente() {
        when(solicitudes.existePendiente(300L)).thenReturn(true);
        assertThrows(IllegalStateException.class, this::pedir);
    }

    @Test
    void motivoYActoSonObligatorios() {
        assertThrows(IllegalArgumentException.class, () -> servicio.solicitar(300L, 11, "corto", ACTO, adminA));
        assertThrows(IllegalArgumentException.class, () -> servicio.solicitar(300L, 11, MOTIVO, " ", adminA));
        assertThrows(IllegalArgumentException.class, () -> servicio.solicitar(300L, 11, MOTIVO, "x".repeat(151), adminA));
        verify(solicitudes, never()).save(any());
    }

    @Test
    void rechazarDejaElOriginalDondeEstaba() {
        SolicitudTrasladoCustodia s = pedir();
        assertThrows(IllegalArgumentException.class, () -> servicio.rechazar(s.getId(), "no", adminB));
        servicio.rechazar(s.getId(), "Falta la resolución firmada", adminB);
        assertEquals(SolicitudTrasladoCustodia.RECHAZADA, s.getEstado());
        assertSame(disuelta, expediente.getDependenciaCustodia());
        verify(auditoriaSistema).save(argThat(e -> "RECHAZO_TRASLADO_CUSTODIA".equals(e.getAccion()) && e.getDetalle().contains("resolución firmada")));
    }

    @Test
    void soloAdministra() {
        doThrow(new AccessDeniedException("No")).when(politica).exigir(any(), eq(Accion.ADMINISTRAR_SISTEMA), anyString());
        assertThrows(AccessDeniedException.class, this::pedir);
        assertThrows(AccessDeniedException.class, () -> servicio.aprobar(40L, adminB));
        assertThrows(AccessDeniedException.class, () -> servicio.custodiasSinReceptor(adminA));
    }

    @Test
    void elPanelMuestraSoloLasDependenciasSinNadie() {
        when(documentos.dependenciasConCustodia()).thenReturn(List.of(11, 90));
        when(documentos.enCustodiaDe(eq(List.of(90)), any())).thenReturn(List.of(expediente));
        when(documentos.contarEnCustodiaDe(List.of(90))).thenReturn(1L);
        TrasladoCustodiaService.AvisoCustodias aviso = servicio.custodiasSinReceptor(adminA);
        assertEquals(1, aviso.total());
        TrasladoCustodiaService.CustodiaSinReceptor fila = aviso.expedientes().get(0);
        assertEquals("DTIC-2026-000300", fila.cuti());
        assertTrue(fila.dependenciaInactiva());
        assertTrue(fila.trasladable());
    }

    @Test
    void elPanelNoOfreceLoQueNoSePuedeTrasladar() {
        when(documentos.dependenciasConCustodia()).thenReturn(List.of(90));
        when(documentos.enCustodiaDe(eq(List.of(90)), any())).thenReturn(List.of(expediente));
        when(documentos.contarEnCustodiaDe(List.of(90))).thenReturn(1L);
        when(actuaciones.conOriginalEnCamino(anyCollection())).thenReturn(List.of(300L));
        TrasladoCustodiaService.AvisoCustodias aviso = servicio.custodiasSinReceptor(adminA);
        assertTrue(aviso.trasladables().isEmpty());
        assertTrue(aviso.expedientes().get(0).originalEnCamino());
    }

    @Test
    void sinCustodiasTrabadasElPanelQuedaVacio() {
        when(documentos.dependenciasConCustodia()).thenReturn(List.of(11));
        assertEquals(TrasladoCustodiaService.AvisoCustodias.NINGUNO, servicio.custodiasSinReceptor(adminA));
    }
}
