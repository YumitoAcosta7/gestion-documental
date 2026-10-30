package py.gov.asuncion.tic.gestion_documental.dto;

import py.gov.asuncion.tic.gestion_documental.domain.Prioridad;

// Datos permitidos en el formulario de ingreso. Evita que se envíen campos internos
// del Documento (CUTI, estado, usuarios, fechas) desde el navegador.
public class NuevoDocumentoDTO {
    private Integer tipoDocumentoId;
    private String numeroDocumentoOrigen;
    private Prioridad prioridad;
    private Integer dependenciaOrigenId;
    private String personaEntrega;
    private Integer nroOrdenLibro;
    private Integer nroPaginaLibro;
    private String asunto;

    // Getters y Setters
    public Integer getTipoDocumentoId() { return tipoDocumentoId; }
    public void setTipoDocumentoId(Integer tipoDocumentoId) { this.tipoDocumentoId = tipoDocumentoId; }
    public String getNumeroDocumentoOrigen() { return numeroDocumentoOrigen; }
    public void setNumeroDocumentoOrigen(String numeroDocumentoOrigen) { this.numeroDocumentoOrigen = numeroDocumentoOrigen; }
    public Prioridad getPrioridad() { return prioridad; }
    public void setPrioridad(Prioridad prioridad) { this.prioridad = prioridad; }
    public Integer getDependenciaOrigenId() { return dependenciaOrigenId; }
    public void setDependenciaOrigenId(Integer dependenciaOrigenId) { this.dependenciaOrigenId = dependenciaOrigenId; }
    public String getPersonaEntrega() { return personaEntrega; }
    public void setPersonaEntrega(String personaEntrega) { this.personaEntrega = personaEntrega; }
    public Integer getNroOrdenLibro() { return nroOrdenLibro; }
    public void setNroOrdenLibro(Integer nroOrdenLibro) { this.nroOrdenLibro = nroOrdenLibro; }
    public Integer getNroPaginaLibro() { return nroPaginaLibro; }
    public void setNroPaginaLibro(Integer nroPaginaLibro) { this.nroPaginaLibro = nroPaginaLibro; }
    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
}
