package py.gov.asuncion.tic.gestion_documental.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.gov.asuncion.tic.gestion_documental.domain.Documento;
import py.gov.asuncion.tic.gestion_documental.domain.Usuario;
import py.gov.asuncion.tic.gestion_documental.repository.DocumentoRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 📥 Bandejas de trabajo por ámbito (docs/diseno-fase2-multiarea.md §4.2). Cada bandeja es una condición
// sobre las dependencias del ámbito del usuario y se resuelve con una consulta paginada.
@Service
@Transactional(readOnly = true)
public class BandejaService {

    public static final int TAMANIO_PAGINA = 20;

    public enum Bandeja {
        // Todo lo activo que el usuario puede ver (paginado como el resto: auditoría B2)
        TODOS("Todos", "d.estado_actual NOT IN ('FINALIZADO', 'ARCHIVADO') AND " + DocumentoRepository.DOCUMENTO_ACCESIBLE),
        POR_RECIBIR("Por recibir", """
            EXISTS (SELECT 1 FROM actuaciones_documento a
                     WHERE a.documento_id = d.id AND a.tipo_actuacion = 'PASE_INTERNO'
                       AND NOT a.pase_confirmado AND NOT a.anulada
                       AND a.dependencia_receptora_pase_id IN (SELECT id FROM deps))
            """),
        EN_MI_PODER("En mi poder", """
            d.estado_actual NOT IN ('FINALIZADO', 'ARCHIVADO')
            AND d.dependencia_custodia_id IN (SELECT id FROM deps)
            """),
        INFORMES_PENDIENTES("Informes pendientes", """
            d.estado_actual = 'EN_ESPERA_INFORME'
            AND EXISTS (SELECT 1 FROM actuaciones_documento a
                         WHERE a.documento_id = d.id AND a.tipo_pase = 'INFORME' AND NOT a.anulada
                           AND a.dependencia_receptora_pase_id IN (SELECT id FROM deps)
                           AND NOT EXISTS (SELECT 1 FROM actuaciones_documento r
                                            WHERE r.documento_id = d.id AND NOT r.anulada
                                              AND r.tipo_actuacion IN ('INFORME_TECNICO', 'MEMO')
                                              AND r.dependencia_emisora_id = a.dependencia_receptora_pase_id))
            """),
        EN_SEGUIMIENTO("En seguimiento", """
            d.estado_actual NOT IN ('FINALIZADO', 'ARCHIVADO')
            AND (d.dependencia_custodia_id IS NULL OR d.dependencia_custodia_id NOT IN (SELECT id FROM deps))
            AND (d.dependencia_registro_id IN (SELECT id FROM deps)
                 OR EXISTS (SELECT 1 FROM actuaciones_documento a
                             WHERE a.documento_id = d.id AND a.dependencia_emisora_id IN (SELECT id FROM deps)))
            """);

        private final String etiqueta;
        private final String condicion;

        Bandeja(String etiqueta, String condicion) {
            this.etiqueta = etiqueta;
            this.condicion = condicion;
        }

        public String getEtiqueta() {
            return etiqueta;
        }
    }

    @PersistenceContext
    private EntityManager entityManager;

    public Page<Documento> listar(Usuario usuario, Bandeja bandeja, int pagina) {
        int paginaValida = Math.max(pagina, 0);
        @SuppressWarnings("unchecked")
        List<Documento> contenido = entityManager
                .createNativeQuery(DocumentoRepository.CTE_AMBITO + "SELECT d.* FROM documentos d WHERE "
                        + bandeja.condicion + " ORDER BY d.fecha_hora_ingreso DESC, d.id DESC", Documento.class)
                .setParameter("usuarioId", usuario.getId())
                .setFirstResult(paginaValida * TAMANIO_PAGINA)
                .setMaxResults(TAMANIO_PAGINA)
                .getResultList();
        return new PageImpl<>(contenido, PageRequest.of(paginaValida, TAMANIO_PAGINA), contar(usuario, bandeja));
    }

    public long contar(Usuario usuario, Bandeja bandeja) {
        Number total = (Number) entityManager
                .createNativeQuery(DocumentoRepository.CTE_AMBITO + "SELECT COUNT(*) FROM documentos d WHERE " + bandeja.condicion)
                .setParameter("usuarioId", usuario.getId())
                .getSingleResult();
        return total.longValue();
    }

    // Contadores de las bandejas e indicadores del panel, del ámbito del usuario
    public record Resumen(Map<String, Long> bandejas, Map<String, Long> indicadores) {}

    // Todo en una sola consulta con COUNT(*) FILTER (auditoría B2). Todas las bandejas son de expedientes
    // activos (al cerrar un expediente se anulan sus pases pendientes), así que se recorre solo esos.
    // Las claves son nombres: Thymeleaf no indexa mapas con claves enum.
    public Resumen resumen(Usuario usuario) {
        StringBuilder sql = new StringBuilder(DocumentoRepository.CTE_AMBITO).append("SELECT ");
        List<Bandeja> conContador = java.util.Arrays.stream(Bandeja.values()).filter(b -> b != Bandeja.TODOS).toList();
        for (Bandeja b : conContador) {
            sql.append("COUNT(*) FILTER (WHERE ").append(b.condicion).append("), ");
        }
        // Indicadores del panel: los expedientes activos que el usuario puede ver, por estado
        sql.append("""
                COUNT(*) FILTER (WHERE d.accesible AND d.estado_actual IN ('INGRESADO', 'EN_REVISION')),
                COUNT(*) FILTER (WHERE d.accesible AND d.estado_actual = 'EN_ESPERA_INFORME'),
                COUNT(*) FILTER (WHERE d.accesible AND d.estado_actual = 'PENDIENTE_SALIDA'),
                COUNT(*) FILTER (WHERE d.accesible AND d.prioridad = 'URGENTE')
                  FROM (SELECT d.*,
                """).append(DocumentoRepository.DOCUMENTO_ACCESIBLE).append("""
                        AS accesible
                          FROM documentos d
                         WHERE d.estado_actual NOT IN ('FINALIZADO', 'ARCHIVADO')) d
                """);
        Object[] fila = (Object[]) entityManager.createNativeQuery(sql.toString())
                .setParameter("usuarioId", usuario.getId())
                .getSingleResult();

        Map<String, Long> bandejas = new LinkedHashMap<>();
        for (int i = 0; i < conContador.size(); i++) {
            bandejas.put(conContador.get(i).name(), ((Number) fila[i]).longValue());
        }
        int base = conContador.size();
        Map<String, Long> indicadores = new LinkedHashMap<>();
        indicadores.put("ingresados", ((Number) fila[base]).longValue());
        indicadores.put("enEsperaInforme", ((Number) fila[base + 1]).longValue());
        indicadores.put("pendienteSalida", ((Number) fila[base + 2]).longValue());
        indicadores.put("urgentes", ((Number) fila[base + 3]).longValue());
        return new Resumen(bandejas, indicadores);
    }
}
