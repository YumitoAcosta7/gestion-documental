        // Escapa texto antes de insertarlo con innerHTML (evita inyección de HTML/JS)
        function esc(valor) {
            if (valor === null || valor === undefined) return "";
            return String(valor)
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;")
                .replace(/'/g, "&#39;");
        }

        // Trámite va a un solo destino; informe y conocimiento, a uno o varios
        function alternarTipoPase(tipo) {
            const unico = document.getElementById('contenedorUnico');
            const multiple = document.getElementById('contenedorMultiple');
            const selectUnico = document.getElementById('selectDestinoUnico');
            const checks = multiple.querySelectorAll('.check-dependencia');
            const esMultiple = tipo !== 'TRAMITE';

            unico.classList.toggle('d-none', esMultiple);
            multiple.classList.toggle('d-none', !esMultiple);

            // Los campos deshabilitados no se envían: así solo viaja la selección del tipo elegido
            selectUnico.disabled = esMultiple;
            selectUnico.required = !esMultiple;
            // Las dependencias sin usuarios en el sistema quedan siempre deshabilitadas
            checks.forEach(c => c.disabled = !esMultiple || c.dataset.sinReceptor === "true");
            actualizarContadorDestinos();
        }

        // Lo que se cargó para el expediente abierto en el modal de derivación
        let derivacion = { destinos: [], soloElevar: false, difusion: false };

        const AYUDA_ALCANCE = {
            INTERNO: 'El pase baja al nivel inmediato inferior. Para llegar más abajo, sigue de nivel en nivel.',
            EXTERNO: 'El pase llega a la mesa de entrada de cada una. A una dirección que depende de una Dirección General se llega por ella.',
            ELEVAR: 'El pase sube a su superior inmediato. Para comunicarse con otras direcciones, elévelo primero a su Dirección General.'
        };
        const GRUPOS_DESTINO = [['DIRECCION_GENERAL', 'Direcciones Generales'], ['DIRECCION', 'Direcciones'], ['AREA', 'Áreas'], ['INTENDENCIA', 'Intendencia']];

        // Quien envía no confirma su pase: si nadie más puede recibirlo, ese destino tampoco se ofrece
        const destinoHabilitado = d => d.conReceptor && !d.soloUsted;
        const marcaDestino = d => !d.conReceptor ? ' — sin usuarios en el sistema' : (d.soloUsted ? ' — solo usted podría recibirlo' : '');
        const nombreDestino = d => `${esc(d.nombre)}${d.sigla ? ' (' + esc(d.sigla) + ')' : ''}`;

        // Solo los tipos de pase que admite el camino elegido
        function ajustarTiposPase(alcance) {
            const selTipo = document.getElementById('selectTipoPase');
            const opcion = t => selTipo.querySelector(`option[value="${t}"]`);
            opcion('TRAMITE').disabled = derivacion.difusion;
            opcion('INFORME').disabled = derivacion.difusion || derivacion.soloElevar || alcance === 'ELEVAR';
            opcion('CONOCIMIENTO').disabled = derivacion.soloElevar;
            if (selTipo.selectedOptions[0] && selTipo.selectedOptions[0].disabled) {
                selTipo.value = [...selTipo.options].find(o => !o.disabled).value;
            }
        }

        function actualizarContadorDestinos() {
            const contador = document.getElementById('contadorDestinos');
            const marcadas = document.querySelectorAll('#listaDestinosMultiple .check-dependencia:checked').length;
            contador.textContent = marcadas > 0 ? `${marcadas} seleccionada${marcadas === 1 ? '' : 's'}` : '';
            const boton = document.getElementById('marcarDirecciones');
            const direcciones = [...document.querySelectorAll('#listaDestinosMultiple .check-dependencia[data-clase="DIRECCION"]:not([data-sin-receptor="true"])')];
            boton.textContent = direcciones.length > 0 && direcciones.every(c => c.checked) ? 'Desmarcar mis direcciones' : 'Marcar todas mis direcciones';
        }

        // Pinta los destinos de un solo camino, agrupados por nivel
        function pintarDestinosDerivacion(alcance) {
            const delCamino = derivacion.destinos.filter(d => d.alcance === alcance);
            const grupos = GRUPOS_DESTINO.map(([clase, titulo]) => ({ clase, titulo, destinos: delCamino.filter(d => d.clase === clase) }))
                .filter(g => g.destinos.length > 0);
            const conTitulos = grupos.length > 1;

            const opcion = d => `<option value="${d.id}" data-buscar="${esc((d.nombre + ' ' + (d.sigla || '')).toLowerCase())}" ${destinoHabilitado(d) ? '' : 'disabled'}>${nombreDestino(d)}${marcaDestino(d)}</option>`;
            document.getElementById('selectDestinoUnico').innerHTML = '<option value="" selected disabled>Seleccione dependencia...</option>'
                + grupos.map(g => conTitulos ? `<optgroup label="${esc(g.titulo)}">${g.destinos.map(opcion).join('')}</optgroup>` : g.destinos.map(opcion).join('')).join('');

            const fila = d => `
                    <div class="form-check" data-buscar="${esc((d.nombre + ' ' + (d.sigla || '')).toLowerCase())}">
                        <input class="form-check-input check-dependencia" type="checkbox" name="dependenciaDestinoId" value="${d.id}" id="check_${d.id}" data-clase="${esc(d.clase)}" data-sin-receptor="${!destinoHabilitado(d)}" ${destinoHabilitado(d) ? '' : 'disabled'}>
                        <label class="form-check-label small ${destinoHabilitado(d) ? '' : 'text-muted'}" for="check_${d.id}">${nombreDestino(d)}<span class="text-muted">${marcaDestino(d)}</span></label>
                    </div>`;
            document.getElementById('listaDestinosMultiple').innerHTML = grupos
                .map(g => (conTitulos ? `<div class="small fw-bold text-muted mt-1" data-titulo-grupo="true">${esc(g.titulo)}</div>` : '') + g.destinos.map(fila).join(''))
                .join('');

            // Una Dirección General puede pedir a todas sus direcciones a la vez
            const conDirecciones = alcance === 'INTERNO' && delCamino.filter(d => d.clase === 'DIRECCION' && destinoHabilitado(d)).length > 1;
            document.getElementById('marcarDirecciones').classList.toggle('d-none', !conDirecciones);

            const buscar = document.getElementById('buscarDestino');
            buscar.value = '';
            document.getElementById('contenedorBuscarDestino').classList.toggle('d-none', delCamino.length <= 8);
            document.getElementById('ayudaAlcance').textContent = AYUDA_ALCANCE[alcance] || '';

            ajustarTiposPase(alcance);
            alternarTipoPase(document.getElementById('selectTipoPase').value);
        }

        // El buscador oculta lo que no coincide: lo ya marcado sigue marcado, y el contador lo muestra
        function filtrarDestinosDerivacion(texto) {
            const buscado = texto.trim().toLowerCase();
            const coincide = el => !buscado || el.dataset.buscar.includes(buscado);
            document.querySelectorAll('#selectDestinoUnico option[data-buscar]').forEach(o => { o.hidden = !coincide(o); });
            document.querySelectorAll('#listaDestinosMultiple .form-check').forEach(f => f.classList.toggle('d-none', !coincide(f)));
            document.querySelectorAll('#listaDestinosMultiple [data-titulo-grupo]').forEach(titulo => {
                titulo.classList.toggle('d-none', Boolean(buscado));
            });
        }

        // Carga los destinos que permite el canal desde quien tiene el original del expediente
        async function cargarDestinosDerivacion(docId) {
            const desde = document.getElementById('derivarDesde');
            const aviso = document.getElementById('derivarSinPermiso');
            const contenedorAlcance = document.getElementById('contenedorAlcance');
            const enviar = document.querySelector('#formDerivar [type=submit]');
            desde.textContent = 'Cargando destinos...';
            aviso.classList.add('d-none');
            contenedorAlcance.classList.add('d-none');
            document.getElementById('contenedorBuscarDestino').classList.add('d-none');
            document.getElementById('marcarDirecciones').classList.add('d-none');
            document.getElementById('selectDestinoUnico').innerHTML = '';
            document.getElementById('listaDestinosMultiple').innerHTML = '';
            enviar.disabled = true;
            try {
                const res = await fetch(`/api/documentos/${docId}/destinos`);
                if (!res.ok) throw new Error(res.status);
                const datos = await res.json();
                derivacion.destinos = datos.destinos;
                derivacion.soloElevar = datos.soloElevar;
                desde.innerHTML = `El original está en <strong>${esc(datos.custodia)}</strong>.`;
                // Sin permiso de derivar (operador de una unidad): solo puede elevar el mismo expediente a su superior
                if (datos.soloElevar) {
                    document.getElementById('selectTipoPase').value = 'TRAMITE';
                    desde.innerHTML += ' Usted puede <strong>elevarlo a su dependencia superior</strong> con un pase de trámite: el expediente conserva su CUTI.';
                }
                ajustarTiposPase(null);
                if (!datos.puedeDerivar || datos.destinos.length === 0) {
                    aviso.textContent = datos.puedeDerivar
                        ? 'No hay destinos habilitados desde esta dependencia.'
                        : `Solo ${datos.custodia} (o quien actúe por ella) puede derivar este expediente.`;
                    aviso.classList.remove('d-none');
                    return;
                }
                // Solo se ofrecen los caminos que tienen destinos; empieza por el propio
                const radios = [...document.querySelectorAll('input[name="alcanceEnvio"]')];
                radios.forEach(r => {
                    const hay = datos.destinos.some(d => d.alcance === r.value);
                    r.disabled = !hay;
                    r.nextElementSibling.classList.toggle('d-none', !hay);
                    r.checked = false;
                });
                const primero = radios.find(r => !r.disabled);
                primero.checked = true;
                contenedorAlcance.classList.remove('d-none');
                pintarDestinosDerivacion(primero.value);
                enviar.disabled = false;
            } catch (e) {
                desde.textContent = '';
                aviso.textContent = 'No se pudieron cargar los destinos del expediente.';
                aviso.classList.remove('d-none');
            }
        }

        // Reparto: baja una copia o un pedido recibido al nivel inmediato inferior, sin mover el original
        function actualizarContadorReparto() {
            const marcadas = document.querySelectorAll('#listaReparto .check-reparto:checked').length;
            document.getElementById('contadorReparto').textContent = marcadas > 0 ? `${marcadas} seleccionada${marcadas === 1 ? '' : 's'}` : '';
            const disponibles = [...document.querySelectorAll('#listaReparto .check-reparto:not(:disabled)')];
            document.getElementById('marcarReparto').textContent = disponibles.length > 0 && disponibles.every(c => c.checked) ? 'Desmarcar todas' : 'Marcar todas';
        }

        function ajustarAyudaReparto(tipo) {
            document.getElementById('ayudaReparto').textContent = tipo === 'INFORME'
                ? 'Su dependencia sigue debiendo la respuesta. Si responde antes que sus áreas, sus pedidos dejan de esperarse.'
                : 'Sus áreas reciben una copia, y no se espera respuesta.';
        }

        async function cargarReparto(paseId) {
            const desde = document.getElementById('repartirDesde');
            const aviso = document.getElementById('repartirAviso');
            const lista = document.getElementById('listaReparto');
            const marcar = document.getElementById('marcarReparto');
            const enviar = document.querySelector('#formRepartir [type=submit]');
            const tipo = document.getElementById('selectTipoReparto');
            desde.textContent = 'Cargando áreas...';
            aviso.classList.add('d-none');
            marcar.classList.add('d-none');
            lista.innerHTML = '';
            document.getElementById('contadorReparto').textContent = '';
            enviar.disabled = true;
            try {
                const res = await fetch(`/api/pases/${paseId}/reparto`);
                const datos = await res.json().catch(() => ({}));
                if (!res.ok) throw new Error(datos.error || 'No se pudo cargar a quién repartir.');
                const queRecibio = datos.tipoPase === 'INFORME' ? 'un pedido de informe' : 'una copia';
                desde.innerHTML = `<strong>${esc(datos.dependencia)}</strong> recibió ${queRecibio} de ${esc(datos.recibidoDe)}. El original no se mueve.`;
                tipo.querySelector('option[value="INFORME"]').disabled = !datos.permiteInforme;
                tipo.value = datos.permiteInforme ? 'INFORME' : 'CONOCIMIENTO';
                ajustarAyudaReparto(tipo.value);

                const grupos = GRUPOS_DESTINO.map(([clase, titulo]) => ({ titulo, destinos: datos.destinos.filter(d => d.clase === clase) })).filter(g => g.destinos.length > 0);
                lista.innerHTML = grupos.map(g => (grupos.length > 1 ? `<div class="small fw-bold text-muted mt-1">${esc(g.titulo)}</div>` : '')
                    + g.destinos.map(d => `
                    <div class="form-check">
                        <input class="form-check-input check-reparto" type="checkbox" name="dependenciaDestinoId" value="${d.id}" id="reparto_${d.id}" ${destinoHabilitado(d) ? '' : 'disabled'}>
                        <label class="form-check-label small ${destinoHabilitado(d) ? '' : 'text-muted'}" for="reparto_${d.id}">${nombreDestino(d)}<span class="text-muted">${marcaDestino(d)}</span></label>
                    </div>`).join('')).join('');

                const disponibles = datos.destinos.filter(destinoHabilitado).length;
                marcar.classList.toggle('d-none', disponibles < 2);
                actualizarContadorReparto();
                if (disponibles === 0) {
                    aviso.textContent = 'Ninguna de sus áreas tiene quien pueda recibirlo.';
                    aviso.classList.remove('d-none');
                    return;
                }
                enviar.disabled = false;
            } catch (e) {
                desde.textContent = '';
                aviso.textContent = e.message;
                aviso.classList.remove('d-none');
            }
        }

        // Alternar visualización y comportamiento según modalidad en Modal de Salida
        function alternarTipoRemision(modo) {
            const bloqueCheckboxes = document.getElementById('bloqueCheckboxesInformes');
            const lbl = document.getElementById('lblNroSalida');
            const input = document.getElementById('inputNroSalida');

            if (modo === 'DIRECTO_INFORME') {
                bloqueCheckboxes.classList.remove('d-none');
                lbl.textContent = 'Referencia / N° de Remisión de Salida';
                input.placeholder = 'Se generará de las casillas marcadas...';
                actualizarTextoReferenciaSalida();
            } else {
                bloqueCheckboxes.classList.add('d-none');
                lbl.textContent = 'N° Nota / Remisión de Salida';
                input.placeholder = 'Ej: 120/2026 o S/N';
                input.readOnly = false;
            }
        }

        // Auto-generar el texto de referencia al marcar/desmarcar casillas de informes
        function actualizarTextoReferenciaSalida() {
            const checkboxes = document.querySelectorAll('.check-informe-salida:checked');
            const input = document.getElementById('inputNroSalida');

            if (checkboxes.length === 0) {
                input.value = 'S/N';
                return;
            }

            let referencias = [];
            checkboxes.forEach(cb => {
                referencias.push(cb.getAttribute('data-ref'));
            });

            input.value = referencias.join(' + ');
        }

        // Función global para abrir el modal de confirmación de acuse desde la Línea de Tiempo
        function abrirModalConfirmarPase(actuacionId) {
            document.getElementById('confirmarPaseActuacionId').value = actuacionId;
            const modalPase = new bootstrap.Modal(document.getElementById('modalConfirmarPasePdf'));
            modalPase.show();
        }

        // Función para preparar la devolución rápida
        function prepararDevolucion(docId) {
            document.getElementById('devolverDocId').value = docId;
            const modalDevolver = new bootstrap.Modal(document.getElementById('modalDevolver'));
            modalDevolver.show();
        }

        // Ingreso: los tipos informativos se archivan directamente y pueden exigir bibliorato
        function ajustarIngresoPorTipo() {
            const sel = document.getElementById('ingresoTipo');
            if (!sel) return;
            const opcion = sel.selectedOptions[0];
            const informativo = opcion && opcion.getAttribute('data-informativo') === 'true';
            const requiere = opcion && opcion.getAttribute('data-requiere-bibliorato') === 'true';
            document.getElementById('avisoInformativo').classList.toggle('d-none', !informativo);
            document.getElementById('biblioratoObligatorio').classList.toggle('d-none', !requiere);
            document.getElementById('ingresoBibliorato').required = requiere;
        }

        // Abre un modal desde el árbol de trazabilidad (cierra primero el árbol)
        function abrirDesdeArbol(idModal) {
            bootstrap.Modal.getInstance(document.getElementById('modalHistorial'))?.hide();
            new bootstrap.Modal(document.getElementById(idModal)).show();
        }

        function abrirModalAnularPase(actuacionId, accion) {
            document.getElementById('anularPaseActuacionId').value = actuacionId;
            document.getElementById('anularPaseAccion').value = accion;
            const retirar = accion === 'RETIRAR';
            document.getElementById('anularPaseTitulo').textContent = retirar ? 'Retirar derivación' : 'Rechazar recepción';
            document.getElementById('anularPaseAyuda').textContent = retirar
                ? 'El pase se anula y el expediente deja de figurar en la bandeja de la dependencia destino.'
                : 'Su dependencia no recibirá el expediente. La dependencia que lo envió verá el rechazo y el motivo.';
            abrirDesdeArbol('modalAnularPase');
        }

        function abrirModalReemplazarPdf(actuacionId) {
            document.getElementById('reemplazarPdfActuacionId').value = actuacionId;
            abrirDesdeArbol('modalReemplazarPdf');
        }

        // 🔐 Verifica la integridad de un adjunto y muestra el resultado junto al botón
        async function verificarIntegridad(adjuntoId, boton) {
            boton.disabled = true;
            boton.innerHTML = '<span class="spinner-border spinner-border-sm"></span>';
            try {
                const res = await fetch(`/api/adjuntos/${adjuntoId}/integridad`);
                if (!res.ok) throw new Error(res.status === 403 ? 'Sin acceso' : 'Error ' + res.status);
                const r = await res.json();
                const estilos = {INTEGRO: ['bg-success', '✔ Íntegro'], ALTERADO: ['bg-danger', '✖ Alterado'],
                                 FALTANTE: ['bg-danger', '✖ Falta el archivo'], SIN_HUELLA: ['bg-secondary', 'Sin huella']};
                const [clase, texto] = estilos[r.estado] || ['bg-secondary', r.estado];
                boton.outerHTML = `<span class="badge ${clase}" title="${esc(r.detalle)}">${texto}</span>`;
            } catch (e) {
                boton.outerHTML = `<span class="badge bg-warning text-dark">${esc(e.message)}</span>`;
            }
        }

        function botonVerificar(adjuntoId) {
            return `<button type="button" class="btn btn-link btn-sm p-0 ms-1 align-baseline" data-accion="verificar-integridad" data-id="${adjuntoId}" title="Comprobar que el archivo no fue alterado"><i class="bi bi-shield-check"></i> Verificar</button>`;
        }

        // Lista de versiones de un PDF (vigente y reemplazadas)
        function renderizarVersiones(versiones) {
            if (!versiones || versiones.length === 0) return '';
            const titulo = versiones.length === 1 ? 'Integridad del PDF' : `Historial del PDF (${versiones.length} versiones)`;
            let html = `<details class="mt-2 small"><summary class="text-muted">${titulo}</summary><ul class="list-unstyled ms-2 mt-1 mb-0">`;
            versiones.slice().reverse().forEach(v => {
                html += `<li class="mb-1">
                    <a href="/documentos/adjuntos/${v.id}/pdf" target="_blank"><i class="bi bi-file-pdf"></i> ${esc(v.nombre)}</a>
                    <span class="badge ${v.vigente ? 'bg-success' : 'bg-secondary'} ms-1">${v.vigente ? 'Vigente' : 'Reemplazada'}</span>
                    <span class="text-muted">· ${esc(v.fecha)} · ${esc(v.usuario)}</span>
                    ${botonVerificar(v.id)}
                    ${v.motivoReemplazo ? `<div class="text-muted fst-italic ms-3">Motivo: ${esc(v.motivoReemplazo)}</div>` : ''}
                </li>`;
            });
            return html + '</ul></details>';
        }

        // Función recursiva para dibujar los nodos del árbol
        function renderizarNodoArbol(nodo, docId) {
            const esPaseInterno = nodo.tipoActuacion === 'PASE_INTERNO' || nodo.tipoActuacion === 'PASE_PENDIENTE_ACUSE';
            const esPendiente = esPaseInterno && !nodo.paseConfirmado && !nodo.anulada;

            let badgeColor = 'bg-success';
            let estadoTexto = nodo.tipoActuacion;

            if (nodo.anulada) {
                badgeColor = 'bg-secondary text-decoration-line-through';
                estadoTexto = nodo.tipoAnulacion === 'RECHAZADO' ? '✖ RECEPCIÓN RECHAZADA'
                    : nodo.tipoAnulacion === 'RETIRADO' ? '✖ PASE RETIRADO' : '✖ PASE ANULADO';
            } else if (esPendiente) {
                badgeColor = 'bg-warning text-dark';
                estadoTexto = nodo.esDevolucion ? '↩️ DEVOLUCIÓN EN TRÁNSITO (PENDIENTE DE ACUSE)' : '🟡 PASE EN TRÁNSITO (PENDIENTE DE ACUSE)';
            } else if (nodo.esDevolucion) {
                badgeColor = 'bg-warning text-dark';
                estadoTexto = '↩️ DEVOLUCIÓN RECIBIDA';
            } else if (esPaseInterno) {
                badgeColor = 'bg-primary';
                estadoTexto = '🟢 PASE ENTREGADO';
            } else if (nodo.tipoActuacion === 'INFORME_TECNICO') {
                badgeColor = 'bg-info text-white';
                estadoTexto = '📝 INFORME TÉCNICO';
            } else if (nodo.tipoActuacion === 'MEMO') {
                badgeColor = 'bg-secondary';
                estadoTexto = '✉️ MEMO INTERNO';
            } else if (nodo.tipoActuacion === 'DEVOLUCION') {
                badgeColor = 'bg-warning text-dark';
                estadoTexto = '↩️ DEVOLUCIÓN';
            } else if (nodo.tipoActuacion === 'CIERRE_EXCEPCIONAL') {
                badgeColor = 'bg-danger';
                estadoTexto = '⚠️ CIERRE EXCEPCIONAL';
            }

            // Acciones disponibles para el usuario actual (calculadas en el servidor)
            let acciones = '';
            if (nodo.pdf) {
                acciones += `<a href="/documentos/actuaciones/${nodo.id}/pdf" target="_blank" class="btn btn-outline-danger btn-sm py-0"><i class="bi bi-file-pdf me-1"></i>Ver PDF</a>`;
                if (nodo.puedeReemplazarPdf) {
                    acciones += `<button type="button" class="btn btn-outline-secondary btn-sm py-0" data-accion="reemplazar-pdf" data-id="${nodo.id}" title="Subir una versión corregida"><i class="bi bi-arrow-repeat"></i></button>`;
                }
            } else if (nodo.puedeConfirmar) {
                acciones += `<button type="button" class="btn btn-warning btn-sm py-0 fw-bold" data-accion="confirmar-pase" data-id="${nodo.id}"><i class="bi bi-upload me-1"></i>Subir Acuse</button>`;
            } else if (esPendiente) {
                acciones += '<span class="badge bg-light text-muted border">Esperando acuse</span>';
            } else {
                acciones += '<span class="badge bg-light text-muted border">Sin PDF</span>';
            }
            if (nodo.puedeRetirar) {
                acciones += `<button type="button" class="btn btn-outline-warning btn-sm py-0" data-accion="anular-pase" data-id="${nodo.id}" data-tipo="RETIRAR" title="Retirar derivación enviada por error"><i class="bi bi-x-circle me-1"></i>Retirar</button>`;
            }
            if (nodo.puedeRechazar) {
                acciones += `<button type="button" class="btn btn-outline-danger btn-sm py-0" data-accion="anular-pase" data-id="${nodo.id}" data-tipo="RECHAZAR" title="No corresponde a su dependencia"><i class="bi bi-hand-thumbs-down me-1"></i>Rechazar</button>`;
            }

            const textoAnulacion = { RECHAZADO: 'Rechazado', RETIRADO: 'Retirado', CIERRE_EXCEPCIONAL: 'Anulado por cierre excepcional', CIERRE_EXPEDIENTE: 'Anulado al finalizar el expediente' };
            const detalleAnulacion = nodo.anulada
                ? `<div class="small text-danger mb-2"><i class="bi bi-info-circle me-1"></i>${textoAnulacion[nodo.tipoAnulacion] || 'Anulado'} el ${esc(nodo.fechaAnulacion)}: ${esc(nodo.motivoAnulacion)}</div>`
                : '';
            // Unidad de expediente: respuesta cosida a su pedido de informe y pertenencia al legajo de la salida
            const vinculos = (nodo.respondeA ? `<span class="badge bg-info-subtle text-info-emphasis border me-1"><i class="bi bi-reply me-1"></i>Responde al pedido de ${esc(nodo.respondeADependencia)}</span>` : '')
                + (nodo.repartidoDe ? `<span class="badge bg-info-subtle text-info-emphasis border me-1"><i class="bi bi-diagram-3 me-1"></i>Reparto de lo recibido de ${esc(nodo.repartidoDe)}</span>` : '')
                + (nodo.enLegajo ? `<span class="badge bg-success-subtle text-success-emphasis border"><i class="bi bi-paperclip me-1"></i>Integra el legajo de la ${esc(nodo.enLegajo)}</span>` : '');

            let html = `
                <div class="tree-node">
                    <div class="tree-card p-3 ${nodo.anulada ? 'opacity-75 bg-light' : ''}">
                        <div class="d-flex justify-content-between align-items-center mb-1">
                            <div>
                                <span class="badge ${badgeColor} me-2">${esc(estadoTexto)} ${nodo.numeroActuacion && nodo.numeroActuacion !== 'S/N' ? 'N° ' + esc(nodo.numeroActuacion) : ''}</span>
                                <strong class="text-dark">${esc(nodo.dependencia)}</strong>
                            </div>
                            <small class="text-muted"><i class="bi bi-clock me-1"></i>${esc(nodo.fecha)}</small>
                        </div>
                        <p class="small text-secondary mb-2 mt-1 ${nodo.anulada ? 'text-decoration-line-through' : ''}">${esc(nodo.descripcion)}</p>
                        ${vinculos ? `<div class="mb-2">${vinculos}</div>` : ''}
                        ${detalleAnulacion}
                        <div class="d-flex justify-content-between align-items-center flex-wrap gap-1 bg-light p-2 rounded">
                            <small class="text-muted"><i class="bi bi-journal-bookmark me-1"></i>Bibliorato: <strong>${esc(nodo.bibliorato)}</strong></small>
                            <div class="btn-group">${acciones}</div>
                        </div>
                        ${renderizarVersiones(nodo.versiones)}
                    </div>
            `;

            if (nodo.hijos && nodo.hijos.length > 0) {
                html += '<div class="ms-3 mt-2">';
                nodo.hijos.forEach(hijo => {
                    html += renderizarNodoArbol(hijo, docId);
                });
                html += '</div>';
            }

            html += '</div>';
            return html;
        }

        document.addEventListener('DOMContentLoaded', function () {

            // 📎 Validación inmediata de los PDF elegidos (tamaño y extensión), antes de subirlos
            const TAMANIO_MAXIMO_PDF = 20 * 1024 * 1024;
            document.querySelectorAll('input[type="file"]').forEach(input => {
                input.addEventListener('change', function () {
                    const archivo = this.files[0];
                    this.setCustomValidity('');
                    if (!archivo) return;
                    if (!archivo.name.toLowerCase().endsWith('.pdf')) {
                        this.setCustomValidity('Solo se aceptan archivos PDF.');
                    } else if (archivo.size > TAMANIO_MAXIMO_PDF) {
                        this.setCustomValidity(`El archivo pesa ${(archivo.size / 1048576).toFixed(1)} MB y el máximo es 20 MB. Reduzca la resolución del escaneo o divida el documento.`);
                    }
                    this.reportValidity();
                });
            });

            // PROTECCIÓN ANTI DOBLE-CLIC EN TODOS LOS FORMULARIOS
            document.querySelectorAll('form').forEach(form => {
                form.addEventListener('submit', function (e) {
                    const submitBtn = this.querySelector('button[type="submit"]');
                    if (submitBtn) {
                        if (submitBtn.disabled) {
                            e.preventDefault();
                            return false;
                        }
                        submitBtn.disabled = true;
                        submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>Procesando...';
                    }
                });
            });

            // Validación para asegurar selección en Derivación Flexible
            const formDerivar = document.getElementById('formDerivar');
            if (formDerivar) {
                formDerivar.addEventListener('submit', function(e) {
                    const tipo = document.getElementById('selectTipoPase').value;
                    if (tipo !== 'TRAMITE') {
                        const checkboxes = this.querySelectorAll('.check-dependencia:checked');
                        if (checkboxes.length === 0) {
                            e.preventDefault();
                            alert('Debe seleccionar al menos una dependencia destino para realizar la derivación múltiple.');
                            const submitBtn = this.querySelector('button[type="submit"]');
                            if (submitBtn) {
                                submitBtn.disabled = false;
                                submitBtn.innerHTML = '<i class="bi bi-send me-1"></i> Confirmar Derivación';
                            }
                            return false;
                        }
                    }
                });
            }

            // Buscador rápido en la tabla de finalizados
            const inputBusqueda = document.getElementById('busquedaFinalizados');
            if (inputBusqueda) {
                inputBusqueda.addEventListener('keyup', function () {
                    const filtro = this.value.toLowerCase();
                    const filas = document.querySelectorAll('#tablaFinalizados tbody tr');
                    filas.forEach(fila => {
                        const textoFila = fila.textContent.toLowerCase();
                        fila.style.display = textoFila.includes(filtro) ? '' : 'none';
                    });
                });
            }

            // Modal Agregar Actuación
            const modalActuacion = document.getElementById('modalAgregarActuacion');
            if (modalActuacion) {
                modalActuacion.addEventListener('show.bs.modal', function (event) {
                    const button = event.relatedTarget;
                    if (button) {
                        modalActuacion.querySelector('#actuacionDocId').value = button.getAttribute('data-doc-id');
                        modalActuacion.querySelector('#labelCutiActuacion').textContent = button.getAttribute('data-doc-cuti') ? `(${button.getAttribute('data-doc-cuti')})` : '';
                        // "Responder informe" ya propone el tipo que responde el pedido
                        const respondeInforme = !!button.getAttribute('data-tipo-actuacion');
                        if (respondeInforme) {
                            modalActuacion.querySelector('[name=tipoActuacion]').value = button.getAttribute('data-tipo-actuacion');
                        }
                        cargarPedidosDeActuacion(button.getAttribute('data-doc-id'), respondeInforme);
                    }
                });
            }

            // Pedidos de informe que el usuario puede responder: solo la respuesta elegida cierra el pedido
            function cargarPedidosDeActuacion(docId, respondeInforme) {
                const fila = document.getElementById('filaPedidoActuacion');
                const select = document.getElementById('selectPedidoActuacion');
                fila.classList.add('d-none');
                select.required = false;
                select.innerHTML = '<option value="">No: es una actuación sin pedido</option>';
                fetch(`/api/documentos/${docId}/pedidos-abiertos`)
                    .then(res => { if (!res.ok) throw new Error('Error ' + res.status); return res.json(); })
                    .then(pedidos => {
                        if (!pedidos.length) return;
                        pedidos.forEach(p => {
                            const opcion = document.createElement('option');
                            opcion.value = p.id;
                            const internos = p.internosAbiertos > 0 ? ` (cierra ${p.internosAbiertos} ${p.internosAbiertos === 1 ? 'pedido interno' : 'pedidos internos'} sin responder)` : '';
                            opcion.textContent = `Pedido de ${p.de} a ${p.para}, ${p.fecha}${internos}`;
                            select.appendChild(opcion);
                        });
                        fila.classList.remove('d-none');
                        // "Responder informe": quien no tiene el original solo puede cargar la respuesta
                        if (respondeInforme) {
                            select.remove(0);
                            select.required = true;
                        }
                    })
                    .catch(() => {
                        fila.classList.remove('d-none');
                        select.innerHTML = '<option value="">No se pudieron cargar los pedidos</option>';
                    });
            }

            // Modal Reparto
            const modalRepartir = document.getElementById('modalRepartir');
            if (modalRepartir) {
                modalRepartir.addEventListener('show.bs.modal', function (event) {
                    const button = event.relatedTarget;
                    if (button) {
                        document.getElementById('repartirPaseId').value = button.getAttribute('data-pase-id');
                        document.getElementById('labelCutiRepartir').textContent = button.getAttribute('data-doc-cuti') ? `(${button.getAttribute('data-doc-cuti')})` : '';
                        document.getElementById('observacionReparto').value = '';
                        cargarReparto(button.getAttribute('data-pase-id'));
                    }
                });
                document.getElementById('marcarReparto').addEventListener('click', () => {
                    const disponibles = [...document.querySelectorAll('#listaReparto .check-reparto:not(:disabled)')];
                    const marcar = !disponibles.every(c => c.checked);
                    disponibles.forEach(c => { c.checked = marcar; });
                    actualizarContadorReparto();
                });
                document.getElementById('formRepartir').addEventListener('submit', function (e) {
                    if (!this.querySelector('.check-reparto:checked')) {
                        e.preventDefault();
                        e.stopImmediatePropagation();
                        const aviso = document.getElementById('repartirAviso');
                        aviso.textContent = 'Seleccione al menos un área.';
                        aviso.classList.remove('d-none');
                    }
                });
            }

            // Modal Derivación
            const modalDerivar = document.getElementById('modalDerivar');
            if (modalDerivar) {
                document.getElementById('buscarDestino').addEventListener('input', e => filtrarDestinosDerivacion(e.target.value));
                document.getElementById('marcarDirecciones').addEventListener('click', () => {
                    const direcciones = [...document.querySelectorAll('#listaDestinosMultiple .check-dependencia[data-clase="DIRECCION"]:not([data-sin-receptor="true"])')];
                    const marcar = !direcciones.every(c => c.checked);
                    direcciones.forEach(c => { c.checked = marcar; });
                    actualizarContadorDestinos();
                });
                modalDerivar.addEventListener('show.bs.modal', function (event) {
                    const button = event.relatedTarget;
                    if (button) {
                        modalDerivar.querySelector('#derivarDocId').value = button.getAttribute('data-doc-id');
                        modalDerivar.querySelector('#labelCuti').textContent = button.getAttribute('data-doc-cuti') ? `(${button.getAttribute('data-doc-cuti')})` : '';

                        // Difusión de un informativo: solo para conocimiento; el resto empieza en trámite
                        const esInformativo = button.getAttribute('data-informativo') === 'true';
                        const selTipo = document.getElementById('selectTipoPase');
                        derivacion.difusion = esInformativo;
                        derivacion.soloElevar = false;
                        selTipo.value = esInformativo ? 'CONOCIMIENTO' : 'TRAMITE';
                        ajustarTiposPase(null);
                        alternarTipoPase(selTipo.value);

                        cargarDestinosDerivacion(button.getAttribute('data-doc-id'));
                    }
                });
            }

            // Pestaña Archivo físico: filtro por bibliorato
            const filtroBibliorato = document.getElementById('filtroBibliorato');
            if (filtroBibliorato) {
                filtroBibliorato.addEventListener('change', function () {
                    const valor = this.value;
                    document.querySelectorAll('#tablaArchivo tbody tr[data-bibliorato]').forEach(fila => {
                        fila.style.display = (!valor || fila.getAttribute('data-bibliorato') === valor) ? '' : 'none';
                    });
                });
            }

            // Modal Ubicación física: el motivo solo se exige al trasladar
            const modalUbicacion = document.getElementById('modalUbicacion');
            if (modalUbicacion) {
                modalUbicacion.addEventListener('show.bs.modal', function (event) {
                    const button = event.relatedTarget;
                    if (!button) return;
                    modalUbicacion.querySelector('#ubicacionDocId').value = button.getAttribute('data-doc-id');
                    modalUbicacion.querySelector('#labelCutiUbicacion').textContent = `(${button.getAttribute('data-doc-cuti')})`;
                    // Desde el Archivo físico viene la carpeta actual: al volver a elegirla, cambiarla exige motivo
                    const actual = button.getAttribute('data-bibliorato-id') || '';
                    const select = document.getElementById('ubicacionBibliorato');
                    const motivo = document.getElementById('ubicacionMotivo');
                    select.value = [...select.options].some(o => o.value === actual) ? actual : '';
                    select.dataset.actual = actual;
                    motivo.value = '';
                    motivo.required = actual !== '' && select.value === actual;
                    modalUbicacion.querySelector('[name=fojaDesde]').value = '';
                    modalUbicacion.querySelector('[name=fojaHasta]').value = '';
                });
                document.getElementById('ubicacionBibliorato').addEventListener('change', function () {
                    document.getElementById('ubicacionMotivo').required = this.dataset.actual !== '' && this.value === this.dataset.actual;
                });
            }

            ajustarIngresoPorTipo();

            // Modal Salida: Carga de Informes Disponibles por API
            const modalSalida = document.getElementById('modalSalida');
            if (modalSalida) {
                modalSalida.addEventListener('show.bs.modal', function (event) {
                    const button = event.relatedTarget;
                    if (!button) return;

                    const docId = button.getAttribute('data-doc-id');
                    const docCuti = button.getAttribute('data-doc-cuti');

                    modalSalida.querySelector('#salidaDocId').value = docId;
                    modalSalida.querySelector('#labelCutiSalida').textContent = docCuti ? `(${docCuti})` : '';

                    const selectRemision = document.getElementById('selectTipoRemision');
                    if (selectRemision) {
                        selectRemision.value = 'NOTA_DIRECCION';
                        alternarTipoRemision('NOTA_DIRECCION');
                    }

                    const contenedor = document.getElementById('contenedorInformesDisponibles');
                    if (contenedor) {
                        contenedor.innerHTML = '<span class="text-muted small"><div class="spinner-border spinner-border-sm text-primary me-2"></div>Buscando informes técnicos...</span>';

                        fetch(`/api/documentos/${docId}/informes-disponibles`)
                            .then(res => res.json())
                            .then(informes => {
                                if (!informes || informes.length === 0) {
                                    contenedor.innerHTML = '<span class="text-muted small fst-italic">No hay informes técnicos cargados para este expediente. Se remite con la Nota de la Dirección.</span>';
                                    return;
                                }

                                let html = '';
                                informes.forEach(inf => {
                                    const refTexto = `${inf.tipo === 'INFORME_TECNICO' ? 'Inf. Téc.' : inf.tipo} N° ${inf.numero}`;
                                    html += `
                                        <div class="form-check mb-2">
                                            <input class="form-check-input check-informe-salida" 
                                                   type="checkbox" 
                                                   name="informeActuacionId" 
                                                   value="${inf.id}" 
                                                   id="inf_${inf.id}" 
                                                   data-ref="${esc(refTexto)}"
                                                   data-accion-cambio="referencia-salida"
                                                   checked>
                                            <label class="form-check-label small" for="inf_${inf.id}">
                                                <strong>${esc(refTexto)}</strong> - ${esc(inf.dependencia)}
                                                <span class="d-block text-secondary text-truncate" style="max-width: 500px;">${esc(inf.resumen)}</span>
                                            </label>
                                        </div>
                                    `;
                                });
                                contenedor.innerHTML = html;
                            })
                            .catch(err => {
                                contenedor.innerHTML = '<span class="text-danger small">Error al consultar informes técnicos.</span>';
                            });
                    }
                });
            }

            // Modal Cierre Excepcional
            const modalForzar = document.getElementById('modalForzarCierre');
            if (modalForzar) {
                modalForzar.addEventListener('show.bs.modal', function (event) {
                    const button = event.relatedTarget;
                    if (button) {
                        modalForzar.querySelector('#forzarCierreDocId').value = button.getAttribute('data-doc-id');
                        modalForzar.querySelector('#labelCutiForzar').textContent = `(${button.getAttribute('data-doc-cuti')})`;
                        modalForzar.querySelector('textarea').value = '';
                    }
                });
            }

            // Modal Versiones de acuses/adjuntos del expediente
            const modalAdjuntosBs = new bootstrap.Modal(document.getElementById('modalAdjuntos'));
            document.querySelectorAll('.btn-ver-adjuntos').forEach(btn => {
                btn.addEventListener('click', function () {
                    const docId = this.getAttribute('data-doc-id');
                    document.getElementById('labelCutiAdjuntos').textContent = `(${this.getAttribute('data-doc-cuti')})`;
                    const campoDoc = document.getElementById('adjuntosDocId');
                    if (campoDoc) campoDoc.value = docId;
                    const contenedor = document.getElementById('contenedorAdjuntos');
                    contenedor.innerHTML = '<div class="text-center py-3"><div class="spinner-border spinner-border-sm text-primary"></div></div>';
                    modalAdjuntosBs.show();

                    fetch(`/api/documentos/${docId}/adjuntos`)
                        .then(res => { if (!res.ok) throw new Error('Error ' + res.status); return res.json(); })
                        .then(lista => {
                            if (!lista.length) {
                                contenedor.innerHTML = '<div class="alert alert-light border small mb-0">El expediente no tiene acuses cargados.</div>';
                                return;
                            }
                            let html = '<table class="table table-sm small align-middle mb-0"><thead><tr><th>Archivo</th><th>Tipo</th><th>Estado</th><th>Subido</th><th>Integridad</th></tr></thead><tbody>';
                            lista.slice().reverse().forEach(a => {
                                html += `<tr class="${a.vigente ? '' : 'text-muted'}">
                                    <td><a href="/documentos/adjuntos/${a.id}/pdf" target="_blank"><i class="bi bi-file-pdf text-danger"></i> ${esc(a.nombre)}</a>
                                        ${a.motivoReemplazo ? `<div class="fst-italic">Motivo: ${esc(a.motivoReemplazo)}</div>` : ''}</td>
                                    <td>${esc(a.tipo)}</td>
                                    <td><span class="badge ${a.vigente ? 'bg-success' : 'bg-secondary'}">${a.vigente ? 'Vigente' : 'Reemplazado'}</span></td>
                                    <td>${esc(a.fecha)}<div>${esc(a.usuario)}</div></td>
                                    <td>${botonVerificar(a.id)}</td>
                                </tr>`;
                            });
                            contenedor.innerHTML = html + '</tbody></table>';
                        })
                        .catch(err => {
                            contenedor.innerHTML = `<div class="alert alert-danger small mb-0">No se pudieron cargar los adjuntos. ${esc(err.message)}</div>`;
                        });
                });
            });

            // Modal Confirmar Acuse Final
            const modalAcuse = document.getElementById('modalConfirmarAcuse');
            if (modalAcuse) {
                modalAcuse.addEventListener('show.bs.modal', function (event) {
                    const button = event.relatedTarget;
                    if (button) {
                        modalAcuse.querySelector('#acuseDocId').value = button.getAttribute('data-doc-id');
                        modalAcuse.querySelector('#labelCutiAcuse').textContent = button.getAttribute('data-doc-cuti') ? `(${button.getAttribute('data-doc-cuti')})` : '';
                    }
                });
            }

            const modalReapertura = document.getElementById('modalSolicitarReapertura');
            if (modalReapertura) {
                modalReapertura.addEventListener('show.bs.modal', function (event) {
                    const button = event.relatedTarget;
                    if (!button) return;
                    modalReapertura.querySelector('#reaperturaDocId').value = button.getAttribute('data-doc-id');
                    modalReapertura.querySelector('#labelCutiReapertura').textContent = `(${button.getAttribute('data-doc-cuti')})`;
                    modalReapertura.querySelector('#reaperturaMotivo').value = '';
                });
            }

            const modalCorregirIngreso = document.getElementById('modalCorregirIngreso');
            if (modalCorregirIngreso) {
                modalCorregirIngreso.addEventListener('show.bs.modal', function (event) {
                    const button = event.relatedTarget;
                    if (!button) return;
                    modalCorregirIngreso.querySelector('#corregirIngresoDocId').value = button.getAttribute('data-doc-id');
                    modalCorregirIngreso.querySelector('#labelCutiCorregirIngreso').textContent = button.getAttribute('data-doc-cuti') ? `(${button.getAttribute('data-doc-cuti')})` : '';
                    modalCorregirIngreso.querySelector('#corregirIngresoTipo').value = button.getAttribute('data-tipo-id');
                    modalCorregirIngreso.querySelector('#corregirIngresoNumero').value = button.getAttribute('data-numero');
                    modalCorregirIngreso.querySelector('#corregirIngresoOrigen').value = button.getAttribute('data-origen-id');
                    modalCorregirIngreso.querySelector('#corregirIngresoPrioridad').value = button.getAttribute('data-prioridad');
                    modalCorregirIngreso.querySelector('#corregirIngresoAsunto').value = button.getAttribute('data-asunto');
                    modalCorregirIngreso.querySelector('#corregirIngresoMotivo').value = '';
                });
            }

            const modalAnularExpediente = document.getElementById('modalAnularExpediente');
            if (modalAnularExpediente) {
                modalAnularExpediente.addEventListener('show.bs.modal', function (event) {
                    const button = event.relatedTarget;
                    if (!button) return;
                    modalAnularExpediente.querySelector('#anularExpedienteDocId').value = button.getAttribute('data-doc-id');
                    modalAnularExpediente.querySelector('#labelCutiAnularExpediente').textContent = button.getAttribute('data-doc-cuti') ? `(${button.getAttribute('data-doc-cuti')})` : '';
                    modalAnularExpediente.querySelector('#anularExpedienteMotivo').value = '';
                });
            }

            const modalCorregirSalida = document.getElementById('modalCorregirSalida');
            if (modalCorregirSalida) {
                modalCorregirSalida.addEventListener('show.bs.modal', function (event) {
                    const button = event.relatedTarget;
                    if (button) {
                        modalCorregirSalida.querySelector('#corregirSalidaDocId').value = button.getAttribute('data-doc-id');
                        modalCorregirSalida.querySelector('#labelCutiCorregirSalida').textContent = button.getAttribute('data-doc-cuti') ? `(${button.getAttribute('data-doc-cuti')})` : '';
                    }
                });
            }

            // Modal Historial con Carga Dinámica y Agrupación Jerárquica en Árbol
            const modalHistorialBs = new bootstrap.Modal(document.getElementById('modalHistorial'));
            document.querySelectorAll('.btn-ver-historial').forEach(btn => {
                btn.addEventListener('click', function () {
                    const docId = this.getAttribute('data-doc-id');
                    const docCuti = this.getAttribute('data-doc-cuti');
                    document.getElementById('labelCutiHistorial').textContent = docCuti ? `(${docCuti})` : '';

                    const contenedor = document.getElementById('contenedorTimeline');
                    contenedor.innerHTML = '<div class="text-center py-4"><div class="spinner-border text-primary"></div><p class="mt-2 text-muted small">Armando árbol de trazabilidad jerárquico...</p></div>';
                    modalHistorialBs.show();

                    fetch(`/api/documentos/${docId}/actuaciones`)
                        .then(res => {
                            if (!res.ok) throw new Error(res.status === 403 ? 'Sin acceso a este expediente.' : 'Error ' + res.status);
                            return res.json();
                        })
                        .then(data => {
                            if (!data || data.length === 0) {
                                contenedor.innerHTML = '<div class="alert alert-light text-center border">No hay actuaciones ni derivaciones registradas aún para este expediente.</div>';
                                return;
                            }

                            // Árbol genealógico: cada actuación cuelga del último pase (o devolución)
                            // que recibió la dependencia que la emitió. Llegan ordenadas por fecha.
                            let raices = [];
                            let ultimoRecibidoPorDependencia = {};

                            data.forEach(act => {
                                act.hijos = act.hijos || [];
                                const padre = act.dependenciaEmisoraId != null ? ultimoRecibidoPorDependencia[act.dependenciaEmisoraId] : null;

                                if (padre) {
                                    padre.hijos.push(act);
                                } else {
                                    raices.push(act);
                                }

                                // Un pase retirado o rechazado nunca llegó: no es padre de nada
                                if (act.dependenciaReceptoraId != null && !act.anulada) {
                                    ultimoRecibidoPorDependencia[act.dependenciaReceptoraId] = act;
                                }
                            });

                            let html = '<div class="tree-container">';
                            raices.forEach(nodo => {
                                html += renderizarNodoArbol(nodo, docId);
                            });
                            html += '</div>';
                            contenedor.innerHTML = html;
                        })
                        .catch(err => {
                            contenedor.innerHTML = `<div class="alert alert-danger">Error al cargar el árbol de trazabilidad. ${esc(err.message)}</div>`;
                        });
                });
            });

            // Modal Auditoría con Carga por API
            const modalAuditoriaBs = new bootstrap.Modal(document.getElementById('modalAuditoria'));
            document.querySelectorAll('.btn-ver-auditoria').forEach(btn => {
                btn.addEventListener('click', function () {
                    const docId = this.getAttribute('data-doc-id');
                    const docCuti = this.getAttribute('data-doc-cuti');
                    document.getElementById('labelCutiAuditoria').textContent = docCuti ? `(${docCuti})` : '';

                    const tbody = document.getElementById('bodyTablaAuditoria');
                    tbody.innerHTML = '<tr><td colspan="8" class="text-center py-4"><div class="spinner-border text-secondary"></div><p class="mt-2 text-muted small">Cargando bitácora inmutable de eventos...</p></td></tr>';
                    modalAuditoriaBs.show();

                    fetch(`/api/documentos/${docId}/auditoria`)
                        .then(res => {
                            if (res.status === 403) {
                                tbody.innerHTML = '<tr><td colspan="8" class="text-center py-3 text-danger">Su dependencia no tiene acceso a este expediente.</td></tr>';
                                return null;
                            }
                            return res.json();
                        })
                        .then(data => {
                            if (data === null) {
                                return;
                            }
                            if (!data || data.length === 0) {
                                tbody.innerHTML = '<tr><td colspan="8" class="text-center py-3 text-muted">No hay registros de auditoría aún.</td></tr>';
                                return;
                            }

                            let html = '';
                            data.forEach(aud => {
                                html += `
                                    <tr>
                                        <td class="fw-bold text-nowrap"><i class="bi bi-clock me-1 text-muted"></i>${esc(aud.fechaHora)}</td>
                                        <td><span class="badge bg-dark">${esc(aud.evento)}</span></td>
                                        <td><span class="badge bg-light text-dark border">${esc(aud.estadoAnterior)}</span></td>
                                        <td><span class="badge bg-primary">${esc(aud.estadoNuevo)}</span></td>
                                        <td class="fw-semibold">${esc(aud.usuario)}</td>
                                        <td class="small">${esc(aud.dependencia)}</td>
                                        <td class="small text-secondary">${esc(aud.descripcion)}</td>
                                        <td><code class="text-muted">${esc(aud.ip)}</code></td>
                                    </tr>
                                `;
                            });
                            tbody.innerHTML = html;
                        })
                        .catch(err => {
                            tbody.innerHTML = '<tr><td colspan="8" class="text-center py-3 text-danger">Error al cargar la bitácora de auditoría.</td></tr>';
                        });
                });
            });

        });

        document.addEventListener("DOMContentLoaded", function () {
        // Recuperar la última pestaña activa desde localStorage
        let activeTab = localStorage.getItem("activeTab");
        if (activeTab) {
            let triggerEl = document.querySelector(`[data-bs-target="${activeTab}"]`);
            if (triggerEl) {
                let tab = new bootstrap.Tab(triggerEl);
                tab.show();
            }
        }

        // Guardar la pestaña activa cuando el usuario hace clic en alguna
        const triggerTabList = document.querySelectorAll('#docTabs button');
        triggerTabList.forEach(triggerEl => {
            let tabInstance = new bootstrap.Tab(triggerEl);
            triggerEl.addEventListener('click', function (event) {
                let target = triggerEl.getAttribute('data-bs-target');
                localStorage.setItem("activeTab", target);
                tabInstance.show();
            });
        });
    });

        // 🛡️ Eventos sin manejadores en línea (auditoría B3: la CSP no admite 'unsafe-inline' en scripts).
        // Los botones generados en el historial llevan data-accion y se atienden por delegación.
        document.addEventListener('click', function (e) {
            const boton = e.target.closest('[data-accion]');
            if (!boton) {
                return;
            }
            const id = Number(boton.dataset.id);
            switch (boton.dataset.accion) {
                case 'verificar-integridad': verificarIntegridad(id, boton); break;
                case 'reemplazar-pdf': abrirModalReemplazarPdf(id); break;
                case 'confirmar-pase': abrirModalConfirmarPase(id); break;
                case 'anular-pase': abrirModalAnularPase(id, boton.dataset.tipo); break;
                case 'preparar-devolucion': prepararDevolucion(id); break;
            }
        });

        document.addEventListener('change', function (e) {
            const t = e.target;
            if (t.matches('[data-accion-cambio="referencia-salida"]')) {
                actualizarTextoReferenciaSalida();
            } else if (t.id === 'selectTipoPase') {
                alternarTipoPase(t.value);
            } else if (t.name === 'alcanceEnvio') {
                pintarDestinosDerivacion(t.value);
            } else if (t.id === 'selectTipoReparto') {
                ajustarAyudaReparto(t.value);
            } else if (t.classList && t.classList.contains('check-reparto')) {
                actualizarContadorReparto();
            } else if (t.classList && t.classList.contains('check-dependencia')) {
                actualizarContadorDestinos();
            } else if (t.id === 'selectTipoRemision') {
                alternarTipoRemision(t.value);
            } else if (t.id === 'ingresoTipo') {
                ajustarIngresoPorTipo();
            }
        });

        // Números del tipo 45/2026 o S/N: en mayúsculas y sin espacios mientras se escribe
        document.addEventListener('input', function (e) {
            if (e.target.matches('.js-numero-documento')) {
                e.target.value = e.target.value.toUpperCase().replace(/\s+/g, '');
            }
        });
