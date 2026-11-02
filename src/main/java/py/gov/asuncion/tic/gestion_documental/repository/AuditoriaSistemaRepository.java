package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.AuditoriaSistema;

import java.util.List;

@Repository
public interface AuditoriaSistemaRepository extends JpaRepository<AuditoriaSistema, Long> {

    @Query("SELECT a FROM AuditoriaSistema a JOIN FETCH a.usuario ORDER BY a.fechaHora DESC, a.id DESC")
    List<AuditoriaSistema> findRecientes(Pageable pagina);
}
