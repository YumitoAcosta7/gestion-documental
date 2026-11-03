package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.Feriado;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FeriadoRepository extends JpaRepository<Feriado, LocalDate> {

    @Query("SELECT f.fecha FROM Feriado f WHERE f.fecha BETWEEN :desde AND :hasta")
    List<LocalDate> fechasEntre(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Query("SELECT f FROM Feriado f LEFT JOIN FETCH f.creadoPor WHERE f.fecha >= :desde ORDER BY f.fecha")
    List<Feriado> findDesde(@Param("desde") LocalDate desde);
}
