package py.gov.asuncion.tic.gestion_documental.service;

import py.gov.asuncion.tic.gestion_documental.domain.ActuacionDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.Dependencia;
import py.gov.asuncion.tic.gestion_documental.domain.Documento;
import py.gov.asuncion.tic.gestion_documental.domain.EstadoDocumento;
import py.gov.asuncion.tic.gestion_documental.domain.TipoPase;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

// 🧭 Qué puede hacer el usuario con un expediente y cuál es su próximo paso, para mostrar en cada fila una acción
// principal y solo las acciones que el servidor va a aceptar. Sigue las mismas reglas que DocumentoService; este
// cálculo decide qué se muestra, no qué se permite: cada operación se valida igual en el servidor.
public record SituacionExpediente(Tipo tipo, String detalle, Long paseRecibir, Long paseRetirar, boolean puedeRechazar,
                                  boolean puedeAgregar, boolean puedeDerivar, boolean puedeDevolver,
                                  boolean puedeSalida, boolean puedeAcuse, boolean puedeCierre,
                                  boolean puedeCorregirSalida) {

    public enum Tipo {
        RECIBIR,        // le enviaron un pase: confirmar la recepción
        EN_CAMINO,      // lo envió y espera el acuse
        RESPONDER,      // le pidieron un informe
        LISTO_SALIDA,   // lo tiene, y falta registrar la salida
        ESPERA_ACUSE,   // salida registrada, falta el acuse final
        EN_PODER,       // lo tiene y puede seguir el trámite
        OTRA            // está en otra dependencia: solo consulta
    }

    // Permisos del usuario por acción (PoliticaAcceso), calculados una vez para toda la página
    public record Permisos(boolean actuacion, boolean derivar, boolean salida, boolean cierre) {}

    // Usado por la plantilla: Thymeleaf compara mejor el nombre que el enum
    public String getTipoNombre() {
        return tipo.name();
    }

    public boolean isConMenu() {
        return (puedeAgregar && tipo != Tipo.RESPONDER && tipo != Tipo.RECIBIR) || puedeDevolver || (puedeDerivar && tipo != Tipo.EN_PODER)
                || (puedeSalida && tipo != Tipo.LISTO_SALIDA) || (puedeAcuse && tipo != Tipo.ESPERA_ACUSE)
                || (paseRetirar != null && tipo != Tipo.EN_CAMINO) || puedeCierre || puedeCorregirSalida;
    }

    public static SituacionExpediente de(Documento doc, Dependencia custodia, List<ActuacionDocumento> actuaciones,
                                         Predicate<Dependencia> actuaPor, Dependencia propia, Permisos permisos) {
        EstadoDocumento estado = doc.getEstadoActual();
        boolean abierto = estado != EstadoDocumento.FINALIZADO && estado != EstadoDocumento.ARCHIVADO;
        boolean tieneOriginal = custodia != null && actuaPor.test(custodia);

        List<ActuacionDocumento> pendientes = actuaciones.stream().filter(SituacionExpediente::esPasePendiente).toList();
        ActuacionDocumento tramiteEnCamino = pendientes.stream().filter(p -> p.getTipoPase() == TipoPase.TRAMITE).findFirst().orElse(null);
        boolean enCamino = tramiteEnCamino != null;

        // El trámite primero: es el que entrega el original
        ActuacionDocumento recibir = pendientes.stream()
                .filter(p -> p.getDependenciaReceptoraPase() != null && actuaPor.test(p.getDependenciaReceptoraPase()))
                .min(Comparator.comparing((ActuacionDocumento p) -> p.getTipoPase() == TipoPase.TRAMITE ? 0 : 1))
                .orElse(null);
        ActuacionDocumento retirar = pendientes.stream()
                .filter(p -> p.getDependenciaEmisora() != null && actuaPor.test(p.getDependenciaEmisora()))
                .min(Comparator.comparing((ActuacionDocumento p) -> p.getTipoPase() == TipoPase.TRAMITE ? 0 : 1))
                .orElse(null);
        ActuacionDocumento pedido = DocumentoService.pedidosQuePuedeResponder(actuaciones, propia, actuaPor).stream().findFirst().orElse(null);
        boolean hayPedidosAbiertos = !DocumentoService.pedidosAbiertos(actuaciones).isEmpty();

        boolean originalDisponible = abierto && tieneOriginal && !enCamino;
        boolean salidaRegistrada = doc.getFechaHoraSalida() != null;

        // Con la salida registrada el original ya salió: solo se responden pedidos, se confirma el acuse o se corrige la salida
        boolean puedeAgregar = permisos.actuacion() && abierto && (pedido != null || (originalDisponible && !salidaRegistrada));
        boolean puedeDerivar = (permisos.derivar() || permisos.actuacion()) && originalDisponible && !salidaRegistrada;
        boolean puedeCorregirSalida = permisos.salida() && originalDisponible && salidaRegistrada;
        // Devuelve quien tiene el original. Lo que todavía no se recibió se confirma o se rechaza
        boolean puedeDevolver = permisos.actuacion() && originalDisponible && estado != EstadoDocumento.PENDIENTE_SALIDA
                && recibir == null && !salidaRegistrada && hayAQuienDevolver(actuaciones, custodia);
        // Con informes sin responder, no hay salida: se esperan o se justifica con el cierre excepcional
        boolean puedeSalida = permisos.salida() && originalDisponible && !salidaRegistrada && !hayPedidosAbiertos;
        boolean puedeAcuse = permisos.salida() && originalDisponible && estado == EstadoDocumento.PENDIENTE_SALIDA && salidaRegistrada;
        boolean puedeCierre = permisos.cierre() && originalDisponible && !salidaRegistrada
                && (estado == EstadoDocumento.EN_ESPERA_INFORME || hayPedidosAbiertos);
        boolean puedeRechazar = recibir != null && propia != null;

        Tipo tipo;
        String detalle;
        if (recibir != null && abierto) {
            tipo = Tipo.RECIBIR;
            detalle = "Se lo envió " + nombre(recibir.getDependenciaEmisora());
        } else if (tieneOriginal && enCamino) {
            tipo = Tipo.EN_CAMINO;
            detalle = "En camino a " + nombre(tramiteEnCamino.getDependenciaReceptoraPase()) + ": falta el acuse";
        } else if (pedido != null && !tieneOriginal && abierto) {
            tipo = Tipo.RESPONDER;
            detalle = "Le pidieron un informe: " + nombre(pedido.getDependenciaEmisora());
        } else if (puedeAcuse) {
            tipo = Tipo.ESPERA_ACUSE;
            detalle = "Salida registrada: falta el acuse";
        } else if (salidaRegistrada && originalDisponible) {
            // Expedientes derivados después de la salida, antes de que eso se bloqueara
            tipo = Tipo.OTRA;
            detalle = "Tiene una salida registrada: corríjala para continuar";
        } else if (puedeSalida && estado == EstadoDocumento.PENDIENTE_SALIDA) {
            tipo = Tipo.LISTO_SALIDA;
            detalle = "Listo para registrar la salida";
        } else if (puedeDerivar) {
            tipo = Tipo.EN_PODER;
            detalle = estado == EstadoDocumento.EN_ESPERA_INFORME ? "En su poder: esperando informes" : "En su poder";
        } else {
            tipo = Tipo.OTRA;
            detalle = enCamino
                    ? "En camino a " + nombre(tramiteEnCamino.getDependenciaReceptoraPase())
                    : "Está en " + nombre(custodia);
        }
        return new SituacionExpediente(tipo, detalle, recibir != null ? recibir.getId() : null,
                retirar != null ? retirar.getId() : null, puedeRechazar, puedeAgregar, puedeDerivar, puedeDevolver,
                puedeSalida, puedeAcuse, puedeCierre, puedeCorregirSalida);
    }

    private static boolean esPasePendiente(ActuacionDocumento a) {
        return "PASE_INTERNO".equals(a.getTipoActuacion()) && !Boolean.TRUE.equals(a.getPaseConfirmado())
                && !Boolean.TRUE.equals(a.getAnulada());
    }

    // Las condiciones de DocumentoService para devolver: un último pase recibido, de otra dependencia, y no devuelto
    private static boolean hayAQuienDevolver(List<ActuacionDocumento> actuaciones, Dependencia dependencia) {
        if (dependencia == null) {
            return false;
        }
        ActuacionDocumento ultimoPase = actuaciones.stream()
                .filter(a -> !Boolean.TRUE.equals(a.getAnulada()) && a.getDependenciaReceptoraPase() != null
                        && dependencia.getId().equals(a.getDependenciaReceptoraPase().getId()))
                .max(Comparator.comparing(ActuacionDocumento::getFechaHoraActuacion))
                .orElse(null);
        if (ultimoPase == null || ultimoPase.getDependenciaEmisora() == null
                || dependencia.getId().equals(ultimoPase.getDependenciaEmisora().getId())) {
            return false;
        }
        LocalDateTime recibido = ultimoPase.getFechaHoraActuacion();
        return actuaciones.stream().noneMatch(a -> "DEVOLUCION".equals(a.getTipoActuacion())
                && a.getDependenciaEmisora() != null && dependencia.getId().equals(a.getDependenciaEmisora().getId())
                && !a.getFechaHoraActuacion().isBefore(recibido));
    }

    private static String nombre(Dependencia d) {
        return d != null ? d.getNombreVisible() : "otra dependencia";
    }
}
