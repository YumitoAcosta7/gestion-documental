package py.gov.asuncion.tic.gestion_documental.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import py.gov.asuncion.tic.gestion_documental.domain.Dependencia;
import py.gov.asuncion.tic.gestion_documental.domain.Documento;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.AsignacionRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Auditoría B1 y B2: el organigrama se lee de la base solo cuando cambió, y el acceso a una lista
// de expedientes se resuelve por lotes, no expediente por expediente.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HallazgosBajosTest {

    @Mock DependenciaRepository dependenciaRepository;
    @Mock AsignacionRepository asignacionRepository;
    @Mock DocumentoRepository documentoRepository;
    @InjectMocks DocumentoService documentoService;

    private CanalService canal() {
        when(dependenciaRepository.findNiveles()).thenReturn(List.of());
        when(dependenciaRepository.findAtajosActivos()).thenReturn(List.of());
        when(dependenciaRepository.findAll()).thenReturn(List.of(
                Dependencia.builder().id(1).nombre("Intendencia").sigla("INT").activo(true).build()));
        return new CanalService(dependenciaRepository, asignacionRepository);
    }

    @Test
    void elOrganigramaSeArmaUnaVezPorVersion() {
        CanalService canal = canal();
        when(dependenciaRepository.versionOrganigrama()).thenReturn(7L);

        Organigrama primero = canal.organigrama();
        assertSame(primero, canal.organigrama(), "misma versión: el mismo organigrama en memoria");
        assertSame(primero, canal.organigrama());
        verify(dependenciaRepository, times(1)).findAll();

        // Alguien modificó el organigrama: los triggers de V16 incrementan la versión
        when(dependenciaRepository.versionOrganigrama()).thenReturn(8L);
        assertNotSame(primero, canal.organigrama(), "versión nueva: se vuelve a leer");
        verify(dependenciaRepository, times(2)).findAll();
    }

    @Test
    void elFiltroDeAccesoConsultaPorLotesYConservaElOrden() {
        Usuario usuario = new Usuario();
        usuario.setId(5);
        List<Documento> documentos = new ArrayList<>();
        LongStream.rangeClosed(1, 2500).forEach(id -> {
            Documento d = new Documento();
            d.setId(id);
            documentos.add(d);
        });
        // Puede ver solo los pares
        when(documentoRepository.idsAccesibles(eq(5), anyCollection())).thenAnswer(inv ->
                ((Collection<Long>) inv.getArgument(1)).stream().filter(id -> id % 2 == 0).toList());

        List<Documento> visibles = documentoService.filtrarAccesibles(usuario, documentos);

        assertEquals(1250, visibles.size());
        assertEquals(2L, visibles.get(0).getId());
        assertEquals(2500L, visibles.get(visibles.size() - 1).getId());
        verify(documentoRepository, times(3)).idsAccesibles(eq(5), anyCollection()); // 1000 + 1000 + 500
        verify(documentoRepository, never()).existeEnAmbito(anyInt(), anyLong());
    }

    @Test
    void seRechazanLosPdfConCodigoEjecutable() {
        assertEquals("/JavaScript", ArchivoService.buscarContenidoActivo(
                pdf("1 0 obj << /Type /Catalog /OpenAction << /S /JavaScript /JS (app.alert(1)) >> >> endobj")));
        assertEquals("/Launch", ArchivoService.buscarContenidoActivo(pdf("<< /S /Launch /F (cmd.exe) >>")));
        // Nombre disfrazado con caracteres #xx
        assertEquals("/JavaScript", ArchivoService.buscarContenidoActivo(pdf("<< /S /J#61vaScript >>")));
    }

    @Test
    void unNombreParecidoNoSeConfundeConJS() {
        // Solo la primera pasada (texto): /JSize no es /JS. El archivo no es un PDF completo, así que
        // la segunda pasada (PDFBox) lo rechaza por no poder analizarlo.
        var e = assertThrows(IllegalArgumentException.class, () -> ArchivoService.buscarContenidoActivo(pdf(
                "1 0 obj << /Type /XObject /Subtype /Image /Filter /DCTDecode /JSize 10 >> endobj")));
        assertTrue(e.getMessage().contains("no se puede verificar"));
    }

    @Test
    void unPdfRealSinCodigoSeAcepta() {
        assertNull(ArchivoService.buscarContenidoActivo(ArchivoServiceTest.pdfReal(1000)));
    }

    @Test
    void seDetectaElJavaScriptEscondidoEnUnFlujoComprimido() throws Exception {
        byte[] conCodigo;
        byte[] limpio;
        try (var doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            var salida = new java.io.ByteArrayOutputStream();
            doc.save(salida);
            limpio = salida.toByteArray();
        }
        try (var doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.getDocumentCatalog().setOpenAction(
                    new org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript("app.alert('hola')"));
            var salida = new java.io.ByteArrayOutputStream();
            doc.save(salida, org.apache.pdfbox.pdfwriter.compress.CompressParameters.DEFAULT_COMPRESSION);
            conCodigo = salida.toByteArray();
        }
        String crudo = new String(conCodigo, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(crudo.contains("JavaScript"), "comprimido: el texto del archivo no lo muestra");
        assertEquals("/JS", ArchivoService.buscarContenidoActivo(conCodigo).replace("/JavaScript", "/JS"));
        assertNull(ArchivoService.buscarContenidoActivo(limpio), "un PDF de una página sin código se acepta");
    }

    private static byte[] pdf(String cuerpo) {
        return ("%PDF-1.7\n" + cuerpo + "\n%%EOF\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    @Test
    void sinExpedientesNoSeConsulta() {
        Usuario usuario = new Usuario();
        usuario.setId(5);
        assertTrue(documentoService.filtrarAccesibles(usuario, List.of()).isEmpty());
        verifyNoInteractions(documentoRepository);
    }
}
