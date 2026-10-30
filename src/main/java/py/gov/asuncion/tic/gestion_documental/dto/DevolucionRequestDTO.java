package py.gov.asuncion.tic.gestion_documental.dto;

public class DevolucionRequestDTO {
    private Long documentoId;
    private String observacion;

    // Getters y Setters
    public Long getDocumentoId() { return documentoId;}
    public void setDocumentoId(Long documentoId) { this.documentoId = documentoId; }
    public String getOservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
