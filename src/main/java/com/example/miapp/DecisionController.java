package com.example.miapp;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/decisiones")
public class DecisionController {

    private static final String[] DIAS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private final JdbcTemplate db;

    public DecisionController(JdbcTemplate db) {
        this.db = db;
    }

    private ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private double r(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private String num(double valor) {
        return String.format(Locale.US, "%.2f", valor);
    }

    private String cantidadTexto(double valor) {
        if (valor == Math.rint(valor)) {
            return String.format(Locale.US, "%.0f", valor);
        }
        return String.format(Locale.US, "%.2f", valor);
    }

    private String unir(double[] valores, String separador) {
        StringBuilder texto = new StringBuilder();
        for (int k = 0; k < valores.length; k++) {
            if (k > 0) {
                texto.append(separador);
            }
            texto.append(num(valores[k]));
        }
        return texto.toString();
    }

    private double[] numeros(Map<String, Object> b, String clave) {
        Object valor = b.get(clave);
        if (!(valor instanceof List)) {
            throw invalido("Falta la lista: " + clave);
        }
        List<?> lista = (List<?>) valor;
        if (lista.size() < 2 || lista.size() > 7) {
            throw invalido("La lista " + clave + " debe tener entre 2 y 7 valores.");
        }
        double[] resultado = new double[lista.size()];
        for (int k = 0; k < lista.size(); k++) {
            try {
                resultado[k] = Double.parseDouble(String.valueOf(lista.get(k)));
            } catch (NumberFormatException e) {
                throw invalido("Número inválido en " + clave);
            }
            if (resultado[k] < 0) {
                throw invalido("No se permiten valores negativos en " + clave);
            }
        }
        return resultado;
    }

    private double numero(Map<String, Object> b, String clave, double porDefecto) {
        Object valor = b.get(clave);
        if (valor == null || valor.toString().isBlank()) {
            return porDefecto;
        }
        try {
            return Double.parseDouble(valor.toString());
        } catch (NumberFormatException e) {
            throw invalido("Número inválido en " + clave);
        }
    }

    private String[] textos(Map<String, Object> b, String clave, int n, String prefijo) {
        String[] salida = new String[n];
        Object valor = b.get(clave);
        List<?> lista = valor instanceof List ? (List<?>) valor : new ArrayList<>();
        for (int k = 0; k < n; k++) {
            String t = "";
            if (k < lista.size() && lista.get(k) != null) {
                t = lista.get(k).toString().trim();
            }
            if (t.isEmpty()) {
                t = prefijo + " " + (k + 1);
            }
            if (t.length() > 60) {
                t = t.substring(0, 60);
            }
            salida[k] = t;
        }
        return salida;
    }

    private LocalDate fechaDe(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        try {
            if (t.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                return LocalDate.parse(t.substring(0, 10));
            }
            String primera = t.split("[ ,]")[0];
            if (primera.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
                String[] partes = primera.split("/");
                return LocalDate.of(Integer.parseInt(partes[2]),
                        Integer.parseInt(partes[1]), Integer.parseInt(partes[0]));
            }
        } catch (DateTimeException e) {
            return null;
        }
        return null;
    }

    private void sumar(Map<String, Double> mapa, String clave, double valor) {
        Double actual = mapa.get(clave);
        mapa.put(clave, (actual == null ? 0.0 : actual) + valor);
    }

    private int indiceMayor(double[] valores) {
        int mejor = 0;
        for (int k = 1; k < valores.length; k++) {
            if (valores[k] > valores[mejor]) {
                mejor = k;
            }
        }
        return mejor;
    }

    private int indiceMenor(double[] valores) {
        int mejor = 0;
        for (int k = 1; k < valores.length; k++) {
            if (valores[k] < valores[mejor]) {
                mejor = k;
            }
        }
        return mejor;
    }

    private List<Double> lista(double[] valores) {
        List<Double> salida = new ArrayList<>();
        for (int k = 0; k < valores.length; k++) {
            salida.add(r(valores[k]));
        }
        return salida;
    }

    private Map<String, Object> producto(Object idTexto) {
        long id;
        try {
            id = (long) Double.parseDouble(String.valueOf(idTexto));
        } catch (NumberFormatException e) {
            throw invalido("Selecciona un producto.");
        }
        List<Map<String, Object>> filas = db.queryForList(
                "SELECT id, sku, nombre, costo_compra, precio, stock FROM productos WHERE id = ?", id);
        if (filas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        }
        return filas.get(0);
    }

    private Map<String, Object> criterio(String nombre, String tipoDecision, String formula, String regla,
                                         double[] valores, int mejor, String ejemplo) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("nombre", nombre);
        c.put("tipoDecision", tipoDecision);
        c.put("formula", formula);
        c.put("regla", regla);
        c.put("valores", lista(valores));
        c.put("mejor", mejor);
        c.put("ejemplo", ejemplo);
        return c;
    }

