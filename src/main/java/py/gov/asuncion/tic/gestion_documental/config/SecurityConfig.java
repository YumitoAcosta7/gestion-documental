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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import py.gov.asuncion.tic.gestion_documental.domain.Accion;
import py.gov.asuncion.tic.gestion_documental.repository.UsuarioRepository;
import py.gov.asuncion.tic.gestion_documental.service.PoliticaAcceso;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Política de contenido: solo recursos propios y los de cdn.jsdelivr.net (con SRI en las plantillas).
    // Los scripts no admiten código en línea: todo el JavaScript está en /js y los eventos se asignan con
    // addEventListener. Un texto inyectado en una página no puede ejecutar código. Los estilos en línea
    // (atributos style) se siguen admitiendo: no ejecutan código.
    static final String POLITICA_CONTENIDO = String.join("; ",
            "default-src 'self'",
            "script-src 'self' https://cdn.jsdelivr.net",
            "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net",
            "font-src 'self' https://cdn.jsdelivr.net",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'self'");

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
            // 🛡️ Cabeceras de seguridad (auditoría B3). Los PDF llevan su propia CSP (sandbox), que tiene prioridad.
            .headers(headers -> headers
                .cacheControl(cache -> cache.disable())
                .contentSecurityPolicy(csp -> csp.policyDirectives(POLITICA_CONTENIDO))
                .frameOptions(marcos -> marcos.sameOrigin())
                .referrerPolicy(referencia -> referencia.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                .permissionsPolicyHeader(permisos -> permisos.policy("camera=(), microphone=(), geolocation=(), payment=()"))
            )
            
            // 🔐 Reglas de autorización por acción (PoliticaAcceso: rol × ámbito × vigencia)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/login").permitAll()
                .requestMatchers("/admin/**").hasAuthority(Accion.ADMINISTRAR_SISTEMA.autoridad())
                .requestMatchers("/documentos/guardar").hasAuthority(Accion.REGISTRAR_INGRESO.autoridad())
                .requestMatchers("/documentos/salida", "/documentos/confirmar-acuse", "/documentos/reemplazar-acuse")
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