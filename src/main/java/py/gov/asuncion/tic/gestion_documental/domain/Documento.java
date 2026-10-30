package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "documentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_cuti", nullable = false, unique = true, length = 30)
    private String codigoCuti;

    @Column(name = "anio")
    private Integer anio;

    @Column(name = "numero_documento_origen", length = 50)
    private String numeroDocumentoOrigen;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridad", nullable = false, length = 20)
    private Prioridad prioridad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_documento_id", nullable = false)
    private TipoDocumento tipoDocumento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependencia_origen_id", nullable = false)
    private Dependencia dependenciaOrigen;

    @Column(name = "persona_entrega", length = 150)
    private String personaEntrega;

    @Column(name = "nro_orden_libro")
    private Integer nroOrdenLibro;

    @Column(name = "nro_pagina_libro")
    private Integer nroPaginaLibro;

    @Column(name = "asunto", nullable = false, columnDefinition = "TEXT")
    private String asunto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_actual", nullable = false, length = 30)
    private EstadoDocumento estadoActual;

    @Column(name = "fecha_hora_ingreso", nullable = false)
    private LocalDateTime fechaHoraIngreso;

    // --- AUDITORÍA DE INGRESO ---
    @Column(name = "funcionario_recibe_fisico", length = 150)
    private String funcionarioRecibeFisico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_carga_ingreso_id")
    private Usuario usuarioCargaIngreso;

    @Column(name = "fecha_hora_carga_ingreso")
    private LocalDateTime fechaHoraCargaIngreso;

    // --- AUDITORÍA DE SALIDA Y DESTINO ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependencia_destino_salida_id")
    private Dependencia dependenciaDestinoSalida;

    @Column(name = "nro_nota_salida", length = 50)
    private String nroNotaSalida;

    @Column(name = "fecha_hora_salida")
    private LocalDateTime fechaHoraSalida;

    @Column(name = "persona_retira_salida", length = 150)
    private String personaRetiraSalida;

    @Column(name = "funcionario_entrega_fisico", length = 150)
    private String funcionarioEntregaFisico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_carga_salida_id")
    private Usuario usuarioCargaSalida;

    @Column(name = "fecha_hora_carga_salida")
    private LocalDateTime fechaHoraCargaSalida;

    // --- ARCHIVAMIENTO FÍSICO ---
    @Column(name = "archivado")
    @Builder.Default
    private Boolean archivado = false;

    @Column(name = "ubicacion_bibliorato", length = 255)
    private String ubicacionBibliorato;

    @Column(name = "fecha_hora_archivado")
    private LocalDateTime fechaHoraArchivado;

    // Mantenemos la compatibilidad con el mapeo anterior si es necesario
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_recepcion_id")
    private Usuario usuarioRecepcion;
}