package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.AuditoriaSistema;

import java.util.List;

@Repository
public interface AuditoriaSistemaRepository extends JpaRepository<AuditoriaSistema, Long> {

    @Query("SELECT a FROM AuditoriaSistema a JOIN FETCH a.usuario ORDER BY a.fechaHora DESC, a.id DESC")
    List<AuditoriaSistema> findRecientes(Pageable pagina);

    // IP del usuario para la bitácora: queda en la transacción y la guarda el trigger de huella
    // (db/seguridad/reforzar_bitacoras.sh). Sin ese trigger instalado, no tiene efecto.
    @Query(value = "SELECT set_config('app.direccion_ip', :ip, true)", nativeQuery = true)
    String marcarIp(@Param("ip") String ip);
}
