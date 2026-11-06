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
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Archiva quien registra actuaciones y actúa por la dependencia dueña de la carpeta, tenga o no el original
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CambioDeUbicacionTest {

    @Mock DocumentoRepository documentoRepository;
    @Mock BiblioratoService biblioratoService;
    @Mock PoliticaAcceso politicaAcceso;
    @Mock CanalService canalService;
    @Mock AuditoriaService auditoriaService;
    @InjectMocks DocumentoService servicio;

    private final Dependencia operativo = Dependencia.builder().id(40).nombre("Departamento Operativo").activo(true).build();
    private final Dependencia mesa = Dependencia.builder().id(11).nombre("Mesa DTIC").activo(true).build();
    private final Bibliorato carpeta = Bibliorato.builder().id(1).codigo("BIB-OPE-001").descripcion("Tomo 1").dependencia(operativo).activo(true).build();
    private final Usuario operador = new Usuario();
    private Documento doc;
    private UbicacionDocumento guardada;

    @BeforeEach
    void preparar() {
        operador.setId(8);
        doc = new Documento();
        doc.setId(300L);
        doc.setCodigoCuti("DTIC-2026-000300");
        doc.setDependenciaCustodia(mesa);
        doc.setEstadoActual(EstadoDocumento.EN_REVISION);
        guardada = UbicacionDocumento.builder().documento(doc).dependencia(operativo).bibliorato(carpeta).fojaDesde(5).fojaHasta(7).build();
        when(documentoRepository.findById(300L)).thenReturn(Optional.of(doc));
        when(documentoRepository.findByIdParaActualizar(300L)).thenReturn(Optional.of(doc));
        when(documentoRepository.existeEnAmbito(8, 300L)).thenReturn(true);
        when(biblioratoService.biblioratoParaGuardar(1, operador)).thenReturn(carpeta);
        when(biblioratoService.ubicacionActual(300L, carpeta)).thenReturn(Optional.empty());
        when(biblioratoService.guardarUbicacion(doc, carpeta, 5, 7, operador)).thenReturn(guardada);
    }

    @Test
    void unaDependenciaUbicaSuCopiaAunqueElOriginalEsteEnOtra() {
        assertSame(guardada, servicio.cambiarUbicacion(300L, 1, 5, 7, null, operador));
        verify(politicaAcceso).exigir(eq(operador), eq(Accion.REGISTRAR_ACTUACION), any());
        verify(auditoriaService).registrarEvento(eq(doc), eq("CAMBIO_UBICACION"), any(), any(),
                argThat(d -> d.contains("registrada por Departamento Operativo") && d.contains("BIB-OPE-001")), eq(operador), isNull());
    }

    @Test
    void cambiarLaUbicacionDeEsaDependenciaExigeMotivo() {
        when(biblioratoService.ubicacionActual(300L, carpeta)).thenReturn(Optional.of(guardada));
        assertThrows(IllegalArgumentException.class, () -> servicio.cambiarUbicacion(300L, 1, 5, 7, null, operador));
        verify(biblioratoService, never()).guardarUbicacion(any(), any(), any(), any(), any());

        servicio.cambiarUbicacion(300L, 1, 5, 7, "Se reorganizó la carpeta", operador);
        verify(auditoriaService).registrarEvento(eq(doc), eq("CAMBIO_UBICACION"), any(), any(), contains("Se reorganizó"), eq(operador), isNull());
    }

    @Test
    void sinElPermisoNoSeArchiva() {
        doThrow(new AccessDeniedException("sin permiso")).when(politicaAcceso).exigir(eq(operador), eq(Accion.REGISTRAR_ACTUACION), any());
        assertThrows(AccessDeniedException.class, () -> servicio.cambiarUbicacion(300L, 1, 5, 7, null, operador));
        verify(biblioratoService, never()).guardarUbicacion(any(), any(), any(), any(), any());
    }

    @Test
    void unExpedienteAnuladoNoSeGuarda() {
        doc.setEstadoActual(EstadoDocumento.ANULADO);
        assertThrows(IllegalStateException.class, () -> servicio.cambiarUbicacion(300L, 1, 5, 7, null, operador));
    }

    @Test
    void unFinalizadoQuedaArchivadoSoloAlGuardarSuOriginal() {
        doc.setEstadoActual(EstadoDocumento.FINALIZADO);
        when(canalService.puedeActuarPor(operador, mesa)).thenReturn(false);
        servicio.cambiarUbicacion(300L, 1, 5, 7, null, operador);
        assertNull(doc.getFechaHoraArchivado(), "una copia no archiva el expediente");

        when(canalService.puedeActuarPor(operador, mesa)).thenReturn(true);
        servicio.cambiarUbicacion(300L, 1, 5, 7, null, operador);
        assertNotNull(doc.getFechaHoraArchivado());
        assertTrue(doc.getArchivado());
    }
}
