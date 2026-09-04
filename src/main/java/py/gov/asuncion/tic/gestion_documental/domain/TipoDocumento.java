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
}


