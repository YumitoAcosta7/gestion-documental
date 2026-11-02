package py.gov.asuncion.tic.gestion_documental.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CambioContrasenaInterceptor cambioContrasenaInterceptor;

    public WebConfig(CambioContrasenaInterceptor cambioContrasenaInterceptor) {
        this.cambioContrasenaInterceptor = cambioContrasenaInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cambioContrasenaInterceptor)
                .excludePathPatterns("/cuenta/**", "/login", "/logout", "/error", "/css/**", "/js/**", "/images/**");
    }
}
