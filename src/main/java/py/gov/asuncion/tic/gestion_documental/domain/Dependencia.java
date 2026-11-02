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

    // Sigla única del organigrama (DTIC, DGAF, DREC-D02...)
    @Column(length = 20, unique = true)
    private String sigla;

    // Ruta materializada (/1/10/40/): la calcula un trigger de la base al crear o mover la dependencia
    @Column(nullable = false, length = 500, insertable = false, updatable = false)
    private String ruta;

    // Secretaría que recibe y registra los documentos de su dirección
    @Builder.Default
    @Column(name = "es_mesa_entrada", nullable = false)
    private Boolean esMesaEntrada = false;

    // Mesa que recibe los documentos externos y los deriva a cada dirección
    @Builder.Default
    @Column(name = "recibe_externos", nullable = false)
    private Boolean recibeExternos = false;

    @Column(length = 100)
    private String resolucion;

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