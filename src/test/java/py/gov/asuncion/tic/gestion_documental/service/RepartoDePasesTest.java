package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.ActuacionDocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Quien recibió una copia o un pedido de informe, sin tener el original, lo baja de a un nivel.
// La DTIC tiene el original y le pide un informe a la DGAF, que lo reparte a sus direcciones.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepartoDePasesTest {

    static final int DTIC_SA = 11, DGAF_SA = 101, DREC = 110, DHAC = 140, CONTAB = 145, UOC = 102;

    @Mock DocumentoRepository documentoRepository;
    @Mock ActuacionDocumentoRepository actuacionRepository;
    @Mock DependenciaRepository dependenciaRepository;
    @Mock PoliticaAcceso politicaAcceso;
    @Mock CanalService canalService;
    @Mock AuditoriaService auditoriaService;
    @InjectMocks DocumentoService servicio;

    private final Organigrama org = new Organigrama(List.of(
            new Organigrama.Nodo(1, "Intendencia", "INT", null, 1, true, false),
            new Organigrama.Nodo(10, "Dirección de TIC", "DTIC", 1, 4, true, false),
            new Organigrama.Nodo(DTIC_SA, "Unidad de Secretaría Administrativa", "DTIC-SA", 10, 7, true, true),
            new Organigrama.Nodo(100, "Dirección General de Administración", "DGAF", 1, 2, true, false),
            new Organigrama.Nodo(DGAF_SA, "Unidad de Secretaría Administrativa", "DGAF-SA", 100, 7, true, true),
            new Organigrama.Nodo(UOC, "Unidad Revisora", "DGAF-U01", 100, 7, true, false),
            new Organigrama.Nodo(DREC, "Dirección de Recaudaciones", "DREC", 100, 4, true, false),
            new Organigrama.Nodo(DHAC, "Dirección de Hacienda", "DHAC", 100, 4, true, false),
            new Organigrama.Nodo(CONTAB, "Departamento de Contabilidad", "DHAC-D01", DHAC, 6, true, false)), Map.of());

    private final Dependencia dtic = dep(DTIC_SA, "Mesa DTIC");
    private final Dependencia dgaf = dep(DGAF_SA, "Mesa DGAF");
    private final Dependencia hacienda = dep(DHAC, "Dirección de Hacienda");
    private final Usuario mesaDgaf = new Usuario();
    private final List<ActuacionDocumento> actuaciones = new ArrayList<>();
    private Documento expediente;
    private long proximoId = 1000;

    private static Dependencia dep(int id, String nombre) {
        return Dependencia.builder().id(id).nombre(nombre).activo(true).build();
    }

    @BeforeEach
    void preparar() {
        ReflectionTestUtils.setField(servicio, "exigirReceptor", false);
        mesaDgaf.setId(5);
        mesaDgaf.setDependencia(dgaf);
        expediente = new Documento();
        expediente.setId(300L);
        expediente.setCodigoCuti("DTIC-2026-000300");
        expediente.setDependenciaCustodia(dtic);
        expediente.setEstadoActual(EstadoDocumento.EN_ESPERA_INFORME);
        when(documentoRepository.findById(300L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.findByIdParaActualizar(300L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentoRepository.existeEnAmbito(5, 300L)).thenReturn(true);
        when(canalService.organigrama()).thenReturn(org);
        when(canalService.puedeActuarPor(eq(mesaDgaf), any())).thenAnswer(inv -> {
            Dependencia d = inv.getArgument(1);
            return d != null && d.getId() != DTIC_SA;
        });
        when(politicaAcceso.puede(mesaDgaf, Accion.DERIVAR)).thenReturn(true);
        when(actuacionRepository.findByDocumentoIdOrderByFechaHoraActuacionAsc(300L)).thenAnswer(inv -> new ArrayList<>(actuaciones));
        when(actuacionRepository.documentoIdDe(anyLong())).thenReturn(Optional.of(300L));
        when(actuacionRepository.save(any())).thenAnswer(inv -> {
            ActuacionDocumento a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(proximoId++);
                actuaciones.add(a);
            }
            return a;
        });
        when(dependenciaRepository.findById(anyInt())).thenAnswer(inv -> Optional.of(dep(inv.getArgument(0), "Dep " + inv.getArgument(0))));
    }

    private ActuacionDocumento pase(long id, Dependencia de, Dependencia a, TipoPase tipo, boolean confirmado, ActuacionDocumento padre) {
        ActuacionDocumento p = ActuacionDocumento.builder().id(id).documento(expediente).dependenciaEmisora(de).dependenciaReceptoraPase(a)
                .tipoActuacion(confirmado ? "PASE_CONFIRMADO" : "PASE_INTERNO").tipoPase(tipo).requiereInforme(tipo == TipoPase.INFORME)
                .paseConfirmado(confirmado).anulada(false).actuacionPadre(padre)
                .fechaHoraActuacion(LocalDateTime.now().minusHours(5)).build();
        actuaciones.add(p);
        when(actuacionRepository.findByIdParaActualizar(id)).thenReturn(Optional.of(p));
        when(actuacionRepository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private ActuacionDocumento pedidoRecibido() {
        return pase(1L, dtic, dgaf, TipoPase.INFORME, true, null);
    }

    @Test
    void unaDireccionGeneralRepartePedidoATodasSusDireccionesSinMoverElOriginal() {
        ActuacionDocumento recibido = pedidoRecibido();

        servicio.repartirPase(1L, List.of(DREC, DHAC), "Informen lo que corresponda", TipoPase.INFORME, mesaDgaf);

        List<ActuacionDocumento> repartidos = actuaciones.stream().filter(a -> a != recibido).toList();
        assertEquals(2, repartidos.size());
        assertTrue(repartidos.stream().allMatch(a -> a.getActuacionPadre() == recibido && a.getTipoPase() == TipoPase.INFORME
                && a.getDependenciaEmisora() == dgaf && Boolean.TRUE.equals(a.getRequiereInforme())));
        assertEquals(DTIC_SA, expediente.getDependenciaCustodia().getId(), "el original no se mueve");
        assertEquals(EstadoDocumento.EN_ESPERA_INFORME, expediente.getEstadoActual());
        verify(auditoriaService).registrarEvento(eq(expediente), eq("REPARTO_PASE"), any(), any(), contains("sin mover el original"), eq(mesaDgaf), isNull());
    }

    @Test
    void lasRespuestasDeLasAreasNoCierranElPedidoDeQuienReparte() {
        ActuacionDocumento recibido = pedidoRecibido();
        ActuacionDocumento interno = pase(2L, dgaf, hacienda, TipoPase.INFORME, true, recibido);
        actuaciones.add(ActuacionDocumento.builder().id(3L).documento(expediente).tipoActuacion("MEMO").dependenciaEmisora(hacienda)
                .actuacionPadre(interno).fechaHoraActuacion(LocalDateTime.now()).anulada(false).build());

        assertEquals(List.of(recibido), DocumentoService.pedidosAbiertos(actuaciones), "falta la respuesta consolidada");
    }

    @Test
    void unPedidoInternoSinCerrarSigueAbierto() {
        ActuacionDocumento recibido = pedidoRecibido();
        ActuacionDocumento interno = pase(2L, dgaf, hacienda, TipoPase.INFORME, true, recibido);
        actuaciones.add(ActuacionDocumento.builder().id(3L).documento(expediente).tipoActuacion("MEMO").dependenciaEmisora(dgaf)
                .actuacionPadre(recibido).fechaHoraActuacion(LocalDateTime.now()).anulada(false).build());

        assertEquals(List.of(interno), DocumentoService.pedidosAbiertos(actuaciones), "sin cerrar, el interno seguiría abierto");
    }

    @Test
    void alResponderSeCierranLosPedidosInternosEnCualquierNivel() {
        ActuacionDocumento recibido = pedidoRecibido();
        ActuacionDocumento direccion = pase(2L, dgaf, hacienda, TipoPase.INFORME, true, recibido);
        ActuacionDocumento departamento = pase(3L, hacienda, dep(CONTAB, "Contabilidad"), TipoPase.INFORME, false, direccion);
        ActuacionDocumento otraDireccion = pase(4L, dgaf, dep(DREC, "Recaudaciones"), TipoPase.CONOCIMIENTO, true, recibido);
        when(dependenciaRepository.findById(DGAF_SA)).thenReturn(Optional.of(dgaf));

        servicio.registrarActuacion(300L, DGAF_SA, "MEMO", "12/2026", "Respuesta consolidada", null, null, 1L, mesaDgaf);

        assertFalse(direccion.getRequiereInforme());
        assertFalse(Boolean.TRUE.equals(direccion.getAnulada()), "lo ya recibido queda como antecedente");
        assertFalse(departamento.getRequiereInforme());
        assertTrue(departamento.getAnulada(), "lo que no se recibió se anula");
        assertEquals(DocumentoService.ANULACION_RETIRADO, departamento.getTipoAnulacion());
        assertFalse(Boolean.TRUE.equals(otraDireccion.getAnulada()), "una copia recibida no cambia");
        assertTrue(DocumentoService.pedidosAbiertos(actuaciones).isEmpty());
        assertEquals(EstadoDocumento.PENDIENTE_SALIDA, expediente.getEstadoActual());
        verify(auditoriaService).registrarEvento(eq(expediente), eq("CIERRE_PEDIDOS_INTERNOS"), any(), any(), contains("sin esperar"), eq(mesaDgaf), isNull());
    }

    @Test
    void responderUnPedidoInternoNoCierraElQueLoOrigino() {
        ActuacionDocumento recibido = pedidoRecibido();
        ActuacionDocumento direccion = pase(2L, dgaf, hacienda, TipoPase.INFORME, true, recibido);
        when(dependenciaRepository.findById(DHAC)).thenReturn(Optional.of(hacienda));

        servicio.registrarActuacion(300L, DHAC, "MEMO", "13/2026", "Respuesta de Hacienda", null, null, 2L, mesaDgaf);

        assertTrue(recibido.getRequiereInforme());
        assertEquals(List.of(recibido), DocumentoService.pedidosAbiertos(actuaciones));
        assertEquals(EstadoDocumento.EN_ESPERA_INFORME, expediente.getEstadoActual());
        assertNotNull(direccion);
    }

    @Test
    void soloSeBajaUnNivel() {
        pedidoRecibido();
        assertThrows(AccessDeniedException.class, () -> servicio.repartirPase(1L, List.of(CONTAB), "Saltea a la Dirección", TipoPase.INFORME, mesaDgaf),
                "a un departamento se llega por su dirección");
        assertThrows(AccessDeniedException.class, () -> servicio.repartirPase(1L, List.of(DTIC_SA), "A otra cabecera", TipoPase.INFORME, mesaDgaf),
                "el reparto no sale de su área");
        assertThrows(AccessDeniedException.class, () -> servicio.repartirPase(1L, List.of(1), "Al superior", TipoPase.CONOCIMIENTO, mesaDgaf),
                "el reparto no sube");
        assertEquals(1, actuaciones.size());
    }

    @Test
    void sinElOriginalNoSeEntregaUnTramite() {
        pedidoRecibido();
        assertThrows(IllegalArgumentException.class, () -> servicio.repartirPase(1L, List.of(DREC), "Entrega el original", TipoPase.TRAMITE, mesaDgaf));
    }

    @Test
    void unaCopiaSeReparteSoloComoCopia() {
        pase(1L, dtic, dgaf, TipoPase.CONOCIMIENTO, true, null);
        assertThrows(IllegalArgumentException.class, () -> servicio.repartirPase(1L, List.of(DREC), "Pide un informe", TipoPase.INFORME, mesaDgaf));
        assertFalse(servicio.obtenerOpcionesReparto(1L, mesaDgaf).permiteInforme());

        servicio.repartirPase(1L, List.of(DREC), "Para su conocimiento", TipoPase.CONOCIMIENTO, mesaDgaf);
        assertEquals(2, actuaciones.size());
    }

    @Test
    void unPedidoYaRespondidoSeReparteSoloComoCopia() {
        ActuacionDocumento recibido = pedidoRecibido();
        actuaciones.add(ActuacionDocumento.builder().id(9L).documento(expediente).tipoActuacion("MEMO").dependenciaEmisora(dgaf)
                .actuacionPadre(recibido).fechaHoraActuacion(LocalDateTime.now()).anulada(false).build());
        assertFalse(servicio.obtenerOpcionesReparto(1L, mesaDgaf).permiteInforme());
        assertThrows(IllegalArgumentException.class, () -> servicio.repartirPase(1L, List.of(DREC), "Otro pedido", TipoPase.INFORME, mesaDgaf));
    }

    @Test
    void loQueTodaviaNoSeRecibioNoSeReparte() {
        pase(1L, dtic, dgaf, TipoPase.INFORME, false, null);
        assertThrows(IllegalStateException.class, () -> servicio.repartirPase(1L, List.of(DREC), "Sin confirmar", TipoPase.INFORME, mesaDgaf));
    }

    @Test
    void unTramiteRecibidoNoSeReparte() {
        pase(1L, dtic, dgaf, TipoPase.TRAMITE, true, null);
        assertThrows(IllegalStateException.class, () -> servicio.repartirPase(1L, List.of(DREC), "Con trámite", TipoPase.CONOCIMIENTO, mesaDgaf));
    }

    @Test
    void conLaSalidaRegistradaYaNoSeReparte() {
        pedidoRecibido();
        expediente.setFechaHoraSalida(LocalDateTime.now());
        assertThrows(IllegalStateException.class, () -> servicio.repartirPase(1L, List.of(DREC), "Tarde", TipoPase.CONOCIMIENTO, mesaDgaf));
    }

    @Test
    void repartirExigeElPermisoDeDerivar() {
        pedidoRecibido();
        doThrow(new AccessDeniedException("sin permiso")).when(politicaAcceso).exigir(eq(mesaDgaf), eq(Accion.DERIVAR), any());
        assertThrows(AccessDeniedException.class, () -> servicio.repartirPase(1L, List.of(DREC), "Sin permiso", TipoPase.INFORME, mesaDgaf));
        assertEquals(1, actuaciones.size());
    }

    @Test
    void reparteSoloQuienActuaPorLaReceptora() {
        pase(1L, dgaf, dtic, TipoPase.INFORME, true, null);
        assertThrows(AccessDeniedException.class, () -> servicio.repartirPase(1L, List.of(40), "De otra dependencia", TipoPase.INFORME, mesaDgaf));
    }

    @Test
    void lasOpcionesMuestranSoloElNivelInferior() {
        pedidoRecibido();
        var opciones = servicio.obtenerOpcionesReparto(1L, mesaDgaf);
        assertTrue(opciones.permiteInforme());
        assertEquals(java.util.Set.of(UOC, DREC, DHAC), opciones.destinos().stream().map(DocumentoService.DestinoPase::id)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void siNadieMasPuedeRecibirloNoSeReparte() {
        ReflectionTestUtils.setField(servicio, "exigirReceptor", true);
        pedidoRecibido();
        when(dependenciaRepository.findConReceptor(anyCollection())).thenReturn(List.of());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> servicio.repartirPase(1L, List.of(DREC), "Sin receptor", TipoPase.INFORME, mesaDgaf));
        assertTrue(e.getMessage().contains("Nadie usa todavía el sistema"));
    }
}
