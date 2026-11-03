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
import py.gov.asuncion.tic.gestion_documental.dto.NuevoDocumentoDTO;
import py.gov.asuncion.tic.gestion_documental.repository.ActuacionDocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.TipoDocumentoRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Un memo interno viaja dentro del mismo expediente (mismo CUTI): se eleva con un pase de trámite,
// no se crea otro expediente. Y mientras el original está en camino, nadie más lo pasa.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnidadDeExpedienteTest {

    @Mock DocumentoRepository documentoRepository;
    @Mock ActuacionDocumentoRepository actuacionRepository;
    @Mock DependenciaRepository dependenciaRepository;
    @Mock TipoDocumentoRepository tipoDocumentoRepository;
    @Mock PoliticaAcceso politicaAcceso;
    @Mock CanalService canalService;
    @Mock AuditoriaService auditoriaService;
    @InjectMocks DocumentoService servicio;

    // DTIC (10) > Departamento Operativo (40) > Unidad de Redes (75); la DTIC tiene su mesa (11)
    private final Organigrama org = new Organigrama(List.of(
            new Organigrama.Nodo(1, "Intendencia", "INT", null, 1, true, false),
            new Organigrama.Nodo(10, "Dirección de TIC", "DTIC", 1, 4, true, false),
            new Organigrama.Nodo(11, "Unidad de Secretaría Administrativa", "DTIC-SA", 10, 7, true, true),
            new Organigrama.Nodo(40, "Departamento Operativo", "DTIC-D03", 10, 6, true, false),
            new Organigrama.Nodo(75, "Unidad de Redes", "DTIC-D03-U01", 40, 7, true, false),
            new Organigrama.Nodo(76, "Unidad de Soporte", "DTIC-D03-U02", 40, 7, true, false)), Map.of());

    private final Dependencia redes = Dependencia.builder().id(75).nombre("Unidad de Redes").ruta("/1/10/40/75/").activo(true).build();
    private final Usuario operadorRedes = new Usuario();
    private Documento expediente;

    @BeforeEach
    void preparar() {
        ReflectionTestUtils.setField(servicio, "exigirReceptor", false);
        operadorRedes.setId(8);
        operadorRedes.setDependencia(redes);
        expediente = new Documento();
        expediente.setId(200L);
        expediente.setCodigoCuti("DTIC-2026-000050");
        expediente.setDependenciaCustodia(redes);
        expediente.setEstadoActual(EstadoDocumento.EN_REVISION);
        when(documentoRepository.findById(200L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.findByIdParaActualizar(200L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.existeEnAmbito(8, 200L)).thenReturn(true);
        when(canalService.organigrama()).thenReturn(org);
        when(canalService.puedeActuarPor(eq(operadorRedes), any())).thenReturn(true);
        // Operador: registra actuaciones pero no tiene el permiso de derivar
        when(politicaAcceso.puede(operadorRedes, Accion.DERIVAR)).thenReturn(false);
        when(dependenciaRepository.findById(anyInt())).thenAnswer(inv ->
                Optional.of(Dependencia.builder().id(inv.getArgument(0)).nombre("Dep " + inv.getArgument(0)).build()));
        when(actuacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void elOperadorDeUnaUnidadElevaElMismoExpedienteASuDepartamento() {
        Documento d = servicio.derivarDocumento(200L, List.of(40), "Se eleva el memo de la unidad", TipoPase.TRAMITE, operadorRedes);
        assertEquals("DTIC-2026-000050", d.getCodigoCuti(), "mismo expediente, mismo CUTI");
        verify(actuacionRepository).save(argThat(a -> a.getTipoPase() == TipoPase.TRAMITE
                && a.getDependenciaEmisora().getId().equals(75) && a.getDependenciaReceptoraPase().getId().equals(40)));
        verify(documentoRepository, never()).save(argThat(doc -> doc != expediente)); // no se crea otro expediente
    }

    @Test
    void sinPermisoDeDerivarSoloSePuedeElevarEnTramite() {
        assertThrows(AccessDeniedException.class, () -> servicio.derivarDocumento(200L, List.of(40),
                "Para informe del departamento", TipoPase.INFORME, operadorRedes), "informe");
        verify(actuacionRepository, never()).save(any());

        var opciones = servicio.obtenerOpcionesDerivacion(200L, operadorRedes);
        assertTrue(opciones.soloElevar());
        assertEquals(List.of(40), opciones.destinos().stream().map(DocumentoService.DestinoPase::id).toList(),
                "solo el superior inmediato");
    }

    @Test
    void conElOriginalEnCaminoNoSePasaNiSiquieraUnaCopia() {
        when(politicaAcceso.puede(operadorRedes, Accion.DERIVAR)).thenReturn(true);
        when(actuacionRepository.existePaseTramitePendiente(200L)).thenReturn(true);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> servicio.derivarDocumento(200L,
                List.of(40), "Para conocimiento", TipoPase.CONOCIMIENTO, operadorRedes));
        assertTrue(e.getMessage().contains("en camino"));
    }

    @Test
    void laMesaNoReingresaComoNuevoUnDocumentoDeSuPropiaDireccion() {
        Usuario mesa = new Usuario();
        mesa.setId(9);
        TipoDocumento memo = new TipoDocumento();
        memo.setId(2);
        memo.setNombre("Memorándum");
        memo.setActivo(true);
        when(tipoDocumentoRepository.findById(2)).thenReturn(Optional.of(memo));
        when(dependenciaRepository.findById(75)).thenReturn(Optional.of(redes));
        when(dependenciaRepository.findById(10)).thenReturn(Optional.of(Dependencia.builder().id(10).nombre("DTIC").ruta("/1/10/").build()));
        when(canalService.cabeceraDelUsuario(mesa)).thenReturn(org.nodo(10));

        NuevoDocumentoDTO dto = new NuevoDocumentoDTO();
        dto.setTipoDocumentoId(2);
        dto.setDependenciaOrigenId(75);   // memo de la Unidad de Redes, de la propia DTIC
        dto.setAsunto("Memo de la unidad");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> servicio.guardarNuevoDocumento(dto, mesa));
        assertTrue(e.getMessage().contains("Derivar / Elevar"));
        verify(documentoRepository, never()).save(any());
    }
}
