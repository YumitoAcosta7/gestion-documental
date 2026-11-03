package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 📑 Actuación (informe o memo) que integra el legajo de la respuesta de salida del expediente (V21)
@Entity
@Table(name = "legajos_salida")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegajoSalida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actuacion_id", nullable = false)
    private ActuacionDocumento actuacion;

    // NOTA_DIRECCION o DIRECTO_INFORME
    @Column(name = "tipo_remision", nullable = false, length = 30)
    private String tipoRemision;

    @Column(name = "nro_nota_salida", length = 50)
    private String nroNotaSalida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id")
    private Usuario registradoPor;

    @Builder.Default
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();
}
