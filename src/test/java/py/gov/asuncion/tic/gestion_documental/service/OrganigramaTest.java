package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.Test;
import py.gov.asuncion.tic.gestion_documental.service.Organigrama.Movimiento;
import py.gov.asuncion.tic.gestion_documental.service.Organigrama.Nodo;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Canal jerárquico sobre un árbol con la forma del organigrama real (Res. 887)
class OrganigramaTest {

    // Niveles: 1 Intendencia, 2 Dirección General, 4 Dirección, 6 Departamento, 7 Unidad
    static final int INT = 1, DGAF = 100, DGAF_SA = 101, DHAC = 140, DHAC_SA = 141, CONTAB = 145, BANCOS = 1455,
            DREC = 110, DREC_SA = 111, DTIC = 10, DTIC_SA = 11, OPERATIVO = 40, REDES = 75, SOPORTE = 76,
            CODENI = 500, CODENI_D01 = 501, INACTIVA = 999;

    private final Organigrama org = new Organigrama(List.of(
            nodo(INT, "Intendencia Municipal", null, 1, false),
            nodo(DGAF, "Dirección General de Administración y Finanzas", INT, 2, false),
            nodo(DGAF_SA, "Unidad de Secretaría Administrativa", DGAF, 7, true),
            nodo(DHAC, "Dirección de Hacienda", DGAF, 4, false),
            nodo(DHAC_SA, "Unidad de Secretaría Administrativa", DHAC, 7, true),
            nodo(CONTAB, "Departamento de Contabilidad", DHAC, 6, false),
            nodo(BANCOS, "Unidad de Bancos", CONTAB, 7, false),
            nodo(DREC, "Dirección de Recaudaciones", DGAF, 4, false),
            nodo(DREC_SA, "Unidad de Secretaría Administrativa", DREC, 7, true),
            nodo(DTIC, "Dirección de TIC", INT, 4, false),
            nodo(DTIC_SA, "Unidad de Secretaría Administrativa", DTIC, 7, true),
            nodo(OPERATIVO, "Departamento Operativo de Informática", DTIC, 6, false),
            nodo(REDES, "Unidad de Redes", OPERATIVO, 7, false),
            nodo(SOPORTE, "Unidad de Soporte Técnico", OPERATIVO, 7, false),
            nodo(CODENI, "Dirección de CODENI", INT, 4, false),        // sin secretaría propia
            nodo(CODENI_D01, "Departamento de CODENI", CODENI, 6, false),
            new Nodo(INACTIVA, "Dependencia dada de baja", null, OPERATIVO, 7, false, false)
    ), Map.of());

    private static Nodo nodo(int id, String nombre, Integer padre, int orden, boolean mesa) {
        return new Nodo(id, nombre, null, padre, orden, true, mesa);
    }

    private List<Integer> destinos(int custodia) {
        return org.destinos(custodia).stream().map(d -> d.nodo().id()).toList();
    }

    private List<Integer> recorrido(int origen, int destino) {
        return org.recorrido(origen, destino).stream().map(Nodo::id).toList();
    }

    @Test
    void unaUnidadSoloSubeASuDepartamento() {
        assertEquals(List.of(OPERATIVO), destinos(REDES));
    }

    @Test
    void unDepartamentoBajaASusUnidadesYSubeALaMesaDeSuDireccion() {
        assertEquals(List.of(REDES, SOPORTE, DTIC_SA), destinos(OPERATIVO));
        assertEquals(Movimiento.SUBIR, org.destinos(OPERATIVO).get(2).movimiento());
    }

