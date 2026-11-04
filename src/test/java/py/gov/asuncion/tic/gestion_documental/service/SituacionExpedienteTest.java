package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.Test;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.service.SituacionExpediente.Permisos;
import py.gov.asuncion.tic.gestion_documental.service.SituacionExpediente.Tipo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

// La fila muestra una acción principal y solo lo que el servidor va a aceptar, según la situación del expediente
class SituacionExpedienteTest {

    private final Dependencia nosotros = dep(10, "Dirección de TIC", "/1/10/");
    private final Dependencia otra = dep(20, "Dirección de Hacienda", "/1/20/");
    private final Predicate<Dependencia> actuaPor = d -> d != null && d.getId().equals(10);
    private final Permisos mesa = new Permisos(true, true, true, true, true);
    private final Permisos operador = new Permisos(true, false, false, false, false);

    private static Dependencia dep(int id, String nombre, String ruta) {
        return Dependencia.builder().id(id).nombre(nombre).ruta(ruta).activo(true).build();
    }

    private static Documento doc(EstadoDocumento estado) {
        Documento d = new Documento();
        d.setId(1L);
        d.setEstadoActual(estado);
        return d;
    }

    private static ActuacionDocumento pase(long id, Dependencia de, Dependencia a, TipoPase tipo, boolean confirmado, int horasAtras) {
        return ActuacionDocumento.builder().id(id).tipoActuacion(confirmado ? "PASE_CONFIRMADO" : "PASE_INTERNO").tipoPase(tipo)
                .requiereInforme(tipo == TipoPase.INFORME).paseConfirmado(confirmado).anulada(false)
                .dependenciaEmisora(de).dependenciaReceptoraPase(a)
                .fechaHoraActuacion(LocalDateTime.now().minusHours(horasAtras)).build();
    }

    private SituacionExpediente de(Documento d, Dependencia custodia, Permisos permisos, ActuacionDocumento... actuaciones) {
        return SituacionExpediente.de(d, custodia, List.of(actuaciones), actuaPor, nosotros, permisos);
    }

    @Test
    void sinElOriginalSoloSeConsulta() {
        SituacionExpediente s = de(doc(EstadoDocumento.EN_REVISION), otra, mesa);
        assertEquals(Tipo.OTRA, s.tipo());
        assertEquals("Está en Dirección de Hacienda", s.detalle());
        assertFalse(s.puedeAgregar() || s.puedeDerivar() || s.puedeSalida() || s.puedeCierre() || s.puedeDevolver());
        assertFalse(s.isConMenu(), "sin acciones no hay menú");
    }

    @Test
    void loQueLeEnviaronSeConfirmaPrimero() {
        SituacionExpediente s = de(doc(EstadoDocumento.EN_REVISION), otra, mesa, pase(7, otra, nosotros, TipoPase.TRAMITE, false, 1));
        assertEquals(Tipo.RECIBIR, s.tipo());
        assertEquals(7L, s.paseRecibir());
        assertTrue(s.puedeRechazar());
        assertFalse(s.puedeDerivar(), "el original todavía no es suyo");
        assertFalse(s.puedeDevolver(), "lo que no se recibió se rechaza, no se devuelve");
    }

    @Test
    void quienLoTieneDerivaYSoloLaMesaDaSalida() {
        SituacionExpediente deLaMesa = de(doc(EstadoDocumento.EN_REVISION), nosotros, mesa);
        assertEquals(Tipo.EN_PODER, deLaMesa.tipo());
        assertTrue(deLaMesa.puedeDerivar() && deLaMesa.puedeAgregar() && deLaMesa.puedeSalida());
        assertFalse(deLaMesa.puedeCierre(), "sin informes pendientes no hay nada que cerrar");

        SituacionExpediente deOperador = de(doc(EstadoDocumento.EN_REVISION), nosotros, operador);
        assertEquals(Tipo.EN_PODER, deOperador.tipo());
        assertTrue(deOperador.puedeDerivar(), "el operador eleva a su superior");
        assertFalse(deOperador.puedeSalida());
    }

    @Test
    void conElOriginalEnCaminoNoSeModificaNada() {
        SituacionExpediente s = de(doc(EstadoDocumento.EN_REVISION), nosotros, mesa, pase(9, nosotros, otra, TipoPase.TRAMITE, false, 1));
        assertEquals(Tipo.EN_CAMINO, s.tipo());
        assertEquals(9L, s.paseRetirar());
        assertTrue(s.detalle().contains("En camino a Dirección de Hacienda"));
        assertFalse(s.puedeAgregar() || s.puedeDerivar() || s.puedeSalida() || s.puedeAcuse() || s.puedeDevolver() || s.puedeCierre());
    }

