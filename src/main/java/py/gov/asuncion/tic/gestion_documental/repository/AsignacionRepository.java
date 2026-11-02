package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.Asignacion;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {

    // Asignaciones del usuario que rigen en la fecha (una suplencia vencida deja de contar sola)
    @Query("""
        SELECT a FROM Asignacion a JOIN FETCH a.dependenciaAmbito
        WHERE a.usuario.id = :usuarioId AND a.activa = true
          AND a.vigenteDesde <= :fecha AND (a.vigenteHasta IS NULL OR a.vigenteHasta >= :fecha)
    """)
    List<Asignacion> findVigentes(@Param("usuarioId") Integer usuarioId, @Param("fecha") LocalDate fecha);
}
