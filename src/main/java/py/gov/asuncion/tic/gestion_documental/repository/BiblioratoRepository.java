package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.Bibliorato;

import java.util.List;

@Repository
public interface BiblioratoRepository extends JpaRepository<Bibliorato, Integer> {

    List<Bibliorato> findByActivoTrueOrderByAnioDescCodigoAsc();

    List<Bibliorato> findAllByOrderByAnioDescCodigoAsc();

    boolean existsByCodigoIgnoreCase(String codigo);
}
