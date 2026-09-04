package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "dependencias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dependencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 150)
    private String nombre;

    // 👇 AQUÍ AGREGAMOS EL NIVEL JERÁRQUICO
    @Column(name = "nivel_jerarquico_id")
    private Integer nivelJerarquicoId;

    @Column(name = "es_interna_tic")
    private Boolean esInternaTic;

    private Boolean activo;

    // 🌳 Mapeo de la Jerarquía Orgánica Inmutable (Padre -> Hijas)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependencia_padre_id")
    private Dependencia dependenciaPadre;

    // 👇 AQUÍ AGREGAMOS EL ID DEL PADRE DE LECTURA DIRECTA
    @Column(name = "dependencia_padre_id", insertable = false, updatable = false)
    private Integer dependenciaPadreId;

    @OneToMany(mappedBy = "dependenciaPadre")
    private List<Dependencia> dependenciasHijas;
}