package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByUsernameAndActivoTrue(String username);

    boolean existsByUsernameIgnoreCase(String username);

    // Listado de administración: por usuario, nombre o dependencia (texto vacío = todos)
    @Query("""
        SELECT u FROM Usuario u JOIN FETCH u.dependencia d
        WHERE :texto = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :texto, '%'))
           OR LOWER(u.nombreCompleto) LIKE LOWER(CONCAT('%', :texto, '%'))
           OR LOWER(d.nombre) LIKE LOWER(CONCAT('%', :texto, '%'))
           OR LOWER(COALESCE(d.sigla, '')) LIKE LOWER(CONCAT('%', :texto, '%'))
        ORDER BY u.activo DESC, u.nombreCompleto
    """)
    List<Usuario> buscar(@Param("texto") String texto);
}
