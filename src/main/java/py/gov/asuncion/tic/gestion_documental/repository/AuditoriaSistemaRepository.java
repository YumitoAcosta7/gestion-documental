package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.AuditoriaSistema;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditoriaSistemaRepository extends JpaRepository<AuditoriaSistema, Long> {

    @Query("SELECT a FROM AuditoriaSistema a JOIN FETCH a.usuario ORDER BY a.fechaHora DESC, a.id DESC")
    List<AuditoriaSistema> findRecientes(Pageable pagina);

    // Búsqueda en toda la auditoría. Los filtros sin elegir llegan como un rango amplio, '%' o '': PostgreSQL no
    // puede deducir el tipo de un parámetro nulo
    @Query(value = """
        SELECT a FROM AuditoriaSistema a JOIN FETCH a.usuario u
         WHERE a.fechaHora >= :desde AND a.fechaHora < :hasta
           AND LOWER(u.username) LIKE :usuario
           AND (:accion = '' OR a.accion = :accion)
         ORDER BY a.fechaHora DESC, a.id DESC
        """, countQuery = """
        SELECT COUNT(a) FROM AuditoriaSistema a JOIN a.usuario u
         WHERE a.fechaHora >= :desde AND a.fechaHora < :hasta
           AND LOWER(u.username) LIKE :usuario
           AND (:accion = '' OR a.accion = :accion)
        """)
    Page<AuditoriaSistema> buscar(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                  @Param("usuario") String usuario, @Param("accion") String accion, Pageable pagina);

    @Query("SELECT DISTINCT a.accion FROM AuditoriaSistema a ORDER BY a.accion")
    List<String> accionesRegistradas();

    // IP del usuario para la bitácora: queda en la transacción y la guarda el trigger de huella
    // (db/seguridad/reforzar_bitacoras.sh). Sin ese trigger instalado, no tiene efecto.
    @Query(value = "SELECT set_config('app.direccion_ip', :ip, true)", nativeQuery = true)
    String marcarIp(@Param("ip") String ip);
}
