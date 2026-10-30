package py.gov.asuncion.tic.gestion_documental.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 🛡️ Configuración de Token CSRF
            .csrf(csrf -> csrf.csrfTokenRepository(
                org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse()))
            
            // 🚫 Desactivar la caché del navegador para forzar re-validación al usar el botón "Atrás"
            .headers(headers -> headers
                .cacheControl(cache -> cache.disable())
            )
            
            // 🔐 Reglas de autorización por Roles Institucionales
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/login").permitAll()
                
                // Acciones exclusivas de Secretaría Administradora y Dirección
                .requestMatchers("/documentos/guardar", "/documentos/salida", "/documentos/salida-directa", "/documentos/confirmar-acuse", "/api/documentos/*/auditoria")
                    .hasAnyRole("SECRETARIA_ADMIN", "DIRECTOR")
                
                // Acciones de derivación para Jefes de Departamento, Secretaría y Dirección
                .requestMatchers("/documentos/derivar")
                    .hasAnyRole("SECRETARIA_ADMIN", "DIRECTOR", "JEFE_DEPTO")
                
                // Carga de actuaciones (Informes/Actas/Memos) para Jefes de Unidad, Jefes de Dpto, Secretaría y Dirección
                .requestMatchers("/documentos/actuaciones/**")
                    .hasAnyRole("SECRETARIA_ADMIN", "DIRECTOR", "JEFE_DEPTO", "JEFE_UNIDAD")
                
                .anyRequest().authenticated()
            )
            
            // 🔑 Formulario de Autenticación
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
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