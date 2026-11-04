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
    @Mock AuditoriaDocumentoRepository auditoriaDocumentoRepository;
    @Mock AdjuntoDocumentoRepository adjuntoRepository;
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
        return servicio.registrarActuacion(500L, emisora.getId(), tipo, numero, "Actuación de " + emisora.getNombre(),
                null, null, null, usuario);
    }

    private ActuacionDocumento responder(Dependencia emisora, String tipo, String numero, ActuacionDocumento pedido) {
        return servicio.registrarActuacion(500L, emisora.getId(), tipo, numero, "Respuesta de " + emisora.getNombre(),
                null, null, pedido.getId(), usuario);
    }

    @Test
    void perteneceAUnaMesaSinAsignacionNoDaAccesoNiDejaIniciarDocumentos() {
        Dependencia mesa = Dependencia.builder().id(11).nombre("Secretaría").ruta("/1/10/11/").esMesaEntrada(true).activo(true).build();
        usuario.setDependencia(mesa);
        expediente.setDependenciaOrigen(mesa);
        when(documentoRepository.existeEnAmbito(anyInt(), eq(500L))).thenReturn(false);
        when(actuacionRepository.existeParticipacionDependencia(500L, 11)).thenReturn(true);
        assertFalse(servicio.puedeAccederDocumento(usuario, 500L));

        TipoDocumento tipo = new TipoDocumento();
        tipo.setActivo(true);
        when(tipoDocumentoRepository.findById(1)).thenReturn(Optional.of(tipo));
        when(canalService.dependenciaDeRegistro(usuario)).thenReturn(mesa);
        when(canalService.puedeActuarPor(usuario, mesa)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> servicio.iniciarDocumentoPropio(1, "S/N", "Asunto", null, null, false, null, usuario));
        verify(documentoRepository, never()).save(any());
    }

    @Test
    void unMemoSinElegirPedidoNoCierraNinguno() {
        ActuacionDocumento pedido = pedidoInforme(1L, dtic, hacienda, 5);
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true); // tiene el original
        ActuacionDocumento memo = registrar(hacienda, "MEMO", "1/2026");
        assertNull(memo.getActuacionPadre());
        assertFalse(DocumentoService.informeRespondido(pedido, respuestas()));
        assertEquals(EstadoDocumento.EN_ESPERA_INFORME, expediente.getEstadoActual());
    }

    @Test
    void unDepartamentoNoCierraElPedidoHechoASuDireccion() {
        ActuacionDocumento pedido = pedidoInforme(1L, hacienda, dtic, 5);
        when(canalService.puedeActuarPor(eq(usuario), any())).thenReturn(false);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> responder(operativo, "MEMO", "1/2026", pedido));
        assertTrue(e.getMessage().contains("no le corresponde"));
        verify(actuacionRepository, never()).save(any());
    }

    @Test
    void unPedidoSoloSeRespondeConInformeOMemo() {
        ActuacionDocumento pedido = pedidoInforme(1L, hacienda, dtic, 5);
        assertThrows(IllegalArgumentException.class, () -> responder(dtic, "PROVIDENCIA", "1/2026", pedido));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CIERRE_EXCEPCIONAL", "DEVOLUCION", "PASE_INTERNO", "PASE_CONFIRMADO", "CUALQUIERA"})
    void losTiposReservadosNoSeCarganAMano(String tipo) {
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> registrar(hacienda, tipo, "S/N"));
        verify(actuacionRepository, never()).save(any());
    }

    @Test
    void conInformesSinResponderNoHaySalida() {
        pedidoInforme(1L, hacienda, conocimiento, 5);
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> servicio.registrarSalidaDocumento(500L, "5/2026", null, "x", "y", null, usuario));
        assertTrue(e.getMessage().contains("cierre excepcional"));
        assertTrue(e.getMessage().contains("Dirección de Copia"));
        assertNull(expediente.getFechaHoraSalida());
    }

    @Test
    void trasElCierreExcepcionalLaSalidaSeAdmite() {
        pedidoInforme(1L, hacienda, conocimiento, 5);
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true);
        servicio.forzarCierreInformes(500L, "Justificación suficientemente larga del cierre", usuario);
        servicio.registrarSalidaDocumento(500L, "5/2026", null, "x", "y", null, usuario);
        assertEquals(EstadoDocumento.PENDIENTE_SALIDA, expediente.getEstadoActual());
        assertNotNull(expediente.getFechaHoraSalida());
    }

    @Test
    void unPedidoAnteriorAlCierreNoTrabaElAvance() {
        pedidoInforme(1L, hacienda, dtic, 50);
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true);
        servicio.forzarCierreInformes(500L, "Justificación suficientemente larga del cierre", usuario);
        ActuacionDocumento nuevo = pedidoInforme(2L, hacienda, conocimiento, 0);
        nuevo.setFechaHoraActuacion(LocalDateTime.now().plusSeconds(1)); // posterior al cierre
        expediente.setEstadoActual(EstadoDocumento.EN_ESPERA_INFORME);
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(false);
        responder(conocimiento, "INFORME_TECNICO", "2/2026", nuevo);
        assertEquals(EstadoDocumento.PENDIENTE_SALIDA, expediente.getEstadoActual());
    }

    @Test
    void unPedidoCerradoNoSePuedeResponderDespues() {
        ActuacionDocumento pedido = pedidoInforme(1L, hacienda, conocimiento, 5);
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true);
        servicio.forzarCierreInformes(500L, "Justificación suficientemente larga del cierre", usuario);
        assertTrue(DocumentoService.pedidosAbiertos(actuaciones).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> responder(conocimiento, "MEMO", "3/2026", pedido));
    }

    @Test
    void sinInformesPendientesNoHayCierreExcepcional() {
        expediente.setEstadoActual(EstadoDocumento.EN_REVISION);
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true);
        assertThrows(IllegalStateException.class,
                () -> servicio.forzarCierreInformes(500L, "Justificación suficientemente larga del cierre", usuario));
    }

    @Test
    void sinElOriginalNoSeDevuelve() {
        usuario.setDependencia(conocimiento);
        pedidoInforme(1L, hacienda, conocimiento, 5);
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> servicio.devolverDocumento(500L, "No corresponde a esta dirección", usuario));
        assertEquals(EstadoDocumento.EN_ESPERA_INFORME, expediente.getEstadoActual(), "el estado del expediente no cambia");
    }

    @Test
    void loQueNoSeRecibioNoSeDevuelve() {
        usuario.setDependencia(hacienda);
        ActuacionDocumento pendiente = ActuacionDocumento.builder().id(3L).tipoActuacion("PASE_INTERNO").anulada(false).paseConfirmado(false)
                .dependenciaEmisora(dtic).dependenciaReceptoraPase(hacienda).fechaHoraActuacion(LocalDateTime.now().minusHours(1)).build();
        actuaciones.add(pendiente);
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> servicio.devolverDocumento(500L, "No corresponde", usuario));
        assertTrue(e.getMessage().contains("sin recibir"));
    }

    private ActuacionDocumento tramiteRecibido(Dependencia de, Dependencia a, int horasAtras) {
        ActuacionDocumento p = ActuacionDocumento.builder().id(30L + actuaciones.size()).documento(expediente).tipoActuacion("PASE_CONFIRMADO")
                .tipoPase(TipoPase.TRAMITE).anulada(false).paseConfirmado(true).dependenciaEmisora(de).dependenciaReceptoraPase(a)
                .fechaHoraActuacion(LocalDateTime.now().minusHours(horasAtras)).build();
        actuaciones.add(p);
        return p;
    }

    private void conElOriginalEnHacienda() {
        expediente.setEstadoActual(EstadoDocumento.EN_REVISION);
        usuario.setDependencia(hacienda);
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true);
    }

    @Test
    void laDevolucionDejaElOriginalEnCaminoHastaSuAcuse() {
        conElOriginalEnHacienda();
        tramiteRecibido(dtic, hacienda, 1);
        servicio.devolverDocumento(500L, "Corresponde a otra área", usuario);

        assertEquals(hacienda, expediente.getDependenciaCustodia(), "sigue a cargo de quien devuelve hasta el acuse");
        ActuacionDocumento pase = actuaciones.get(actuaciones.size() - 1);
        assertEquals("PASE_INTERNO", pase.getTipoActuacion());
        assertEquals(TipoPase.TRAMITE, pase.getTipoPase());
        assertTrue(pase.getEsDevolucion());
        assertFalse(pase.getPaseConfirmado());
        assertEquals(dtic, pase.getDependenciaReceptoraPase());
    }

    @Test
    void alDevolverSiguenEsperandoLosInformesAbiertos() {
        conElOriginalEnHacienda();
        pedidoInforme(1L, hacienda, conocimiento, 9);
        tramiteRecibido(dtic, hacienda, 1);
        servicio.devolverDocumento(500L, "Corresponde a otra área", usuario);
        assertEquals(EstadoDocumento.EN_ESPERA_INFORME, expediente.getEstadoActual());
    }

    @Test
    void unaDevolucionNoSeDevuelveDeVuelta() {
        conElOriginalEnHacienda();
        ActuacionDocumento devuelta = tramiteRecibido(dtic, hacienda, 1);
        devuelta.setEsDevolucion(true);
        assertThrows(IllegalStateException.class, () -> servicio.devolverDocumento(500L, "Se la devuelvo otra vez", usuario));

        // Con una entrega anterior, se devuelve a quien se la entregó, no a quien la devolvió
        tramiteRecibido(conocimiento, hacienda, 9);
        servicio.devolverDocumento(500L, "Corresponde a quien lo entregó", usuario);
        assertEquals(conocimiento, actuaciones.get(actuaciones.size() - 1).getDependenciaReceptoraPase());
    }

    @Test
    void noSeDevuelveDosVecesLaMismaEntrega() {
        conElOriginalEnHacienda();
        tramiteRecibido(dtic, hacienda, 2);
        servicio.devolverDocumento(500L, "Corresponde a otra área", usuario);
        ActuacionDocumento devolucion = actuaciones.get(actuaciones.size() - 1);
        devolucion.setTipoActuacion("PASE_CONFIRMADO");
        devolucion.setPaseConfirmado(true);
        assertTrue(assertThrows(IllegalStateException.class, () -> servicio.devolverDocumento(500L, "Otra vez", usuario))
                .getMessage().contains("ya devolvió"));

        // Si se la rechazaron, se puede volver a intentar
        devolucion.setAnulada(true);
        servicio.devolverDocumento(500L, "Segundo intento", usuario);
        assertEquals(dtic, actuaciones.get(actuaciones.size() - 1).getDependenciaReceptoraPase());
    }

    @Test
    void sinNadieQueReciba_noSeDevuelve() {
        conElOriginalEnHacienda();
        org.springframework.test.util.ReflectionTestUtils.setField(servicio, "exigirReceptor", true);
        tramiteRecibido(dtic, hacienda, 1);
        when(dependenciaRepository.findConReceptor(any())).thenReturn(List.of());
        assertTrue(assertThrows(IllegalStateException.class, () -> servicio.devolverDocumento(500L, "Corresponde a otra área", usuario))
                .getMessage().contains("Nadie puede recibir"));
        assertEquals(hacienda, expediente.getDependenciaCustodia());
    }

    @Test
    void conUnPaseYaPendienteHaciaEseDestinoNoSeDevuelve() {
        conElOriginalEnHacienda();
        tramiteRecibido(dtic, hacienda, 1);
        when(actuacionRepository.existePasePendiente(500L, 10)).thenReturn(true);
        assertTrue(assertThrows(IllegalStateException.class, () -> servicio.devolverDocumento(500L, "Corresponde a otra área", usuario))
                .getMessage().contains("esperando acuse"));
    }

    @Test
    void otraDireccionRegistraElMismoDocumentoConSuPropioCuti() {
        Organigrama org = new Organigrama(List.of(
                new Organigrama.Nodo(1, "Intendencia", "INT", null, 1, true, false),
                new Organigrama.Nodo(10, "Dirección de TIC", "DTIC", 1, 4, true, false),
                new Organigrama.Nodo(40, "Departamento Operativo", "DTIC-D03", 10, 6, true, false),
                new Organigrama.Nodo(20, "Dirección de Hacienda", "DHAC", 1, 4, true, false)), Map.of());
        when(canalService.organigrama()).thenReturn(org);
        Documento deTic = new Documento();
        deTic.setCodigoCuti("DTIC-2026-000003");
        deTic.setNumeroDocumentoOrigen("45/2026");
        deTic.setDependenciaRegistro(dtic);
        when(documentoRepository.findByDependenciaOrigenIdAndNumeroDocumentoOrigen(1, "45/2026")).thenReturn(List.of(deTic));
        Dependencia intendencia = dep(1, "Intendencia", "/1/");

        Documento enHacienda = new Documento();
        enHacienda.setDependenciaOrigen(intendencia);
        enHacienda.setNumeroDocumentoOrigen("45/2026");
        enHacienda.setDependenciaRegistro(hacienda);
        enHacienda.setCodigoCuti("DHAC-2026-000001");
        assertEquals("DHAC-2026-000001", servicio.guardarNuevoDocumento(enHacienda, usuario).getCodigoCuti());

        // Dentro de la misma dirección sigue siendo un duplicado, aunque lo cargue un departamento
        Documento enOperativo = new Documento();
        enOperativo.setDependenciaOrigen(intendencia);
        enOperativo.setNumeroDocumentoOrigen("45/2026");
        enOperativo.setDependenciaRegistro(operativo);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> servicio.guardarNuevoDocumento(enOperativo, usuario))
                .getMessage().contains("DTIC-2026-000003"));
    }

    @Test
    void elMensajeDeDuplicadoNoMandaACargarEnUnExpedienteFinalizado() {
        Documento finalizado = new Documento();
        finalizado.setCodigoCuti("DHAC-2026-000002");
        finalizado.setNumeroDocumentoOrigen("7/2026");
        finalizado.setEstadoActual(EstadoDocumento.FINALIZADO);
        when(documentoRepository.findByDependenciaOrigenIdAndNumeroDocumentoOrigen(20, "7/2026")).thenReturn(List.of(finalizado));
        Documento nuevo = new Documento();
        nuevo.setDependenciaOrigen(hacienda);
        nuevo.setNumeroDocumentoOrigen("7/2026");
        String mensaje = assertThrows(IllegalArgumentException.class, () -> servicio.guardarNuevoDocumento(nuevo, usuario)).getMessage();
        assertTrue(mensaje.contains("finalizado"));
        assertFalse(mensaje.contains("registra como actuación"));
    }

    @Test
    void lasRespuestasNuevasSinVinculoNoCuentanPorArea() {
        ActuacionDocumento pedido = pedidoInforme(1L, hacienda, dtic, 5);
        ActuacionDocumento sinVinculo = ActuacionDocumento.builder().id(8L).tipoActuacion("MEMO").anulada(false)
                .dependenciaEmisora(dtic).fechaHoraActuacion(LocalDateTime.now()).build();
        assertFalse(DocumentoService.informeRespondido(pedido, List.of(sinVinculo)));
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
        ActuacionDocumento dictamen = responder(operativo, "INFORME_TECNICO", "10/2026", pedidoTicAlDepartamento);
        assertSame(pedidoTicAlDepartamento, dictamen.getActuacionPadre());
        assertTrue(DocumentoService.informeRespondido(pedidoTicAlDepartamento, respuestas()));
        assertFalse(DocumentoService.informeRespondido(pedidoHaciendaATic, respuestas()),
                "el pedido de Hacienda sigue abierto hasta el memo consolidado de la Dirección");

        // La Dirección consolida y responde a Hacienda
        ActuacionDocumento memo = responder(dtic, "MEMO", "11/2026", pedidoHaciendaATic);
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
        when(documentoRepository.findByDependenciaOrigenIdAndNumeroDocumentoOrigen(20, "45/2026"))
                .thenReturn(List.of(existente));
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
        when(documentoRepository.findByDependenciaOrigenIdAndNumeroDocumentoOrigen(20, numero))
                .thenReturn(List.of(existente));
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
        when(canalService.puedeActuarPor(usuario, hacienda)).thenReturn(true);
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
        verify(documentoRepository, never()).findByDependenciaOrigenIdAndNumeroDocumentoOrigen(any(), any());
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

    @Test
    void elFormularioDeSalidaSoloOfreceLoQueElLegajoAcepta() {
        ActuacionDocumento informe = ActuacionDocumento.builder().id(1L).tipoActuacion("INFORME_TECNICO").numeroActuacion("10/2026")
                .dependenciaEmisora(operativo).anulada(false).build();
        ActuacionDocumento anulado = ActuacionDocumento.builder().id(2L).tipoActuacion("MEMO").anulada(true).build();
        ActuacionDocumento providencia = ActuacionDocumento.builder().id(3L).tipoActuacion("PROVIDENCIA").anulada(false).build();
        actuaciones.addAll(List.of(informe, anulado, providencia));
        assertEquals(List.of(informe), servicio.actuacionesParaLegajo(500L));

        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true);
        servicio.registrarSalidaDocumento(500L, "5/2026", null, "x", "y", null, "NOTA_DIRECCION", List.of(1L), usuario);
        assertEquals("5/2026", expediente.getNroNotaSalida());
    }

    private void conSalidaRegistrada() {
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(true);
        when(canalService.organigrama()).thenReturn(null);
        expediente.setEstadoActual(EstadoDocumento.PENDIENTE_SALIDA);
        expediente.setNroNotaSalida("5/2026");
        expediente.setPersonaRetiraSalida("Quien retira");
        expediente.setFechaHoraSalida(LocalDateTime.now());
    }

    @Test
    void conLaSalidaRegistradaNoSeTocaElExpediente() {
        conSalidaRegistrada();
        assertTrue(assertThrows(IllegalStateException.class, () -> registrar(hacienda, "PROVIDENCIA", "2/2026"))
                .getMessage().contains("salida ya está registrada"));
        assertTrue(assertThrows(IllegalStateException.class, () -> servicio.derivarDocumento(500L, List.of(10), "Motivo del pase", TipoPase.TRAMITE, usuario))
                .getMessage().contains("salida ya está registrada"));
        assertTrue(assertThrows(IllegalStateException.class, () -> servicio.registrarSalidaDocumento(500L, "6/2026", null, "x", "y", null, usuario))
                .getMessage().contains("salida ya está registrada"), "no pisa los datos de la salida anterior");
        assertEquals("5/2026", expediente.getNroNotaSalida());
        verify(actuacionRepository, never()).save(any());
    }

    @Test
    void laRespuestaAUnPedidoSeAdmiteAunqueElOriginalHayaSalido() {
        ActuacionDocumento pedido = pedidoInforme(1L, hacienda, operativo, 5);
        conSalidaRegistrada();
        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(false);
        ActuacionDocumento respuesta = responder(operativo, "INFORME_TECNICO", "10/2026", pedido);
        assertNotNull(respuesta.getActuacionPadre());
    }

    @Test
    void corregirLaSalidaLaDejaSinEfectoYLaAsientaEnLaBitacora() {
        conSalidaRegistrada();
        AuditoriaDocumento salida = new AuditoriaDocumento();
        salida.setTipoEvento("SALIDA_EXTERNA");
        salida.setEstadoAnterior("EN_ESPERA_INFORME");
        when(auditoriaDocumentoRepository.findByDocumentoIdOrderByFechaHoraDesc(500L)).thenReturn(List.of(salida));
        AdjuntoDocumento acuse = new AdjuntoDocumento();
        acuse.setTipoAdjunto("ACUSE_SALIDA");
        acuse.setVigente(true);
        when(adjuntoRepository.findByDocumentoIdAndActuacionIsNullOrderByFechaHoraSubidaAsc(500L)).thenReturn(List.of(acuse));

        servicio.corregirSalida(500L, "Se cargó mal el número de nota", usuario);

        verify(politicaAcceso).exigir(eq(usuario), eq(Accion.REGISTRAR_SALIDA), any());
        assertNull(expediente.getFechaHoraSalida());
        assertNull(expediente.getNroNotaSalida());
        assertEquals(EstadoDocumento.EN_ESPERA_INFORME, expediente.getEstadoActual(), "vuelve al estado anterior a la salida");
        assertFalse(acuse.getVigente());
        assertEquals("Se cargó mal el número de nota", acuse.getMotivoReemplazo());
        verify(legajoSalidaRepository).deleteAll(any());
        verify(auditoriaService).registrarEvento(eq(expediente), eq("CORRECCION_SALIDA"), eq(EstadoDocumento.PENDIENTE_SALIDA),
                eq(EstadoDocumento.EN_ESPERA_INFORME), argThat(d -> d.contains("5/2026") && d.contains("Se cargó mal")), eq(usuario), isNull());
    }

    @Test
    void corregirLaSalidaTieneSusLimites() {
        conSalidaRegistrada();
        assertThrows(IllegalArgumentException.class, () -> servicio.corregirSalida(500L, "corto", usuario), "sin un motivo válido");

        when(actuacionRepository.existePaseTramitePendiente(500L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> servicio.corregirSalida(500L, "Motivo suficiente", usuario), "original en camino");
        when(actuacionRepository.existePaseTramitePendiente(500L)).thenReturn(false);

        expediente.setEstadoActual(EstadoDocumento.FINALIZADO);
        assertThrows(IllegalStateException.class, () -> servicio.corregirSalida(500L, "Motivo suficiente", usuario), "con el acuse ya confirmado");

        expediente.setEstadoActual(EstadoDocumento.PENDIENTE_SALIDA);
        expediente.setFechaHoraSalida(null);
        assertThrows(IllegalStateException.class, () -> servicio.corregirSalida(500L, "Motivo suficiente", usuario), "sin salida registrada");

        when(canalService.puedeActuarPor(eq(usuario), eq(hacienda))).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> servicio.corregirSalida(500L, "Motivo suficiente", usuario), "sin el original");
        verify(documentoRepository, never()).save(any());
    }
}
