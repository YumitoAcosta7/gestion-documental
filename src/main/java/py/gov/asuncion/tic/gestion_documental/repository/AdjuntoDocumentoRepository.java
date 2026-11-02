package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.AdjuntoDocumento;

import java.util.List;

@Repository
public interface AdjuntoDocumentoRepository extends JpaRepository<AdjuntoDocumento, Long> {
    List<AdjuntoDocumento> findByDocumentoId(Long documentoId);

    // Adjuntos propios del documento (salida / acuse final), sin los de actuaciones
    List<AdjuntoDocumento> findByDocumentoIdAndActuacionIsNullOrderByFechaHoraSubidaAsc(Long documentoId);

    // Versiones del PDF de una actuación, de la más antigua a la vigente
    List<AdjuntoDocumento> findByActuacionIdOrderByFechaHoraSubidaAsc(Long actuacionId);

    // Versiones de varias actuaciones a la vez (árbol de trazabilidad)
    List<AdjuntoDocumento> findByActuacionIdInOrderByFechaHoraSubidaAsc(List<Long> actuacionIds);
}