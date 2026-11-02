package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

// Casos CD-10, CD-12, CD-14 y CD-15 de docs/auditoria-validacion-qa.md
class ArchivoServiceTest {

    @TempDir
    Path carpeta;

    private ArchivoService servicio;

    private static final byte[] PDF_VALIDO = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @BeforeEach
    void preparar() {
        servicio = new ArchivoService();
        ReflectionTestUtils.setField(servicio, "uploadDir", carpeta.toString());
        ReflectionTestUtils.setField(servicio, "tamanioMaximo", DataSize.ofMegabytes(20));
    }

    @AfterEach
    void limpiarTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static MockMultipartFile pdf(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, "application/pdf", contenido);
    }

    @Test
    void pdfCompletoSeAcepta() {
        assertDoesNotThrow(() -> servicio.validarPdf(pdf("ok.pdf", PDF_VALIDO)));
    }

    @Test
    void extensionEnganosaSeRechaza() { // CD-10
        var e = assertThrows(IllegalArgumentException.class,
                () -> servicio.validarPdf(pdf("falso.pdf", "esto no es un pdf".getBytes(StandardCharsets.UTF_8))));
        assertTrue(e.getMessage().contains("no es un PDF válido"));
    }

    @Test
    void pdfTruncadoSeRechaza() { // CD-12
        var e = assertThrows(IllegalArgumentException.class,
                () -> servicio.validarPdf(pdf("cortado.pdf", "%PDF-1.4\n1 0 obj<<>>".getBytes(StandardCharsets.US_ASCII))));
        assertTrue(e.getMessage().contains("incompleto"));
    }

    @Test
    void pdfGrandeConCierreAlFinalSeAcepta() {
        byte[] grande = new byte[3 * 1024 * 1024];
        System.arraycopy(PDF_VALIDO, 0, grande, 0, 9);                                  // cabecera
        byte[] cierre = "\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(cierre, 0, grande, grande.length - cierre.length, cierre.length); // cierre
        assertDoesNotThrow(() -> servicio.validarPdf(pdf("grande.pdf", grande)));
    }

    @Test
    void guardaConHuellaSha256Correcta() throws Exception { // CD-15
        var guardado = servicio.guardarPdf(pdf("ok.pdf", PDF_VALIDO), "TEST", "TIC-2026-000001");

        String esperado = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PDF_VALIDO));
        assertEquals(esperado, guardado.sha256());
        assertEquals(PDF_VALIDO.length, guardado.tamanio());
        assertTrue(Files.exists(guardado.ruta()));
        assertEquals(esperado, servicio.calcularSha256(guardado.ruta()));
    }

    @Test
    void dosCargasSeguidasNoSeSobrescriben() {
        var a = servicio.guardarPdf(pdf("a.pdf", PDF_VALIDO), "TEST", "TIC-2026-000001");
        var b = servicio.guardarPdf(pdf("b.pdf", PDF_VALIDO), "TEST", "TIC-2026-000001");
        assertNotEquals(a.ruta(), b.ruta());
    }

    @Test
    void archivoSeBorraSiLaTransaccionSeRevierte() { // CD-14
        TransactionSynchronizationManager.initSynchronization();
        var guardado = servicio.guardarPdf(pdf("ok.pdf", PDF_VALIDO), "TEST", "TIC-2026-000001");
        assertTrue(Files.exists(guardado.ruta()));

        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        assertFalse(Files.exists(guardado.ruta()), "El archivo huérfano debe eliminarse tras el rollback");
    }

    @Test
    void archivoSeConservaSiLaTransaccionConfirma() {
        TransactionSynchronizationManager.initSynchronization();
        var guardado = servicio.guardarPdf(pdf("ok.pdf", PDF_VALIDO), "TEST", "TIC-2026-000001");

        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        assertTrue(Files.exists(guardado.ruta()));
    }
}
