package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import py.gov.asuncion.tic.gestion_documental.domain.Dependencia;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.ActuacionDocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.ReporteRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReporteServiceTest {

    @Mock ReporteRepository reporteRepository;
    @Mock DependenciaRepository dependenciaRepository;
    @Mock DocumentoRepository documentoRepository;
    @Mock ActuacionDocumentoRepository actuacionRepository;
    @Mock BiblioratoService biblioratoService;
    @Mock FeriadoService feriadoService;
    @InjectMocks ReporteService servicio;

    private final Dependencia direccion = dep(10, "Dirección", "/10/");
    private final Dependencia departamento = dep(40, "Departamento", "/10/40/");
    private final Dependencia unidad = dep(76, "Unidad", "/10/40/76/");
    private final Usuario usuario = new Usuario();
    private final LocalDate hoy = LocalDate.now();

    private static Dependencia dep(int id, String nombre, String ruta) {
        return Dependencia.builder().id(id).nombre(nombre).ruta(ruta).activo(true).build();
    }

    // Un activo en la custodia indicada, con su última actividad hace tantos días corridos
    private Object[] activo(String cuti, int custodia, String prioridad, int diasAtras) {
        LocalDateTime fecha = hoy.minusDays(diasAtras).atTime(9, 0);
        return new Object[] { 1L, cuti, "Asunto " + cuti, "EN_REVISION", prioridad, custodia, fecha, fecha, null, "Nota" };
    }

    private ReporteService.Filtro filtro(Integer dependenciaId) {
        return ReporteService.Filtro.de(dependenciaId, null, null, null, hoy);
    }

    @BeforeEach
    void preparar() {
        ReflectionTestUtils.setField(servicio, "diasAdvertencia", 5);
        ReflectionTestUtils.setField(servicio, "diasCritico", 10);
        ReflectionTestUtils.setField(servicio, "urgenteDiasAdvertencia", 2);
        ReflectionTestUtils.setField(servicio, "urgenteDiasCritico", 4);
        ReflectionTestUtils.setField(servicio, "diasInforme", 8);
        when(biblioratoService.dependenciasDelAlcance(usuario)).thenReturn(List.of(unidad, direccion, departamento));
        when(dependenciaRepository.findAll()).thenReturn(List.of(direccion, departamento, unidad));
        when(feriadoService.feriadosEntre(any(), any())).thenReturn(Set.of());
    }

    @Test
    void lasDireccionesSumanASusAreas() {
        when(reporteRepository.activos(any(), any())).thenReturn(List.of(activo("A", 76, "NORMAL", 0), activo("B", 40, "NORMAL", 0), activo("C", 10, "NORMAL", 0)));
        when(reporteRepository.terminadosPorCustodia(any(), any())).thenReturn(List.<Object[]>of(new Object[] { 76, 2L }));

        var filas = servicio.generar(usuario, filtro(null)).dependencias();

        assertEquals(List.of("Dirección", "Departamento", "Unidad"), filas.stream().map(ReporteService.FilaDependencia::nombre).toList());
        assertEquals(List.of(0, 1, 2), filas.stream().map(ReporteService.FilaDependencia::nivel).toList());
        assertEquals(List.of(3L, 2L, 1L), filas.stream().map(ReporteService.FilaDependencia::activos).toList());
        assertEquals(List.of(1L, 1L, 1L), filas.stream().map(ReporteService.FilaDependencia::activosAqui).toList());
        assertEquals(2L, filas.get(0).terminados(), "lo terminado de la unidad suma hacia arriba");
    }

    @Test
    void unaDependenciaSinDocumentosNoAparece() {
        when(reporteRepository.activos(any(), any())).thenReturn(List.<Object[]>of(activo("C", 10, "NORMAL", 0)));
        var filas = servicio.generar(usuario, filtro(null)).dependencias();
        assertEquals(1, filas.size());
        assertEquals(10, filas.get(0).id());
    }

    @Test
    void elegirUnaDependenciaConsultaSoloEsaYSusAreas() {
        servicio.generar(usuario, filtro(40));
        verify(reporteRepository).activos(eq(new LinkedHashSet<>(List.of(40, 76))), isNull());
    }

    @Test
    void elegirUnaDependenciaAjenaNoAmpliaElAlcance() {
        servicio.generar(usuario, filtro(999));
        verify(reporteRepository).activos(eq(new LinkedHashSet<>(List.of(10, 40, 76))), isNull());
    }

    @Test
    void sinAlcanceNoSeConsultaNada() {
        when(biblioratoService.dependenciasDelAlcance(usuario)).thenReturn(List.of());
        var datos = servicio.generar(usuario, filtro(null));
        assertEquals(0, datos.resumen().activos());
        verifyNoInteractions(reporteRepository);
    }

    @Test
    void laDemoraUsaLosLimitesDeCadaPrioridadYOrdenaPorDias() {
        // Distancias holgadas respecto de cada límite, para no depender de los fines de semana
        when(reporteRepository.activos(any(), any())).thenReturn(List.of(
                activo("HOY", 76, "NORMAL", 0),
                activo("ADVERTENCIA", 76, "NORMAL", 9),
                activo("CRITICO", 76, "NORMAL", 40),
                activo("URGENTE", 76, "URGENTE", 40)));

        var datos = servicio.generar(usuario, filtro(null));

        assertEquals(3, datos.resumen().conDemora());
        assertEquals(2, datos.resumen().criticos());
        assertFalse(datos.demorados().stream().anyMatch(f -> f.cuti().equals("HOY")));
        assertTrue(datos.demorados().stream().filter(f -> f.cuti().equals("ADVERTENCIA")).findFirst().map(f -> !f.critico()).orElse(false));
        assertTrue(datos.demorados().stream().filter(f -> f.cuti().equals("URGENTE")).findFirst().orElseThrow().critico());
        assertTrue(datos.demorados().get(0).diasSinMovimiento() >= datos.demorados().get(2).diasSinMovimiento());
    }

    @Test
    void laAntiguedadSeReparteEnFranjas() {
        when(reporteRepository.activos(any(), any())).thenReturn(List.of(activo("A", 76, "NORMAL", 0), activo("B", 76, "NORMAL", 100)));
        var fila = servicio.generar(usuario, filtro(null)).antiguedad().stream().filter(f -> f.estado().equals("En revisión")).findFirst().orElseThrow();
        assertEquals(1, fila.hasta5());
        assertEquals(1, fila.mas30());
        assertEquals(2, fila.total());
    }

    @Test
    void elFlujoIncluyeLosMesesSinMovimientoYCalculaElSaldo() {
        LocalDate inicio = hoy.withDayOfMonth(1).minusMonths(1);
        var f = ReporteService.Filtro.de(null, null, inicio, hoy, hoy);
        when(reporteRepository.ingresosPorMes(any(), any(), any(), any())).thenReturn(List.<Object[]>of(new Object[] { hoy.toString().substring(0, 7), 5L }));
        when(reporteRepository.finalizadosPorMes(any(), any(), any(), any())).thenReturn(List.<Object[]>of(new Object[] { hoy.toString().substring(0, 7), 2L }));

        var datos = servicio.generar(usuario, f);

        assertEquals(2, datos.flujo().size());
        assertEquals(0, datos.flujo().get(0).ingresos());
        assertEquals(3, datos.flujo().get(1).saldo());
        assertEquals(2, datos.resumen().finalizadosPeriodo());
    }

    @Test
    void elFiltroPoneValoresPorDefectoYOrdenaLasFechas() {
        LocalDate dia = LocalDate.of(2026, 11, 6);
        var porDefecto = ReporteService.Filtro.de(null, null, null, null, dia);
        assertEquals(LocalDate.of(2025, 12, 1), porDefecto.desde());
        assertEquals(dia, porDefecto.hasta());

        var invertido = ReporteService.Filtro.de(null, null, dia, dia.minusDays(3), dia);
        assertEquals(dia.minusDays(3), invertido.desde());
        assertEquals(dia, invertido.hasta());

        var largo = ReporteService.Filtro.de(null, null, LocalDate.of(2000, 1, 1), dia, dia);
        assertEquals(dia.minusYears(ReporteService.MAXIMO_ANIOS), largo.desde());
    }

    @Test
    void lasCarpetasCalculanSuOcupacion() {
        assertEquals(50, new ReporteService.FilaCarpeta("B", "d", "dep", true, 2, 10, 20).porcentaje());
        assertEquals(100, new ReporteService.FilaCarpeta("B", "d", "dep", true, 2, 30, 20).porcentaje());
        assertNull(new ReporteService.FilaCarpeta("B", "d", "dep", true, 2, 30, null).porcentaje());
    }

    @Test
    void laExportacionNoDejaPasarFormulasYRespetaElSeparador() {
        assertEquals("'=HIPERVINCULO(1)", ReporteCsv.celda("=HIPERVINCULO(1)"));
        assertEquals("'-1+1", ReporteCsv.celda("-1+1"));
        assertEquals("\"a;b\"", ReporteCsv.celda("a;b"));
        assertEquals("\"di \"\"algo\"\"\"", ReporteCsv.celda("di \"algo\""));
        assertEquals("normal", ReporteCsv.celda("normal"));
    }

    @Test
    void elCsvLlevaEncabezadosYMarcaParaExcel() {
        var datos = servicio.generar(usuario, filtro(null));
        String csv = new String(ReporteCsv.generar("flujo", datos), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿Mes;Ingresos;Finalizados;Saldo\r\n"));
        assertNull(ReporteCsv.generar("inexistente", datos));
        for (String reporte : ReporteCsv.REPORTES) {
            assertNotNull(ReporteCsv.generar(reporte, datos), reporte);
        }
    }
}
