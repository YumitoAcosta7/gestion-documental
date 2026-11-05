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
import py.gov.asuncion.tic.gestion_documental.repository.AdjuntoDocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// El acuse prueba la entrega y mueve la custodia: lo da quien recibe, no quien envía.
// Caso observado en pruebas: DTIC-2026-000003, la mesa de DTIC confirmó su propio pase a la DGAF.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeparacionFuncionesPaseTest {

    @Mock DocumentoRepository documentoRepository;
    @Mock ActuacionDocumentoRepository actuacionRepository;
    @Mock AdjuntoDocumentoRepository adjuntoRepository;
    @Mock ArchivoService archivoService;
    @Mock AuditoriaService auditoriaService;
    @Mock PoliticaAcceso politicaAcceso;
    @Mock CanalService canalService;
    @InjectMocks DocumentoService servicio;

    private final Dependencia dtic = Dependencia.builder().id(11).nombre("Mesa DTIC").esMesaEntrada(true).build();
    private final Dependencia dgaf = Dependencia.builder().id(20).nombre("Mesa DGAF").esMesaEntrada(true).build();
    private final Usuario mesaDtic = new Usuario();
    private final Usuario mesaDgaf = new Usuario();
    private Documento expediente;
    private ActuacionDocumento pase;

    @BeforeEach
    void preparar() {
        mesaDtic.setId(1);
        mesaDtic.setDependencia(dtic);
        mesaDgaf.setId(2);
        mesaDgaf.setDependencia(dgaf);

        expediente = new Documento();
        expediente.setId(100L);
        expediente.setCodigoCuti("DTIC-2026-000003");
        expediente.setDependenciaCustodia(dtic);
        expediente.setEstadoActual(EstadoDocumento.INGRESADO);

        pase = ActuacionDocumento.builder()
                .id(5L).documento(expediente).tipoActuacion("PASE_INTERNO").tipoPase(TipoPase.TRAMITE)
                .paseConfirmado(false).anulada(false)
                .dependenciaEmisora(dtic).dependenciaReceptoraPase(dgaf).build();

        when(actuacionRepository.findByIdParaActualizar(5L)).thenReturn(Optional.of(pase));
        when(actuacionRepository.documentoIdDe(5L)).thenReturn(Optional.of(100L));
        when(actuacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(actuacionRepository.findByDocumentoIdOrderByFechaHoraActuacionAsc(100L)).thenReturn(List.of(pase));
        when(documentoRepository.findById(100L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.findByIdParaActualizar(100L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.existeEnAmbito(anyInt(), eq(100L))).thenReturn(true);
        when(archivoService.guardarPdf(any(), any(), any())).thenReturn(
                new ArchivoService.ArchivoGuardado(Path.of("acuse.pdf"), "hash", 10L, "application/pdf"));
        when(adjuntoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Las dos mesas tienen todos los permisos de su rol, SUPERVISAR_PASES incluido
        when(politicaAcceso.puede(any(), any())).thenReturn(true);
        when(canalService.puedeActuarPor(eq(mesaDtic), any())).thenAnswer(i -> dtic.equals(i.getArgument(1)));
        when(canalService.puedeActuarPor(eq(mesaDgaf), any())).thenAnswer(i -> dgaf.equals(i.getArgument(1)));
    }

    private static MockMultipartFile acuse() {
        return new MockMultipartFile("a", "a.pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes());
    }

    @Test
    void laMesaQueEnviaNoConfirmaSuPropioPaseNiMueveLaCustodia() {
        assertFalse(servicio.puedeConfirmarPase(pase, mesaDtic));
        assertThrows(AccessDeniedException.class,
                () -> servicio.confirmarRecepcionPaseInterno(5L, acuse(), mesaDtic));

        assertEquals("PASE_INTERNO", pase.getTipoActuacion());
        assertFalse(pase.getPaseConfirmado());
        assertEquals(dtic, expediente.getDependenciaCustodia(), "el original sigue en la emisora");
        verifyNoInteractions(archivoService);
    }

    @Test
    void laMesaReceptoraConfirmaYRecibeElOriginal() {
        assertTrue(servicio.puedeConfirmarPase(pase, mesaDgaf));

        servicio.confirmarRecepcionPaseInterno(5L, acuse(), mesaDgaf);

        assertEquals("PASE_CONFIRMADO", pase.getTipoActuacion());
        assertTrue(pase.getPaseConfirmado());
        assertEquals(dgaf, expediente.getDependenciaCustodia());
    }

    @Test
    void laMesaDeLaDireccionRecibePorSuSubordinadaAunqueNoSeaSuDependencia() {
        Usuario mesaSuperior = new Usuario();
        mesaSuperior.setId(3);
        mesaSuperior.setDependencia(Dependencia.builder().id(30).nombre("Otra mesa").build());
        when(canalService.puedeActuarPor(mesaSuperior, dgaf)).thenReturn(true);

        assertTrue(servicio.puedeConfirmarPase(pase, mesaSuperior));
    }

    @Test
    void quienEnvioNoConfirmaAunqueSuRolAbarqueALaReceptora() {
        // Caso observado en pruebas: la mesa de la DTIC derivó a su Departamento Operativo y veía "Confirmar recepción"
        Usuario mesaConSubordinadas = new Usuario();
        mesaConSubordinadas.setId(3);
        mesaConSubordinadas.setDependencia(dtic);
        when(canalService.puedeActuarPor(mesaConSubordinadas, dgaf)).thenReturn(true);
        pase.setUsuario(mesaConSubordinadas);

        assertFalse(servicio.puedeConfirmarPase(pase, mesaConSubordinadas));
        assertFalse(servicio.puedeRechazarPase(pase, mesaConSubordinadas));
        AccessDeniedException e = assertThrows(AccessDeniedException.class,
                () -> servicio.confirmarRecepcionPaseInterno(5L, acuse(), mesaConSubordinadas));
        assertTrue(e.getMessage().contains("envió usted"));
        assertFalse(pase.getPaseConfirmado());
        assertEquals(dtic, expediente.getDependenciaCustodia());
        verifyNoInteractions(archivoService);
    }

    @Test
    void otraPersonaDeLaMesaSiRecibePorLaSubordinada() {
        Usuario quienEnvia = new Usuario();
        quienEnvia.setId(3);
        Usuario otraPersona = new Usuario();
        otraPersona.setId(4);
        otraPersona.setDependencia(dtic);
        when(canalService.puedeActuarPor(otraPersona, dgaf)).thenReturn(true);
        pase.setUsuario(quienEnvia);

        assertTrue(servicio.puedeConfirmarPase(pase, otraPersona));
    }

    @Test
    void elEmisorConservaElRetiroYNoRechazaPorLaReceptora() {
        assertTrue(servicio.puedeRetirarPase(pase, mesaDtic));
        assertFalse(servicio.puedeRechazarPase(pase, mesaDtic));
    }

    @Test
    void unPaseYaConfirmadoOAnuladoNoSeOfreceParaConfirmar() {
        pase.setPaseConfirmado(true);
        assertFalse(servicio.puedeConfirmarPase(pase, mesaDgaf));

        pase.setPaseConfirmado(false);
        pase.setAnulada(true);
        assertFalse(servicio.puedeConfirmarPase(pase, mesaDgaf));
    }

    @Test
    void elAcuseConfirmadoSoloLoReemplazaLaReceptora() {
        pase.setTipoActuacion("PASE_CONFIRMADO");
        pase.setPaseConfirmado(true);
        pase.setRutaCopiaSelladaPdf("acuse.pdf");

        assertFalse(servicio.puedeReemplazarPdf(pase, mesaDtic), "la mesa emisora no toca la prueba de la entrega");
        assertTrue(servicio.puedeReemplazarPdf(pase, mesaDgaf));
    }

    @Test
    void laMesaSigueReemplazandoLosPdfDeOtrasActuacionesDeSuAmbito() {
        ActuacionDocumento informe = ActuacionDocumento.builder()
                .id(6L).documento(expediente).tipoActuacion("INFORME_TECNICO").anulada(false)
                .dependenciaEmisora(dtic).rutaCopiaSelladaPdf("informe.pdf").build();
        assertTrue(servicio.puedeReemplazarPdf(informe, mesaDtic));

        // El PDF de un pase todavía pendiente es del emisor: no es un acuse
        pase.setRutaCopiaSelladaPdf("pase.pdf");
        assertTrue(servicio.puedeReemplazarPdf(pase, mesaDtic));
    }
}
