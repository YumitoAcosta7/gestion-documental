package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.LegajoSalida;

import java.util.List;

@Repository
public interface LegajoSalidaRepository extends JpaRepository<LegajoSalida, Long> {

    @Query("SELECT l FROM LegajoSalida l WHERE l.documento.id = :documentoId")
    List<LegajoSalida> findByDocumento(@Param("documentoId") Long documentoId);
}
