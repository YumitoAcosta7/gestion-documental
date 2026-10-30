package py.gov.asuncion.tic.gestion_documental.dto;

public class SalidaDocumentoDTO {
    private Long documentoId;
    private String destinoExterno; // Institución o dependencia externa destinataria
    private String nroNotaSalida;  // Número de oficio o nota de salida formal
    private String observacion;

    // Getters y Setters
    public Long getDocumentoId() { return documentoId; }
    public void setDocumentoId(Long documentoId) { this.documentoId = documentoId; }
    public String getDestinoExterno() { return destinoExterno; }
    public void setDestinoExterno(String destinoExterno) { this.destinoExterno = destinoExterno; }
    public String getNroNotaSalida() { return nroNotaSalida; }
    public void setNroNotaSalida(String nroNotaSalida) { this.nroNotaSalida = nroNotaSalida; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
