package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.Test;
import py.gov.asuncion.tic.gestion_documental.domain.Feriado;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.AuditoriaSistemaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.FeriadoRepository;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 📅 Los feriados no cuentan como días hábiles; el calendario lo mantiene el administrador, con auditoría
class FeriadoServiceTest {

    private final FeriadoRepository feriados = mock(FeriadoRepository.class);
    private final AuditoriaSistemaRepository auditoria = mock(AuditoriaSistemaRepository.class);
    private final FeriadoService servicio = new FeriadoService(feriados, auditoria, mock(PoliticaAcceso.class));
    private final Usuario admin = new Usuario();

    @Test
    void losFeriadosNoCuentanComoDiasHabiles() {
        // Del miércoles 13 al lunes 18 de mayo de 2026: jueves 14 y viernes 15 son feriados (Independencia)
        LocalDate miercoles = LocalDate.of(2026, 5, 13);
        LocalDate lunes = LocalDate.of(2026, 5, 18);
        assertEquals(3, FeriadoService.diasHabilesEntre(miercoles, lunes, Set.of()), "sin feriados: jueves, viernes y lunes");
        assertEquals(1, FeriadoService.diasHabilesEntre(miercoles, lunes,
                Set.of(LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 15))), "con feriados: solo el lunes");
    }

    @Test
    void seAgregaUnFeriadoConAuditoria() {
        when(feriados.save(any(Feriado.class))).thenAnswer(inv -> inv.getArgument(0));
        Feriado f = servicio.agregar(LocalDate.of(2026, 6, 15), "  Paz del  Chaco (trasladado) ", admin);
        assertEquals("Paz del Chaco (trasladado)", f.getDescripcion());
        assertSame(admin, f.getCreadoPor());
        verify(auditoria).save(argThat(e -> "ALTA_FERIADO".equals(e.getAccion()) && e.getDetalle().startsWith("15/06/2026")));
    }

    @Test
    void noSeCarganFinesDeSemanaNiRepetidos() {
        assertThrows(IllegalArgumentException.class, () -> servicio.agregar(LocalDate.of(2026, 6, 13), "Sábado", admin));
        when(feriados.existsById(LocalDate.of(2026, 5, 14))).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> servicio.agregar(LocalDate.of(2026, 5, 14), "Independencia", admin));
        verify(feriados, never()).save(any());
    }
}
