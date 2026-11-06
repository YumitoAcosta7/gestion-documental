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
import py.gov.asuncion.tic.gestion_documental.repository.BiblioratoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.UbicacionDocumentoRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Cada dependencia tiene sus biblioratos y ubica su propio papel de cada documento
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BiblioratosPorDependenciaTest {

    @Mock BiblioratoRepository biblioratoRepository;
    @Mock DependenciaRepository dependenciaRepository;
    @Mock UbicacionDocumentoRepository ubicacionRepository;
    @Mock PoliticaAcceso politicaAcceso;
    @Mock CanalService canalService;
    @InjectMocks BiblioratoService servicio;

    private final Dependencia propia = dep(40, "Departamento Operativo");
    private final Dependencia ajena = dep(101, "Mesa de la DGAF");
    private final Bibliorato carpetaPropia = carpeta(1, "BIB-OPE-001", propia, 20);
    private final Bibliorato carpetaAjena = carpeta(2, "BIB-DGAF-001", ajena, null);
    private final Usuario usuario = new Usuario();
    private final Documento doc = new Documento();

    private static Dependencia dep(int id, String nombre) {
        return Dependencia.builder().id(id).nombre(nombre).activo(true).build();
    }

    private static Bibliorato carpeta(int id, String codigo, Dependencia dep, Integer capacidad) {
        return Bibliorato.builder().id(id).codigo(codigo).descripcion("Tomo 1").dependencia(dep).activo(true).capacidadFojas(capacidad).build();
    }

    @BeforeEach
    void preparar() {
        usuario.setId(8);
        doc.setId(300L);
        doc.setCodigoCuti("DTIC-2026-000300");
        Predicate<Dependencia> alcance = d -> d != null && d.getId().equals(40);
        when(canalService.alcance(usuario)).thenReturn(alcance);
        when(canalService.puedeActuarPor(eq(usuario), any())).thenAnswer(inv -> alcance.test(inv.getArgument(1)));
        when(biblioratoRepository.findById(1)).thenReturn(Optional.of(carpetaPropia));
        when(biblioratoRepository.findById(2)).thenReturn(Optional.of(carpetaAjena));
        when(ubicacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ubicacionRepository.findDeDocumentoYDependencia(anyLong(), anyInt())).thenReturn(Optional.empty());
    }

    @Test
    void cadaUsuarioVeSoloLasCarpetasDeSuAlcance() {
        when(biblioratoRepository.findByActivoTrueOrderByAnioDescCodigoAsc()).thenReturn(List.of(carpetaAjena, carpetaPropia));
        when(biblioratoRepository.findAllByOrderByAnioDescCodigoAsc()).thenReturn(List.of(carpetaAjena, carpetaPropia));
        assertEquals(List.of(carpetaPropia), servicio.listarActivos(usuario));
        assertEquals(List.of(carpetaPropia), servicio.listarTodos(usuario));
    }

    @Test
    void soloSeCreaLaCarpetaDeUnaDependenciaPropia() {
        when(dependenciaRepository.findById(101)).thenReturn(Optional.of(ajena));
        when(dependenciaRepository.findById(40)).thenReturn(Optional.of(propia));
        assertThrows(AccessDeniedException.class, () -> servicio.crear("BIB-X-001", "Tomo", 101, 2026, "Armario", null, usuario));
        verify(biblioratoRepository, never()).save(any());

        when(biblioratoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(propia, servicio.crear("BIB-X-001", "Tomo", 40, 2026, "Armario", null, usuario).getDependencia());
    }

    @Test
    void noSeCierraLaCarpetaDeOtraDependencia() {
        assertThrows(AccessDeniedException.class, () -> servicio.cambiarEstado(2, false, usuario));
        assertTrue(carpetaAjena.getActivo());
    }

    @Test
    void administrarLasCarpetasExigeElPermisoDeArchivo() {
        doThrow(new AccessDeniedException("sin permiso")).when(politicaAcceso).exigir(eq(usuario), eq(Accion.GESTIONAR_ARCHIVO), any());
        assertThrows(AccessDeniedException.class, () -> servicio.cambiarEstado(1, false, usuario));
        assertThrows(AccessDeniedException.class, () -> servicio.crear("BIB-X-001", "Tomo", 40, 2026, "Armario", null, usuario));
    }

    @Test
    void noSeGuardaEnLaCarpetaDeOtraDependencia() {
        assertThrows(AccessDeniedException.class, () -> servicio.biblioratoParaGuardar(2, usuario));
        assertEquals(carpetaPropia, servicio.biblioratoParaGuardar(1, usuario));
    }

    @Test
    void noSeGuardaEnUnaCarpetaCerrada() {
        carpetaPropia.setActivo(false);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> servicio.biblioratoParaGuardar(1, usuario));
        assertTrue(e.getMessage().contains("cerrado"));
    }

    @Test
    void laUbicacionQuedaEnLaDependenciaDuenaDeLaCarpeta() {
        UbicacionDocumento u = servicio.guardarUbicacion(doc, carpetaPropia, 1, 3, usuario);
        assertEquals(propia, u.getDependencia());
        assertEquals(doc, u.getDocumento());
        assertEquals("BIB-OPE-001 — Tomo 1 · Fojas 1–3", u.getTexto());
        assertEquals(usuario, u.getUsuario());
    }

    @Test
    void cambiarLaUbicacionReemplazaLaDeEsaDependenciaSinTocarLasDeOtras() {
        UbicacionDocumento existente = UbicacionDocumento.builder().id(9L).documento(doc).dependencia(propia).bibliorato(carpetaPropia).fojaDesde(1).fojaHasta(3).build();
        when(ubicacionRepository.findDeDocumentoYDependencia(300L, 40)).thenReturn(Optional.of(existente));

        UbicacionDocumento u = servicio.guardarUbicacion(doc, carpetaPropia, 5, 7, usuario);

        assertSame(existente, u);
        assertEquals("5–7", u.getFojasTexto());
        verify(ubicacionRepository).findDeDocumentoYDependencia(300L, 40);
        verify(ubicacionRepository, times(1)).save(any());
    }

    @Test
    void noSeUsanFojasOcupadasNiSeSuperaLaCapacidad() {
        Documento otro = new Documento();
        otro.setCodigoCuti("DTIC-2026-000001");
        when(ubicacionRepository.findFojasSuperpuestas(1, 2, 4, 300L)).thenReturn(List.of(
                UbicacionDocumento.builder().documento(otro).fojaDesde(1).fojaHasta(3).build()));

        IllegalArgumentException ocupadas = assertThrows(IllegalArgumentException.class, () -> servicio.guardarUbicacion(doc, carpetaPropia, 2, 4, usuario));
        assertTrue(ocupadas.getMessage().contains("DTIC-2026-000001"));
        IllegalArgumentException capacidad = assertThrows(IllegalArgumentException.class, () -> servicio.guardarUbicacion(doc, carpetaPropia, 19, 25, usuario));
        assertTrue(capacidad.getMessage().contains("capacidad"));
        verify(ubicacionRepository, never()).save(any());
    }

    @Test
    void anularLiberaLasUbicacionesDeTodasLasDependencias() {
        UbicacionDocumento a = UbicacionDocumento.builder().documento(doc).dependencia(propia).bibliorato(carpetaPropia).fojaDesde(1).fojaHasta(1).build();
        UbicacionDocumento b = UbicacionDocumento.builder().documento(doc).dependencia(ajena).bibliorato(carpetaAjena).build();
        when(ubicacionRepository.findByDocumentoId(300L)).thenReturn(List.of(a, b));

        String texto = servicio.liberarUbicaciones(300L);

        assertTrue(texto.contains("BIB-OPE-001") && texto.contains("BIB-DGAF-001"));
        verify(ubicacionRepository).deleteAll(List.of(a, b));
    }

    @Test
    void sinUbicacionesNoHayNadaQueLiberar() {
        when(ubicacionRepository.findByDocumentoId(300L)).thenReturn(List.of());
        assertNull(servicio.liberarUbicaciones(300L));
    }
}
