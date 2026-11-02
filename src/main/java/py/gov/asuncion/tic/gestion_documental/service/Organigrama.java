package py.gov.asuncion.tic.gestion_documental.service;

import java.util.*;

// 🏛️ Árbol del organigrama en memoria y reglas del canal jerárquico (docs/diseno-fase2-multiarea.md §4.3).
// Sin acceso a la base: CanalService lo arma con los datos y las pruebas, con un árbol de ejemplo.
//
// El expediente sube por la jerarquía hasta la cabecera, pasa solo entre cabeceras hermanas y baja.
// Cabecera = Dirección o nivel superior (Dirección General, Intendencia). Toda cabecera recibe en su
// mesa de entrada (Unidad de Secretaría Administrativa); la mesa actúa en nombre de su cabecera.
public final class Organigrama {

    // Orden de nivel de "Dirección": de ahí para arriba, la dependencia es cabecera
    public static final int ORDEN_DIRECCION = 4;

    public record Nodo(Integer id, String nombre, String sigla, Integer padreId, int ordenNivel,
                       boolean activo, boolean mesaEntrada) {}

    public enum Movimiento { SUBIR, BAJAR, CABECERA, ATAJO }

    public record Destino(Nodo nodo, Movimiento movimiento) {}

    private final Map<Integer, Nodo> porId = new HashMap<>();
    private final Map<Integer, List<Nodo>> hijos = new HashMap<>();
    private final Map<Integer, Set<Integer>> atajos;

    // atajos: origen -> destinos habilitados fuera del canal (tabla reglas_derivacion)
    public Organigrama(Collection<Nodo> nodos, Map<Integer, Set<Integer>> atajos) {
        nodos.forEach(n -> porId.put(n.id(), n));
        nodos.stream()
                .filter(n -> n.padreId() != null)
                .sorted(Comparator.comparing(Nodo::nombre))
                .forEach(n -> hijos.computeIfAbsent(n.padreId(), k -> new ArrayList<>()).add(n));
        this.atajos = atajos;
    }

    public Nodo nodo(Integer id) {
        Nodo n = porId.get(id);
        if (n == null) {
            throw new IllegalArgumentException("Dependencia inexistente en el organigrama: " + id);
        }
        return n;
    }

    public Optional<Nodo> padre(Nodo n) {
        return n.padreId() == null ? Optional.empty() : Optional.ofNullable(porId.get(n.padreId()));
    }

    public List<Nodo> hijos(Nodo n) {
        return hijos.getOrDefault(n.id(), List.of());
    }

    public boolean esCabecera(Nodo n) {
        return n.ordenNivel() <= ORDEN_DIRECCION;
    }

    // La cabecera de una dependencia: ella misma o su primer superior de nivel Dirección o mayor
    public Nodo cabecera(Nodo n) {
        Nodo actual = n;
        while (!esCabecera(actual)) {
            Optional<Nodo> p = padre(actual);
            if (p.isEmpty()) {
                return actual;
            }
            actual = p.get();
        }
        return actual;
    }

    // Mesa de entrada de una cabecera: la Unidad de Secretaría Administrativa, o la primera mesa activa
    public Optional<Nodo> mesa(Nodo cabecera) {
        return hijos(cabecera).stream()
                .filter(h -> h.mesaEntrada() && h.activo())
                .min(Comparator.comparing((Nodo h) -> !h.nombre().contains("Secretaría")).thenComparing(Nodo::id));
    }

    // Dónde se recibe un pase dirigido a la dependencia: si es cabecera, en su mesa
    public Nodo puntoDeRecepcion(Nodo n) {
        return esCabecera(n) ? mesa(n).orElse(n) : n;
    }

    // La mesa de una cabecera actúa en nombre de ella
    public Nodo nodoEfectivo(Nodo n) {
        Optional<Nodo> p = padre(n);
        if (n.mesaEntrada() && p.isPresent() && esCabecera(p.get()) && mesa(p.get()).map(m -> m.id().equals(n.id())).orElse(false)) {
            return p.get();
        }
        return n;
    }

