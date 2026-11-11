const fs = require('fs');
const path = require('path');
const { minify } = require('html-minifier-terser');
const { minify: minifyJs } = require('terser');
const CleanCSS = require('clean-css');

const origen = path.join(__dirname, 'src', 'main', 'resources', 'static');
const destino = path.join(__dirname, 'build-static');

const opcionesHtml = {
    collapseWhitespace: true,
    removeComments: true,
    minifyCSS: true,
    minifyJS: {
        compress: true,
        mangle: { toplevel: false }
    }
};

const opcionesJs = {
    compress: true,
    mangle: { toplevel: true },
    format: { comments: false }
};

async function procesar(carpeta, salida) {
    fs.mkdirSync(salida, { recursive: true });
    const elementos = fs.readdirSync(carpeta, { withFileTypes: true });
    for (const elemento of elementos) {
        const rutaOrigen = path.join(carpeta, elemento.name);
        const rutaSalida = path.join(salida, elemento.name);
        if (elemento.isDirectory()) {
            await procesar(rutaOrigen, rutaSalida);
        } else if (elemento.name.endsWith('.html')) {
            const texto = fs.readFileSync(rutaOrigen, 'utf8');
            const resultado = await minify(texto, opcionesHtml);
            fs.writeFileSync(rutaSalida, resultado);
        } else if (elemento.name.endsWith('.css')) {
            const texto = fs.readFileSync(rutaOrigen, 'utf8');
            const resultado = new CleanCSS({ level: 2 }).minify(texto);
            if (resultado.errors.length > 0) {
                throw new Error(resultado.errors.join(' '));
            }
            fs.writeFileSync(rutaSalida, resultado.styles);
        } else if (elemento.name.endsWith('.js')) {
            const texto = fs.readFileSync(rutaOrigen, 'utf8');
            const resultado = await minifyJs(texto, opcionesJs);
            if (resultado.code === undefined) {
                throw new Error('No se pudo minificar ' + elemento.name);
            }
            fs.writeFileSync(rutaSalida, resultado.code);
        } else {
            fs.copyFileSync(rutaOrigen, rutaSalida);
        }
    }
}

procesar(origen, destino).then(() => {
    console.log('Build terminado');
}).catch(error => {
    console.error(error);
    process.exit(1);
});