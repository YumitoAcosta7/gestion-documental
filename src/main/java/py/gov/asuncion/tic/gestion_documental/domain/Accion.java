package py.gov.asuncion.tic.gestion_documental.domain;

// Acciones que se habilitan según las asignaciones vigentes (PoliticaAcceso).
// En Spring Security se publican como autoridades "PERMISO_<ACCION>".
public enum Accion {
    REGISTRAR_INGRESO,
    REGISTRAR_SALIDA,      // salida exterior, salida directa, acuse final y reemplazo del acuse
    DERIVAR,
    REGISTRAR_ACTUACION,   // informes, memos, actas, devoluciones y confirmación de pases recibidos
    GESTIONAR_ARCHIVO,     // biblioratos y ubicación física
    FORZAR_CIERRE,
    VER_AUDITORIA,
    SUPERVISAR_PASES,      // retirar o reemplazar PDF de pases de otras dependencias del ámbito (confirmar es solo de la receptora)
    ADMINISTRAR_SISTEMA;   // usuarios, asignaciones, suplencias y feriados (solo ADMIN_SISTEMA)

    public String autoridad() {
        return "PERMISO_" + name();
    }
}
