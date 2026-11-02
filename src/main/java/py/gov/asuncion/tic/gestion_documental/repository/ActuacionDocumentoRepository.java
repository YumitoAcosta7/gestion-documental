package py.gov.asuncion.tic.gestion_documental.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.ActuacionDocumento;

import java.util.List;
import java.util.Optional;

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
               OR (r.id = :dependenciaId AND a.anulada = false)
               OR (r IS NULL AND a.anulada = false AND LOWER(a.descripcionResumen) LIKE LOWER(CONCAT('%➔ ', (SELECT dep.nombre FROM Dependencia dep WHERE dep.id = :dependenciaId), ':%')))
              )
    """)
    boolean existeParticipacionDependencia(@Param("documentoId") Long documentoId,
                                           @Param("dependenciaId") Integer dependenciaId);

    ActuacionDocumento findTopByDocumentoIdAndDependenciaEmisoraIdAndTipoActuacionOrderByFechaHoraActuacionDesc(
            Long documentoId, Integer dependenciaEmisoraId, String tipoActuacion);

    // 🔒 Lee la actuación bloqueando su fila (SELECT ... FOR UPDATE): su estado no cambia hasta el commit
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ActuacionDocumento a WHERE a.id = :id")
    Optional<ActuacionDocumento> findByIdParaActualizar(@Param("id") Long id);

    // ¿La dependencia ya tiene un pase pendiente (sin acuse, no anulado) de este documento?
    @Query("""
        SELECT COUNT(a) > 0 FROM ActuacionDocumento a
        WHERE a.documento.id = :documentoId AND a.dependenciaReceptoraPase.id = :dependenciaId
          AND a.tipoActuacion = 'PASE_INTERNO' AND a.anulada = false AND a.paseConfirmado = false
    """)
    boolean existePasePendiente(@Param("documentoId") Long documentoId, @Param("dependenciaId") Integer dependenciaId);

    // Último pase vigente (no anulado) recibido por la dependencia: base de la devolución
    ActuacionDocumento findTopByDocumentoIdAndDependenciaReceptoraPaseIdAndAnuladaFalseOrderByFechaHoraActuacionDesc(
            Long documentoId, Integer dependenciaReceptoraId);

    // ⏱️ SLA: fecha del último movimiento vigente de cada documento (una sola consulta para toda la bandeja)
    @Query("""
        SELECT a.documento.id, MAX(a.fechaHoraActuacion) FROM ActuacionDocumento a
        WHERE a.documento.id IN :documentoIds AND a.anulada = false
        GROUP BY a.documento.id
    """)
    List<Object[]> obtenerUltimoMovimientoPorDocumento(@Param("documentoIds") List<Long> documentoIds);
}