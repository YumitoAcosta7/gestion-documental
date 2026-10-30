package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

// 📎 Almacenamiento de PDF en disco.
// Nunca sobrescribe: cada archivo recibe un nombre único (prefijo + CUTI + marca de tiempo).
@Service
public class ArchivoService {

    // Carpeta histórica relativa donde quedaron los PDF anteriores a la unificación
    private static final Path CARPETA_HISTORICA = Paths.get("uploads/acuses");

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.upload.max-pdf-size:20MB}")
    private DataSize tamanioMaximo;

    // Valida que el archivo exista, no supere el tamaño permitido y sea realmente un PDF
    // (firma "%PDF-" al inicio, no solo la extensión).
    public void validarPdf(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar un archivo PDF.");
        }
        if (archivo.getSize() > tamanioMaximo.toBytes()) {
            throw new IllegalArgumentException("El PDF supera el tamaño máximo permitido (" + tamanioMaximo.toMegabytes() + " MB).");
        }
        byte[] firma = new byte[5];
        try (InputStream in = archivo.getInputStream()) {
            if (in.readNBytes(firma, 0, 5) < 5 || !"%PDF-".equals(new String(firma, java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("El archivo '" + archivo.getOriginalFilename() + "' no es un PDF válido.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo adjunto: " + e.getMessage());
        }
    }

    // Valida y guarda el PDF; devuelve la ruta completa donde quedó almacenado
    public Path guardarPdf(MultipartFile archivo, String prefijo, String codigoCuti) {
        validarPdf(archivo);
        try {
            Path carpeta = Paths.get(uploadDir);
            Files.createDirectories(carpeta);
            Path destino = carpeta.resolve(prefijo + "_" + codigoCuti + "_" + System.currentTimeMillis() + ".pdf");
            try (InputStream in = archivo.getInputStream()) {
                Files.copy(in, destino); // sin REPLACE_EXISTING: nunca sobrescribe
            }
            return destino;
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo PDF: " + e.getMessage(), e);
        }
    }

    // Ubica un PDF por su ruta o nombre guardado. Solo usa el nombre del archivo para evitar
    // rutas manipuladas, y lo busca en app.upload.dir y en la carpeta histórica.
    public Path resolver(String rutaGuardada) {
        if (rutaGuardada == null || rutaGuardada.isBlank()) {
            return null;
        }
        Path nombre = Paths.get(rutaGuardada).getFileName();
        for (Path candidato : List.of(Paths.get(uploadDir).resolve(nombre), CARPETA_HISTORICA.resolve(nombre))) {
            if (Files.isRegularFile(candidato)) {
                return candidato;
            }
        }
        return null;
    }
}
