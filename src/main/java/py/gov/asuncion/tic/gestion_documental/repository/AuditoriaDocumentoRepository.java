package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.AuditoriaDocumento;

import java.util.List;

@Repository
public interface AuditoriaDocumentoRepository extends JpaRepository<AuditoriaDocumento, Long> {

    List<AuditoriaDocumento> findByDocumentoIdOrderByFechaHoraDesc(Long documentoId);

}