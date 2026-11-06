package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import py.gov.asuncion.tic.gestion_documental.domain.Documento;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

// Consultas de solo lectura de los reportes. Todas reciben las dependencias que el usuario puede ver: el alcance
// lo calcula ReporteService. "Dónde está" un documento es quien tiene el original (custodia).
public interface ReporteRepository extends Repository<Documento, Long> {

    String CUSTODIA = "COALESCE(d.dependencia_custodia_id, d.dependencia_registro_id, d.dependencia_origen_id)";
    String DEL_TIPO = "(CAST(:tipoId AS integer) IS NULL OR d.tipo_documento_id = CAST(:tipoId AS integer))";

    // [id, cuti, asunto, estado, prioridad, custodia, ingreso, último movimiento, llegada a la custodia, tipo]
    @Query(value = "SELECT d.id, d.codigo_cuti, d.asunto, d.estado_actual, d.prioridad, " + CUSTODIA + ", d.fecha_hora_ingreso, "
            + "(SELECT MAX(a.fecha_hora_actuacion) FROM actuaciones_documento a WHERE a.documento_id = d.id AND NOT a.anulada), "
            + "(SELECT MAX(a.fecha_hora_actuacion) FROM actuaciones_documento a WHERE a.documento_id = d.id AND NOT a.anulada "
            + "   AND a.tipo_actuacion = 'PASE_CONFIRMADO' AND a.tipo_pase = 'TRAMITE' AND a.dependencia_receptora_pase_id = " + CUSTODIA + "), "
            + "t.nombre "
            + "FROM documentos d JOIN tipos_documentos t ON t.id = d.tipo_documento_id "
            + "WHERE d.estado_actual NOT IN ('FINALIZADO', 'ARCHIVADO', 'ANULADO') AND " + CUSTODIA + " IN (:deps) AND " + DEL_TIPO,
            nativeQuery = true)
    List<Object[]> activos(@Param("deps") Collection<Integer> deps, @Param("tipoId") Integer tipoId);

    // [custodia, cantidad de terminados]
    @Query(value = "SELECT " + CUSTODIA + ", COUNT(*) FROM documentos d "
            + "WHERE d.estado_actual IN ('FINALIZADO', 'ARCHIVADO') AND " + CUSTODIA + " IN (:deps) AND " + DEL_TIPO + " GROUP BY 1",
            nativeQuery = true)
    List<Object[]> terminadosPorCustodia(@Param("deps") Collection<Integer> deps, @Param("tipoId") Integer tipoId);

    // [cuti, asunto, tipo de pase, emisora, receptora, fecha]
    @Query(value = "SELECT d.codigo_cuti, d.asunto, a.tipo_pase, a.dependencia_emisora_id, a.dependencia_receptora_pase_id, a.fecha_hora_actuacion "
            + "FROM actuaciones_documento a JOIN documentos d ON d.id = a.documento_id "
            + "WHERE a.tipo_actuacion = 'PASE_INTERNO' AND NOT a.pase_confirmado AND NOT a.anulada AND d.estado_actual <> 'ANULADO' "
            + "AND (a.dependencia_emisora_id IN (:deps) OR a.dependencia_receptora_pase_id IN (:deps)) AND " + DEL_TIPO
            + " ORDER BY a.fecha_hora_actuacion, a.id",
            nativeQuery = true)
    List<Object[]> pasesSinRecibir(@Param("deps") Collection<Integer> deps, @Param("tipoId") Integer tipoId);

    // Los documentos en trámite con un pedido de informe dirigido a estas dependencias. Cuáles siguen abiertos lo
    // decide la misma regla de la bandeja (DocumentoService.pedidosAbiertos)
    @Query(value = "SELECT DISTINCT a.documento_id FROM actuaciones_documento a JOIN documentos d ON d.id = a.documento_id "
            + "WHERE a.requiere_informe AND NOT a.anulada AND a.dependencia_receptora_pase_id IN (:deps) "
            + "AND d.estado_actual NOT IN ('FINALIZADO', 'ARCHIVADO', 'ANULADO') AND " + DEL_TIPO,
            nativeQuery = true)
    List<Number> documentosConPedidos(@Param("deps") Collection<Integer> deps, @Param("tipoId") Integer tipoId);