    @GetMapping("/costos-proveedor")
    public List<Map<String, Object>> costosProveedor(@RequestParam("producto_id") String productoId) {
        Map<String, Object> p = producto(productoId);
        List<Map<String, Object>> filas = db.queryForList(
                "SELECT c.proveedor AS proveedor, d.costo AS costo, c.fecha AS fecha "
                        + "FROM compra_detalle d JOIN compras c ON c.id = d.compra_id "
                        + "WHERE d.sku = ? ORDER BY c.id DESC", String.valueOf(p.get("sku")));
        Map<String, Map<String, Object>> ultimos = new LinkedHashMap<>();
        for (int k = 0; k < filas.size(); k++) {
            Map<String, Object> fila = filas.get(k);
            String proveedor = String.valueOf(fila.get("proveedor"));
            if (!ultimos.containsKey(proveedor)) {
                Map<String, Object> nuevo = new LinkedHashMap<>();
                nuevo.put("proveedor", proveedor);
                nuevo.put("costo", r(((Number) fila.get("costo")).doubleValue()));
                nuevo.put("fecha", String.valueOf(fila.get("fecha")));
                ultimos.put(proveedor, nuevo);
            }
        }
        return new ArrayList<>(ultimos.values());
    }

    @GetMapping("/precio-apoyo")
    public Map<String, Object> precioApoyo(@RequestParam("producto_id") String productoId,
                                           @RequestParam(defaultValue = "90") int dias) {
        if (dias < 7) {
            dias = 7;
        }
        if (dias > 365) {
            dias = 365;
        }
        Map<String, Object> p = producto(productoId);
        LocalDate hoy = LocalDate.now(ZONA);
        LocalDate inicio = hoy.minusDays(dias - 1L);
        double vendidas = 0;

        List<Map<String, Object>> ventas = db.queryForList(
                "SELECT d.cantidad AS cantidad, v.fecha AS fecha "
                        + "FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id "
                        + "WHERE v.tipo = 'VENTA' AND d.producto_id = ?", String.valueOf(((Number) p.get("id")).longValue()));
        for (int k = 0; k < ventas.size(); k++) {
            Map<String, Object> fila = ventas.get(k);
            LocalDate f = fechaDe(String.valueOf(fila.get("fecha")));
            if (f != null && !f.isBefore(inicio) && !f.isAfter(hoy)) {
                vendidas = vendidas + ((Number) fila.get("cantidad")).doubleValue();
            }
        }
        List<Map<String, Object>> refacciones = db.queryForList(
                "SELECT r.cantidad AS cantidad, c.fecha_pago AS fecha_pago "
                        + "FROM cobro_refacciones r JOIN cobros c ON c.id = r.cobro_id WHERE r.sku = ?",
                String.valueOf(p.get("sku")));
        for (int k = 0; k < refacciones.size(); k++) {
            Map<String, Object> fila = refacciones.get(k);
            LocalDate f = Instant.ofEpochMilli(((Number) fila.get("fecha_pago")).longValue()).atZone(ZONA).toLocalDate();
            if (!f.isBefore(inicio) && !f.isAfter(hoy)) {
                vendidas = vendidas + ((Number) fila.get("cantidad")).doubleValue();
            }
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("dias", dias);
        respuesta.put("vendidas", r(vendidas));
        respuesta.put("demandaMensual", r(vendidas * 30.0 / dias));
        respuesta.put("costo", r(((Number) p.get("costo_compra")).doubleValue()));
        respuesta.put("precio", r(((Number) p.get("precio")).doubleValue()));
        return respuesta;
    }

    @GetMapping("/apoyo")
    public Map<String, Object> apoyo(@RequestParam(defaultValue = "90") int dias) {
        if (dias < 7) {
            dias = 7;
        }
        if (dias > 365) {
            dias = 365;
        }
        LocalDate hoy = LocalDate.now(ZONA);
        LocalDate inicio = hoy.minusDays(dias - 1L);

        int[] totales = new int[7];
        int[] ocurrencias = new int[7];
        for (LocalDate f = inicio; !f.isAfter(hoy); f = f.plusDays(1)) {
            ocurrencias[f.getDayOfWeek().getValue() - 1]++;
        }
        List<String> fechasCitas = db.queryForList("SELECT fecha_hora FROM citas", String.class);
        for (int k = 0; k < fechasCitas.size(); k++) {
            LocalDate f = fechaDe(fechasCitas.get(k));
            if (f != null && !f.isBefore(inicio) && !f.isAfter(hoy)) {
                totales[f.getDayOfWeek().getValue() - 1]++;
            }
        }
        List<Map<String, Object>> atenciones = new ArrayList<>();
        double totalSemana = 0;
        for (int k = 0; k < 7; k++) {
            double promedio = ocurrencias[k] > 0 ? (double) totales[k] / ocurrencias[k] : 0;
            totalSemana = totalSemana + promedio;
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("dia", DIAS[k]);
            fila.put("total", totales[k]);
            fila.put("ocurrencias", ocurrencias[k]);
            fila.put("promedio", r(promedio));
            atenciones.add(fila);
        }

        Map<String, Double> vendidasPorId = new HashMap<>();
        Map<String, Double> vendidasPorSku = new HashMap<>();
        List<Map<String, Object>> ventas = db.queryForList(
                "SELECT d.producto_id AS pid, d.cantidad AS cantidad, v.fecha AS fecha "
                        + "FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id WHERE v.tipo = 'VENTA'");
        for (int k = 0; k < ventas.size(); k++) {
            Map<String, Object> fila = ventas.get(k);
            LocalDate f = fechaDe(String.valueOf(fila.get("fecha")));
            if (f != null && !f.isBefore(inicio) && !f.isAfter(hoy)) {
                sumar(vendidasPorId, String.valueOf(fila.get("pid")), ((Number) fila.get("cantidad")).doubleValue());
            }
        }
        List<Map<String, Object>> refacciones = db.queryForList(
                "SELECT r.sku AS sku, r.cantidad AS cantidad, c.fecha_pago AS fecha_pago "
                        + "FROM cobro_refacciones r JOIN cobros c ON c.id = r.cobro_id");
        for (int k = 0; k < refacciones.size(); k++) {
            Map<String, Object> fila = refacciones.get(k);
            LocalDate f = Instant.ofEpochMilli(((Number) fila.get("fecha_pago")).longValue()).atZone(ZONA).toLocalDate();
            if (!f.isBefore(inicio) && !f.isAfter(hoy)) {
                sumar(vendidasPorSku, String.valueOf(fila.get("sku")), ((Number) fila.get("cantidad")).doubleValue());
            }
        }

        List<Map<String, Object>> productos = db.queryForList(
                "SELECT id, sku, nombre, stock, costo_compra, precio FROM productos");
        List<Map<String, Object>> rotacion = new ArrayList<>();
        for (int k = 0; k < productos.size(); k++) {
            Map<String, Object> p = productos.get(k);
            String id = String.valueOf(((Number) p.get("id")).longValue());
            String sku = String.valueOf(p.get("sku"));
            double vendidas = 0;
            if (vendidasPorId.get(id) != null) {
                vendidas = vendidas + vendidasPorId.get(id);
            }
            if (vendidasPorSku.get(sku) != null) {
                vendidas = vendidas + vendidasPorSku.get(sku);
            }
            int stock = ((Number) p.get("stock")).intValue();
            double indice = stock > 0 ? vendidas / stock : vendidas;
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", ((Number) p.get("id")).longValue());
            fila.put("sku", sku);
            fila.put("nombre", p.get("nombre"));
            fila.put("stock", stock);
            fila.put("vendidas", r(vendidas));
            fila.put("rotacion", r(indice));
            fila.put("costo", r(((Number) p.get("costo_compra")).doubleValue()));
            fila.put("precio", r(((Number) p.get("precio")).doubleValue()));
            rotacion.add(fila);
        }
        Collections.sort(rotacion, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                int c = Double.compare(((Number) a.get("rotacion")).doubleValue(), ((Number) b.get("rotacion")).doubleValue());
                if (c != 0) {
                    return c;
                }
                return Integer.compare(((Number) b.get("stock")).intValue(), ((Number) a.get("stock")).intValue());
            }
        });

