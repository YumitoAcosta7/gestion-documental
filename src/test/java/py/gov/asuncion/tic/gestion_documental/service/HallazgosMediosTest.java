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
import org.springframework.test.util.ReflectionTestUtils;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.ActuacionDocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.AsignacionRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Auditoría, hallazgos medios: M1 (trámite pendiente), M2 (destino sin receptor) y M5 (alcance del operador)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HallazgosMediosTest {

    @Mock DocumentoRepository documentoRepository;
    @Mock ActuacionDocumentoRepository actuacionRepository;
    @Mock DependenciaRepository dependenciaRepository;
    @Mock PoliticaAcceso politicaAcceso;
    @Mock CanalService canalService;
    @InjectMocks DocumentoService servicio;

    private final Dependencia mesaTic = Dependencia.builder().id(11).nombre("Unidad de Secretaría Administrativa").esMesaEntrada(true).build();
    private final Usuario usuario = new Usuario();
    private Documento expediente;

    @BeforeEach
    void preparar() {
        usuario.setId(7);
        usuario.setDependencia(mesaTic);
        expediente = new Documento();
        expediente.setId(100L);
        expediente.setDependenciaCustodia(mesaTic);
        expediente.setEstadoActual(EstadoDocumento.PENDIENTE_SALIDA);
        expediente.setFechaHoraSalida(java.time.LocalDateTime.now());
        when(documentoRepository.findById(100L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.findByIdParaActualizar(100L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.existeEnAmbito(7, 100L)).thenReturn(true);
        when(canalService.puedeActuarPor(eq(usuario), any())).thenReturn(true);   // tiene el original
        when(politicaAcceso.puede(any(), any())).thenReturn(true);
    }

    @Test
    void m1ConUnTramitePendienteNoSeDaSalidaNiSeFinaliza() {
        when(actuacionRepository.existePaseTramitePendiente(100L)).thenReturn(true);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> servicio.registrarSalidaDocumento(100L, "Nota 1/2026", null, "x", "y", null, usuario));
        assertTrue(e.getMessage().contains("en camino"));
        assertThrows(IllegalStateException.class, () -> servicio.confirmarEntregaYAcuse(100L,
                new MockMultipartFile("a", "a.pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes()), usuario));
        verify(documentoRepository, never()).save(any());
    }

    @Test
    void m2NoSeDerivaAUnaDependenciaSinNadieQueReciba() {
        ReflectionTestUtils.setField(servicio, "exigirReceptor", true);
        expediente.setEstadoActual(EstadoDocumento.INGRESADO);
        // DTIC (10) con su mesa (11) y un departamento (40) que todavía no tiene usuarios
        Organigrama org = new Organigrama(List.of(
                new Organigrama.Nodo(1, "Intendencia", "INT", null, 1, true, false),
                new Organigrama.Nodo(10, "Dirección de TIC", "DTIC", 1, 4, true, false),
                new Organigrama.Nodo(11, "Unidad de Secretaría Administrativa", "DTIC-SA", 10, 7, true, true),
                new Organigrama.Nodo(40, "Departamento Operativo", "DTIC-D03", 10, 6, true, false)), Map.of());
        when(canalService.organigrama()).thenReturn(org);
        when(dependenciaRepository.findConReceptor(anyCollection())).thenReturn(List.of());   // nadie en el 40
        when(dependenciaRepository.findById(40)).thenReturn(Optional.of(Dependencia.builder().id(40).nombre("Departamento Operativo").build()));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> servicio.derivarDocumento(100L, List.of(40), "Para su tramitación", TipoPase.TRAMITE, usuario));
        assertTrue(e.getMessage().contains("Nadie usa todavía el sistema"));
        verify(actuacionRepository, never()).save(any());

        // El modal muestra ese destino deshabilitado
        var opciones = servicio.obtenerOpcionesDerivacion(100L, usuario);
        assertFalse(opciones.destinos().stream().filter(d -> d.id() == 40).findFirst().orElseThrow().conReceptor());
    }

    @Test
    void m5ElOperadorActuaSoloPorSuDependencia() {
        AsignacionRepository asignaciones = mock(AsignacionRepository.class);
        CanalService canal = new CanalService(dependenciaRepository, asignaciones);
        Dependencia direccion = Dependencia.builder().id(10).nombre("DTIC").ruta("/1/10/").build();
        Dependencia departamento = Dependencia.builder().id(40).nombre("Departamento Operativo").ruta("/1/10/40/").build();
        Usuario ana = new Usuario();
        ana.setId(30);
        ana.setDependencia(Dependencia.builder().id(99).nombre("Otra").build());

        when(asignaciones.findVigentes(eq(30), any(LocalDate.class))).thenReturn(List.of(Asignacion.builder()
                .rol(RolAsignacion.OPERADOR).dependenciaAmbito(direccion).incluyeSubordinadas(true).build()));
        assertTrue(canal.puedeActuarPor(ana, direccion));
        assertFalse(canal.puedeActuarPor(ana, departamento), "el operador no recibe por las subordinadas");

        when(asignaciones.findVigentes(eq(30), any(LocalDate.class))).thenReturn(List.of(Asignacion.builder()
                .rol(RolAsignacion.RESPONSABLE).dependenciaAmbito(direccion).incluyeSubordinadas(true).build()));
        assertTrue(canal.puedeActuarPor(ana, departamento), "el responsable sí");
    }
}