    // [mes AAAA-MM, cantidad]. Ingresa en la dependencia que lo registró
    @Query(value = "SELECT TO_CHAR(d.fecha_hora_ingreso, 'YYYY-MM'), COUNT(*) FROM documentos d "
            + "WHERE d.estado_actual <> 'ANULADO' AND COALESCE(d.dependencia_registro_id, d.dependencia_origen_id) IN (:deps) "
            + "AND d.fecha_hora_ingreso >= :desde AND d.fecha_hora_ingreso < :hasta AND " + DEL_TIPO + " GROUP BY 1",
            nativeQuery = true)
    List<Object[]> ingresosPorMes(@Param("deps") Collection<Integer> deps, @Param("tipoId") Integer tipoId,
                                  @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    // [mes AAAA-MM, cantidad]
    @Query(value = "SELECT TO_CHAR(d.fecha_hora_salida, 'YYYY-MM'), COUNT(*) FROM documentos d "
            + "WHERE d.estado_actual = 'FINALIZADO' AND " + CUSTODIA + " IN (:deps) "
            + "AND d.fecha_hora_salida >= :desde AND d.fecha_hora_salida < :hasta AND " + DEL_TIPO + " GROUP BY 1",
            nativeQuery = true)
    List<Object[]> finalizadosPorMes(@Param("deps") Collection<Integer> deps, @Param("tipoId") Integer tipoId,
                                     @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    String DIAS_DE_RESOLUCION = "EXTRACT(EPOCH FROM (d.fecha_hora_salida - d.fecha_hora_ingreso)) / 86400";
    String RESOLUCION = "COUNT(*), PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY " + DIAS_DE_RESOLUCION + "), MAX(" + DIAS_DE_RESOLUCION + ") ";
    String RESUELTOS = "WHERE d.estado_actual = 'FINALIZADO' AND d.fecha_hora_salida >= d.fecha_hora_ingreso AND " + CUSTODIA + " IN (:deps) "
            + "AND d.fecha_hora_salida >= :desde AND d.fecha_hora_salida < :hasta AND " + DEL_TIPO;

    // [custodia, cantidad, mediana de días, máximo de días]
    @Query(value = "SELECT " + CUSTODIA + ", " + RESOLUCION + "FROM documentos d " + RESUELTOS + " GROUP BY 1", nativeQuery = true)
    List<Object[]> resolucionPorDependencia(@Param("deps") Collection<Integer> deps, @Param("tipoId") Integer tipoId,
                                            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    // [tipo, cantidad, mediana de días, máximo de días]
    @Query(value = "SELECT t.nombre, " + RESOLUCION + "FROM documentos d JOIN tipos_documentos t ON t.id = d.tipo_documento_id "
            + RESUELTOS + " GROUP BY 1 ORDER BY 1", nativeQuery = true)
    List<Object[]> resolucionPorTipo(@Param("deps") Collection<Integer> deps, @Param("tipoId") Integer tipoId,
                                     @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    // [cuti, asunto, custodia, salida]: finalizados cuyo original todavía no tiene carpeta en quien lo custodia
    @Query(value = "SELECT d.codigo_cuti, d.asunto, " + CUSTODIA + ", d.fecha_hora_salida FROM documentos d "
            + "WHERE d.estado_actual = 'FINALIZADO' AND " + CUSTODIA + " IN (:deps) AND " + DEL_TIPO
            + " AND NOT EXISTS (SELECT 1 FROM ubicaciones_documento u WHERE u.documento_id = d.id AND u.dependencia_id = " + CUSTODIA + ")"
            + " ORDER BY d.fecha_hora_salida NULLS LAST, d.id",
            nativeQuery = true)
    List<Object[]> finalizadosSinGuardar(@Param("deps") Collection<Integer> deps, @Param("tipoId") Integer tipoId);
}
