const fs = require('fs');
const path = require('path');
const { minify } = require('html-minifier-terser');

const origen = path.join(__dirname, 'src', 'main', 'resources', 'static');
const destino = path.join(__dirname, 'build-static');

const opciones = {
    collapseWhitespace: true,
    removeComments: true,
    minifyCSS: true,
    minifyJS: {
        compress: true,
        mangle: { toplevel: false }
    }
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
            const resultado = await minify(texto, opciones);
            fs.writeFileSync(rutaSalida, resultado);
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