package py.gov.asuncion.tic.gestion_documental.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import py.gov.asuncion.tic.gestion_documental.domain.Documento;
import py.gov.asuncion.tic.gestion_documental.domain.EstadoDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.Prioridad;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    long countByEstadoActual(EstadoDocumento estado);

    long countByPrioridadAndEstadoActualNotIn(Prioridad prioridad, java.util.Collection<EstadoDocumento> estados);

    // Método para obtener los documentos emitidos/creados por tu propia dependencia
    List<Documento> findByDependenciaOrigenIdOrderByFechaHoraIngresoDesc(Integer dependenciaOrigenId);

    // 🔒 Bloquea la fila del expediente hasta el fin de la transacción (SELECT ... FOR UPDATE).
    // Serializa las operaciones simultáneas sobre un mismo expediente (derivar, anular, confirmar...).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Documento d WHERE d.id = :id")
    Optional<Documento> findByIdParaActualizar(@Param("id") Long id);

    // 🔢 CORRELATIVO DEL CUTI DE UNA CABECERA
    // Incrementa la serie (cabecera, año) en una sola sentencia: la fila queda bloqueada hasta el commit,
    // así dos registros simultáneos no obtienen el mismo número. La primera vez arranca desde el mayor
    // número ya usado con ese prefijo, por si hubiera expedientes cargados antes de la serie.
    @Query(value = """
        INSERT INTO series_cuti (dependencia_id, anio, ultimo)
        VALUES (:cabeceraId, :anio,
                (SELECT COALESCE(MAX(CAST(SUBSTRING(codigo_cuti FROM '[0-9]+$') AS INTEGER)), 0)
                   FROM documentos WHERE codigo_cuti LIKE :prefijo) + 1)
        ON CONFLICT (dependencia_id, anio) DO UPDATE SET ultimo = series_cuti.ultimo + 1
        RETURNING ultimo
        """, nativeQuery = true)
    Integer siguienteCorrelativoCuti(@Param("cabeceraId") Integer cabeceraId, @Param("anio") int anio,
                                     @Param("prefijo") String prefijo);

    // 🧭 ÁMBITO DEL USUARIO (docs/diseno-fase2-multiarea.md §3.2)
    // Dependencias abarcadas por sus asignaciones vigentes hoy, por la ruta del organigrama.
    // Sin asignaciones vigentes, el ámbito es solo su propia dependencia.
    String CTE_AMBITO = """
        WITH amb AS (
            SELECT dep.ruta, a.incluye_subordinadas
              FROM asignaciones a JOIN dependencias dep ON dep.id = a.dependencia_ambito_id
             WHERE a.usuario_id = :usuarioId AND a.activa AND a.rol <> 'ADMIN_SISTEMA'
               AND a.vigente_desde <= CURRENT_DATE AND (a.vigente_hasta IS NULL OR a.vigente_hasta >= CURRENT_DATE)
            UNION ALL
            SELECT dep.ruta, FALSE
              FROM usuarios u JOIN dependencias dep ON dep.id = u.dependencia_id
             WHERE u.id = :usuarioId
               AND NOT EXISTS (SELECT 1 FROM asignaciones a WHERE a.usuario_id = :usuarioId AND a.activa AND a.rol <> 'ADMIN_SISTEMA'
                                 AND a.vigente_desde <= CURRENT_DATE AND (a.vigente_hasta IS NULL OR a.vigente_hasta >= CURRENT_DATE))
        ), deps AS (
            SELECT x.id FROM dependencias x JOIN amb ON x.ruta = amb.ruta OR (amb.incluye_subordinadas AND x.ruta LIKE amb.ruta || '%')
        )
        """;

    // Un expediente está en el ámbito si lo envió, lo registró, lo derivó o lo recibió una dependencia del ámbito
    String DOCUMENTO_EN_AMBITO = """
        (d.dependencia_origen_id IN (SELECT id FROM deps)
         OR EXISTS (SELECT 1 FROM usuarios uc WHERE uc.id = d.usuario_carga_ingreso_id AND uc.dependencia_id IN (SELECT id FROM deps))
         OR EXISTS (SELECT 1 FROM actuaciones_documento a WHERE a.documento_id = d.id
                      AND (a.dependencia_emisora_id IN (SELECT id FROM deps)
                           OR (a.dependencia_receptora_pase_id IN (SELECT id FROM deps) AND NOT a.anulada))))
        """;

    @Query(value = CTE_AMBITO + "SELECT EXISTS (SELECT 1 FROM documentos d WHERE d.id = :documentoId AND " + DOCUMENTO_EN_AMBITO + ")",
           nativeQuery = true)
    boolean existeEnAmbito(@Param("usuarioId") Integer usuarioId, @Param("documentoId") Long documentoId);

    @Query(value = CTE_AMBITO + "SELECT d.* FROM documentos d WHERE d.estado_actual NOT IN ('FINALIZADO', 'ARCHIVADO') AND "
                 + DOCUMENTO_EN_AMBITO + " ORDER BY d.fecha_hora_ingreso DESC", nativeQuery = true)
    List<Documento> findActivosEnAmbito(@Param("usuarioId") Integer usuarioId);

    @Query(value = CTE_AMBITO + "SELECT d.* FROM documentos d WHERE d.estado_actual = 'FINALIZADO' AND "
                 + DOCUMENTO_EN_AMBITO + " ORDER BY d.fecha_hora_salida DESC NULLS LAST", nativeQuery = true)
    List<Documento> findFinalizadosEnAmbito(@Param("usuarioId") Integer usuarioId);

    // 🛡️ FILTRO DE TRÁMITES ACTIVOS POR DEPENDENCIA
    // Se usa la dependencia receptora del pase (obligatoria desde V15: ya no se busca por el nombre
    // en la descripción, que se repite entre dependencias)
    @Query("""
        SELECT DISTINCT d FROM Documento d
        LEFT JOIN ActuacionDocumento a ON a.documento.id = d.id
        LEFT JOIN a.dependenciaReceptoraPase r
        WHERE d.estadoActual NOT IN ('FINALIZADO', 'ARCHIVADO')
          AND (
               a.dependenciaEmisora.id = :dependenciaId
               OR (r.id = :dependenciaId AND a.anulada = false)
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
              )
        ORDER BY d.fechaHoraSalida DESC
    """)
    List<Documento> findDocumentosFinalizadosPorDependencia(@Param("dependenciaId") Integer dependenciaId);

    // El texto también busca en el bibliorato (código, descripción o texto histórico)
    @Query("SELECT d FROM Documento d LEFT JOIN d.bibliorato b WHERE " +
           "(CAST(:texto AS string) IS NULL OR " +
           " LOWER(d.codigoCuti) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) OR " +
           " LOWER(d.asunto) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) OR " +
           " LOWER(d.numeroDocumentoOrigen) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) OR " +
           " LOWER(d.nroNotaSalida) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) OR " +
           " LOWER(d.ubicacionBibliorato) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) OR " +
           " LOWER(b.codigo) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) OR " +
           " LOWER(b.descripcion) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%'))) AND " +
           "(:dependenciaId IS NULL OR d.dependenciaOrigen.id = :dependenciaId) AND " +
           "(:tipoDocId IS NULL OR d.tipoDocumento.id = :tipoDocId) AND " +
           "(:estado IS NULL OR d.estadoActual = :estado) AND " +
           "(:biblioratoId IS NULL OR b.id = :biblioratoId) AND " +
           "(CAST(:fechaDesde AS timestamp) IS NULL OR d.fechaHoraIngreso >= :fechaDesde) AND " +
           "(CAST(:fechaHasta AS timestamp) IS NULL OR d.fechaHoraIngreso <= :fechaHasta) " +
           "ORDER BY d.fechaHoraIngreso DESC")
    List<Documento> buscarMulticriterio(
            @Param("texto") String texto,
            @Param("dependenciaId") Integer dependenciaId,
            @Param("tipoDocId") Integer tipoDocId,
            @Param("estado") EstadoDocumento estado,
            @Param("biblioratoId") Integer biblioratoId,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta
    );

    // 📚 Archivo físico: documentos con bibliorato asignado o archivados, ordenados por ubicación
    @Query("""
        SELECT d FROM Documento d LEFT JOIN d.bibliorato b
        WHERE (b IS NOT NULL OR d.estadoActual = 'ARCHIVADO')
          AND (:biblioratoId IS NULL OR b.id = :biblioratoId)
        ORDER BY b.codigo, d.fojaDesde, d.fechaHoraIngreso DESC
    """)
    List<Documento> findDocumentosArchivo(@Param("biblioratoId") Integer biblioratoId);

    // Documentos del bibliorato cuyas fojas se superponen con el rango indicado
    @Query("""
        SELECT d FROM Documento d
        WHERE d.bibliorato.id = :biblioratoId AND d.fojaDesde IS NOT NULL
          AND d.fojaDesde <= :hasta AND d.fojaHasta >= :desde
          AND (:excluirId IS NULL OR d.id <> :excluirId)
    """)
    List<Documento> findFojasSuperpuestas(@Param("biblioratoId") Integer biblioratoId,
                                         @Param("desde") Integer desde,
                                         @Param("hasta") Integer hasta,
                                         @Param("excluirId") Long excluirId);
}