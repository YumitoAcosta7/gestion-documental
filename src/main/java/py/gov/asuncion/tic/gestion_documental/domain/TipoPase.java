package py.gov.asuncion.tic.gestion_documental.domain;

// docs/diseno-fase2-multiarea.md §4.1
public enum TipoPase {
    TRAMITE,       // el expediente cambia de manos: la custodia pasa al receptor cuando confirma
    INFORME,       // la custodia queda en el emisor y se espera un informe o memo
    CONOCIMIENTO   // copia para conocimiento: no mueve la custodia ni espera respuesta
}
