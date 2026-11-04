package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.SolicitudReapertura;

import java.util.List;

@Repository
public interface SolicitudReaperturaRepository extends JpaRepository<SolicitudReapertura, Long> {

    @Query("SELECT COUNT(s) > 0 FROM SolicitudReapertura s WHERE s.documento.id = :documentoId AND s.estado = 'PENDIENTE'")
    boolean existePendiente(@Param("documentoId") Long documentoId);

    @Query("""
        SELECT s FROM SolicitudReapertura s JOIN FETCH s.documento d JOIN FETCH s.solicitadaPor
        LEFT JOIN FETCH d.dependenciaCustodia LEFT JOIN FETCH d.dependenciaRegistro LEFT JOIN FETCH d.dependenciaOrigen
        WHERE s.estado = 'PENDIENTE' ORDER BY s.fechaSolicitud
    """)
    List<SolicitudReapertura> findPendientes();
}