    @Test
    void aQuienSeLePidioInformeLeTocaResponder() {
        SituacionExpediente s = de(doc(EstadoDocumento.EN_ESPERA_INFORME), otra, operador, pase(3, otra, nosotros, TipoPase.INFORME, true, 5));
        assertEquals(Tipo.RESPONDER, s.tipo());
        assertTrue(s.puedeAgregar());
        assertFalse(s.puedeDerivar());
    }

    @Test
    void elMenuNoRepiteLaAccionDeResponder() {
        ActuacionDocumento pedido = pase(3, otra, nosotros, TipoPase.INFORME, true, 5);
        ActuacionDocumento devolucion = ActuacionDocumento.builder().id(5L).tipoActuacion("DEVOLUCION").anulada(false)
                .dependenciaEmisora(nosotros).dependenciaReceptoraPase(otra).fechaHoraActuacion(LocalDateTime.now()).build();
        SituacionExpediente s = de(doc(EstadoDocumento.EN_ESPERA_INFORME), otra, operador, pedido, devolucion);
        assertEquals(Tipo.RESPONDER, s.tipo());
        assertTrue(s.puedeAgregar());
        assertFalse(s.isConMenu(), "sin otras acciones, Agregar no abre un menú que repite Responder");
    }

    @Test
    void elCierreExcepcionalSoloSiEsperaInformes() {
        assertTrue(de(doc(EstadoDocumento.EN_ESPERA_INFORME), nosotros, mesa).puedeCierre());
        assertFalse(de(doc(EstadoDocumento.EN_ESPERA_INFORME), nosotros, operador).puedeCierre(), "sin el permiso");
    }

    @Test
    void laSalidaSigueSuOrden() {
        Documento d = doc(EstadoDocumento.PENDIENTE_SALIDA);
        assertEquals(Tipo.LISTO_SALIDA, de(d, nosotros, mesa).tipo());
        d.setFechaHoraSalida(LocalDateTime.now());
        SituacionExpediente registrada = de(d, nosotros, mesa);
        assertEquals(Tipo.ESPERA_ACUSE, registrada.tipo());
        assertFalse(registrada.puedeSalida(), "la salida no se registra dos veces");
    }

    @Test
    void conInformesSinResponderSeOfreceElCierreYNoLaSalida() {
        SituacionExpediente s = de(doc(EstadoDocumento.EN_ESPERA_INFORME), nosotros, mesa, pase(3, nosotros, otra, TipoPase.INFORME, true, 5));
        assertFalse(s.puedeSalida());
        assertTrue(s.puedeCierre());

        SituacionExpediente sinEstado = de(doc(EstadoDocumento.EN_REVISION), nosotros, mesa, pase(3, nosotros, otra, TipoPase.INFORME, true, 5));
        assertTrue(sinEstado.puedeCierre(), "aunque el estado no lo diga, hay informes por responder");
    }

    @Test
    void loQueTodaviaNoSeRecibioNoOfreceAgregarActuacion() {
        SituacionExpediente s = de(doc(EstadoDocumento.EN_ESPERA_INFORME), otra, mesa, pase(3, otra, nosotros, TipoPase.INFORME, false, 5));
        assertEquals(Tipo.RECIBIR, s.tipo());
        assertFalse(s.isConMenu(), "primero se confirma o se rechaza");
    }

    @Test
    void laDevolucionSeVeComoTalYNoSePuedeDevolverDeVuelta() {
        ActuacionDocumento enCamino = pase(9, nosotros, otra, TipoPase.TRAMITE, false, 1);
        enCamino.setEsDevolucion(true);
        SituacionExpediente quienDevuelve = de(doc(EstadoDocumento.EN_REVISION), nosotros, mesa, enCamino);
        assertEquals(Tipo.EN_CAMINO, quienDevuelve.tipo());
        assertTrue(quienDevuelve.detalle().startsWith("Devuelto a"));

        ActuacionDocumento llega = pase(9, otra, nosotros, TipoPase.TRAMITE, false, 1);
        llega.setEsDevolucion(true);
        assertTrue(de(doc(EstadoDocumento.EN_REVISION), otra, mesa, llega).detalle().startsWith("Se lo devolvió"));

        ActuacionDocumento recibida = pase(9, otra, nosotros, TipoPase.TRAMITE, true, 1);
        recibida.setEsDevolucion(true);
        assertFalse(de(doc(EstadoDocumento.EN_REVISION), nosotros, mesa, recibida).puedeDevolver());
    }

