package com.example.miapp;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/decisiones")
public class DecisionController {

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

    private double[] numeros(Map<String, Object> b, String clave) {
        Object valor = b.get(clave);
        if (!(valor instanceof List)) {
            throw invalido("Falta la lista: " + clave);
        }
        List<?> lista = (List<?>) valor;
        if (lista.size() < 2 || lista.size() > 6) {
            throw invalido("La lista " + clave + " debe tener entre 2 y 6 valores.");
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

    private List<Double> lista(double[] valores) {
        List<Double> salida = new ArrayList<>();
        for (int k = 0; k < valores.length; k++) {
            salida.add(r(valores[k]));
        }
        return salida;
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

    private Map<String, Object> criterio(String nombre, String regla, double[] valores, boolean maximizar) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("nombre", nombre);
        c.put("regla", regla);
        c.put("valores", lista(valores));
        c.put("mejor", maximizar ? indiceMayor(valores) : indiceMenor(valores));
        return c;
    }

    @PostMapping("/calcular")
    public Map<String, Object> calcular(@RequestBody Map<String, Object> b) {
        long productoId;
        try {
            productoId = Long.parseLong(String.valueOf(b.get("producto_id")));
        } catch (NumberFormatException e) {
            throw invalido("Selecciona un producto.");
        }

        List<Map<String, Object>> filas = db.queryForList(
                "SELECT sku, nombre, costo_compra, precio FROM productos WHERE id = ?", productoId);
        if (filas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        }
        Map<String, Object> producto = filas.get(0);
        double costo = Double.parseDouble(producto.get("costo_compra").toString());
        double precio = Double.parseDouble(producto.get("precio").toString());

        double[] alternativas = numeros(b, "alternativas");
        double[] escenarios = numeros(b, "escenarios");
        double[] probabilidades = numeros(b, "probabilidades");
        double rescate = numero(b, "rescate", 0);
        double alfa = numero(b, "alfa", 0.5);

        if (probabilidades.length != escenarios.length) {
            throw invalido("Debe haber una probabilidad por cada escenario.");
        }
        double sumaProb = 0;
        for (int j = 0; j < probabilidades.length; j++) {
            sumaProb = sumaProb + probabilidades[j];
        }
        if (Math.abs(sumaProb - 1.0) > 0.001) {
            throw invalido("Las probabilidades deben sumar 1.");
        }
        if (alfa < 0 || alfa > 1) {
            throw invalido("El coeficiente alfa debe estar entre 0 y 1.");
        }
        if (rescate > costo) {
            throw invalido("El valor de rescate no puede ser mayor al costo.");
        }

        int n = alternativas.length;
        int m = escenarios.length;

        double[][] pagos = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double vendidas = Math.min(alternativas[i], escenarios[j]);
                double sobrantes = Math.max(alternativas[i] - escenarios[j], 0);
                pagos[i][j] = precio * vendidas + rescate * sobrantes - costo * alternativas[i];
            }
        }

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
        double mejorEsperado = esperado[indiceMayor(esperado)];
        double veip = conInformacionPerfecta - mejorEsperado;

        List<List<Double>> matrizPagos = new ArrayList<>();
        List<List<Double>> matrizArrepentimiento = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            matrizPagos.add(lista(pagos[i]));
            matrizArrepentimiento.add(lista(arrepentimiento[i]));
        }

        List<Map<String, Object>> criterios = new ArrayList<>();
        criterios.add(criterio("Laplace", "Mayor promedio", laplace, true));
        criterios.add(criterio("Wald (maximin)", "Mayor de los peores resultados", wald, true));
        criterios.add(criterio("Maximax", "Mayor de los mejores resultados", maximax, true));
        criterios.add(criterio("Hurwicz (α = " + alfa + ")", "Mayor combinación optimista y pesimista", hurwicz, true));
        criterios.add(criterio("Savage", "Menor arrepentimiento máximo", savage, false));
        criterios.add(criterio("VME", "Mayor valor monetario esperado", esperado, true));

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("producto", producto.get("nombre"));
        resultado.put("sku", producto.get("sku"));
        resultado.put("costo", r(costo));
        resultado.put("precio", r(precio));
        resultado.put("alternativas", lista(alternativas));
        resultado.put("escenarios", lista(escenarios));
        resultado.put("pagos", matrizPagos);
        resultado.put("arrepentimientos", matrizArrepentimiento);
        resultado.put("criterios", criterios);
        resultado.put("valorConInformacionPerfecta", r(conInformacionPerfecta));
        resultado.put("mejorVME", r(mejorEsperado));
        resultado.put("veip", r(veip));
        return resultado;
    }
}
