package py.gov.asuncion.tic.gestion_documental.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.gov.asuncion.tic.gestion_documental.domain.*;
import py.gov.asuncion.tic.gestion_documental.repository.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// 📦 Auditoría M7: traslado del original de una dependencia que quedó sin nadie que actúe por ella.
// Sin este camino el original no se puede mover. Pide un administrador y aprueba otro, con motivo y acto
// administrativo. Solo vale para esas dependencias: las que funcionan mueven lo suyo con un pase y su acuse.
@Service
public class TrasladoCustodiaService {

    static final int EXPEDIENTES_EN_PANEL = 200;

    private final SolicitudTrasladoCustodiaRepository solicitudes;
    private final DocumentoRepository documentos;
    private final DependenciaRepository dependencias;
    private final ActuacionDocumentoRepository actuaciones;
    private final PoliticaAcceso politicaAcceso;
    private final AuditoriaService auditoriaService;
    private final AuditoriaSistemaRepository auditoriaSistema;

    public TrasladoCustodiaService(SolicitudTrasladoCustodiaRepository solicitudes, DocumentoRepository documentos,
                                   DependenciaRepository dependencias, ActuacionDocumentoRepository actuaciones,
                                   PoliticaAcceso politicaAcceso, AuditoriaService auditoriaService,
                                   AuditoriaSistemaRepository auditoriaSistema) {
        this.solicitudes = solicitudes;
        this.documentos = documentos;
        this.dependencias = dependencias;
        this.actuaciones = actuaciones;
        this.politicaAcceso = politicaAcceso;
        this.auditoriaService = auditoriaService;
        this.auditoriaSistema = auditoriaSistema;
    }

    // Solo datos de circulación: el administrador no ve el contenido del expediente
    public record CustodiaSinReceptor(Long documentoId, String cuti, EstadoDocumento estado, LocalDateTime ingreso, String custodia,
                                      boolean dependenciaInactiva, boolean originalEnCamino, boolean trasladoPedido) {
        public boolean trasladable() {
            return !originalEnCamino && !trasladoPedido;
        }
    }

    public record AvisoCustodias(long total, List<CustodiaSinReceptor> expedientes) {
        public static final AvisoCustodias NINGUNO = new AvisoCustodias(0, List.of());

        public List<CustodiaSinReceptor> trasladables() {
            return expedientes.stream().filter(CustodiaSinReceptor::trasladable).toList();
        }
    }

    private static Dependencia custodiaDe(Documento doc) {
        if (doc.getDependenciaCustodia() != null) {
            return doc.getDependenciaCustodia();
        }
        return doc.getDependenciaRegistro() != null ? doc.getDependenciaRegistro() : doc.getDependenciaOrigen();
    }

    private boolean tieneQuienActue(Dependencia dependencia) {
        return !dependencias.findConReceptor(List.of(dependencia.getId())).isEmpty();
    }

    private void exigirAdministrador(Usuario usuario) {
        politicaAcceso.exigir(usuario, Accion.ADMINISTRAR_SISTEMA, "Solo un administrador del sistema puede trasladar la custodia de un original.");
    }

    // Las condiciones se revisan al pedir y otra vez al aprobar: entre uno y otro la dependencia pudo volver a tener personal
    private void exigirTrasladable(Documento doc, Dependencia origen, Dependencia destino) {
        if (doc.getEstadoActual() == EstadoDocumento.ANULADO) {
            throw new IllegalStateException("El expediente " + doc.getCodigoCuti() + " está anulado: no tiene original que trasladar.");
        }
        if (tieneQuienActue(origen)) {
            throw new IllegalStateException(origen.getNombreVisible() + " tiene quién actúe por ella: el original se mueve con un pase.");
        }
        if (origen.getId().equals(destino.getId())) {
            throw new IllegalArgumentException("La dependencia de destino debe ser otra.");
        }
        if (!tieneQuienActue(destino)) {
            throw new IllegalStateException("Nadie puede hacerse cargo del original en " + destino.getNombreVisible() + ". Elija otra dependencia o habilite primero a su personal.");
        }
        if (!actuaciones.conOriginalEnCamino(List.of(doc.getId())).isEmpty()) {
            throw new IllegalStateException("El original de " + doc.getCodigoCuti() + " está en camino. Primero debe recibirse o retirarse ese pase.");
        }
    }

