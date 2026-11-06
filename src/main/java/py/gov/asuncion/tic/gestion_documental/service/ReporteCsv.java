package py.gov.asuncion.tic.gestion_documental.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

// Exporta cada reporte a CSV para abrirlo en Excel: UTF-8 con BOM y punto y coma, como lo espera Excel en español.
public final class ReporteCsv {

    private ReporteCsv() {}

    public static final List<String> REPORTES = List.of("dependencias", "sin-finalizar", "demorados", "pases", "pedidos", "flujo", "tiempos", "archivo");

    // null si el reporte no existe
    public static byte[] generar(String reporte, ReporteService.Datos d) {
        List<List<String>> filas = new ArrayList<>();
        switch (reporte) {
            case "dependencias" -> {
                filas.add(List.of("Dependencia", "Activos aquí", "Activos con sus áreas", "Con demora", "Críticos", "Terminados"));
                d.dependencias().forEach(f -> filas.add(List.of(f.nombre(), n(f.activosAqui()), n(f.activos()), n(f.conDemora()), n(f.criticos()), n(f.terminados()))));
            }
            case "sin-finalizar" -> {
                filas.add(List.of("Estado", "Hasta 5 días", "6 a 10 días", "11 a 30 días", "Más de 30 días", "Total"));
                d.antiguedad().forEach(f -> filas.add(List.of(f.estado(), n(f.hasta5()), n(f.de6a10()), n(f.de11a30()), n(f.mas30()), n(f.total()))));
            }
            case "demorados" -> {
                filas.add(List.of("CUTI", "Asunto", "Tipo", "Dependencia", "Estado", "Prioridad", "Días sin movimiento", "Días en la dependencia", "Nivel"));
                d.demorados().forEach(f -> filas.add(List.of(f.cuti(), f.asunto(), f.tipo(), f.dependencia(), f.estado(), f.urgente() ? "Urgente" : "Normal",
                        n(f.diasSinMovimiento()), n(f.diasEnDependencia()), f.critico() ? "Crítico" : "Advertencia")));
            }
            case "pases" -> {
                filas.add(List.of("CUTI", "Asunto", "Tipo", "De", "Para", "Enviado", "Días hábiles"));
                d.pases().forEach(f -> filas.add(List.of(f.cuti(), f.asunto(), f.tipo(), f.de(), f.para(), f.fecha(), n(f.dias()))));
            }
            case "pedidos" -> {
                filas.add(List.of("CUTI", "Asunto", "Pidió", "Debe responder", "Fecha", "Días hábiles", "Fuera de plazo"));
                d.pedidos().forEach(f -> filas.add(List.of(f.cuti(), f.asunto(), f.de(), f.para(), f.fecha(), n(f.dias()), f.vencido() ? "Sí" : "No")));
            }
            case "flujo" -> {
                filas.add(List.of("Mes", "Ingresos", "Finalizados", "Saldo"));
                d.flujo().forEach(f -> filas.add(List.of(f.mes(), n(f.ingresos()), n(f.finalizados()), n(f.saldo()))));
            }
            case "tiempos" -> {
                filas.add(List.of("Agrupado por", "Nombre", "Finalizados", "Mediana de días", "Máximo de días"));
                d.tiemposPorDependencia().forEach(f -> filas.add(tiempo("Dependencia", f)));
                d.tiemposPorTipo().forEach(f -> filas.add(tiempo("Tipo", f)));
            }
            case "archivo" -> {
                filas.add(List.of("CUTI", "Asunto", "Dependencia con el original", "Salida"));
                d.sinGuardar().forEach(f -> filas.add(List.of(f.cuti(), f.asunto(), f.dependencia(), f.salida())));
            }
            default -> {
                return null;
            }
        }
        String cuerpo = filas.stream().map(f -> f.stream().map(ReporteCsv::celda).collect(Collectors.joining(";")))
                .collect(Collectors.joining("\r\n", "﻿", "\r\n"));
        return cuerpo.getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> tiempo(String grupo, ReporteService.FilaTiempo f) {
        return List.of(grupo, f.nombre(), n(f.cantidad()), String.format(Locale.forLanguageTag("es-PY"), "%.1f", f.mediana()),
                String.format(Locale.forLanguageTag("es-PY"), "%.1f", f.maximo()));
    }

    private static String n(long valor) {
        return Long.toString(valor);
    }

    // Un asunto lo escribe cualquiera: lo que Excel tomaría como fórmula se exporta como texto
    static String celda(String valor) {
        String v = valor == null ? "" : valor;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
