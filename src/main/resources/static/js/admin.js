// 👥 Pantalla de administración. Sin manejadores en línea: la CSP no admite 'unsafe-inline' en scripts (auditoría B3).

// Formularios que piden confirmación antes de enviarse
document.addEventListener('submit', function (e) {
    const mensaje = e.target.dataset ? e.target.dataset.confirmar : null;
    if (mensaje && !confirm(mensaje)) {
        e.preventDefault();
    }
});

document.addEventListener('change', function (e) {
    const t = e.target;
    // Mesa de entrada, responsable y administrador abarcan por defecto las subordinadas; operador y consulta, no
    if (t.id === 'selRol') {
        document.getElementById('chkSub').checked = ['MESA_ENTRADA', 'RESPONSABLE', 'ADMIN_SISTEMA'].includes(t.value);
    }
    // La suplencia necesita indicar a quién suple
    if (t.id === 'chkSuplencia') {
        document.getElementById('bloqueTitular').classList.toggle('d-none', !t.checked);
        document.getElementById('selTitular').required = t.checked;
    }
});
