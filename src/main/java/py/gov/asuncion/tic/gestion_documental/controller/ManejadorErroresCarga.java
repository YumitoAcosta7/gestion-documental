package py.gov.asuncion.tic.gestion_documental.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.support.RequestContextUtils;

// 📎 Errores de carga de archivos que ocurren antes de llegar al controlador: se muestran como
// mensaje en la bandeja en lugar de la página de error del servidor.
// Nota: el caso habitual (PDF de más de 20 MB) lo resuelve ArchivoService, porque el límite técnico
// de recepción (spring.servlet.multipart, 50 MB) es mayor. Este manejador cubre los errores de
// parseo multipart que lleguen al DispatcherServlet.
@ControllerAdvice
public class ManejadorErroresCarga {

    @Value("${app.upload.max-pdf-size:20MB}")
    private String tamanioMaximo;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String archivoDemasiadoGrande(HttpServletRequest request) {
        return volverConError(request, "El archivo supera el tamaño máximo permitido (" + tamanioMaximo
                + "). Reduzca la resolución del escaneo o divida el documento.");
    }

    @ExceptionHandler(MultipartException.class)
    public String errorDeCarga(HttpServletRequest request, MultipartException e) {
        return volverConError(request, "No se pudo recibir el archivo adjunto. Intente nuevamente.");
    }

    private String volverConError(HttpServletRequest request, String mensaje) {
        RequestContextUtils.getOutputFlashMap(request).put("error", mensaje);
        return "redirect:/";
    }
}
