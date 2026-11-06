// Conserva la pestaña abierta al filtrar o al entrar a una dependencia
document.addEventListener('DOMContentLoaded', function () {
    const botones = document.querySelectorAll('[data-bs-toggle="tab"]');
    const seccion = location.hash && document.querySelector('[data-bs-target="' + CSS.escape(location.hash) + '"]');
    if (seccion) {
        bootstrap.Tab.getOrCreateInstance(seccion).show();
    }

    function recordar(destino) {
        history.replaceState(null, '', location.pathname + location.search + destino);
        document.getElementById('filtroReportes').action = document.getElementById('filtroReportes').action.split('#')[0] + destino;
        document.querySelectorAll('#dependencias a[href*="dependenciaId"]').forEach(function (a) {
            a.hash = destino;
        });
    }

    botones.forEach(function (b) {
        b.addEventListener('shown.bs.tab', function () {
            recordar(b.getAttribute('data-bs-target'));
        });
    });
    if (location.hash) {
        recordar(location.hash);
    }
});
