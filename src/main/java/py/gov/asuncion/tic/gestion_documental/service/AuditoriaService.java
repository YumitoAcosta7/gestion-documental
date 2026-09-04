package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .direccionIp(ip != null ? ip : "127.0.0.1")
                .fechaHora(LocalDateTime.now())
                .build();

        auditoriaRepository.save(aud);
    }

    public List<AuditoriaDocumento> obtenerHistorialAuditoria(Long documentoId) {
        return auditoriaRepository.findByDocumentoIdOrderByFechaHoraDesc(documentoId);
    }
}