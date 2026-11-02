package py.gov.asuncion.tic.gestion_documental.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

// 📎 Almacenamiento de PDF en disco.
// - Nunca sobrescribe: cada archivo recibe un nombre único.
// - Registra la huella SHA-256 para verificar después su integridad.
// - Si la transacción que lo registra falla, borra el archivo (no quedan huérfanos).
@Service
public class ArchivoService {

    private static final Logger log = LoggerFactory.getLogger(ArchivoService.class);

    public static final String TIPO_MIME_PDF = "application/pdf";

    // Un PDF completo termina con "%%EOF" (se busca en los últimos bytes, puede haber saltos de línea después)
    private static final int BYTES_FINALES_PDF = 2048;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.upload.max-pdf-size:20MB}")
    private DataSize tamanioMaximo;

    public record ArchivoGuardado(Path ruta, String sha256, long tamanio, String tipoMime) {}

    // Valida que el archivo exista, no supere el tamaño permitido y sea un PDF completo:
    // firma "%PDF-" al inicio y marca "%%EOF" al final (no solo la extensión).
    public void validarPdf(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar un archivo PDF.");
        }
        if (archivo.getSize() > tamanioMaximo.toBytes()) {
            throw new IllegalArgumentException("El PDF supera el tamaño máximo permitido (" + tamanioMaximo.toMegabytes() + " MB).");
        }
        String nombre = archivo.getOriginalFilename();
        try (InputStream in = archivo.getInputStream()) {
            byte[] firma = in.readNBytes(5);
            if (!"%PDF-".equals(new String(firma, StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("El archivo '" + nombre + "' no es un PDF válido.");
            }
            long aSaltar = archivo.getSize() - 5 - BYTES_FINALES_PDF;
            if (aSaltar > 0) {
                in.skipNBytes(aSaltar);
            }
            String finalArchivo = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            if (!finalArchivo.contains("%%EOF")) {
                throw new IllegalArgumentException("El PDF '" + nombre + "' está incompleto o dañado (falta el cierre del archivo). Vuelva a escanearlo o exportarlo.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo adjunto: " + e.getMessage());
        }
    }

    // Valida y guarda el PDF calculando su huella SHA-256 en la misma lectura
    public ArchivoGuardado guardarPdf(MultipartFile archivo, String prefijo, String codigoCuti) {
        validarPdf(archivo);
        Path destino = null;
        try {
            Path carpeta = Paths.get(uploadDir);
            Files.createDirectories(carpeta);
            // Marca de tiempo + sufijo aleatorio: dos cargas en el mismo milisegundo no chocan
            destino = carpeta.resolve(prefijo + "_" + codigoCuti + "_" + System.currentTimeMillis()
                    + "_" + UUID.randomUUID().toString().substring(0, 8) + ".pdf");

            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(archivo.getInputStream(), sha)) {
                Files.copy(in, destino); // sin REPLACE_EXISTING: nunca sobrescribe
            }
            borrarSiLaTransaccionFalla(destino);
            return new ArchivoGuardado(destino, HexFormat.of().formatHex(sha.digest()), Files.size(destino), TIPO_MIME_PDF);
        } catch (IOException | NoSuchAlgorithmException e) {
            borrarSilenciosamente(destino); // copia parcial
            throw new RuntimeException("Error al guardar el archivo PDF: " + e.getMessage(), e);
        }
    }

    // Si la transacción de base de datos se revierte, el archivo escrito no tiene registro: se elimina
    private void borrarSiLaTransaccionFalla(Path archivo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    borrarSilenciosamente(archivo);
                    log.info("Archivo huérfano eliminado tras rollback: {}", archivo.getFileName());
                }
            }
        });
    }

    private void borrarSilenciosamente(Path archivo) {
        if (archivo == null) {
            return;
        }
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            log.warn("No se pudo eliminar el archivo {}: {}", archivo, e.getMessage());
        }
    }

    // Huella SHA-256 del archivo en disco (para verificar integridad)
    public String calcularSha256(Path archivo) {
        try (InputStream in = Files.newInputStream(archivo)) {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int leidos;
            while ((leidos = in.read(buffer)) != -1) {
                sha.update(buffer, 0, leidos);
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("No se pudo leer el archivo para verificar su integridad: " + e.getMessage(), e);
        }
    }

    // Ubica un PDF por su ruta o nombre guardado dentro de app.upload.dir.
    // Solo usa el nombre del archivo para evitar rutas manipuladas.
    public Path resolver(String rutaGuardada) {
        if (rutaGuardada == null || rutaGuardada.isBlank()) {
            return null;
        }
        Path candidato = Paths.get(uploadDir).resolve(Paths.get(rutaGuardada).getFileName());
        return Files.isRegularFile(candidato) ? candidato : null;
    }
}