        List<Map<String, Object>> filasProveedores = db.queryForList(
                "SELECT p.id AS id, p.nombre AS nombre, COUNT(pr.id) AS productos, "
                        + "COALESCE(AVG(pr.costo_compra), 0) AS costo "
                        + "FROM proveedores p LEFT JOIN productos pr ON pr.proveedor_id = p.id "
                        + "GROUP BY p.id, p.nombre ORDER BY p.nombre");
        List<Map<String, Object>> proveedores = new ArrayList<>();
        for (int k = 0; k < filasProveedores.size(); k++) {
            Map<String, Object> p = filasProveedores.get(k);
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", ((Number) p.get("id")).longValue());
            fila.put("nombre", p.get("nombre"));
            fila.put("productos", ((Number) p.get("productos")).longValue());
            fila.put("costoPromedio", r(((Number) p.get("costo")).doubleValue()));
            proveedores.add(fila);
        }

        List<Map<String, Object>> cobros = db.queryForList("SELECT fecha_pago, subtotal FROM cobros");
        int servicios = 0;
        double ingresos = 0;
        for (int k = 0; k < cobros.size(); k++) {
            Map<String, Object> fila = cobros.get(k);
            LocalDate f = Instant.ofEpochMilli(((Number) fila.get("fecha_pago")).longValue()).atZone(ZONA).toLocalDate();
            if (!f.isBefore(inicio) && !f.isAfter(hoy)) {
                servicios++;
                ingresos = ingresos + ((Number) fila.get("subtotal")).doubleValue();
            }
        }
        Integer activos = db.queryForObject("SELECT COUNT(*) FROM mecanicos WHERE activo = TRUE", Integer.class);
        Map<String, Object> resumenServicios = new LinkedHashMap<>();
        resumenServicios.put("total", servicios);
        resumenServicios.put("porMes", r(servicios * 30.0 / dias));
        resumenServicios.put("ingresoPromedio", r(servicios > 0 ? ingresos / servicios : 0));
        resumenServicios.put("mecanicosActivos", activos == null ? 0 : activos);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("dias", dias);
        respuesta.put("inicio", inicio.toString());
        respuesta.put("fin", hoy.toString());
        respuesta.put("atenciones", atenciones);
        respuesta.put("totalSemana", r(totalSemana));
        respuesta.put("rotacion", rotacion);
        respuesta.put("proveedores", proveedores);
        respuesta.put("servicios", resumenServicios);
        return respuesta;
    }

    @PostMapping("/calcular")
    public Map<String, Object> calcular(@RequestBody Map<String, Object> b) {
        String tipo = String.valueOf(b.get("tipo"));
        double[] escenarios = numeros(b, "escenarios");
        int m = escenarios.length;
        double[] probabilidades = numeros(b, "probabilidades");
        if (probabilidades.length != m) {
            throw invalido("Debe haber una probabilidad por cada escenario.");
        }
        double sumaProb = 0;
        for (int j = 0; j < m; j++) {
            sumaProb = sumaProb + probabilidades[j];
        }
        if (Math.abs(sumaProb - 1.0) > 0.001) {
            throw invalido("Las probabilidades deben sumar 1.");
        }
        double alfa = numero(b, "alfa", 0.5);
        if (alfa < 0 || alfa > 1) {
            throw invalido("El coeficiente alfa debe estar entre 0 y 1.");
        }
        String[] etiquetasEsc = textos(b, "nombres_escenarios", m, "Escenario");

        double[][] pagos = null;
        String[] etiquetasAlt = null;
        String tipoProblema = "";
        String descripcion = "";
        String unidad = "$";

        if (tipo.equals("piezas")) {
            Map<String, Object> producto = producto(b.get("producto_id"));
            double costo = ((Number) producto.get("costo_compra")).doubleValue();
            double precio = ((Number) producto.get("precio")).doubleValue();
            double[] cantidades = numeros(b, "alternativas");
            double rescate = numero(b, "rescate", 0);
            if (rescate > costo) {
                throw invalido("El valor de rescate no puede ser mayor al costo.");
            }
            int n = cantidades.length;
            etiquetasAlt = new String[n];
            pagos = new double[n][m];
            for (int i = 0; i < n; i++) {
                etiquetasAlt[i] = "Surtir " + cantidadTexto(cantidades[i]) + " pzas";
                for (int j = 0; j < m; j++) {
                    double vendidas = Math.min(cantidades[i], escenarios[j]);
                    double sobrantes = Math.max(cantidades[i] - escenarios[j], 0);
                    pagos[i][j] = precio * vendidas + rescate * sobrantes - costo * cantidades[i];
                }
            }
            tipoProblema = "Cuántas piezas surtir";
            descripcion = "Producto: [" + producto.get("sku") + "] " + producto.get("nombre")
                    + " | Último costo por pieza: $" + num(costo) + " | Precio de venta: $" + num(precio)
                    + ". Pago = precio × piezas vendidas + rescate × sobrantes − costo × piezas surtidas.";
        } else if (tipo.equals("proveedor")) {
            Map<String, Object> producto = producto(b.get("producto_id"));
            double precio = ((Number) producto.get("precio")).doubleValue();
            double[] pedidos = numeros(b, "alternativas");
            double[] costos = numeros(b, "costos");
            if (costos.length != pedidos.length) {
                throw invalido("Cada proveedor necesita su costo y su pedido.");
            }
            double rescate = numero(b, "rescate", 0);
            double costoMinimo = costos[indiceMenor(costos)];
            if (rescate > costoMinimo) {
                throw invalido("El valor de rescate no puede ser mayor al costo más bajo.");
            }
            int n = pedidos.length;
            etiquetasAlt = textos(b, "nombres_alternativas", n, "Proveedor");
            pagos = new double[n][m];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) {
                    double vendidas = Math.min(pedidos[i], escenarios[j]);
                    double sobrantes = Math.max(pedidos[i] - escenarios[j], 0);
                    pagos[i][j] = precio * vendidas + rescate * sobrantes - costos[i] * pedidos[i];
                }
            }
            tipoProblema = "Qué proveedor elegir";
            descripcion = "Producto: [" + producto.get("sku") + "] " + producto.get("nombre")
                    + " | Precio de venta: $" + num(precio)
                    + ". Pago = precio × piezas vendidas + rescate × sobrantes − costo del proveedor × piezas pedidas.";
        } else if (tipo.equals("mecanico")) {
            double ingreso = numero(b, "ingreso", 0);
            if (ingreso <= 0) {
                throw invalido("Captura el ingreso promedio por servicio.");
            }
            double[] capacidades = numeros(b, "alternativas");
            double[] sueldos = numeros(b, "costos");
            if (sueldos.length != capacidades.length) {
                throw invalido("Cada opción necesita su capacidad y su costo mensual.");
            }
            int n = capacidades.length;
            etiquetasAlt = textos(b, "nombres_alternativas", n, "Opción");
            pagos = new double[n][m];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) {
                    pagos[i][j] = ingreso * Math.min(capacidades[i], escenarios[j]) - sueldos[i];
                }
            }
            tipoProblema = "Contratar otro mecánico";
            descripcion = "Ingreso promedio por servicio: $" + num(ingreso)
                    + ". Pago mensual = ingreso × servicios atendidos (el menor entre capacidad y demanda) − costo mensual.";
        } else if (tipo.equals("descanso")) {
            double[] valores = numeros(b, "alternativas");
            for (int j = 0; j < m; j++) {
                if (escenarios[j] <= 0) {
                    throw invalido("El factor de afluencia debe ser mayor a cero.");
                }
            }
            double suma = 0;
            for (int i = 0; i < valores.length; i++) {
                suma = suma + valores[i];
            }
            double base = numero(b, "total_semana", suma);
            int n = valores.length;
            etiquetasAlt = textos(b, "nombres_alternativas", n, "Día");
            pagos = new double[n][m];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) {
                    pagos[i][j] = base - escenarios[j] * valores[i];
                }
            }
            unidad = "atenciones";
            tipoProblema = "Qué día descansar";
            descripcion = "Atenciones promedio por semana: " + num(base)
                    + ". Pago = atenciones que se conservan en la semana = total semanal − (factor de afluencia × atenciones promedio del día de descanso). Conviene descansar el día con más atenciones conservadas.";
        } else if (tipo.equals("promocion")) {
            double[] ids = numeros(b, "productos");
            double descuento = numero(b, "descuento", 0);
            double costoPromo = numero(b, "costo_promocion", 0);
            if (descuento < 0 || descuento > 100) {
                throw invalido("El descuento debe estar entre 0 y 100.");
            }
            for (int j = 0; j < m; j++) {
                if (escenarios[j] > 100) {
                    throw invalido("El porcentaje de stock vendido no puede pasar de 100.");
                }
            }
            int n = ids.length;
            etiquetasAlt = new String[n];
            pagos = new double[n][m];
            for (int i = 0; i < n; i++) {
                Map<String, Object> producto = producto(ids[i]);
                double costo = ((Number) producto.get("costo_compra")).doubleValue();
                double precio = ((Number) producto.get("precio")).doubleValue();
                int stock = ((Number) producto.get("stock")).intValue();
                etiquetasAlt[i] = "[" + producto.get("sku") + "] " + producto.get("nombre");
                double margen = precio * (1 - descuento / 100.0) - costo;
                for (int j = 0; j < m; j++) {
                    pagos[i][j] = margen * stock * (escenarios[j] / 100.0) - costoPromo;
                }
            }
            tipoProblema = "Qué productos promocionar";
            descripcion = "Descuento: " + num(descuento) + "% | Costo de la promoción: $" + num(costoPromo)
                    + ". Pago = (precio con descuento − costo) × piezas vendidas por la promoción − costo de la promoción.";
        } else if (tipo.equals("precio")) {
            Map<String, Object> producto = producto(b.get("producto_id"));
            double costo = numero(b, "costo", ((Number) producto.get("costo_compra")).doubleValue());
            if (costo < 0) {
                throw invalido("El costo no puede ser negativo.");
            }
            double actual = ((Number) producto.get("precio")).doubleValue();
            if (actual <= 0) {
                throw invalido("El producto necesita un precio actual mayor a cero.");
            }
            double base = numero(b, "demanda_base", 0);
            if (base <= 0) {
                throw invalido("Captura la demanda base mensual (piezas por mes).");
            }
            double elasticidad = numero(b, "elasticidad", -1.0);
            if (elasticidad > 0) {
                throw invalido("La elasticidad debe ser cero o negativa.");
            }
            double[] precios = numeros(b, "alternativas");
            int n = precios.length;
            etiquetasAlt = new String[n];
            pagos = new double[n][m];
            for (int i = 0; i < n; i++) {
                if (precios[i] <= 0) {
                    throw invalido("Cada precio debe ser mayor a cero.");
                }
                etiquetasAlt[i] = "Precio $" + num(precios[i]);
                double efecto = Math.pow(precios[i] / actual, elasticidad);
                for (int j = 0; j < m; j++) {
                    double piezas = base * escenarios[j] * efecto;
                    pagos[i][j] = (precios[i] - costo) * piezas;
                }
            }
            tipoProblema = "A qué precio vender";
            descripcion = "Producto: [" + producto.get("sku") + "] " + producto.get("nombre")
                    + " | Precio actual: $" + num(actual) + " | Costo por pieza: $" + num(costo)
                    + " | Demanda base: " + num(base) + " pzas/mes | Elasticidad (supuesto): " + num(elasticidad)
                    + ". Piezas = demanda base × factor del escenario × (precio / precio actual) ^ elasticidad. Pago mensual = (precio − costo) × piezas.";
        }
        if (pagos == null) {
            throw invalido("Tipo de problema no válido.");
        }

        int n = pagos.length;

        double[] maximoColumna = new double[m];
        for (int j = 0; j < m; j++) {
            maximoColumna[j] = pagos[0][j];
            for (int i = 1; i < n; i++) {
                if (pagos[i][j] > maximoColumna[j]) {
                    maximoColumna[j] = pagos[i][j];
                }
            }
        }

        double[][] arrepentimiento = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                arrepentimiento[i][j] = maximoColumna[j] - pagos[i][j];
            }
        }

        double[] laplace = new double[n];
        double[] wald = new double[n];
        double[] maximax = new double[n];
        double[] hurwicz = new double[n];
        double[] savage = new double[n];
        double[] esperado = new double[n];

        for (int i = 0; i < n; i++) {
            double suma = 0;
            double minimo = pagos[i][0];
            double maximo = pagos[i][0];
            double peorArrepentimiento = arrepentimiento[i][0];
            double valorEsperado = 0;
            for (int j = 0; j < m; j++) {
                suma = suma + pagos[i][j];
                if (pagos[i][j] < minimo) {
                    minimo = pagos[i][j];
                }
                if (pagos[i][j] > maximo) {
                    maximo = pagos[i][j];
                }
                if (arrepentimiento[i][j] > peorArrepentimiento) {
                    peorArrepentimiento = arrepentimiento[i][j];
                }
                valorEsperado = valorEsperado + probabilidades[j] * pagos[i][j];
            }
            laplace[i] = suma / m;
            wald[i] = minimo;
            maximax[i] = maximo;
            hurwicz[i] = alfa * maximo + (1 - alfa) * minimo;
            savage[i] = peorArrepentimiento;
            esperado[i] = valorEsperado;
        }

        double conInformacionPerfecta = 0;
        for (int j = 0; j < m; j++) {
            conInformacionPerfecta = conInformacionPerfecta + probabilidades[j] * maximoColumna[j];
        }
        int mejorVME = indiceMayor(esperado);
        double veip = conInformacionPerfecta - esperado[mejorVME];

        int iLaplace = indiceMayor(laplace);
        int iWald = indiceMayor(wald);
        int iMaximax = indiceMayor(maximax);
        int iHurwicz = indiceMayor(hurwicz);
        int iSavage = indiceMenor(savage);

        StringBuilder terminos = new StringBuilder();
        for (int j = 0; j < m; j++) {
            if (j > 0) {
                terminos.append(" + ");
            }
            terminos.append(num(probabilidades[j])).append(" × ").append(num(pagos[mejorVME][j]));
        }

        List<Map<String, Object>> criterios = new ArrayList<>();
        criterios.add(criterio("Laplace", "Incertidumbre: escenarios igualmente probables",
                "Promedio de la fila = (suma de pagos) / número de escenarios",
                "Se elige el mayor promedio", laplace, iLaplace,
                etiquetasAlt[iLaplace] + ": (" + unir(pagos[iLaplace], " + ") + ") / " + m + " = " + num(laplace[iLaplace])));
        criterios.add(criterio("Wald (maximin)", "Incertidumbre: criterio pesimista",
                "Mínimo de cada fila; se toma la alternativa con el mayor mínimo",
                "Se elige el mayor de los peores resultados", wald, iWald,
                etiquetasAlt[iWald] + ": mín(" + unir(pagos[iWald], "; ") + ") = " + num(wald[iWald])));
        criterios.add(criterio("Maximax", "Incertidumbre: criterio optimista",
                "Máximo de cada fila; se toma la alternativa con el mayor máximo",
                "Se elige el mayor de los mejores resultados", maximax, iMaximax,
                etiquetasAlt[iMaximax] + ": máx(" + unir(pagos[iMaximax], "; ") + ") = " + num(maximax[iMaximax])));
        criterios.add(criterio("Hurwicz (α = " + num(alfa) + ")", "Incertidumbre: criterio intermedio con coeficiente de optimismo",
                "α × máximo + (1 − α) × mínimo",
                "Se elige el mayor valor combinado", hurwicz, iHurwicz,
                etiquetasAlt[iHurwicz] + ": " + num(alfa) + " × " + num(maximax[iHurwicz]) + " + "
                        + num(1 - alfa) + " × " + num(wald[iHurwicz]) + " = " + num(hurwicz[iHurwicz])));
        criterios.add(criterio("Savage", "Incertidumbre: minimiza el arrepentimiento (minimax)",
                "Arrepentimiento = mejor pago del escenario − pago; se toma el máximo de cada fila",
                "Se elige el menor arrepentimiento máximo", savage, iSavage,
                etiquetasAlt[iSavage] + ": máx(" + unir(arrepentimiento[iSavage], "; ") + ") = " + num(savage[iSavage])));
        criterios.add(criterio("VME", "Riesgo: probabilidades conocidas",
                "Valor monetario esperado = suma de (probabilidad × pago)",
                "Se elige el mayor valor esperado", esperado, mejorVME,
                etiquetasAlt[mejorVME] + ": " + terminos + " = " + num(esperado[mejorVME])));

        List<List<Double>> matrizPagos = new ArrayList<>();
        List<List<Double>> matrizArrepentimiento = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            matrizPagos.add(lista(pagos[i]));
            matrizArrepentimiento.add(lista(arrepentimiento[i]));
        }

        List<String> listaAlt = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            listaAlt.add(etiquetasAlt[i]);
        }
        List<String> listaEsc = new ArrayList<>();
        for (int j = 0; j < m; j++) {
            listaEsc.add(etiquetasEsc[j]);
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("tipo", tipo);
        resultado.put("tipoProblema", tipoProblema);
        resultado.put("descripcion", descripcion);
        resultado.put("unidad", unidad);
        resultado.put("etiquetasAlternativas", listaAlt);
        resultado.put("etiquetasEscenarios", listaEsc);
        resultado.put("escenarios", lista(escenarios));
        resultado.put("probabilidades", lista(probabilidades));
        resultado.put("pagos", matrizPagos);
        resultado.put("arrepentimientos", matrizArrepentimiento);
        resultado.put("criterios", criterios);
        resultado.put("valorConInformacionPerfecta", r(conInformacionPerfecta));
        resultado.put("mejorVME", r(esperado[mejorVME]));
        resultado.put("veip", r(veip));
        resultado.put("formulaVeip", "VEIP = valor esperado con información perfecta − mejor VME");
        return resultado;
    }
}