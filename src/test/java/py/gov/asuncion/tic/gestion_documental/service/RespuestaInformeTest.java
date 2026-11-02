package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.Test;
import py.gov.asuncion.tic.gestion_documental.domain.ActuacionDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.Dependencia;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Auditoría A4: un pedido de informe se responde desde el área que lo recibió y después del pedido
class RespuestaInformeTest {

    private final Dependencia hacienda = Dependencia.builder().id(140).nombre("Dirección de Hacienda").ruta("/1/100/140/").build();
    private final Dependencia mesaHacienda = Dependencia.builder().id(141).nombre("Unidad de Secretaría Administrativa")
            .ruta("/1/100/140/141/").esMesaEntrada(true).dependenciaPadre(hacienda).build();
    private final Dependencia contabilidad = Dependencia.builder().id(145).nombre("Departamento de Contabilidad").ruta("/1/100/140/145/").build();
    private final Dependencia recaudaciones = Dependencia.builder().id(110).nombre("Dirección de Recaudaciones").ruta("/1/100/110/").build();

    private final LocalDateTime pedido = LocalDateTime.of(2026, 11, 2, 10, 0);

    private ActuacionDocumento pase() {
        return ActuacionDocumento.builder().tipoActuacion("PASE_CONFIRMADO").requiereInforme(true)
                .dependenciaReceptoraPase(mesaHacienda).fechaHoraActuacion(pedido).build();
    }

    private static ActuacionDocumento informe(Dependencia emisora, LocalDateTime cuando) {
        return ActuacionDocumento.builder().tipoActuacion("INFORME_TECNICO").dependenciaEmisora(emisora).fechaHoraActuacion(cuando).build();
    }

    @Test
    void unaRespuestaDeUnPedidoAnteriorNoCierraElNuevo() {
        assertFalse(DocumentoService.informeRespondido(pase(), List.of(informe(hacienda, pedido.minusDays(3)))));
    }

    @Test
    void responderDesdeLaDireccionOUnaSubordinadaDeLaMesaCuenta() {
        // El pedido llegó a la mesa de Hacienda: puede contestar la Dirección o el departamento que lo trabajó
        assertTrue(DocumentoService.informeRespondido(pase(), List.of(informe(hacienda, pedido.plusHours(2)))));
        assertTrue(DocumentoService.informeRespondido(pase(), List.of(informe(contabilidad, pedido.plusDays(1)))));
        assertTrue(DocumentoService.informeRespondido(pase(), List.of(informe(mesaHacienda, pedido.plusDays(1)))));
    }

    @Test
    void unaRespuestaDeOtraAreaNoCuenta() {
        assertFalse(DocumentoService.informeRespondido(pase(), List.of(informe(recaudaciones, pedido.plusDays(1)))));
    }
}
