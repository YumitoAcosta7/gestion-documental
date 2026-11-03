package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Auditoría M2: los pases que nadie puede recibir se avisan y el administrador puede retirarlos;
// los que tienen receptor los resuelven las dependencias.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasesTrabadosTest {

    @Mock ActuacionDocumentoRepository actuacionRepository;
    @Mock DocumentoRepository documentoRepository;
    @Mock DependenciaRepository dependenciaRepository;
    @Mock PoliticaAcceso politicaAcceso;
    @Mock AuditoriaService auditoriaService;
    @Mock AuditoriaSistemaRepository auditoriaSistemaRepository;
    @Mock CanalService canalService;
    @Mock FeriadoService feriadoService;
    @InjectMocks DocumentoService servicio;

    private final Usuario admin = new Usuario();
    private final Dependencia tic = Dependencia.builder().id(11).nombre("Mesa DTIC").activo(true).build();
    private final Dependencia sinUsuarios = Dependencia.builder().id(90).nombre("Unidad sin usuarios").activo(true).build();
    private final Dependencia dgaf = Dependencia.builder().id(101).nombre("Mesa DGAF").activo(true).build();
    private Documento expediente;

    @BeforeEach
    void preparar() {
        ReflectionTestUtils.setField(servicio, "slaDiasCritico", 10);
        admin.setId(1);
        expediente = new Documento();
        expediente.setId(300L);
        expediente.setCodigoCuti("DTIC-2026-000300");
        expediente.setEstadoActual(EstadoDocumento.EN_REVISION);
        when(documentoRepository.findByIdParaActualizar(300L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.findById(300L)).thenReturn(Optional.of(expediente));
        when(dependenciaRepository.findConReceptor(anyCollection())).thenAnswer(inv ->
                ((java.util.Collection<Integer>) inv.getArgument(0)).stream().filter(id -> id != 90).toList());
    }

    private ActuacionDocumento pase(long id, Dependencia receptora, int diasAtras) {
        ActuacionDocumento a = new ActuacionDocumento();
        a.setId(id);
        a.setDocumento(expediente);
        a.setTipoActuacion("PASE_INTERNO");
        a.setTipoPase(TipoPase.TRAMITE);
        a.setDependenciaEmisora(tic);
        a.setDependenciaReceptoraPase(receptora);
        a.setFechaHoraActuacion(LocalDateTime.now().minusDays(diasAtras));
        a.setPaseConfirmado(false);
        a.setAnulada(false);
        when(actuacionRepository.findByIdParaActualizar(id)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void elPanelMuestraLosTrabadosYLosFueraDePlazo() {
        List<ActuacionDocumento> pendientes = List.of(
                pase(1, sinUsuarios, 0),   // trabado aunque sea de hoy
                pase(2, dgaf, 30),         // fuera de plazo
                pase(3, dgaf, 0));         // en plazo y con receptor: no se muestra
        when(actuacionRepository.findPasesPendientes()).thenReturn(pendientes);
        var lista = servicio.pasesTrabados(admin);
        assertEquals(List.of(1L, 2L), lista.stream().map(DocumentoService.PaseDemorado::actuacionId).toList());
        assertTrue(lista.get(0).sinReceptor());
        assertFalse(lista.get(1).sinReceptor());
    }

    @Test
    void elAdministradorRetiraUnPaseQueNadiePuedeRecibir() {
        ActuacionDocumento a = pase(1, sinUsuarios, 3);
        servicio.retirarPaseTrabado(1L, "La unidad todavía no usa el sistema", admin);
        assertTrue(a.getAnulada());
        assertEquals("RETIRADO", a.getTipoAnulacion());
        verify(auditoriaService).registrarEvento(eq(expediente), eq("PASE_RETIRADO"), any(), any(), contains("administrador"), eq(admin), isNull());
        verify(auditoriaSistemaRepository).save(argThat(e -> "RETIRO_PASE_TRABADO".equals(e.getAccion())));
    }

    @Test
    void siLaReceptoraTieneQuienLoRecibaElAdministradorNoLoRetira() {
        ActuacionDocumento a = pase(2, dgaf, 30);
        assertThrows(IllegalStateException.class, () -> servicio.retirarPaseTrabado(2L, "Pase demorado hace mucho", admin));
        assertFalse(a.getAnulada());
        verify(auditoriaSistemaRepository, never()).save(any());
    }
}