    @Transactional
    public SolicitudTrasladoCustodia solicitar(Long documentoId, Integer destinoId, String motivo, String actoAdministrativo, Usuario admin) {
        exigirAdministrador(admin);
        validarTexto(motivo, 10, "el motivo del traslado");
        validarTexto(actoAdministrativo, 3, "el acto administrativo que lo dispone");
        if (actoAdministrativo.trim().length() > 150) {
            throw new IllegalArgumentException("El acto administrativo admite hasta 150 caracteres.");
        }
        Documento doc = documentos.findByIdParaActualizar(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Expediente no encontrado: " + documentoId));
        Dependencia destino = dependencias.findById(destinoId)
                .orElseThrow(() -> new IllegalArgumentException("Dependencia no encontrada: " + destinoId));
        Dependencia origen = custodiaDe(doc);
        exigirTrasladable(doc, origen, destino);
        if (solicitudes.existePendiente(documentoId)) {
            throw new IllegalStateException("El expediente " + doc.getCodigoCuti() + " ya tiene un traslado esperando aprobación.");
        }
        SolicitudTrasladoCustodia solicitud = solicitudes.save(SolicitudTrasladoCustodia.builder()
                .documento(doc).dependenciaOrigen(origen).dependenciaDestino(destino)
                .motivo(motivo.trim()).actoAdministrativo(actoAdministrativo.trim()).solicitadaPor(admin).build());
        auditoriaService.registrarEvento(doc, "SOLICITUD_TRASLADO_CUSTODIA", doc.getEstadoActual(), doc.getEstadoActual(),
                "Traslado del original pedido, de " + origen.getNombreVisible() + " a " + destino.getNombreVisible()
                        + ". Acto: " + solicitud.getActoAdministrativo() + ". Motivo: " + solicitud.getMotivo(), admin, null);
        auditar(admin, "SOLICITUD_TRASLADO_CUSTODIA", solicitud.getId(), doc.getCodigoCuti() + ": " + origen.getNombreVisible()
                + " ➔ " + destino.getNombreVisible() + ". Acto: " + solicitud.getActoAdministrativo() + ". Motivo: " + solicitud.getMotivo());
        return solicitud;
    }

    // Toma el expediente antes de leer la solicitud: si dos administradores resuelven a la vez, el segundo la ve resuelta
    private SolicitudTrasladoCustodia solicitudParaResolver(Long solicitudId, Usuario admin) {
        exigirAdministrador(admin);
        Long documentoId = solicitudes.documentoIdDe(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada: " + solicitudId));
        documentos.findByIdParaActualizar(documentoId);
        SolicitudTrasladoCustodia solicitud = solicitudes.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada: " + solicitudId));
        if (!SolicitudTrasladoCustodia.PENDIENTE.equals(solicitud.getEstado())) {
            throw new IllegalStateException("Esa solicitud ya fue resuelta.");
        }
        return solicitud;
    }

    @Transactional
    public Documento aprobar(Long solicitudId, Usuario admin) {
        SolicitudTrasladoCustodia solicitud = solicitudParaResolver(solicitudId, admin);
        if (solicitud.getSolicitadaPor().getId().equals(admin.getId())) {
            throw new IllegalStateException("No puede aprobar un traslado que pidió usted: debe hacerlo otro administrador.");
        }
        Documento doc = solicitud.getDocumento();
        Dependencia origen = custodiaDe(doc);
        if (!origen.getId().equals(solicitud.getDependenciaOrigen().getId())) {
            throw new IllegalStateException("El original ya no está en " + solicitud.getDependenciaOrigen().getNombreVisible() + ". Rechace esta solicitud.");
        }
        Dependencia destino = solicitud.getDependenciaDestino();
        exigirTrasladable(doc, origen, destino);

        solicitud.setEstado(SolicitudTrasladoCustodia.APROBADA);
        solicitud.setResueltaPor(admin);
        solicitud.setFechaResolucion(LocalDateTime.now());
        solicitudes.save(solicitud);

        doc.setDependenciaCustodia(destino);
        documentos.save(doc);
        auditoriaService.registrarEvento(doc, "CAMBIO_CUSTODIA", doc.getEstadoActual(), doc.getEstadoActual(),
                "Traslado administrativo: el original pasa de " + origen.getNombreVisible() + " a " + destino.getNombreVisible()
                        + ". Lo pidió " + solicitud.getSolicitadaPor().getUsername() + ". Acto: " + solicitud.getActoAdministrativo()
                        + ". Motivo: " + solicitud.getMotivo(), admin, null);
        auditar(admin, "APROBACION_TRASLADO_CUSTODIA", solicitud.getId(), doc.getCodigoCuti() + ": " + origen.getNombreVisible()
                + " ➔ " + destino.getNombreVisible() + ". Lo pidió " + solicitud.getSolicitadaPor().getUsername() + ".");
        return doc;
    }

    @Transactional
    public void rechazar(Long solicitudId, String motivo, Usuario admin) {
        validarTexto(motivo, 5, "el motivo del rechazo");
        SolicitudTrasladoCustodia solicitud = solicitudParaResolver(solicitudId, admin);
        solicitud.setEstado(SolicitudTrasladoCustodia.RECHAZADA);
        solicitud.setResueltaPor(admin);
        solicitud.setFechaResolucion(LocalDateTime.now());
        solicitud.setMotivoRechazo(motivo.trim());
        solicitudes.save(solicitud);
        Documento doc = solicitud.getDocumento();
        auditoriaService.registrarEvento(doc, "TRASLADO_CUSTODIA_RECHAZADO", doc.getEstadoActual(), doc.getEstadoActual(),
                "Traslado del original no realizado. Motivo: " + motivo.trim(), admin, null);
        auditar(admin, "RECHAZO_TRASLADO_CUSTODIA", solicitud.getId(), doc.getCodigoCuti() + ". Motivo: " + motivo.trim());
    }

    @Transactional(readOnly = true)
    public List<SolicitudTrasladoCustodia> pendientes(Usuario admin) {
        exigirAdministrador(admin);
        return solicitudes.findPendientes();
    }

    // Panel de administración: los originales a cargo de una dependencia inactiva o sin nadie que actúe por ella
    @Transactional(readOnly = true)
    public AvisoCustodias custodiasSinReceptor(Usuario admin) {
        exigirAdministrador(admin);
        List<Integer> conCustodia = documentos.dependenciasConCustodia();
        if (conCustodia.isEmpty()) {
            return AvisoCustodias.NINGUNO;
        }
        Set<Integer> conReceptor = new HashSet<>(dependencias.findConReceptor(conCustodia));
        List<Integer> sinReceptor = conCustodia.stream().filter(id -> !conReceptor.contains(id)).toList();
        if (sinReceptor.isEmpty()) {
            return AvisoCustodias.NINGUNO;
        }
        List<Documento> docs = documentos.enCustodiaDe(sinReceptor, PageRequest.of(0, EXPEDIENTES_EN_PANEL));
        Set<Long> enCamino = docs.isEmpty() ? Set.of()
                : new HashSet<>(actuaciones.conOriginalEnCamino(docs.stream().map(Documento::getId).toList()));
        Set<Long> pedidos = solicitudes.findPendientes().stream().map(s -> s.getDocumento().getId()).collect(Collectors.toSet());
        List<CustodiaSinReceptor> filas = docs.stream().map(d -> {
            Dependencia custodia = custodiaDe(d);
            return new CustodiaSinReceptor(d.getId(), d.getCodigoCuti(), d.getEstadoActual(), d.getFechaHoraIngreso(),
                    custodia.getNombreVisible(), Boolean.FALSE.equals(custodia.getActivo()), enCamino.contains(d.getId()), pedidos.contains(d.getId()));
        }).toList();
        return new AvisoCustodias(documentos.contarEnCustodiaDe(sinReceptor), filas);
    }

    private static void validarTexto(String texto, int minimo, String nombre) {
        if (texto == null || texto.trim().length() < minimo) {
            throw new IllegalArgumentException("Debe ingresar " + nombre + " (mínimo " + minimo + " caracteres).");
        }
    }

    private void auditar(Usuario admin, String accion, Long entidadId, String detalle) {
        auditoriaSistema.marcarIp(AuditoriaService.ipDeLaPeticion());
        auditoriaSistema.save(AuditoriaSistema.builder()
                .usuario(admin).accion(accion).entidad("TRASLADO_CUSTODIA").entidadId(entidadId).detalle(detalle).build());
    }
}
