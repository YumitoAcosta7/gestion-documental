package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.ActuacionDocumento;

import java.util.List;

@Repository
public interface ActuacionDocumentoRepository extends JpaRepository<ActuacionDocumento, Long> {
    List<ActuacionDocumento> findByDocumentoIdOrderByFechaHoraActuacionAsc(Long documentoId);

    ActuacionDocumento findTopByDocumentoIdAndDependenciaReceptoraPaseIdOrderByFechaHoraActuacionDesc(Long documentoId, Integer dependenciaReceptoraId);

    // 🛡️ ¿La dependencia emitió o recibió alguna actuación del documento? (mismo criterio que las bandejas)
    @Query("""
        SELECT COUNT(a) > 0 FROM ActuacionDocumento a
        LEFT JOIN a.dependenciaReceptoraPase r
        WHERE a.documento.id = :documentoId
          AND (
               a.dependenciaEmisora.id = :dependenciaId
               OR r.id = :dependenciaId
               OR (r IS NULL AND LOWER(a.descripcionResumen) LIKE LOWER(CONCAT('%➔ ', (SELECT dep.nombre FROM Dependencia dep WHERE dep.id = :dependenciaId), ':%')))
              )
    """)
    boolean existeParticipacionDependencia(@Param("documentoId") Long documentoId,
                                           @Param("dependenciaId") Integer dependenciaId);

    ActuacionDocumento findTopByDocumentoIdAndDependenciaEmisoraIdAndTipoActuacionOrderByFechaHoraActuacionDesc(
            Long documentoId, Integer dependenciaEmisoraId, String tipoActuacion);
}