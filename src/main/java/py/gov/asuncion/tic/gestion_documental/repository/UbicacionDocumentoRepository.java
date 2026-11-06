package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.UbicacionDocumento;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UbicacionDocumentoRepository extends JpaRepository<UbicacionDocumento, Long> {

    @Query("""
        SELECT u FROM UbicacionDocumento u JOIN FETCH u.bibliorato
        WHERE u.documento.id = :documentoId AND u.dependencia.id = :dependenciaId
    """)
    Optional<UbicacionDocumento> findDeDocumentoYDependencia(@Param("documentoId") Long documentoId,
                                                             @Param("dependenciaId") Integer dependenciaId);

    boolean existsByDocumentoId(Long documentoId);

    List<UbicacionDocumento> findByDocumentoId(Long documentoId);

    // Las ubicaciones de varios documentos, para una página de resultados
    @Query("""
        SELECT u FROM UbicacionDocumento u JOIN FETCH u.bibliorato JOIN FETCH u.dependencia
        WHERE u.documento.id IN :documentoIds
        ORDER BY u.dependencia.nombre
    """)
    List<UbicacionDocumento> findDeDocumentos(@Param("documentoIds") Collection<Long> documentoIds);

    // Lo guardado en estas carpetas, en orden físico
    @Query("""
        SELECT u FROM UbicacionDocumento u JOIN FETCH u.documento JOIN FETCH u.bibliorato b JOIN FETCH u.dependencia
        WHERE b.id IN :biblioratoIds
        ORDER BY b.codigo, u.fojaDesde, u.id
    """)
    List<UbicacionDocumento> findDeBiblioratos(@Param("biblioratoIds") Collection<Integer> biblioratoIds);

    // Documentos del bibliorato cuyas fojas se superponen con el rango indicado
    @Query("""
        SELECT u FROM UbicacionDocumento u JOIN FETCH u.documento
        WHERE u.bibliorato.id = :biblioratoId AND u.fojaDesde IS NOT NULL
          AND u.fojaDesde <= :hasta AND u.fojaHasta >= :desde
          AND u.documento.id <> :documentoId
    """)
    List<UbicacionDocumento> findFojasSuperpuestas(@Param("biblioratoId") Integer biblioratoId,
                                                   @Param("desde") Integer desde,
                                                   @Param("hasta") Integer hasta,
                                                   @Param("documentoId") Long documentoId);

    // Cantidad de documentos guardados en cada bibliorato: [id, cantidad, foja máxima]
    @Query("SELECT u.bibliorato.id, COUNT(u), MAX(u.fojaHasta) FROM UbicacionDocumento u GROUP BY u.bibliorato.id")
    List<Object[]> contarPorBibliorato();
}
