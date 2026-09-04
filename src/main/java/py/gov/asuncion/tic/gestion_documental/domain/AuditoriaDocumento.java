package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auditorias_documento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditoriaDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @Column(name = "tipo_evento", nullable = false, length = 50)
    private String tipoEvento; // Ej: REGISTRO_INGRESO, DERIVACION_MULTIPLE, CAMBIO_ESTADO, SALIDA_EXTERNA, CONFIRMACION_ACUSE

    @Column(name = "estado_anterior", length = 50)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", length = 50)
    private String estadoNuevo;

    @Column(name = "descripcion_detalle", columnDefinition = "TEXT")
    private String descripcionDetalle;

    @Column(name = "usuario_username", nullable = false, length = 100)
    private String usuarioUsername;

    @Column(name = "usuario_nombre_completo", nullable = false, length = 150)
    private String usuarioNombreCompleto;

    @Column(name = "dependencia_usuario", length = 150)
    private String dependenciaUsuario;

    @Column(name = "direccion_ip", length = 45)
    private String direccionIp;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;
}