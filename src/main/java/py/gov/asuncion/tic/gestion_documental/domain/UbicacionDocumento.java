package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 📚 Dónde guarda una dependencia su papel de un documento: una ubicación por dependencia
@Entity
@Table(name = "ubicaciones_documento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UbicacionDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dependencia_id", nullable = false)
    private Dependencia dependencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bibliorato_id", nullable = false)
    private Bibliorato bibliorato;

    @Column(name = "foja_desde")
    private Integer fojaDesde;

    @Column(name = "foja_hasta")
    private Integer fojaHasta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    public String getFojasTexto() {
        if (fojaDesde == null) {
            return "-";
        }
        return fojaDesde.equals(fojaHasta) ? String.valueOf(fojaDesde) : fojaDesde + "–" + fojaHasta;
    }

    public String getTexto() {
        String fojas = fojaDesde == null ? "" : (fojaDesde.equals(fojaHasta) ? " · Foja " + fojaDesde : " · Fojas " + fojaDesde + "–" + fojaHasta);
        return bibliorato.getCodigo() + " — " + bibliorato.getDescripcion() + fojas;
    }
}
