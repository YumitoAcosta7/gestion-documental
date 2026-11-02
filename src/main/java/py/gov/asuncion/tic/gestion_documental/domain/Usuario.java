package py.gov.asuncion.tic.gestion_documental.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "usuarios")
@Data
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String email;

    @ManyToOne
    @JoinColumn(name = "dependencia_id", nullable = false)
    private Dependencia dependencia;

    // Rol anterior a las asignaciones (V8): ya no autoriza nada y los usuarios nuevos no lo llevan
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", length = 50)
    private RolUsuario rol;

    @Column(nullable = false)
    private Boolean activo = true;

    // Tras un alta o un reseteo entra con una contraseña temporal y debe cambiarla
    @Column(name = "debe_cambiar_contrasena", nullable = false)
    private Boolean debeCambiarContrasena = false;
}
