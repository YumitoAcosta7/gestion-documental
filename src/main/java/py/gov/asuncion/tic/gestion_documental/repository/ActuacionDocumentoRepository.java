package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.ActuacionDocumento;

import java.util.List;

@Repository
public interface ActuacionDocumentoRepository extends JpaRepository<ActuacionDocumento, Long> {
    List<ActuacionDocumento> findByDocumentoIdOrderByFechaHoraActuacionAsc(Long documentoId);

    ActuacionDocumento findTopByDocumentoIdAndDependenciaReceptoraPaseIdOrderByFechaHoraActuacionDesc(Long documentoId, Integer dependenciaReceptoraId);
}