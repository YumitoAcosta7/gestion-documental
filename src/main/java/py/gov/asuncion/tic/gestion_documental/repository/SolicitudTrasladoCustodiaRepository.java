package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.SolicitudTrasladoCustodia;

import java.util.List;
import java.util.Optional;

@Repository
public interface SolicitudTrasladoCustodiaRepository extends JpaRepository<SolicitudTrasladoCustodia, Long> {

    @Query("SELECT COUNT(s) > 0 FROM SolicitudTrasladoCustodia s WHERE s.documento.id = :documentoId AND s.estado = 'PENDIENTE'")
    boolean existePendiente(@Param("documentoId") Long documentoId);

    // Para tomar el expediente antes de leer la solicitud: quien espera el bloqueo ve la solicitud ya resuelta
    @Query("SELECT s.documento.id FROM SolicitudTrasladoCustodia s WHERE s.id = :id")
    Optional<Long> documentoIdDe(@Param("id") Long id);

    @Query("""
        SELECT s FROM SolicitudTrasladoCustodia s JOIN FETCH s.documento JOIN FETCH s.solicitadaPor
        JOIN FETCH s.dependenciaOrigen JOIN FETCH s.dependenciaDestino
        WHERE s.estado = 'PENDIENTE' ORDER BY s.fechaSolicitud
    """)
    List<SolicitudTrasladoCustodia> findPendientes();
}
