package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.SolicitudContrasena;

import java.util.List;

@Repository
public interface SolicitudContrasenaRepository extends JpaRepository<SolicitudContrasena, Long> {

    @Query("SELECT COUNT(s) > 0 FROM SolicitudContrasena s WHERE s.usuario.id = :usuarioId AND s.estado = 'PENDIENTE'")
    boolean existePendiente(@Param("usuarioId") Integer usuarioId);

    @Query("""
        SELECT s FROM SolicitudContrasena s JOIN FETCH s.usuario JOIN FETCH s.solicitadaPor
        WHERE s.estado = 'PENDIENTE' ORDER BY s.fechaSolicitud
    """)
    List<SolicitudContrasena> findPendientes();
}
