package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 🧭 Permiso = rol × ámbito × vigencia. Un usuario puede tener varias; las suplencias son asignaciones temporales.
@Entity
@Table(name = "asignaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RolAsignacion rol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependencia_ambito_id", nullable = false)
    private Dependencia dependenciaAmbito;

    @Builder.Default
    @Column(name = "incluye_subordinadas", nullable = false)
    private Boolean incluyeSubordinadas = true;

    @Builder.Default
    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde = LocalDate.now();

    // NULL = sin fecha de fin
    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Builder.Default
    @Column(name = "es_suplencia", nullable = false)
    private Boolean esSuplencia = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "titular_usuario_id")
    private Usuario titular;

    @Column(name = "acto_administrativo", length = 100)
    private String actoAdministrativo;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activa = true;

    // NULL = carga inicial desde los roles anteriores (migración V8)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creada_por_id")
    private Usuario creadaPor;

    @Builder.Default
    @Column(name = "fecha_hora_creacion", nullable = false)
    private LocalDateTime fechaHoraCreacion = LocalDateTime.now();
}
