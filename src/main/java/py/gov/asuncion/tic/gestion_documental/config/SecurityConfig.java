package py.gov.asuncion.tic.gestion_documental.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import py.gov.asuncion.tic.gestion_documental.domain.Accion;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;
import py.gov.asuncion.tic.gestion_documental.service.PoliticaAcceso;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Registro de sesiones abiertas: permite cerrar las de un usuario al resetear su contraseña o darlo de baja
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    // Avisa al registro cuando una sesión termina (cierre o vencimiento)
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // Publica los eventos de inicio de sesión (exitoso o fallido) que cuenta LimiteIntentosService
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publicador) {
        return new DefaultAuthenticationEventPublisher(publicador);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, PoliticaAcceso politicaAcceso,
                                           UsuarioRepository usuarioRepository, SessionRegistry sessionRegistry) throws Exception {
        http
            // 🔒 Sesiones registradas (sin límite de cantidad) para poder expirarlas desde la administración
            .sessionManagement(sesiones -> sesiones
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
                .expiredUrl("/login?expirada"))

            // 🔄 Permisos recalculados en cada petición desde las asignaciones vigentes
            .addFilterBefore(new ActualizacionPermisosFilter(politicaAcceso, usuarioRepository), AuthorizationFilter.class)

            // 🛡️ Configuración de Token CSRF
            .csrf(csrf -> csrf.csrfTokenRepository(
                org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse()))
            
            // 🚫 Desactivar la caché del navegador para forzar re-validación al usar el botón "Atrás"
            .headers(headers -> headers
                .cacheControl(cache -> cache.disable())
            )
            
            // 🔐 Reglas de autorización por acción (PoliticaAcceso: rol × ámbito × vigencia)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/login").permitAll()
                .requestMatchers("/admin/**").hasAuthority(Accion.ADMINISTRAR_SISTEMA.autoridad())
                .requestMatchers("/documentos/guardar").hasAuthority(Accion.REGISTRAR_INGRESO.autoridad())
                .requestMatchers("/documentos/salida", "/documentos/salida-directa", "/documentos/confirmar-acuse", "/documentos/reemplazar-acuse")
                    .hasAuthority(Accion.REGISTRAR_SALIDA.autoridad())
                .requestMatchers("/documentos/ubicacion", "/biblioratos/**").hasAuthority(Accion.GESTIONAR_ARCHIVO.autoridad())
                .requestMatchers("/documentos/forzar-cierre").hasAuthority(Accion.FORZAR_CIERRE.autoridad())
                .requestMatchers("/api/documentos/*/auditoria").hasAuthority(Accion.VER_AUDITORIA.autoridad())
                // Derivar: con DERIVAR a cualquier destino del canal; con solo REGISTRAR_ACTUACION, elevar al superior
                // (el servicio limita ese caso a un pase de trámite hacia arriba)
                .requestMatchers("/documentos/derivar").hasAnyAuthority(Accion.DERIVAR.autoridad(), Accion.REGISTRAR_ACTUACION.autoridad())
                // Ver los PDF de las actuaciones (GET) queda para todo usuario con acceso al expediente
                .requestMatchers(HttpMethod.POST, "/documentos/actuaciones/**", "/documentos/devolver", "/documentos/propio")
                    .hasAuthority(Accion.REGISTRAR_ACTUACION.autoridad())
                .anyRequest().authenticated()
            )
            
            // 🔑 Formulario de Autenticación
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                // Bloqueado por intentos fallidos (LimiteIntentosService): mensaje propio
                .failureHandler((pedido, respuesta, error) -> respuesta.sendRedirect(pedido.getContextPath()
                        + (error instanceof LockedException ? "/login?bloqueado" : "/login?error")))
                .permitAll()
            )
            
            // 🚪 Cierre de Sesión Seguro y Destrucción de Cookie
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true) // Destruye la sesión en el servidor
                .clearAuthentication(true)   // Limpia las credenciales del contexto
                .deleteCookies("JSESSIONID") // Elimina la cookie de sesión del navegador
                .permitAll()
            );

        return http.build();
    }
}