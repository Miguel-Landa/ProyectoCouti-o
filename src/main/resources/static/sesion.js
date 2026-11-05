var fetchOriginal = window.fetch.bind(window);

function leerCookie(nombre) {
    var partes = document.cookie.split('; ');
    for (var i = 0; i < partes.length; i++) {
        if (partes[i].indexOf(nombre + '=') === 0) {
            return decodeURIComponent(partes[i].substring(nombre.length + 1));
        }
    }
    return null;
}

window.fetch = async function (recurso, opciones) {
    var config = Object.assign({}, opciones || {});
    var metodo = (config.method || 'GET').toUpperCase();
    var url = recurso;
    if (typeof recurso !== 'string') {
        url = recurso.url;
    }

    config.credentials = 'same-origin';

    if (metodo !== 'GET' && metodo !== 'HEAD') {
        var token = leerCookie('XSRF-TOKEN');
        if (token) {
            var cabeceras = new Headers(config.headers || {});
            cabeceras.set('X-XSRF-TOKEN', token);
            config.headers = cabeceras;
        }
    }

    var respuesta = await fetchOriginal(recurso, config);

    if (respuesta.status === 401 && url.indexOf('/api/login') === -1) {
        window.location.href = '/index.html';
    }
    return respuesta;
};

document.addEventListener('click', async function (evento) {
    var enlace = evento.target.closest('a.btn-logout');
    if (!enlace) {
        return;
    }
    evento.preventDefault();
    try {
        await window.fetch('/api/logout', { method: 'POST' });
    } catch (e) {
        window.location.href = '/index.html';
    }
    window.location.href = '/index.html';
});