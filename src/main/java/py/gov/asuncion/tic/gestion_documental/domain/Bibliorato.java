package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 📚 Bibliorato físico: dónde está guardado el papel
@Entity
@Table(name = "biblioratos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bibliorato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo; // Ej: BIB-DTIC-2026-001

    @Column(nullable = false, length = 150)
    private String descripcion; // Ej: Circulares recibidas 2026 - Tomo 1

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependencia_id", nullable = false)
    private Dependencia dependencia;

    @Column(nullable = false)
    private Integer anio;

    @Column(name = "ubicacion_fisica", nullable = false, length = 150)
    private String ubicacionFisica; // Ej: Archivo DTIC · Armario 2 · Estante 3

    @Column(name = "capacidad_fojas")
    private Integer capacidadFojas;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "fecha_hora_alta", nullable = false)
    private LocalDateTime fechaHoraAlta;
}
