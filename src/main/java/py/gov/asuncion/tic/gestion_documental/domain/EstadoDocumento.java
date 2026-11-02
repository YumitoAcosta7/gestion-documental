package py.gov.asuncion.tic.gestion_documental.domain;

public enum EstadoDocumento {
    INGRESADO,
    EN_REVISION,
    EN_ESPERA_INFORME,
    CON_RESPUESTA_TECNICA,
    PENDIENTE_SALIDA,
    FINALIZADO,
    ARCHIVADO   // Documento informativo (ej. circular): se archiva al registrarse, no se tramita
}
