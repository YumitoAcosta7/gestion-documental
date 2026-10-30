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

    // Método para obtener los documentos emitidos/creados por tu propia dependencia
    List<Documento> findByDependenciaOrigenIdOrderByFechaHoraIngresoDesc(Integer dependenciaOrigenId);

    List<Documento> findByEstadoActualNotOrderByFechaHoraIngresoDesc(EstadoDocumento estado);

    boolean existsByCodigoCuti(String codigoCuti);

    long countByAnio(Integer anio);

    // 🔢 CORRELATIVO DEL CUTI
    // Bloqueo transaccional de PostgreSQL: dos ingresos simultáneos no pueden calcular el mismo número.
    // Se libera solo al terminar la transacción (no requiere crear objetos en la BD).
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:clave)) AS bloqueo", nativeQuery = true)
    Integer bloquearCorrelativoCuti(@Param("clave") long clave);

    // Último número usado en el año (toma el sufijo numérico del CUTI, no la cantidad de filas)
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(codigo_cuti FROM '[0-9]+$') AS INTEGER)), 0) "
                 + "FROM documentos WHERE codigo_cuti LIKE :prefijo", nativeQuery = true)
    Integer obtenerUltimoCorrelativoCuti(@Param("prefijo") String prefijo);

    List<Documento> findByEstadoActualOrderByFechaHoraSalidaDesc(EstadoDocumento estado);

    // 🛡️ FILTRO DE TRÁMITES ACTIVOS POR DEPENDENCIA
    // Se usa la dependencia receptora del pase; la descripción "➔ <dependencia>:" solo
    // se consulta para pases antiguos que no tienen dependencia receptora cargada.
    @Query("""
        SELECT DISTINCT d FROM Documento d
        LEFT JOIN ActuacionDocumento a ON a.documento.id = d.id
        LEFT JOIN a.dependenciaReceptoraPase r
        WHERE d.estadoActual <> 'FINALIZADO'
          AND (
               a.dependenciaEmisora.id = :dependenciaId
               OR (r.id = :dependenciaId AND a.anulada = false)
               OR (r IS NULL AND a.anulada = false AND LOWER(a.descripcionResumen) LIKE LOWER(CONCAT('%➔ ', (SELECT dep.nombre FROM Dependencia dep WHERE dep.id = :dependenciaId), ':%')))
              )
        ORDER BY d.fechaHoraIngreso DESC
    """)
    List<Documento> findDocumentosActivosPorDependencia(@Param("dependenciaId") Integer dependenciaId);

    // 🛡️ FILTRO DE TRÁMITES FINALIZADOS POR DEPENDENCIA
    @Query("""
        SELECT DISTINCT d FROM Documento d 
        LEFT JOIN ActuacionDocumento a ON a.documento.id = d.id
        LEFT JOIN a.dependenciaReceptoraPase r
        WHERE d.estadoActual = 'FINALIZADO'
          AND (
               a.dependenciaEmisora.id = :dependenciaId
               OR (r.id = :dependenciaId AND a.anulada = false)
               OR (r IS NULL AND a.anulada = false AND LOWER(a.descripcionResumen) LIKE LOWER(CONCAT('%➔ ', (SELECT dep.nombre FROM Dependencia dep WHERE dep.id = :dependenciaId), ':%')))
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