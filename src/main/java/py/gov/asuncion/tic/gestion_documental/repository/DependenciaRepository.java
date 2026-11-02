package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.Dependencia;
import py.gov.asuncion.tic.gestion_documental.domain.NivelJerarquico;

import java.util.Collection;
import java.util.List;

@Repository
public interface DependenciaRepository extends JpaRepository<Dependencia, Integer> {

    // Buscar solo las dependencias que dependen jerarquicamente
    List<Dependencia> findByDependenciaPadreIdAndActivoTrueOrderByNombreAsc(Integer padreId);

    List<Dependencia> findByDependenciaPadreIdInAndActivoTrueOrderByNombreAsc(Collection<Integer> padreIds);

    @Query("SELECT n FROM NivelJerarquico n")
    List<NivelJerarquico> findNiveles();

    // Atajos habilitados fuera del canal jerárquico: [origen_id, destino_id]
    @Query(value = "SELECT origen_id, destino_id FROM reglas_derivacion WHERE activa", nativeQuery = true)
    List<Object[]> findAtajosActivos();
}