    @Test
    void laMesaDeUnaDireccionPasaACabecerasHermanasYSubeAlSuperior() {
        // DTIC depende de la Intendencia: baja a sus departamentos, sube a la Intendencia
        // y pasa a las cabeceras hermanas (DGAF y CODENI), siempre a su mesa si la tienen
        List<Integer> d = destinos(DTIC_SA);
        assertTrue(d.contains(OPERATIVO));
        assertTrue(d.contains(INT));
        assertTrue(d.contains(DGAF_SA));
        assertTrue(d.contains(CODENI), "sin secretaría propia recibe la cabecera misma");
        assertFalse(d.contains(DTIC_SA), "no se envía a sí misma");
        assertFalse(d.contains(DHAC_SA), "a Hacienda se llega pasando por la DGAF");
        assertFalse(d.contains(REDES), "a una unidad se baja por su departamento");
    }

    @Test
    void entreDireccionesDeLaMismaDireccionGeneralSePasaDirecto() {
        assertTrue(destinos(DHAC_SA).contains(DREC_SA));
        assertTrue(destinos(DHAC_SA).contains(DGAF_SA));
        assertFalse(destinos(DHAC_SA).contains(DTIC_SA), "a TIC se llega por la DGAF");
    }

    @Test
    void laDireccionGeneralBajaALaMesaDeSusDirecciones() {
        List<Integer> d = destinos(DGAF_SA);
        assertTrue(d.containsAll(List.of(DHAC_SA, DREC_SA, DTIC_SA, INT)));
    }

    @Test
    void lasDependenciasInactivasNoRecibenPases() {
        assertFalse(destinos(OPERATIVO).contains(INACTIVA));
    }

    @Test
    void recorridoDeTicAHaciendaPasaPorLaDgaf() {
        assertEquals(List.of(DGAF_SA, DHAC_SA), recorrido(DTIC_SA, DHAC_SA));
        assertEquals(List.of(DGAF_SA, DTIC_SA), recorrido(DHAC_SA, DTIC_SA));
    }

    @Test
    void recorridoCompletoDeUnaUnidadDeTicAUnaUnidadDeHacienda() {
        assertEquals(List.of(OPERATIVO, DTIC_SA, DGAF_SA, DHAC_SA, CONTAB, BANCOS), recorrido(REDES, BANCOS));
    }

    @Test
    void recorridoDentroDeLaMismaDireccionSubeYBaja() {
        assertEquals(List.of(OPERATIVO, SOPORTE), recorrido(REDES, SOPORTE));
        assertEquals(List.of(DREC_SA), recorrido(DHAC_SA, DREC_SA));
    }

    @Test
    void cadaPasoDelRecorridoEsUnPasePermitido() {
        int actual = REDES;
        for (Nodo paso : org.recorrido(REDES, BANCOS)) {
            assertTrue(org.puedePasar(actual, paso.id()), "paso no permitido: " + actual + " -> " + paso.id());
            actual = paso.id();
        }
        assertEquals(BANCOS, actual);
    }

    @Test
    void unAtajoHabilitaUnDestinoFueraDelCanal() {
        Organigrama conAtajo = new Organigrama(List.of(
                nodo(INT, "Intendencia Municipal", null, 1, false),
                nodo(DTIC, "Dirección de TIC", INT, 4, false),
                nodo(OPERATIVO, "Departamento Operativo de Informática", DTIC, 6, false),
                nodo(REDES, "Unidad de Redes", OPERATIVO, 7, false),
                nodo(CODENI, "Dirección de CODENI", INT, 4, false)), Map.of(REDES, Set.of(CODENI)));
        assertEquals(Movimiento.ATAJO, conAtajo.destinos(REDES).stream()
                .filter(d -> d.nodo().id() == CODENI).findFirst().orElseThrow().movimiento());
    }

    @Test
    void cabeceraYMesa() {
        assertEquals(DTIC, org.cabecera(org.nodo(REDES)).id());
        assertEquals(DTIC_SA, org.mesa(org.nodo(DTIC)).orElseThrow().id());
        assertEquals(DTIC, org.nodoEfectivo(org.nodo(DTIC_SA)).id());
        assertTrue(org.mesa(org.nodo(CODENI)).isEmpty());
    }
}
