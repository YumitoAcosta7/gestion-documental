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
    // Los 200 más recientes (auditoría B2: las listas de la pantalla no crecen sin límite)
    List<Documento> findTop200ByDependenciaOrigenIdOrderByFechaHoraIngresoDesc(Integer dependenciaOrigenId);

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
        ), propia AS (
            SELECT dependencia_id AS id FROM usuarios WHERE id = :usuarioId AND dependencia_id IS NOT NULL
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

    // Lo mismo que DocumentoService.puedeAccederDocumento, en SQL: el ámbito, más lo originado en la propia
    // dependencia del usuario o en lo que esta emitió o recibió una actuación
    String DOCUMENTO_ACCESIBLE = "(" + DOCUMENTO_EN_AMBITO + """
         OR d.dependencia_origen_id IN (SELECT id FROM propia)
         OR EXISTS (SELECT 1 FROM actuaciones_documento p WHERE p.documento_id = d.id
                      AND (p.dependencia_emisora_id IN (SELECT id FROM propia)
                           OR (p.dependencia_receptora_pase_id IN (SELECT id FROM propia) AND NOT p.anulada))))
        """;

    // 📦 Conjuntos de expedientes para las listas y contadores (auditoría B2, medido con 200.000 expedientes):
    // en lugar de comprobar expediente por expediente con EXISTS, se arma una vez cada conjunto con los índices
    // de origen, emisora y receptora, y las listas lo cruzan. Se agrega después de CTE_AMBITO; PostgreSQL solo
    // calcula los que la consulta usa.
    //   acc     = lo mismo que DOCUMENTO_ACCESIBLE (ámbito + propia dependencia)
    //   recibir = con un pase esperando acuse dirigido al ámbito
    //   emitio  = con alguna actuación emitida por el ámbito
    String CTE_CONJUNTOS = """
        , vistas AS (
            SELECT id FROM deps UNION SELECT id FROM propia
        ), acc AS (
            SELECT d.id FROM documentos d WHERE d.dependencia_origen_id IN (SELECT id FROM vistas)
            UNION
            SELECT d.id FROM documentos d JOIN usuarios uc ON uc.id = d.usuario_carga_ingreso_id
             WHERE uc.dependencia_id IN (SELECT id FROM deps)
            UNION
            SELECT a.documento_id FROM actuaciones_documento a WHERE a.dependencia_emisora_id IN (SELECT id FROM vistas)
            UNION
            SELECT a.documento_id FROM actuaciones_documento a
             WHERE NOT a.anulada AND a.dependencia_receptora_pase_id IN (SELECT id FROM vistas)
        ), recibir AS (
            SELECT DISTINCT a.documento_id AS id FROM actuaciones_documento a
             WHERE a.tipo_actuacion = 'PASE_INTERNO' AND NOT a.pase_confirmado AND NOT a.anulada
               AND a.dependencia_receptora_pase_id IN (SELECT id FROM deps)
        ), emitio AS (
            SELECT DISTINCT a.documento_id AS id FROM actuaciones_documento a WHERE a.dependencia_emisora_id IN (SELECT id FROM deps)
        )
        """;

    String EN_ACC = "d.id IN (SELECT id FROM acc)";

    @Query(value = CTE_AMBITO + "SELECT EXISTS (SELECT 1 FROM documentos d WHERE d.id = :documentoId AND " + DOCUMENTO_EN_AMBITO + ")",
           nativeQuery = true)
    boolean existeEnAmbito(@Param("usuarioId") Integer usuarioId, @Param("documentoId") Long documentoId);

    // De los expedientes indicados, los que el usuario puede ver, en una sola consulta (auditoría B2:
    // el buscador y el archivo ya no verifican el acceso expediente por expediente)
    @Query(value = CTE_AMBITO + "SELECT d.id FROM documentos d WHERE d.id IN (:ids) AND " + DOCUMENTO_ACCESIBLE,
           nativeQuery = true)
    List<Long> idsAccesibles(@Param("usuarioId") Integer usuarioId, @Param("ids") java.util.Collection<Long> ids);

    // Historial de finalizados: los 200 más recientes que el usuario puede ver (auditoría B2: la lista
    // ya no crece sin límite; los anteriores se encuentran con el buscador)
    @Query(value = CTE_AMBITO + CTE_CONJUNTOS + "SELECT d.* FROM documentos d WHERE d.estado_actual = 'FINALIZADO' AND "
                 + EN_ACC + " ORDER BY d.fecha_hora_salida DESC NULLS LAST, d.id DESC LIMIT 200", nativeQuery = true)
    List<Documento> findFinalizadosAccesibles(@Param("usuarioId") Integer usuarioId);

    // Máximo de resultados del buscador: para más, el usuario afina los filtros
    int MAXIMO_RESULTADOS_BUSQUEDA = 500;

    // El texto también busca en el bibliorato (código, descripción o texto histórico).
    // Solo devuelve expedientes que el usuario puede ver: el ámbito se filtra en la misma consulta (auditoría B2)
    @Query(value = CTE_AMBITO + CTE_CONJUNTOS + """
        SELECT d.* FROM documentos d LEFT JOIN biblioratos b ON b.id = d.bibliorato_id
         WHERE (CAST(:texto AS text) IS NULL
                OR d.codigo_cuti ILIKE '%' || CAST(:texto AS text) || '%'
                OR d.asunto ILIKE '%' || CAST(:texto AS text) || '%'
                OR d.numero_documento_origen ILIKE '%' || CAST(:texto AS text) || '%'
                OR d.nro_nota_salida ILIKE '%' || CAST(:texto AS text) || '%'
                OR d.ubicacion_bibliorato ILIKE '%' || CAST(:texto AS text) || '%'
                OR b.codigo ILIKE '%' || CAST(:texto AS text) || '%'
                OR b.descripcion ILIKE '%' || CAST(:texto AS text) || '%')
           AND (CAST(:dependenciaId AS integer) IS NULL OR d.dependencia_origen_id = CAST(:dependenciaId AS integer))
           AND (CAST(:tipoDocId AS integer) IS NULL OR d.tipo_documento_id = CAST(:tipoDocId AS integer))
           AND (CAST(:estado AS text) IS NULL OR d.estado_actual = CAST(:estado AS text))
           AND (CAST(:biblioratoId AS integer) IS NULL OR b.id = CAST(:biblioratoId AS integer))
           AND (CAST(:fechaDesde AS timestamp) IS NULL OR d.fecha_hora_ingreso >= CAST(:fechaDesde AS timestamp))
           AND (CAST(:fechaHasta AS timestamp) IS NULL OR d.fecha_hora_ingreso <= CAST(:fechaHasta AS timestamp))
           AND """ + EN_ACC + """

         ORDER BY d.fecha_hora_ingreso DESC, d.id DESC
         LIMIT\s""" + MAXIMO_RESULTADOS_BUSQUEDA, nativeQuery = true)
    List<Documento> buscarMulticriterio(
            @Param("usuarioId") Integer usuarioId,
            @Param("texto") String texto,
            @Param("dependenciaId") Integer dependenciaId,
            @Param("tipoDocId") Integer tipoDocId,
            @Param("estado") String estado,
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