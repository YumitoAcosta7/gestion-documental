package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.ActuacionDocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Auditoría C1: las operaciones sobre el original exigen acceso al expediente y actuar por quien lo tiene,
// y la supervisión de pases vale solo dentro del ámbito
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ControlAccesoExpedienteTest {

    @Mock DocumentoRepository documentoRepository;
    @Mock ActuacionDocumentoRepository actuacionRepository;
    @Mock PoliticaAcceso politicaAcceso;
    @Mock CanalService canalService;
    @InjectMocks DocumentoService servicio;

    private final Dependencia hacienda = Dependencia.builder().id(141).nombre("Unidad de Secretaría Administrativa").esMesaEntrada(true).build();
    private final Dependencia tic = Dependencia.builder().id(11).nombre("Unidad de Secretaría Administrativa").esMesaEntrada(true).build();
    private final Usuario mesaTic = new Usuario();
    private Documento expediente;

    @BeforeEach
    void preparar() {
        mesaTic.setId(7);
        mesaTic.setDependencia(tic);
        expediente = new Documento();
        expediente.setId(100L);
        expediente.setDependenciaCustodia(hacienda);
        expediente.setEstadoActual(EstadoDocumento.PENDIENTE_SALIDA);
        expediente.setFechaHoraSalida(java.time.LocalDateTime.now());
        when(documentoRepository.findById(100L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.findByIdParaActualizar(100L)).thenReturn(Optional.of(expediente));
        when(politicaAcceso.puede(any(), any())).thenReturn(true);   // tiene todos los permisos de su rol
    }

    private void fueraDelAmbito() {
        when(documentoRepository.existeEnAmbito(7, 100L)).thenReturn(false);
        when(actuacionRepository.existeParticipacionDependencia(100L, 11)).thenReturn(false);
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("a", "a.pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes());
    }

    @Test
    void laMesaDeOtraDireccionNoFinalizaUnExpedienteAjenoPorId() {
        fueraDelAmbito();
        assertThrows(AccessDeniedException.class, () -> servicio.confirmarEntregaYAcuse(100L, pdf(), mesaTic));
        assertEquals(EstadoDocumento.PENDIENTE_SALIDA, expediente.getEstadoActual());
        verify(documentoRepository, never()).save(any());
    }

    @Test
    void noSeRegistraLaSalidaNiSeFuerzaElCierreDeUnExpedienteAjeno() {
        fueraDelAmbito();
        assertThrows(AccessDeniedException.class,
                () -> servicio.registrarSalidaDocumento(100L, "Nota 1/2026", null, "x", "y", null, mesaTic));
        assertThrows(AccessDeniedException.class,
                () -> servicio.forzarCierreInformes(100L, "Justificación suficientemente larga", mesaTic));
        assertThrows(AccessDeniedException.class,
                () -> servicio.cambiarUbicacion(100L, 1, 1, 2, null, mesaTic));
        verify(documentoRepository, never()).save(any());
    }

    @Test
    void conAccesoPeroSinElOriginalTampoco() {
        when(documentoRepository.existeEnAmbito(7, 100L)).thenReturn(true);   // lo ve, porque pasó por su área
        when(canalService.puedeActuarPor(mesaTic, hacienda)).thenReturn(false); // pero el original está en Hacienda
        AccessDeniedException e = assertThrows(AccessDeniedException.class,
                () -> servicio.confirmarEntregaYAcuse(100L, pdf(), mesaTic));
        assertTrue(e.getMessage().contains("tiene el original"));
    }

    @Test
    void laSupervisionDePasesNoAlcanzaPasesDeOtroAmbito() {
        ActuacionDocumento pase = ActuacionDocumento.builder()
                .id(5L).documento(expediente).tipoActuacion("PASE_INTERNO").paseConfirmado(false).anulada(false)
                .dependenciaEmisora(hacienda).dependenciaReceptoraPase(hacienda)
                .rutaCopiaSelladaPdf("x.pdf").build();
        when(canalService.puedeActuarPor(eq(mesaTic), any())).thenReturn(false);
        assertFalse(servicio.puedeRetirarPase(pase, mesaTic), "SUPERVISAR_PASES ya no es global");
        assertFalse(servicio.puedeRechazarPase(pase, mesaTic));
        assertFalse(servicio.puedeReemplazarPdf(pase, mesaTic));

        when(canalService.puedeActuarPor(mesaTic, hacienda)).thenReturn(true);   // si fuera su ámbito, sí
        assertTrue(servicio.puedeRetirarPase(pase, mesaTic));
    }
}
