package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.ActuacionDocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DependenciaRepository;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;
import py.gov.asuncion.tic.gestion_documental.repository.ReporteRepository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// Reportes de la pantalla "Reportes": cada persona ve solo lo que está a cargo de las dependencias por las que actúa.
@Service
@Transactional(readOnly = true)
public class ReporteService {

    public static final int MAXIMO_ANIOS = 5;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("es-PY"));

    private static final Map<String, String> ESTADOS = Map.of(
            "INGRESADO", "Ingresado",
            "EN_REVISION", "En revisión",
            "EN_ESPERA_INFORME", "En espera de informe",
            "CON_RESPUESTA_TECNICA", "Con respuesta técnica",
            "PENDIENTE_SALIDA", "Pendiente de salida");

    @Autowired private ReporteRepository reporteRepository;
    @Autowired private DependenciaRepository dependenciaRepository;
    @Autowired private DocumentoRepository documentoRepository;
    @Autowired private ActuacionDocumentoRepository actuacionRepository;
    @Autowired private BiblioratoService biblioratoService;
    @Autowired private FeriadoService feriadoService;

    @Value("${app.sla.dias-advertencia:5}") private int diasAdvertencia;
    @Value("${app.sla.dias-critico:10}") private int diasCritico;
    @Value("${app.sla.urgente.dias-advertencia:2}") private int urgenteDiasAdvertencia;
    @Value("${app.sla.urgente.dias-critico:4}") private int urgenteDiasCritico;
    // RI 2653/12: plazo para producir un informe
    @Value("${app.reportes.dias-informe:8}") private int diasInforme;

    public record Filtro(Integer dependenciaId, Integer tipoId, LocalDate desde, LocalDate hasta) {
        // Sin fechas: los últimos 12 meses. Fechas invertidas se ordenan, y el período tiene un máximo
        public static Filtro de(Integer dependenciaId, Integer tipoId, LocalDate desde, LocalDate hasta, LocalDate hoy) {
            LocalDate fin = hasta != null ? hasta : hoy;
            LocalDate inicio = desde != null ? desde : fin.minusMonths(11).withDayOfMonth(1);
            if (inicio.isAfter(fin)) {
                LocalDate t = inicio;
                inicio = fin;
                fin = t;
            }
            if (inicio.isBefore(fin.minusYears(MAXIMO_ANIOS))) {
                inicio = fin.minusYears(MAXIMO_ANIOS);
            }
            return new Filtro(dependenciaId, tipoId, inicio, fin);
        }
    }

    public record Resumen(long activos, long conDemora, long criticos, long pasesSinRecibir, long pedidosVencidos, long finalizadosPeriodo) {}
    public record FilaDependencia(Integer id, String nombre, int nivel, long activosAqui, long activos, long conDemora, long criticos, long terminados) {}
    public record FilaAntiguedad(String estado, long hasta5, long de6a10, long de11a30, long mas30, long total) {}
    public record FilaDemorado(String cuti, String asunto, String tipo, Integer dependenciaId, String dependencia, String estado, boolean urgente,
                               long diasSinMovimiento, long diasEnDependencia, String nivel) {
        public boolean critico() { return DocumentoService.IndicadorSla.CRITICO.equals(nivel); }
    }
    public record FilaPase(String cuti, String asunto, String tipo, String de, String para, String fecha, long dias) {}
    public record FilaPedido(String cuti, String asunto, String de, String para, String fecha, long dias, boolean vencido) {}
    public record FilaMes(String mes, long ingresos, long finalizados) {
        public long saldo() { return ingresos - finalizados; }
    }
    public record FilaTiempo(String nombre, long cantidad, double mediana, double maximo) {}
    public record FilaSinGuardar(String cuti, String asunto, String dependencia, String salida) {}
    public record FilaCarpeta(String codigo, String descripcion, String dependencia, boolean abierta, long documentos, long ultimaFoja, Integer capacidad) {
        public Integer porcentaje() {
            return capacidad == null || capacidad <= 0 ? null : (int) Math.min(100, ultimaFoja * 100 / capacidad);
        }
    }

    public record Datos(Resumen resumen, List<FilaDependencia> dependencias, List<FilaAntiguedad> antiguedad, List<FilaDemorado> demorados,
                        List<FilaPase> pases, List<FilaPedido> pedidos, List<FilaMes> flujo,
                        List<FilaTiempo> tiemposPorDependencia, List<FilaTiempo> tiemposPorTipo,
                        List<FilaSinGuardar> sinGuardar, List<FilaCarpeta> carpetas, int diasInforme,
                        int diasAdvertencia, int diasCritico, int urgenteDiasAdvertencia, int urgenteDiasCritico) {}

    private record Activo(String cuti, String asunto, String estado, boolean urgente, Integer custodia, LocalDate ingreso,
                          LocalDate movimiento, LocalDate llegada, String tipo) {}

    public List<Dependencia> dependenciasDelAlcance(Usuario usuario) {
        return biblioratoService.dependenciasDelAlcance(usuario);
    }

    public Datos generar(Usuario usuario, Filtro filtro) {
        LocalDate hoy = LocalDate.now();
        List<Dependencia> alcance = dependenciasFiltradas(usuario, filtro.dependenciaId());
        Set<Integer> deps = alcance.stream().map(Dependencia::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (deps.isEmpty()) {
            return vacio();
        }
        Function<Integer, String> nombres = nombresDeDependencias();
        Integer tipo = filtro.tipoId();
        LocalDateTime desde = filtro.desde().atStartOfDay();
        LocalDateTime hasta = filtro.hasta().plusDays(1).atStartOfDay();

        List<Activo> activos = reporteRepository.activos(deps, tipo).stream().map(ReporteService::aActivo).toList();
        List<Object[]> pasesCrudos = reporteRepository.pasesSinRecibir(deps, tipo);
        List<PedidoAbierto> pedidosAbiertos = pedidosAbiertos(deps, tipo);

        // Los feriados de todo el período en una sola consulta
        Set<LocalDate> feriados = feriados(activos, pasesCrudos, pedidosAbiertos, hoy);

        List<FilaDemorado> demorados = demorados(activos, nombres, feriados, hoy);
        List<FilaPase> pases = pasesCrudos.stream().map(f -> {
            LocalDate fecha = aFecha(f[5]);
            return new FilaPase((String) f[0], (String) f[1], nombrePase((String) f[2]), nombres.apply(entero(f[3])), nombres.apply(entero(f[4])),
                    texto(fecha), dias(fecha, hoy, feriados));
        }).toList();
        List<FilaPedido> pedidos = pedidosAbiertos.stream().map(p -> {
            long dias = dias(p.fecha(), hoy, feriados);
            return new FilaPedido(p.cuti(), p.asunto(), p.de(), p.para(), texto(p.fecha()), dias, dias > diasInforme);
        }).sorted(Comparator.comparingLong(FilaPedido::dias).reversed()).toList();
        List<FilaMes> flujo = flujo(deps, tipo, filtro, desde, hasta);

        Resumen resumen = new Resumen(activos.size(), demorados.size(), demorados.stream().filter(FilaDemorado::critico).count(),
                pases.size(), pedidos.stream().filter(FilaPedido::vencido).count(), flujo.stream().mapToLong(FilaMes::finalizados).sum());

        List<FilaTiempo> tiemposDep = reporteRepository.resolucionPorDependencia(deps, tipo, desde, hasta).stream()
                .map(f -> new FilaTiempo(nombres.apply(entero(f[0])), numero(f[1]), decimal(f[2]), decimal(f[3])))
                .sorted(Comparator.comparing(FilaTiempo::mediana).reversed()).toList();
        List<FilaTiempo> tiemposTipo = reporteRepository.resolucionPorTipo(deps, tipo, desde, hasta).stream()
                .map(f -> new FilaTiempo((String) f[0], numero(f[1]), decimal(f[2]), decimal(f[3]))).toList();
        List<FilaSinGuardar> sinGuardar = reporteRepository.finalizadosSinGuardar(deps, tipo).stream()
                .map(f -> new FilaSinGuardar((String) f[0], (String) f[1], nombres.apply(entero(f[2])), texto(aFecha(f[3])))).toList();

        return new Datos(resumen, porDependencia(alcance, activos, demorados, deps, tipo, nombres), antiguedad(activos, feriados, hoy),
                demorados, pases, pedidos, flujo, tiemposDep, tiemposTipo, sinGuardar, carpetas(usuario, deps),
                diasInforme, diasAdvertencia, diasCritico, urgenteDiasAdvertencia, urgenteDiasCritico);
    }

    // El alcance del usuario. Si eligió una dependencia, esa y sus áreas; una ajena no amplía nada: se ignora
    private List<Dependencia> dependenciasFiltradas(Usuario usuario, Integer dependenciaId) {
        List<Dependencia> alcance = biblioratoService.dependenciasDelAlcance(usuario).stream()
                .sorted(Comparator.comparing(d -> d.getRuta() != null ? d.getRuta() : "")).toList();
        if (dependenciaId == null) {
            return alcance;
        }
        Dependencia elegida = alcance.stream().filter(d -> d.getId().equals(dependenciaId)).findFirst().orElse(null);
        return elegida == null ? alcance : alcance.stream().filter(d -> incluye(elegida, d)).toList();
    }

    private static boolean incluye(Dependencia superior, Dependencia otra) {
        return superior.getId().equals(otra.getId())
                || (superior.getRuta() != null && otra.getRuta() != null && otra.getRuta().startsWith(superior.getRuta()));
    }

    private Function<Integer, String> nombresDeDependencias() {
        Map<Integer, Dependencia> todas = dependenciaRepository.findAll().stream().collect(Collectors.toMap(Dependencia::getId, d -> d));
        return id -> {
            Dependencia d = todas.get(id);
            return d != null ? d.getNombreVisible() : "-";
        };
    }

    private List<FilaDependencia> porDependencia(List<Dependencia> alcance, List<Activo> activos, List<FilaDemorado> demorados,
                                                 Set<Integer> deps, Integer tipo, Function<Integer, String> nombres) {
        Map<Integer, Long> aqui = activos.stream().collect(Collectors.groupingBy(Activo::custodia, Collectors.counting()));
        Map<Integer, Long> demoras = demorados.stream().collect(Collectors.groupingBy(FilaDemorado::dependenciaId, Collectors.counting()));
        Map<Integer, Long> enCritico = demorados.stream().filter(FilaDemorado::critico)
                .collect(Collectors.groupingBy(FilaDemorado::dependenciaId, Collectors.counting()));
        Map<Integer, Long> terminados = new HashMap<>();
        for (Object[] f : reporteRepository.terminadosPorCustodia(deps, tipo)) {
            terminados.put(entero(f[0]), numero(f[1]));
        }

        List<FilaDependencia> filas = new ArrayList<>();
        for (Dependencia d : alcance) {
            long total = 0, demora = 0, criticos = 0, fin = 0;
            int nivel = 0;
            for (Dependencia otra : alcance) {
                if (incluye(d, otra)) {
                    total += aqui.getOrDefault(otra.getId(), 0L);
                    demora += demoras.getOrDefault(otra.getId(), 0L);
                    criticos += enCritico.getOrDefault(otra.getId(), 0L);
                    fin += terminados.getOrDefault(otra.getId(), 0L);
                }
                if (!otra.getId().equals(d.getId()) && incluye(otra, d)) {
                    nivel++;
                }
            }
            if (total + fin > 0) {
                filas.add(new FilaDependencia(d.getId(), nombres.apply(d.getId()), nivel, aqui.getOrDefault(d.getId(), 0L), total, demora, criticos, fin));
            }
        }
        return filas;
    }

    private List<FilaAntiguedad> antiguedad(List<Activo> activos, Set<LocalDate> feriados, LocalDate hoy) {
        Map<String, long[]> porEstado = new LinkedHashMap<>();
        ESTADOS.keySet().stream().sorted(Comparator.comparingInt(e -> EstadoDocumento.valueOf(e).ordinal())).forEach(e -> porEstado.put(e, new long[4]));
        for (Activo a : activos) {
            long dias = dias(a.ingreso(), hoy, feriados);
            int franja = dias <= 5 ? 0 : dias <= 10 ? 1 : dias <= 30 ? 2 : 3;
            porEstado.computeIfAbsent(a.estado(), k -> new long[4])[franja]++;
        }
        List<FilaAntiguedad> filas = new ArrayList<>();
        porEstado.forEach((estado, c) -> filas.add(new FilaAntiguedad(ESTADOS.getOrDefault(estado, estado), c[0], c[1], c[2], c[3], c[0] + c[1] + c[2] + c[3])));
        return filas;
    }

    private List<FilaDemorado> demorados(List<Activo> activos, Function<Integer, String> nombres, Set<LocalDate> feriados, LocalDate hoy) {
        List<FilaDemorado> filas = new ArrayList<>();
        for (Activo a : activos) {
            LocalDate movimiento = a.movimiento() != null && (a.ingreso() == null || a.movimiento().isAfter(a.ingreso())) ? a.movimiento() : a.ingreso();
            long sinMovimiento = dias(movimiento, hoy, feriados);
            int advertencia = a.urgente() ? urgenteDiasAdvertencia : diasAdvertencia;
            int critico = a.urgente() ? urgenteDiasCritico : diasCritico;
            if (sinMovimiento < advertencia) {
                continue;
            }
            filas.add(new FilaDemorado(a.cuti(), a.asunto(), a.tipo(), a.custodia(), nombres.apply(a.custodia()), ESTADOS.getOrDefault(a.estado(), a.estado()), a.urgente(),
                    sinMovimiento, dias(a.llegada() != null ? a.llegada() : a.ingreso(), hoy, feriados),
                    sinMovimiento >= critico ? DocumentoService.IndicadorSla.CRITICO : DocumentoService.IndicadorSla.ADVERTENCIA));
        }
        filas.sort(Comparator.comparingLong(FilaDemorado::diasSinMovimiento).reversed());
        return filas;
    }

    private record PedidoAbierto(String cuti, String asunto, String de, String para, LocalDate fecha) {}

    private List<PedidoAbierto> pedidosAbiertos(Set<Integer> deps, Integer tipo) {
        List<Long> ids = reporteRepository.documentosConPedidos(deps, tipo).stream().map(Number::longValue).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Documento> documentos = documentoRepository.findAllById(ids).stream().collect(Collectors.toMap(Documento::getId, d -> d));
        Map<Long, List<ActuacionDocumento>> porDocumento = actuacionRepository.findDeDocumentos(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getDocumento().getId()));
        List<PedidoAbierto> abiertos = new ArrayList<>();
        porDocumento.forEach((id, actuaciones) -> {
            Documento doc = documentos.get(id);
            for (ActuacionDocumento p : DocumentoService.pedidosAbiertos(actuaciones)) {
                if (deps.contains(p.getDependenciaReceptoraPase().getId())) {
                    abiertos.add(new PedidoAbierto(doc.getCodigoCuti(), doc.getAsunto(), p.getDependenciaEmisora().getNombreVisible(),
                            p.getDependenciaReceptoraPase().getNombreVisible(), p.getFechaHoraActuacion().toLocalDate()));
                }
            }
        });
        return abiertos;
    }

    private List<FilaMes> flujo(Set<Integer> deps, Integer tipo, Filtro filtro, LocalDateTime desde, LocalDateTime hasta) {
        Map<String, Long> ingresos = porMes(reporteRepository.ingresosPorMes(deps, tipo, desde, hasta));
        Map<String, Long> finalizados = porMes(reporteRepository.finalizadosPorMes(deps, tipo, desde, hasta));
        List<FilaMes> filas = new ArrayList<>();
        YearMonth fin = YearMonth.from(filtro.hasta());
        for (YearMonth m = YearMonth.from(filtro.desde()); !m.isAfter(fin); m = m.plusMonths(1)) {
            String clave = m.toString();
            filas.add(new FilaMes(m.format(MES), ingresos.getOrDefault(clave, 0L), finalizados.getOrDefault(clave, 0L)));
        }
        return filas;
    }

    private static Map<String, Long> porMes(List<Object[]> filas) {
        Map<String, Long> meses = new HashMap<>();
        filas.forEach(f -> meses.put((String) f[0], numero(f[1])));
        return meses;
    }

    private List<FilaCarpeta> carpetas(Usuario usuario, Set<Integer> deps) {
        Map<Integer, long[]> uso = biblioratoService.obtenerUso();
        return biblioratoService.listarTodos(usuario).stream()
                .filter(b -> b.getDependencia() != null && deps.contains(b.getDependencia().getId()))
                .map(b -> {
                    long[] u = uso.getOrDefault(b.getId(), new long[2]);
                    return new FilaCarpeta(b.getCodigo(), b.getDescripcion(), b.getDependencia().getNombreVisible(),
                            Boolean.TRUE.equals(b.getActivo()), u[0], u[1], b.getCapacidadFojas());
                }).toList();
    }

    private Set<LocalDate> feriados(List<Activo> activos, List<Object[]> pases, List<PedidoAbierto> pedidos, LocalDate hoy) {
        LocalDate desde = hoy;
        for (Activo a : activos) {
            for (LocalDate f : new LocalDate[] { a.ingreso(), a.movimiento(), a.llegada() }) {
                if (f != null && f.isBefore(desde)) desde = f;
            }
        }
        for (Object[] p : pases) {
            LocalDate f = aFecha(p[5]);
            if (f != null && f.isBefore(desde)) desde = f;
        }
        for (PedidoAbierto p : pedidos) {
            if (p.fecha().isBefore(desde)) desde = p.fecha();
        }
        return feriadoService.feriadosEntre(desde, hoy);
    }

    private static long dias(LocalDate desde, LocalDate hoy, Set<LocalDate> feriados) {
        return desde == null ? 0 : FeriadoService.diasHabilesEntre(desde, hoy, feriados);
    }

    private Datos vacio() {
        return new Datos(new Resumen(0, 0, 0, 0, 0, 0), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), diasInforme, diasAdvertencia, diasCritico, urgenteDiasAdvertencia, urgenteDiasCritico);
    }

    private static Activo aActivo(Object[] f) {
        return new Activo((String) f[1], (String) f[2], (String) f[3], "URGENTE".equals(f[4]), entero(f[5]),
                aFecha(f[6]), aFecha(f[7]), aFecha(f[8]), (String) f[9]);
    }

    private static String nombrePase(String tipo) {
        if (tipo == null) return "-";
        return switch (tipo) {
            case "TRAMITE" -> "Original";
            case "INFORME" -> "Pedido de informe";
            case "CONOCIMIENTO" -> "Copia";
            default -> tipo;
        };
    }

    private static String texto(LocalDate fecha) {
        return fecha != null ? fecha.format(FECHA) : "-";
    }

    private static LocalDate aFecha(Object valor) {
        if (valor instanceof LocalDateTime f) return f.toLocalDate();
        if (valor instanceof Timestamp f) return f.toLocalDateTime().toLocalDate();
        if (valor instanceof LocalDate f) return f;
        return null;
    }

    private static Integer entero(Object valor) {
        return valor != null ? ((Number) valor).intValue() : null;
    }

    private static long numero(Object valor) {
        return valor != null ? ((Number) valor).longValue() : 0;
    }

    private static double decimal(Object valor) {
        return valor != null ? ((Number) valor).doubleValue() : 0;
    }
}
