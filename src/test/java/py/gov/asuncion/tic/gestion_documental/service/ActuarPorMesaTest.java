package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.Test;
import py.gov.asuncion.tic.gestion_documental.domain.Asignacion;
import py.gov.asuncion.tic.gestion_documental.domain.Dependencia;
import py.gov.asuncion.tic.gestion_documental.domain.RolAsignacion;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.AsignacionRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Auditoría A5: por una mesa de entrada pasa el original de toda su dirección, así que pertenecer a ella no alcanza
class ActuarPorMesaTest {

    private final AsignacionRepository asignaciones = mock(AsignacionRepository.class);
    private final CanalService canal = new CanalService(mock(DependenciaRepository.class), asignaciones);

    private final Dependencia dtic = Dependencia.builder().id(10).nombre("Dirección de TIC").ruta("/1/10/").build();
    private final Dependencia mesa = Dependencia.builder().id(11).nombre("Secretaría").ruta("/1/10/11/").esMesaEntrada(true).build();
    private final Dependencia redes = Dependencia.builder().id(75).nombre("Redes").ruta("/1/10/75/").build();

    private Usuario en(Dependencia dependencia) {
        Usuario u = new Usuario();
        u.setId(5);
        u.setDependencia(dependencia);
        return u;
    }

    private void conAsignacion(RolAsignacion rol, Dependencia ambito) {
        when(asignaciones.findVigentes(eq(5), any(LocalDate.class))).thenReturn(List.of(
                Asignacion.builder().rol(rol).dependenciaAmbito(ambito).incluyeSubordinadas(true).build()));
    }

    @Test
    void perteneceALaMesaSinAsignacionNoActua() {
        assertFalse(canal.puedeActuarPor(en(mesa), mesa));
    }

    @Test
    void conAsignacionActua() {
        conAsignacion(RolAsignacion.MESA_ENTRADA, dtic);
        assertTrue(canal.puedeActuarPor(en(mesa), mesa));
    }

    @Test
    void enOtraDependenciaLaPropiaSigueAlcanzando() {
        assertTrue(canal.puedeActuarPor(en(redes), redes));
    }

    @Test
    void quienSoloAdministraNoActuaPorSuDependencia() {
        conAsignacion(RolAsignacion.ADMIN_SISTEMA, dtic);
        assertFalse(canal.puedeActuarPor(en(redes), redes));
        assertFalse(canal.alcance(en(redes)).test(redes));
    }

    @Test
    void unaCuentaDeAdministracionNoActuaAunqueTengaOtrasAsignaciones() {
        when(asignaciones.findVigentes(eq(5), any(LocalDate.class))).thenReturn(List.of(
                Asignacion.builder().rol(RolAsignacion.ADMIN_SISTEMA).dependenciaAmbito(dtic).incluyeSubordinadas(true).build(),
                Asignacion.builder().rol(RolAsignacion.MESA_ENTRADA).dependenciaAmbito(dtic).incluyeSubordinadas(true).build()));
        assertFalse(canal.puedeActuarPor(en(redes), mesa));
        assertFalse(canal.puedeActuarPor(en(redes), redes));
    }

    @Test
    void elAlcanceSigueLaMismaRegla() {
        assertFalse(canal.alcance(en(mesa)).test(mesa));
        assertTrue(canal.alcance(en(redes)).test(redes));
    }
}
