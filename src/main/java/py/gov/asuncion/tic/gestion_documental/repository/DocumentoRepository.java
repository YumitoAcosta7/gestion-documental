package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import py.gov.asuncion.tic.gestion_documental.domain.Documento;
import py.gov.asuncion.tic.gestion_documental.domain.EstadoDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.Prioridad;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    long countByEstadoActual(EstadoDocumento estado);

    long countByPrioridadAndEstadoActualNot(Prioridad prioridad, EstadoDocumento estadoFinalizado);

    List<Documento> findByEstadoActualNotOrderByFechaHoraIngresoDesc(EstadoDocumento estado);

    boolean existsByCodigoCuti(String codigoCuti);

    long countByAnio(Integer anio);

    List<Documento> findByEstadoActualOrderByFechaHoraSalidaDesc(EstadoDocumento estado);

    // 🛡️ FILTRO DE TRÁMITES ACTIVOS POR DEPENDENCIA (Requerido por DocumentoService L:554)
    @Query("""
        SELECT DISTINCT d FROM Documento d 
        LEFT JOIN ActuacionDocumento a ON a.documento.id = d.id 
        WHERE d.estadoActual <> 'FINALIZADO' 
          AND (
               a.dependenciaEmisora.id = :dependenciaId 
               OR LOWER(a.descripcionResumen) LIKE LOWER(CONCAT('%➔ ', (SELECT dep.nombre FROM Dependencia dep WHERE dep.id = :dependenciaId), '%'))
              )
        ORDER BY d.fechaHoraIngreso DESC
    """)
    List<Documento> findDocumentosActivosPorDependencia(@Param("dependenciaId") Integer dependenciaId);

    // 🛡️ FILTRO DE TRÁMITES FINALIZADOS POR DEPENDENCIA
    @Query("""
        SELECT DISTINCT d FROM Documento d 
        LEFT JOIN ActuacionDocumento a ON a.documento.id = d.id 
        WHERE d.estadoActual = 'FINALIZADO' 
          AND (
               a.dependenciaEmisora.id = :dependenciaId 
               OR LOWER(a.descripcionResumen) LIKE LOWER(CONCAT('%➔ ', (SELECT dep.nombre FROM Dependencia dep WHERE dep.id = :dependenciaId), '%'))
              )
        ORDER BY d.fechaHoraSalida DESC
    """)
    List<Documento> findDocumentosFinalizadosPorDependencia(@Param("dependenciaId") Integer dependenciaId);

    @Query("SELECT d FROM Documento d WHERE " +
           "(CAST(:texto AS string) IS NULL OR " +
           " LOWER(d.codigoCuti) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) OR " +
           " LOWER(d.asunto) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) OR " +
           " LOWER(d.numeroDocumentoOrigen) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) OR " +
           " LOWER(d.nroNotaSalida) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%'))) AND " +
           "(:dependenciaId IS NULL OR d.dependenciaOrigen.id = :dependenciaId) AND " +
           "(:tipoDocId IS NULL OR d.tipoDocumento.id = :tipoDocId) AND " +
           "(:estado IS NULL OR d.estadoActual = :estado) AND " +
           "(CAST(:fechaDesde AS timestamp) IS NULL OR d.fechaHoraIngreso >= :fechaDesde) AND " +
           "(CAST(:fechaHasta AS timestamp) IS NULL OR d.fechaHoraIngreso <= :fechaHasta) " +
           "ORDER BY d.fechaHoraIngreso DESC")
    List<Documento> buscarMulticriterio(
            @Param("texto") String texto,
            @Param("dependenciaId") Integer dependenciaId,
            @Param("tipoDocId") Integer tipoDocId,
            @Param("estado") EstadoDocumento estado,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta
    );
}