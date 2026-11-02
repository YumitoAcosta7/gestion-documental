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
        TODOS("Todos", null),
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

    // Cantidad de expedientes de cada bandeja, para los contadores del selector.
    // Las claves son el nombre de la bandeja: Thymeleaf no indexa mapas con claves enum.
    public Map<String, Long> contadores(Usuario usuario) {
        Map<String, Long> contadores = new LinkedHashMap<>();
        for (Bandeja b : Bandeja.values()) {
            if (b != Bandeja.TODOS) {
                contadores.put(b.name(), contar(usuario, b));
            }
        }
        return contadores;
    }
}
