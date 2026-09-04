package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.AdjuntoDocumento;

import java.util.List;

@Repository
public interface AdjuntoDocumentoRepository extends JpaRepository<AdjuntoDocumento, Long> {
    List<AdjuntoDocumento> findByDocumentoId(Long documentoId);
}