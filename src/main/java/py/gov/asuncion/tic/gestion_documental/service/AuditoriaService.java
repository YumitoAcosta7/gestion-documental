package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import py.gov.asuncion.tic.gestion_documental.domain.AuditoriaDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.Documento;
import py.gov.asuncion.tic.gestion_documental.domain.EstadoDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.AuditoriaDocumentoRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditoriaService {

    @Autowired
    private AuditoriaDocumentoRepository auditoriaRepository;

    @Transactional
    public void registrarEvento(Documento doc, 
                                String tipoEvento, 
                                EstadoDocumento estadoAnterior, 
                                EstadoDocumento estadoNuevo, 
                                String descripcion, 
                                Usuario usuario, 
                                String ip) {

        AuditoriaDocumento aud = AuditoriaDocumento.builder()
                .documento(doc)
                .tipoEvento(tipoEvento)
                .estadoAnterior(estadoAnterior != null ? estadoAnterior.name() : null)
                .estadoNuevo(estadoNuevo != null ? estadoNuevo.name() : null)
                .descripcionDetalle(descripcion)
                .usuarioUsername(usuario != null ? usuario.getUsername() : "SISTEMA")
                .usuarioNombreCompleto(usuario != null ? usuario.getNombreCompleto() : "Sistema Automático")
                .dependenciaUsuario(usuario != null && usuario.getDependencia() != null ? usuario.getDependencia().getNombre() : "N/A")
                .direccionIp(resolverIp(ip))
                .fechaHora(LocalDateTime.now())
                .build();

        auditoriaRepository.save(aud);
    }

    // Toma la IP de la petición HTTP en curso. Si el evento no proviene de una petición
    // (proceso interno), usa la IP recibida o "SISTEMA".
    // Si la aplicación se publica detrás de un proxy inverso, configurar
    // server.forward-headers-strategy=native para que getRemoteAddr() devuelva la IP del cliente.
    private String resolverIp(String ipRecibida) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            if (request.getRemoteAddr() != null) {
                return request.getRemoteAddr();
            }
        }
        return ipRecibida != null ? ipRecibida : "SISTEMA";
    }

    public List<AuditoriaDocumento> obtenerHistorialAuditoria(Long documentoId) {
        return auditoriaRepository.findByDocumentoIdOrderByFechaHoraDesc(documentoId);
    }
}