    @Test
    void corregirYAnularSeOfrecenSoloCuandoCorresponde() {
        SituacionExpediente nuevo = de(doc(EstadoDocumento.INGRESADO), nosotros, mesa);
        assertTrue(nuevo.puedeCorregirIngreso() && nuevo.puedeAnular());
        assertFalse(de(doc(EstadoDocumento.INGRESADO), nosotros, operador).puedeCorregirIngreso(), "sin permiso ni haberlo cargado");
        assertFalse(de(doc(EstadoDocumento.INGRESADO), otra, mesa).puedeCorregirIngreso(), "sin el original");

        SituacionExpediente conMovimientos = de(doc(EstadoDocumento.EN_REVISION), nosotros, mesa, pase(4, otra, nosotros, TipoPase.TRAMITE, true, 5));
        assertTrue(conMovimientos.puedeCorregirIngreso());
        assertFalse(conMovimientos.puedeAnular());

        Documento conSalida = doc(EstadoDocumento.PENDIENTE_SALIDA);
        conSalida.setFechaHoraSalida(LocalDateTime.now());
        assertFalse(de(conSalida, nosotros, mesa).puedeCorregirIngreso());

        SituacionExpediente anulado = de(doc(EstadoDocumento.ANULADO), nosotros, mesa);
        assertFalse(anulado.puedeCorregirIngreso() || anulado.puedeDerivar() || anulado.puedeSalida());
    }

    @Test
    void sinElOriginalNoSeOfreceDevolver() {
        SituacionExpediente s = de(doc(EstadoDocumento.EN_ESPERA_INFORME), otra, mesa, pase(3, otra, nosotros, TipoPase.INFORME, true, 5));
        assertEquals(Tipo.RESPONDER, s.tipo());
        assertFalse(s.puedeDevolver());
    }

    @Test
    void conLaSalidaRegistradaSoloQuedaElAcuseOCorregirla() {
        Documento d = doc(EstadoDocumento.PENDIENTE_SALIDA);
        d.setFechaHoraSalida(LocalDateTime.now());
        SituacionExpediente s = de(d, nosotros, mesa);
        assertTrue(s.puedeAcuse() && s.puedeCorregirSalida());
        assertFalse(s.puedeAgregar() || s.puedeDerivar() || s.puedeDevolver());

        assertFalse(de(d, nosotros, operador).puedeCorregirSalida(), "sin el permiso de salida");
        assertFalse(de(d, otra, mesa).puedeCorregirSalida(), "sin el original");
        assertFalse(de(doc(EstadoDocumento.PENDIENTE_SALIDA), nosotros, mesa).puedeCorregirSalida(), "sin salida registrada");
    }

    @Test
    void unExpedienteDerivadoDespuesDeLaSalidaSePuedeDestrabar() {
        Documento d = doc(EstadoDocumento.EN_REVISION);
        d.setFechaHoraSalida(LocalDateTime.now());
        SituacionExpediente s = de(d, nosotros, mesa);
        assertEquals(Tipo.OTRA, s.tipo());
        assertTrue(s.detalle().contains("corríjala"));
        assertTrue(s.puedeCorregirSalida() && s.isConMenu());
        assertFalse(s.puedeAcuse() || s.puedeDerivar());
    }

    @Test
    void devolverSoloSiHayAQuienYNoSeDevolvio() {
        ActuacionDocumento recibido = pase(4, otra, nosotros, TipoPase.TRAMITE, true, 5);
        assertTrue(de(doc(EstadoDocumento.EN_REVISION), nosotros, mesa, recibido).puedeDevolver());
        assertFalse(de(doc(EstadoDocumento.EN_REVISION), nosotros, mesa).puedeDevolver(), "nadie se lo envió");

        ActuacionDocumento devolucion = ActuacionDocumento.builder().id(5L).tipoActuacion("DEVOLUCION").anulada(false)
                .dependenciaEmisora(nosotros).dependenciaReceptoraPase(otra).fechaHoraActuacion(LocalDateTime.now()).build();
        assertFalse(de(doc(EstadoDocumento.EN_REVISION), nosotros, mesa, recibido, devolucion).puedeDevolver(), "ya se devolvió");
    }

    @Test
    void lasMarcasDeLaPlantillaSonConsistentes() {
        assertEquals("EN_PODER", de(doc(EstadoDocumento.EN_REVISION), nosotros, mesa).getTipoNombre());
        assertTrue(de(doc(EstadoDocumento.EN_REVISION), nosotros, mesa).isConMenu());
    }
}
