package py.gov.asuncion.tic.gestion_documental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // De las dependencias indicadas, las que tienen quién reciba un pase: un usuario activo propio o una
    // asignación de trámite vigente que las abarque (auditoría M2: sin receptor, el pase quedaría trabado)
    @Query(value = """
        SELECT d.id FROM dependencias d
         WHERE d.id IN (:ids)
           AND (EXISTS (SELECT 1 FROM usuarios u WHERE u.activo AND u.dependencia_id = d.id)
                OR EXISTS (SELECT 1 FROM asignaciones a
                             JOIN usuarios u ON u.id = a.usuario_id
                             JOIN dependencias amb ON amb.id = a.dependencia_ambito_id
                            WHERE u.activo AND a.activa AND a.rol IN ('MESA_ENTRADA', 'RESPONSABLE', 'OPERADOR')
                              AND a.vigente_desde <= CURRENT_DATE AND (a.vigente_hasta IS NULL OR a.vigente_hasta >= CURRENT_DATE)
                              AND (amb.id = d.id
                                   OR (a.incluye_subordinadas AND a.rol <> 'OPERADOR' AND d.ruta LIKE amb.ruta || '%'))))
        """, nativeQuery = true)
    List<Integer> findConReceptor(@Param("ids") Collection<Integer> ids);

    // Versión del organigrama (V16): cambia con cada alta, baja o modificación de dependencias, niveles o atajos
    @Query(value = "SELECT version FROM organigrama_version WHERE id = 1", nativeQuery = true)
    long versionOrganigrama();

    // Atajos habilitados fuera del canal jerárquico: [origen_id, destino_id]
    @Query(value = "SELECT origen_id, destino_id FROM reglas_derivacion WHERE activa", nativeQuery = true)
    List<Object[]> findAtajosActivos();
}
