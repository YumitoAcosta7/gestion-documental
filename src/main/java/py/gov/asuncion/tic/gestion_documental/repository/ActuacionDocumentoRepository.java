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

    // ¿Hay un pase de trámite (entrega del original) esperando acuse?
    @Query("""
        SELECT COUNT(a) > 0 FROM ActuacionDocumento a
        WHERE a.documento.id = :documentoId AND a.tipoPase = py.gov.asuncion.tic.gestion_documental.domain.TipoPase.TRAMITE
          AND a.tipoActuacion = 'PASE_INTERNO' AND a.anulada = false AND a.paseConfirmado = false
    """)
    boolean existePaseTramitePendiente(@Param("documentoId") Long documentoId);

    // Todos los pases esperando acuse, los más viejos primero (auditoría M2: panel de pases trabados)
    @Query("""
        SELECT a FROM ActuacionDocumento a JOIN FETCH a.documento JOIN FETCH a.dependenciaEmisora
        LEFT JOIN FETCH a.dependenciaReceptoraPase
        WHERE a.tipoActuacion = 'PASE_INTERNO' AND a.anulada = false AND a.paseConfirmado = false
        ORDER BY a.fechaHoraActuacion
    """)
    List<ActuacionDocumento> findPasesPendientes();

    // Pases esperando acuse emitidos por dependencias del ámbito del usuario o por la suya (aviso de plazos)
    @Query(value = DocumentoRepository.CTE_AMBITO + """
        SELECT a.* FROM actuaciones_documento a
         WHERE a.tipo_actuacion = 'PASE_INTERNO' AND NOT a.anulada AND NOT a.pase_confirmado
           AND (a.dependencia_emisora_id IN (SELECT id FROM deps) OR a.dependencia_emisora_id IN (SELECT id FROM propia))
         ORDER BY a.fecha_hora_actuacion
        """, nativeQuery = true)
    List<ActuacionDocumento> findPasesPendientesEmitidos(@Param("usuarioId") Integer usuarioId);

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