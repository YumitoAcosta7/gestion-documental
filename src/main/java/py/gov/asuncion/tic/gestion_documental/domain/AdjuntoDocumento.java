package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "adjuntos_documento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjuntoDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "ruta_almacenamiento", nullable = false, length = 500)
    private String rutaAlmacenamiento;

    @Column(name = "tipo_adjunto", length = 50)
    private String tipoAdjunto; // Ej: "ACUSE_SALIDA"

    @Column(name = "tamanio_bytes")
    private Long tamanioBytes;

    @Column(name = "fecha_hora_subida")
    private LocalDateTime fechaHoraSubida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_subida_id", nullable = false)
    private Usuario usuarioSubida;
}