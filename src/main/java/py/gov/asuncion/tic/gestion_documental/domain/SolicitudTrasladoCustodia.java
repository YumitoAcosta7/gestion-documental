package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 📦 Pedido de trasladar un original de una dependencia que quedó sin nadie: lo aprueba otro administrador (V28)
@Entity
@Table(name = "solicitudes_traslado_custodia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudTrasladoCustodia {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependencia_origen_id", nullable = false)
    private Dependencia dependenciaOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependencia_destino_id", nullable = false)
    private Dependencia dependenciaDestino;

    @Column(name = "motivo", nullable = false, columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "acto_administrativo", nullable = false, length = 150)
    private String actoAdministrativo;

    @Builder.Default
    @Column(name = "estado", nullable = false, length = 20)
    private String estado = PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitada_por_id", nullable = false)
    private Usuario solicitadaPor;

    @Builder.Default
    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelta_por_id")
    private Usuario resueltaPor;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;
}
