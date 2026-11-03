package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 📅 Día no hábil: no cuenta para los días sin movimiento ni para los plazos de los pases (V18)
@Entity
@Table(name = "feriados")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feriado {

    @Id
    private LocalDate fecha;

    @Column(nullable = false, length = 150)
    private String descripcion;

    // NULL = carga inicial de la migración
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id")
    private Usuario creadoPor;

    @Builder.Default
    @Column(name = "fecha_hora_creacion", nullable = false)
    private LocalDateTime fechaHoraCreacion = LocalDateTime.now();
}
