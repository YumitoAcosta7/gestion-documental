package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import java.util.List;
import java.time.LocalDateTime;

@Entity
@Table(name = "actuaciones_documento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActuacionDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependencia_emisora_id", nullable = false)
    private Dependencia dependenciaEmisora;

    @Column(name = "tipo_actuacion", nullable = false, length = 50)
    private String tipoActuacion; // INFORME_TECNICO, MEMO, ACTA_ENTREGA, PROVIDENCIA

    @Pattern(
        regexp = "^(S/N|\\d+/\\d{4})$",
        message = "El número de actuación debe utilizar el formato N°/AÑO (ej. 45/2026) o S/N"
    )
    @Column(name = "numero_actuacion", nullable = false, length = 50)
    private String numeroActuacion;

    @Column(name = "descripcion_resumen", nullable = false, columnDefinition = "TEXT")
    private String descripcionResumen;

    @Column(name = "ubicacion_bibliorato", length = 150)
    private String ubicacionBibliorato;

    @Column(name = "ruta_copia_sellada_pdf", length = 255)
    private String rutaCopiaSelladaPdf;

    @Column(name = "fecha_hora_actuacion")
    private LocalDateTime fechaHoraActuacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "pase_confirmado")
    @Builder.Default
    private Boolean paseConfirmado = false;

    // Relación para la vista en árbol (Jerarquía de Actuaciones)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actuacion_padre_id")
    private ActuacionDocumento actuacionPadre;

    @OneToMany(mappedBy = "actuacionPadre", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ActuacionDocumento> actuacionesHijas = new java.util.ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependencia_receptora_pase_id")
    private Dependencia dependenciaReceptoraPase;

    // --- ANULACIÓN LÓGICA (no se borra: queda en el historial) ---
    @Column(name = "anulada", nullable = false)
    @Builder.Default
    private Boolean anulada = false;

    @Column(name = "tipo_anulacion", length = 30)
    private String tipoAnulacion; // RETIRADO, RECHAZADO

    @Column(name = "motivo_anulacion", columnDefinition = "TEXT")
    private String motivoAnulacion;

    @Column(name = "fecha_hora_anulacion")
    private LocalDateTime fechaHoraAnulacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_anulacion_id")
    private Usuario usuarioAnulacion;
}