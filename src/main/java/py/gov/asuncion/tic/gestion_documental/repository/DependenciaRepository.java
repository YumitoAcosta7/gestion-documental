package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.Dependencia;

import java.util.List;

@Repository
public interface DependenciaRepository extends JpaRepository<Dependencia, Integer> {
    
    List<Dependencia> findByActivoTrueOrderByNombreAsc();
    
    List<Dependencia> findByEsInternaTicTrueAndActivoTrueOrderByNombreAsc();


    // Buscar solo las dependencias que dependen jerarquicamente
    List<Dependencia> findByDependenciaPadreIdAndActivoTrueOrderByNombreAsc(Integer padreId);
}