package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Gobernanza del expediente: quién escribe en él, respuestas cosidas a su pedido, nada se altera con el original
// en camino, un documento no se registra dos veces y el legajo de la salida solo admite informes del expediente.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GobernanzaExpedienteTest {

    @Mock DocumentoRepository documentoRepository;
    @Mock ActuacionDocumentoRepository actuacionRepository;
    @Mock DependenciaRepository dependenciaRepository;
    @Mock CanalService canalService;
    @Mock AuditoriaService auditoriaService;
    @Mock PoliticaAcceso politicaAcceso;
    @Mock LegajoSalidaRepository legajoSalidaRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock TipoDocumentoRepository tipoDocumentoRepository;
    @InjectMocks DocumentoService servicio;

    // Dirección de TIC (10) > Departamento Operativo (40); Dirección de Hacienda (20) pidió el informe
    private final Dependencia dtic = dep(10, "Dirección de TIC", "/1/10/");
    private final Dependencia operativo = dep(40, "Departamento Operativo", "/1/10/40/");
    private final Dependencia hacienda = dep(20, "Dirección de Hacienda", "/1/20/");
    private final Dependencia conocimiento = dep(30, "Dirección de Copia", "/1/30/");

    private final Usuario usuario = new Usuario();
    private Documento expediente;
    private final List<ActuacionDocumento> actuaciones = new ArrayList<>();

    private static Dependencia dep(int id, String nombre, String ruta) {
        return Dependencia.builder().id(id).nombre(nombre).ruta(ruta).activo(true).build();
    }

    @BeforeEach
    void preparar() {
        usuario.setId(7);
        expediente = new Documento();
        expediente.setId(500L);
        expediente.setCodigoCuti("DHAC-2026-000010");
        expediente.setEstadoActual(EstadoDocumento.EN_ESPERA_INFORME);
        expediente.setDependenciaCustodia(hacienda);
        when(documentoRepository.findById(500L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.findByIdParaActualizar(500L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.existeEnAmbito(anyInt(), eq(500L))).thenReturn(true);
        when(documentoRepository.save(any(Documento.class))).thenAnswer(inv -> inv.getArgument(0));
        when(actuacionRepository.findByDocumentoIdOrderByFechaHoraActuacionAsc(500L)).thenReturn(actuaciones);
        when(actuacionRepository.save(any())).thenAnswer(inv -> {
            ActuacionDocumento a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(1000L + actuaciones.size());
            }
            actuaciones.add(a);
            return a;
        });
        for (Dependencia d : List.of(dtic, operativo, hacienda, conocimiento)) {
            when(dependenciaRepository.findById(d.getId())).thenReturn(Optional.of(d));
        }
    }

    private ActuacionDocumento pedidoInforme(long id, Dependencia de, Dependencia a, int horasAtras) {
        ActuacionDocumento p = ActuacionDocumento.builder().id(id).documento(expediente).tipoActuacion("PASE_CONFIRMADO")
                .tipoPase(TipoPase.INFORME).requiereInforme(true).paseConfirmado(true).anulada(false)
                .dependenciaEmisora(de).dependenciaReceptoraPase(a)
                .fechaHoraActuacion(LocalDateTime.now().minusHours(horasAtras)).build();
        actuaciones.add(p);
        return p;
    }

    // Las respuestas del expediente, como las pasa el servicio a informeRespondido
    private List<ActuacionDocumento> respuestas() {
        return actuaciones.stream().filter(a -> "INFORME_TECNICO".equals(a.getTipoActuacion()) || "MEMO".equals(a.getTipoActuacion())).toList();
    }

    private ActuacionDocumento registrar(Dependencia emisora, String tipo, String numero) {
        return servicio.registrarActuacion(500L, emisora.getId(), tipo, numero, "Respuesta de " + emisora.getNombre(),
                null, null, usuario);
    }

    @Test
    void unaCopiaParaConocimientoNoHabilitaAEscribirEnElExpediente() {
        when(canalService.puedeActuarPor(eq(usuario), any())).thenReturn(false); // no tiene el original
        assertThrows(AccessDeniedException.class, () -> registrar(conocimiento, "MEMO", "1/2026"));
        verify(actuacionRepository, never()).save(any());
    }

    @Test
    void conElOriginalEnCaminoNiSiquieraQuienLoTieneAgregaActuaciones() {
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true);
        when(actuacionRepository.existePaseTramitePendiente(500L)).thenReturn(true);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> registrar(hacienda, "PROVIDENCIA", "2/2026"));
        assertTrue(e.getMessage().contains("en camino"));
    }

    @Test
    void elDictamenDelDepartamentoNoCierraElPedidoDeLaDireccion() {
        ActuacionDocumento pedidoHaciendaATic = pedidoInforme(1L, hacienda, dtic, 5);
        ActuacionDocumento pedidoTicAlDepartamento = pedidoInforme(2L, dtic, operativo, 3);

        // El departamento responde: queda cosido al pedido que le hizo la Dirección
        ActuacionDocumento dictamen = registrar(operativo, "INFORME_TECNICO", "10/2026");
        assertSame(pedidoTicAlDepartamento, dictamen.getActuacionPadre());
        assertTrue(DocumentoService.informeRespondido(pedidoTicAlDepartamento, respuestas()));
        assertFalse(DocumentoService.informeRespondido(pedidoHaciendaATic, respuestas()),
                "el pedido de Hacienda sigue abierto hasta el memo consolidado de la Dirección");

        // La Dirección consolida y responde a Hacienda
        ActuacionDocumento memo = registrar(dtic, "MEMO", "11/2026");
        assertSame(pedidoHaciendaATic, memo.getActuacionPadre());
        assertTrue(DocumentoService.informeRespondido(pedidoHaciendaATic, respuestas()));
        assertEquals(EstadoDocumento.PENDIENTE_SALIDA, expediente.getEstadoActual(), "con todos los pedidos respondidos");
        verify(auditoriaService).registrarEvento(eq(expediente), eq("REGISTRO_ACTUACION"), any(), any(),
                contains("en respuesta al pedido de informe de Dirección de Hacienda"), eq(usuario), isNull());
    }

    @Test
    void elMismoDocumentoNoSeRegistraDosVeces() {
        Documento existente = new Documento();
        existente.setCodigoCuti("DTIC-2026-000003");
        existente.setNumeroDocumentoOrigen("45/2026");
        when(documentoRepository.findFirstByDependenciaOrigenIdAndNumeroDocumentoOrigen(20, "45/2026"))
                .thenReturn(Optional.of(existente));
        Documento nuevo = new Documento();
        nuevo.setDependenciaOrigen(hacienda);
        nuevo.setNumeroDocumentoOrigen(" 45/2026 ");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> servicio.guardarNuevoDocumento(nuevo, usuario));
        assertTrue(e.getMessage().contains("DTIC-2026-000003"));
        verify(documentoRepository, never()).save(any());
    }

    // Hacienda ya tiene un documento registrado con ese número
    private void yaRegistrado(String numero) {
        Documento existente = new Documento();
        existente.setCodigoCuti("DHAC-2026-000003");
        existente.setNumeroDocumentoOrigen(numero);
        when(documentoRepository.findFirstByDependenciaOrigenIdAndNumeroDocumentoOrigen(20, numero))
                .thenReturn(Optional.of(existente));
    }

    private Documento registrarDeHacienda(String numero, String cuti) {
        Documento nuevo = new Documento();
        nuevo.setDependenciaOrigen(hacienda);
        nuevo.setDependenciaRegistro(hacienda);
        nuevo.setCodigoCuti(cuti);
        nuevo.setNumeroDocumentoOrigen(numero);
        return servicio.guardarNuevoDocumento(nuevo, usuario);
    }

    private Documento iniciarPropioDeHacienda(String numero) {
        TipoDocumento tipo = new TipoDocumento();
        tipo.setActivo(true);
        when(tipoDocumentoRepository.findById(1)).thenReturn(Optional.of(tipo));
        when(canalService.dependenciaDeRegistro(usuario)).thenReturn(hacienda);
        when(canalService.organigrama()).thenReturn(new Organigrama(List.of(
                new Organigrama.Nodo(1, "Intendencia", "INT", null, 1, true, false),
                new Organigrama.Nodo(20, "Dirección de Hacienda", "DHAC", 1, 4, true, false)), Map.of()));
        usuario.setDependencia(hacienda);
        return servicio.iniciarDocumentoPropio(1, numero, "Asunto", null, null, false, null, usuario);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"S/N", "s/n", " S/N ", "s / n", "   "})
    void dosDocumentosSinNumeroDeLaMismaDependenciaNoSonElMismoDocumento(String numero) {
        yaRegistrado("S/N");
        Documento primero = registrarDeHacienda(numero, "DHAC-2026-000004");
        Documento segundo = registrarDeHacienda(numero, "DHAC-2026-000005");
        assertEquals("S/N", primero.getNumeroDocumentoOrigen());
        assertEquals("S/N", segundo.getNumeroDocumentoOrigen());
        verify(documentoRepository, times(2)).save(any());
        verify(documentoRepository, never()).findFirstByDependenciaOrigenIdAndNumeroDocumentoOrigen(any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"S/N", "s/n", " s / n "})
    void unDocumentoPropioSinNumeroNoChocaConOtroSinNumero(String numero) {
        yaRegistrado("S/N");
        Documento propio = iniciarPropioDeHacienda(numero);
        assertEquals("S/N", propio.getNumeroDocumentoOrigen());
        verify(documentoRepository).save(any());
    }

    @Test
    void unDocumentoPropioConNumeroRepetidoSeSigueRechazando() {
        yaRegistrado("45/2026");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> iniciarPropioDeHacienda("45/2026"));
        assertTrue(e.getMessage().contains("DHAC-2026-000003"));
        verify(documentoRepository, never()).save(any());
    }

    @Test
    void elMismoNumeroEnOtraDependenciaNoEsElMismoDocumento() {
        yaRegistrado("45/2026");
        Documento nuevo = new Documento();
        nuevo.setDependenciaOrigen(dtic);
        nuevo.setDependenciaRegistro(dtic);
        nuevo.setCodigoCuti("DTIC-2026-000009");
        nuevo.setNumeroDocumentoOrigen("45/2026");
        assertEquals("45/2026", servicio.guardarNuevoDocumento(nuevo, usuario).getNumeroDocumentoOrigen());
    }

    @Test
    void unNumeroMalEscritoNoPasaPorSinNumero() {
        assertThrows(IllegalArgumentException.class, () -> registrarDeHacienda("SN", "DHAC-2026-000004"));
        assertThrows(IllegalArgumentException.class, () -> registrarDeHacienda("S/N/2026", "DHAC-2026-000004"));
        verify(documentoRepository, never()).save(any());
    }

    @Test
    void elLegajoDeLaSalidaSoloAdmiteInformesDelExpediente() {
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true);
        expediente.setEstadoActual(EstadoDocumento.PENDIENTE_SALIDA);
        assertThrows(IllegalArgumentException.class, () -> servicio.registrarSalidaDocumento(500L, "5/2026", null, "x", "y",
                null, "DIRECTO_INFORME", List.of(), usuario), "remisión directa sin informes");
        assertThrows(IllegalArgumentException.class, () -> servicio.registrarSalidaDocumento(500L, "5/2026", null, "x", "y",
                null, "DIRECTO_INFORME", List.of(999L), usuario), "un informe de otro expediente");
        verify(documentoRepository, never()).save(any());
    }
}
