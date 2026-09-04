package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "niveles_jerarquicos")
@Data
public class NivelJerarquico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(name = "orden_nivel", nullable = false)
    private Integer ordenNivel;
}
