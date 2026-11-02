package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Reglas de fojas de la ubicación física (casos BI-04 de docs/auditoria-validacion-qa.md)
class ValidacionFojasTest {

    @Test
    void sinFojasEsValido() {
        assertDoesNotThrow(() -> BiblioratoService.validarFojas(null, null));
    }

    @Test
    void unaSolaFojaEsValida() {
        assertDoesNotThrow(() -> BiblioratoService.validarFojas(5, 5));
    }

    @Test
    void rangoValido() {
        assertDoesNotThrow(() -> BiblioratoService.validarFojas(12, 14));
    }

    @Test
    void faltaUnExtremo() {
        assertThrows(IllegalArgumentException.class, () -> BiblioratoService.validarFojas(12, null));
        assertThrows(IllegalArgumentException.class, () -> BiblioratoService.validarFojas(null, 14));
    }

    @Test
    void fojaCeroONegativa() {
        assertThrows(IllegalArgumentException.class, () -> BiblioratoService.validarFojas(0, 3));
        assertThrows(IllegalArgumentException.class, () -> BiblioratoService.validarFojas(-2, 3));
    }

    @Test
    void finalMenorQueInicial() {
        assertThrows(IllegalArgumentException.class, () -> BiblioratoService.validarFojas(14, 12));
    }
}
