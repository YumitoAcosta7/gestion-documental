package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.Test;
import py.gov.asuncion.tic.gestion_documental.domain.Accion;
import py.gov.asuncion.tic.gestion_documental.domain.Asignacion;
import py.gov.asuncion.tic.gestion_documental.domain.RolAsignacion;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.AsignacionRepository;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PoliticaAccesoTest {

    private final AsignacionRepository repositorio = mock(AsignacionRepository.class);
    private final PoliticaAcceso politica = new PoliticaAcceso(repositorio);

    private Usuario usuarioCon(RolAsignacion... roles) {
        Usuario u = new Usuario();
        u.setId(7);
        List<Asignacion> asignaciones = java.util.Arrays.stream(roles)
                .map(r -> Asignacion.builder().rol(r).build()).toList();
        when(repositorio.findVigentes(eq(7), any())).thenReturn(asignaciones);
        return u;
    }

    @Test
    void mesaDeEntradaHaceTodoElTramiteSinAdministrarElSistema() {
        assertEquals(EnumSet.complementOf(EnumSet.of(Accion.ADMINISTRAR_SISTEMA)),
                politica.acciones(usuarioCon(RolAsignacion.MESA_ENTRADA)));
    }

    @Test
    void responsableDerivaPeroNoRegistraIngresosNiSalidas() {
        Usuario u = usuarioCon(RolAsignacion.RESPONSABLE);
        assertTrue(politica.puede(u, Accion.DERIVAR));
        assertTrue(politica.puede(u, Accion.FORZAR_CIERRE));
        assertFalse(politica.puede(u, Accion.REGISTRAR_INGRESO));
        assertFalse(politica.puede(u, Accion.REGISTRAR_SALIDA));
        assertFalse(politica.puede(u, Accion.SUPERVISAR_PASES));
    }

    @Test
    void variasAsignacionesSumanAcciones() {
        Usuario u = usuarioCon(RolAsignacion.RESPONSABLE, RolAsignacion.ADMIN_SISTEMA);
        assertEquals(EnumSet.of(Accion.DERIVAR, Accion.REGISTRAR_ACTUACION, Accion.FORZAR_CIERRE, Accion.VER_AUDITORIA,
                Accion.ADMINISTRAR_SISTEMA), politica.acciones(u));
    }

    @Test
    void elAdministradorDelSistemaNoTramitaPorEsaAsignacion() {
        // Solo con ADMIN_SISTEMA: administra y trabaja como operador de su propia dependencia
        assertEquals(EnumSet.of(Accion.ADMINISTRAR_SISTEMA, Accion.REGISTRAR_ACTUACION),
                politica.acciones(usuarioCon(RolAsignacion.ADMIN_SISTEMA)));
    }

    @Test
    void consultaSoloLee() {
        assertTrue(politica.acciones(usuarioCon(RolAsignacion.CONSULTA)).isEmpty());
    }

    @Test
    void sinAsignacionesVigentesTrabajaComoOperador() {
        // Una suplencia vencida ya no aparece entre las vigentes y deja de habilitar sola
        assertEquals(EnumSet.of(Accion.REGISTRAR_ACTUACION), politica.acciones(usuarioCon()));
    }

    @Test
    void lasAutoridadesLlevanElPrefijoDePermiso() {
        assertTrue(politica.autoridades(usuarioCon(RolAsignacion.OPERADOR)).stream()
                .anyMatch(a -> "PERMISO_REGISTRAR_ACTUACION".equals(a.getAuthority())));
    }
}
