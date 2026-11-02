package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Cálculo de días hábiles usado por las alertas de demora (no requiere base de datos)
class DiasHabilesTest {

    @Test
    void mismoDiaEsCero() {
        assertEquals(0, DocumentoService.diasHabilesEntre(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 26)));
    }

    @Test
    void lunesAViernesSonCuatroDias() {
        // lunes 26/10 -> viernes 30/10
        assertEquals(4, DocumentoService.diasHabilesEntre(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 30)));
    }

    @Test
    void elFinDeSemanaNoCuenta() {
        // viernes 30/10 -> lunes 02/11: solo cuenta el lunes
        assertEquals(1, DocumentoService.diasHabilesEntre(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2)));
    }

    @Test
    void dosSemanasCompletasSonDiezDias() {
        assertEquals(10, DocumentoService.diasHabilesEntre(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 2)));
    }
}
