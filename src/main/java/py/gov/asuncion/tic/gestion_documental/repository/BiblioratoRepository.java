package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.Bibliorato;

import java.util.List;

@Repository
public interface BiblioratoRepository extends JpaRepository<Bibliorato, Integer> {

    List<Bibliorato> findByActivoTrueOrderByAnioDescCodigoAsc();

    List<Bibliorato> findAllByOrderByAnioDescCodigoAsc();

    boolean existsByCodigoIgnoreCase(String codigo);

    // Cantidad de documentos guardados en cada bibliorato: [id, cantidad, foja máxima]
    @Query("SELECT d.bibliorato.id, COUNT(d), MAX(d.fojaHasta) FROM Documento d WHERE d.bibliorato IS NOT NULL GROUP BY d.bibliorato.id")
    List<Object[]> contarDocumentosPorBibliorato();
}