    // Destinos a los que puede pasar un expediente que está en custodia de la dependencia
    public List<Destino> destinos(Integer custodiaId) {
        Nodo custodia = nodo(custodiaId);
        Nodo efectivo = nodoEfectivo(custodia);
        Map<Integer, Destino> resultado = new LinkedHashMap<>();
        java.util.function.BiConsumer<Nodo, Movimiento> agregar = (destino, movimiento) -> {
            Nodo recibe = puntoDeRecepcion(destino);
            if (recibe.activo() && !recibe.id().equals(custodia.id()) && !recibe.id().equals(efectivo.id())) {
                resultado.putIfAbsent(recibe.id(), new Destino(recibe, movimiento));
            }
        };

        Integer mesaPropia = esCabecera(efectivo) ? mesa(efectivo).map(Nodo::id).orElse(null) : null;
        for (Nodo h : hijos(efectivo)) {
            if (!h.id().equals(mesaPropia)) {
                agregar.accept(h, Movimiento.BAJAR);
            }
        }
        Optional<Nodo> superior = padre(efectivo);
        superior.ifPresent(p -> agregar.accept(p, Movimiento.SUBIR));
        if (esCabecera(efectivo) && superior.isPresent()) {
            for (Nodo hermana : hijos(superior.get())) {
                if (esCabecera(hermana) && !hermana.id().equals(efectivo.id())) {
                    agregar.accept(hermana, Movimiento.CABECERA);
                }
            }
        }
        for (Integer origen : new LinkedHashSet<>(List.of(custodia.id(), efectivo.id()))) {
            for (Integer destino : atajos.getOrDefault(origen, Set.of())) {
                Nodo d = porId.get(destino);
                if (d != null) {
                    agregar.accept(d, Movimiento.ATAJO);
                }
            }
        }
        return new ArrayList<>(resultado.values());
    }

    public boolean puedePasar(Integer custodiaId, Integer destinoId) {
        return destinos(custodiaId).stream().anyMatch(d -> d.nodo().id().equals(destinoId));
    }

    // Recorrido sugerido hacia un destino lejano: cada paso es un pase permitido por el canal.
    // Sube hasta la cabecera hermana del destino (o el ancestro común), pasa y baja.
    public List<Nodo> recorrido(Integer origenId, Integer destinoId) {
        Nodo origen = nodoEfectivo(nodo(origenId));
        Nodo destino = nodoEfectivo(nodo(destinoId));
        List<Nodo> subida = ancestros(origen);   // origen, padre, ..., raíz
        List<Nodo> bajada = ancestros(destino);  // destino, padre, ..., raíz
        Set<Integer> idsBajada = new HashSet<>();
        bajada.forEach(n -> idsBajada.add(n.id()));

        int iComun = 0;
        while (!idsBajada.contains(subida.get(iComun).id())) {
            iComun++;
        }
        Nodo comun = subida.get(iComun);
        int jComun = 0;
        while (!bajada.get(jComun).id().equals(comun.id())) {
            jComun++;
        }

        List<Nodo> pasos = new ArrayList<>();
        // Hijos del ancestro común de cada lado: si ambos son cabeceras, se pasa directo entre ellas
        Nodo ladoOrigen = iComun > 0 ? subida.get(iComun - 1) : null;
        Nodo ladoDestino = jComun > 0 ? bajada.get(jComun - 1) : null;
        boolean entreHermanas = ladoOrigen != null && ladoDestino != null && esCabecera(ladoOrigen) && esCabecera(ladoDestino);

        int tope = entreHermanas ? iComun - 1 : iComun;
        for (int i = 1; i <= tope; i++) {
            pasos.add(subida.get(i));
        }
        for (int j = jComun - 1; j >= 0; j--) {
            pasos.add(bajada.get(j));
        }
        return pasos.stream().map(this::puntoDeRecepcion).filter(n -> !n.id().equals(origen.id())).distinct().toList();
    }

    private List<Nodo> ancestros(Nodo n) {
        List<Nodo> lista = new ArrayList<>();
        for (Nodo actual = n; actual != null; actual = padre(actual).orElse(null)) {
            lista.add(actual);
        }
        return lista;
    }
}
