package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.gov.asuncion.tic.gestion_documental.domain.Accion;
import py.gov.asuncion.tic.gestion_documental.domain.AuditoriaSistema;
import py.gov.asuncion.tic.gestion_documental.domain.Feriado;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.AuditoriaSistemaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.FeriadoRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 📅 Calendario de feriados (V18) y conteo de días hábiles: lunes a viernes, menos los feriados.
// Los carga el administrador; cada alta y baja queda en la auditoría de administración.
@Service
public class FeriadoService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FeriadoRepository feriadoRepository;
    private final AuditoriaSistemaRepository auditoriaRepository;
    private final PoliticaAcceso politicaAcceso;

    public FeriadoService(FeriadoRepository feriadoRepository, AuditoriaSistemaRepository auditoriaRepository,
                          PoliticaAcceso politicaAcceso) {
        this.feriadoRepository = feriadoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.politicaAcceso = politicaAcceso;
    }

    // Feriados entre las dos fechas, en una sola consulta (para contar varios plazos de una vez)
    public Set<LocalDate> feriadosEntre(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            return Set.of();
        }
        return new HashSet<>(feriadoRepository.fechasEntre(desde, hasta));
    }

    // Días hábiles transcurridos después de "desde" y hasta "hasta" inclusive
    public static long diasHabilesEntre(LocalDate desde, LocalDate hasta, Set<LocalDate> feriados) {
        long dias = 0;
        for (LocalDate f = desde.plusDays(1); !f.isAfter(hasta); f = f.plusDays(1)) {
            if (f.getDayOfWeek() != DayOfWeek.SATURDAY && f.getDayOfWeek() != DayOfWeek.SUNDAY && !feriados.contains(f)) {
                dias++;
            }
        }
        return dias;
    }

    // Desde el 1 de enero del año en curso: los anteriores ya no influyen en plazos abiertos
    public List<Feriado> calendario(Usuario admin) {
        exigirAdministrador(admin);
        return feriadoRepository.findDesde(LocalDate.now().withDayOfYear(1));
    }

    @Transactional
    public Feriado agregar(LocalDate fecha, String descripcion, Usuario admin) {
        exigirAdministrador(admin);
        if (fecha == null) {
            throw new IllegalArgumentException("Indique la fecha.");
        }
        String texto = descripcion == null ? "" : descripcion.trim().replaceAll("\\s+", " ");
        if (texto.length() < 3 || texto.length() > 150) {
            throw new IllegalArgumentException("Indique el motivo del feriado o asueto (3 a 150 caracteres).");
        }
        if (fecha.getDayOfWeek() == DayOfWeek.SATURDAY || fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException("El " + fecha.format(FECHA) + " es fin de semana: ya no se cuenta como día hábil.");
        }
        if (feriadoRepository.existsById(fecha)) {
            throw new IllegalStateException("El " + fecha.format(FECHA) + " ya está cargado como feriado.");
        }
        Feriado f = feriadoRepository.save(Feriado.builder().fecha(fecha).descripcion(texto).creadoPor(admin).build());
        auditar(admin, "ALTA_FERIADO", fecha.format(FECHA) + ": " + texto);
        return f;
    }

    @Transactional
    public void quitar(LocalDate fecha, Usuario admin) {
        exigirAdministrador(admin);
        Feriado f = feriadoRepository.findById(fecha)
                .orElseThrow(() -> new IllegalArgumentException("No hay feriado cargado el " + fecha.format(FECHA) + "."));
        feriadoRepository.delete(f);
        auditar(admin, "BAJA_FERIADO", fecha.format(FECHA) + ": " + f.getDescripcion());
    }

    private void exigirAdministrador(Usuario admin) {
        politicaAcceso.exigir(admin, Accion.ADMINISTRAR_SISTEMA, "Solo un administrador del sistema puede modificar el calendario.");
    }

    private void auditar(Usuario admin, String accion, String detalle) {
        auditoriaRepository.save(AuditoriaSistema.builder()
                .usuario(admin).accion(accion).entidad("FERIADO").detalle(detalle).build());
    }
}
