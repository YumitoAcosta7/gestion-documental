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
    // Un asunto = un CUTI por cabecera: el mismo documento (origen y número) ya registrado, en cualquier dirección
    List<Documento> findByDependenciaOrigenIdAndNumeroDocumentoOrigen(Integer dependenciaOrigenId, String numeroDocumentoOrigen);

    // Los 50 más recientes (auditoría B2: las listas de la pantalla no crecen sin límite)
    List<Documento> findTop50ByDependenciaOrigenIdAndEstadoActualNotOrderByFechaHoraIngresoDesc(Integer dependenciaOrigenId, EstadoDocumento estado);

    // 🔒 Bloquea la fila del expediente hasta el fin de la transacción (SELECT ... FOR UPDATE).
    // Serializa las operaciones simultáneas sobre un mismo expediente (derivar, anular, confirmar...).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Documento d WHERE d.id = :id")
    Optional<Documento> findByIdParaActualizar(@Param("id") Long id);

    // Auditoría M7: las dependencias que tienen originales a su cargo. Los expedientes anulados no tienen original que mover
    @Query(value = """
        SELECT DISTINCT COALESCE(dependencia_custodia_id, dependencia_registro_id, dependencia_origen_id)
          FROM documentos WHERE estado_actual <> 'ANULADO'
        """, nativeQuery = true)
    List<Integer> dependenciasConCustodia();

    @Query(value = """
        SELECT COUNT(*) FROM documentos
         WHERE estado_actual <> 'ANULADO'
           AND COALESCE(dependencia_custodia_id, dependencia_registro_id, dependencia_origen_id) IN (:dependencias)
        """, nativeQuery = true)
    long contarEnCustodiaDe(@Param("dependencias") java.util.Collection<Integer> dependencias);

    @Query("""
        SELECT d FROM Documento d
          LEFT JOIN FETCH d.dependenciaCustodia LEFT JOIN FETCH d.dependenciaRegistro LEFT JOIN FETCH d.dependenciaOrigen
         WHERE d.estadoActual <> py.gov.asuncion.tic.gestion_documental.domain.EstadoDocumento.ANULADO
           AND COALESCE(d.dependenciaCustodia.id, d.dependenciaRegistro.id, d.dependenciaOrigen.id) IN :dependencias
         ORDER BY d.fechaHoraIngreso, d.id
        """)
    List<Documento> enCustodiaDe(@Param("dependencias") java.util.Collection<Integer> dependencias,
                                 org.springframework.data.domain.Pageable pagina);

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
    // Sin una asignación de trámite vigente, no ve expedientes (auditoría M12). Una cuenta de administración
    // tampoco: ni por sus asignaciones ni por su dependencia (auditoría M6). "propia" suma lo de su dependencia,
    // salvo que sea una mesa de entrada: ahí cuenta solo la asignación (auditoría A5).
    String CTE_AMBITO = """
        WITH amb AS (
            SELECT dep.ruta, a.incluye_subordinadas
              FROM asignaciones a JOIN dependencias dep ON dep.id = a.dependencia_ambito_id
             WHERE a.usuario_id = :usuarioId AND a.activa AND a.rol <> 'ADMIN_SISTEMA'
               AND a.vigente_desde <= CURRENT_DATE AND (a.vigente_hasta IS NULL OR a.vigente_hasta >= CURRENT_DATE)
               AND NOT EXISTS (SELECT 1 FROM asignaciones adm WHERE adm.usuario_id = :usuarioId AND adm.activa AND adm.rol = 'ADMIN_SISTEMA'
                                 AND adm.vigente_desde <= CURRENT_DATE AND (adm.vigente_hasta IS NULL OR adm.vigente_hasta >= CURRENT_DATE))
        ), deps AS (
            SELECT x.id FROM dependencias x JOIN amb ON x.ruta = amb.ruta OR (amb.incluye_subordinadas AND x.ruta LIKE amb.ruta || '%')
        ), propia AS (
            SELECT u.dependencia_id AS id FROM usuarios u JOIN dependencias dp ON dp.id = u.dependencia_id
             WHERE u.id = :usuarioId AND NOT dp.es_mesa_entrada
               AND EXISTS (SELECT 1 FROM amb)
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
               AND a.usuario_id <> :usuarioId
        ), en_camino AS (
            SELECT DISTINCT a.documento_id AS id FROM actuaciones_documento a
             WHERE a.tipo_actuacion = 'PASE_INTERNO' AND a.tipo_pase = 'TRAMITE' AND NOT a.pase_confirmado AND NOT a.anulada
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

    // Historial de finalizados: los 50 más recientes que el usuario puede ver (auditoría B2: la lista
    // ya no crece sin límite; los anteriores se encuentran con el buscador). Recorre los finalizados por fecha con
    // el índice de V19 y comprueba el acceso de cada uno hasta juntar 50: no arma el conjunto completo.
    @Query(value = CTE_AMBITO + "SELECT d.* FROM documentos d WHERE d.estado_actual = 'FINALIZADO' AND "
                 + DOCUMENTO_ACCESIBLE + " ORDER BY d.fecha_hora_salida DESC NULLS LAST, d.id DESC LIMIT 50", nativeQuery = true)
    List<Documento> findFinalizadosAccesibles(@Param("usuarioId") Integer usuarioId);

    // Respuestas a lo que pidió el ámbito del usuario: expedientes finalizados desde :desde, que tienen a ese ámbito
    // como origen o destino de la salida, y cuyo original quedó a cargo de otra dependencia
    @Query(value = CTE_AMBITO + """
        SELECT d.* FROM documentos d
         WHERE d.estado_actual = 'FINALIZADO' AND d.fecha_hora_salida >= :desde
           AND (d.dependencia_origen_id IN (SELECT id FROM deps UNION SELECT id FROM propia)
                OR d.dependencia_destino_salida_id IN (SELECT id FROM deps UNION SELECT id FROM propia))
           AND COALESCE(d.dependencia_custodia_id, d.dependencia_registro_id, d.dependencia_origen_id)
               NOT IN (SELECT id FROM deps UNION SELECT id FROM propia)
         ORDER BY d.fecha_hora_salida DESC, d.id DESC LIMIT 10
        """, nativeQuery = true)
    List<Documento> respuestasRecibidas(@Param("usuarioId") Integer usuarioId, @Param("desde") LocalDateTime desde);

    // Máximo de resultados del buscador: para más, el usuario afina los filtros
    int MAXIMO_RESULTADOS_BUSQUEDA = 500;

    // El texto también busca en el bibliorato (código, descripción o texto histórico).
    // Solo devuelve expedientes que el usuario puede ver: el ámbito se filtra en la misma consulta (auditoría B2),
    // expediente por expediente sobre los que coinciden, en orden de fecha, hasta juntar el máximo
    @Query(value = CTE_AMBITO + """
        SELECT d.* FROM documentos d
         WHERE (CAST(:texto AS text) IS NULL
                -- En una subconsulta sobre documentos, para que use los índices de trigramas de V22
                OR d.id IN (SELECT t.id FROM documentos t
                             WHERE t.codigo_cuti ILIKE '%' || CAST(:texto AS text) || '%'
                                OR t.asunto ILIKE '%' || CAST(:texto AS text) || '%'
                                OR t.numero_documento_origen ILIKE '%' || CAST(:texto AS text) || '%'
                                OR t.nro_nota_salida ILIKE '%' || CAST(:texto AS text) || '%'
                                OR t.ubicacion_bibliorato ILIKE '%' || CAST(:texto AS text) || '%')
                OR d.id IN (SELECT ut.documento_id FROM ubicaciones_documento ut JOIN biblioratos bt ON bt.id = ut.bibliorato_id
                              WHERE bt.codigo ILIKE '%' || CAST(:texto AS text) || '%'
                                 OR bt.descripcion ILIKE '%' || CAST(:texto AS text) || '%'))
           AND (CAST(:dependenciaId AS integer) IS NULL OR d.dependencia_origen_id = CAST(:dependenciaId AS integer))
           AND (CAST(:tipoDocId AS integer) IS NULL OR d.tipo_documento_id = CAST(:tipoDocId AS integer))
           AND (CAST(:estado AS text) IS NULL OR d.estado_actual = CAST(:estado AS text))
           AND (CAST(:biblioratoId AS integer) IS NULL
                OR d.id IN (SELECT ub.documento_id FROM ubicaciones_documento ub WHERE ub.bibliorato_id = CAST(:biblioratoId AS integer)))
           AND (CAST(:fechaDesde AS timestamp) IS NULL OR d.fecha_hora_ingreso >= CAST(:fechaDesde AS timestamp))
           AND (CAST(:fechaHasta AS timestamp) IS NULL OR d.fecha_hora_ingreso <= CAST(:fechaHasta AS timestamp))
           AND\s""" + DOCUMENTO_ACCESIBLE + """

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

    // 📚 Documentos archivados que nadie ubicó todavía
    @Query("""
        SELECT d FROM Documento d
        WHERE d.estadoActual = 'ARCHIVADO'
          AND NOT EXISTS (SELECT 1 FROM UbicacionDocumento u WHERE u.documento = d)
        ORDER BY d.fechaHoraIngreso DESC
    """)
    List<Documento> findArchivadosSinUbicacion();
}