package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import py.gov.asuncion.tic.gestion_documental.domain.Accion;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.*;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditoriaAdministracionTest {

    private final AuditoriaSistemaRepository auditoria = mock(AuditoriaSistemaRepository.class);
    private final PoliticaAcceso politica = mock(PoliticaAcceso.class);
    private final AdministracionService servicio = new AdministracionService(mock(UsuarioRepository.class), mock(AsignacionRepository.class),
            mock(DependenciaRepository.class), auditoria, politica, new BCryptPasswordEncoder(4),
            new org.springframework.security.core.session.SessionRegistryImpl(), mock(SolicitudContrasenaRepository.class));
    private final Usuario admin = new Usuario();

    private void conResultadoVacio() {
        when(auditoria.buscar(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    void lasFechasSonInclusivas() {
        conResultadoVacio();
        LocalDate dia = LocalDate.of(2026, 11, 4);
        servicio.buscarAuditoria(dia, dia, null, null, 0, admin);
        verify(auditoria).buscar(eq(dia.atStartOfDay()), eq(dia.plusDays(1).atStartOfDay()), eq("%%"), eq(""), eq(PageRequest.of(0, 50)));
    }

    @Test
    void sinFiltrosBuscaEnTodaLaAuditoria() {
        conResultadoVacio();
        servicio.buscarAuditoria(null, null, "  ", "", 0, admin);
        verify(auditoria).buscar(argThat(d -> d.getYear() == 2000), argThat(h -> h.getYear() >= 9999), eq("%%"), eq(""), any());
    }

    @Test
    void laCuentaSeBuscaPorParteDelNombreSinImportarMayusculas() {
        conResultadoVacio();
        servicio.buscarAuditoria(null, null, " Juan.Admin ", "BAJA_USUARIO", 3, admin);
        verify(auditoria).buscar(any(), any(), eq("%juan.admin%"), eq("BAJA_USUARIO"), eq(PageRequest.of(3, 50)));
    }

    @Test
    void unaPaginaNegativaEsLaPrimera() {
        conResultadoVacio();
        servicio.buscarAuditoria(null, null, null, null, -5, admin);
        verify(auditoria).buscar(any(), any(), any(), any(), eq(PageRequest.of(0, 50)));
    }

    @Test
    void unRangoInvertidoSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> servicio.buscarAuditoria(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 4), null, null, 0, admin));
        verify(auditoria, never()).buscar(any(), any(), any(), any(), any());
    }

    @Test
    void soloUnAdministradorLaConsulta() {
        doThrow(new AccessDeniedException("No")).when(politica).exigir(any(), eq(Accion.ADMINISTRAR_SISTEMA), anyString());
        assertThrows(AccessDeniedException.class, () -> servicio.buscarAuditoria(null, null, null, null, 0, admin));
        assertThrows(AccessDeniedException.class, () -> servicio.accionesDeAuditoria(admin));
    }
}
