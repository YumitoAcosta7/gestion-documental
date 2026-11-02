package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "tipos_documentos")
@Data

public class TipoDocumento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    private String descripcion;
    private Boolean activo = true;

    // TRAMITE: circula (derivaciones, informes, salida). INFORMATIVO: se archiva (ej. circulares).
    @Column(nullable = false, length = 20)
    private String categoria = CATEGORIA_TRAMITE;

    // Obliga a indicar el bibliorato físico al registrar el documento
    @Column(name = "requiere_bibliorato", nullable = false)
    private Boolean requiereBibliorato = false;

    public static final String CATEGORIA_TRAMITE = "TRAMITE";
    public static final String CATEGORIA_INFORMATIVO = "INFORMATIVO";

    public boolean isInformativo() {
        return CATEGORIA_INFORMATIVO.equals(categoria);
    }
}


