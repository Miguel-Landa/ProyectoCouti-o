package com.example.miapp;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final JdbcTemplate db;

    public ApiController(JdbcTemplate db) {
        this.db = db;
    }

    // ---------- helpers ----------
    private String s(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString().trim();
    }

    private String req(Map<String, Object> m, String k) {
        String v = s(m, k);
        if (v == null || v.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el campo: " + k);
        }
        return v;
    }

    private BigDecimal n(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null || v.toString().isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Número inválido en: " + k);
        }
    }

    private int i(Map<String, Object> m, String k) {
        return n(m, k).intValue();
    }

    private Long provId(Map<String, Object> b) {
        Object prov = b.get("proveedor_id");
        return (prov == null || prov.toString().isBlank()) ? null : Long.valueOf(prov.toString());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lista(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? List.of() : (List<Map<String, Object>>) v;
    }

    // ---------- PROVEEDORES ----------
    @GetMapping("/proveedores")
    public List<Map<String, Object>> proveedores() {
        return db.queryForList("SELECT * FROM proveedores ORDER BY nombre");
    }

    @PostMapping("/proveedores")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crearProveedor(@RequestBody Map<String, Object> b) {
        try {
            return db.queryForMap(
                    "INSERT INTO proveedores (nombre, rfc, telefono, email, direccion) "
                            + "VALUES (?, ?, ?, ?, ?) RETURNING *",
                    req(b, "nombre"), s(b, "rfc"), s(b, "telefono"), s(b, "email"), s(b, "direccion"));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un proveedor con ese RFC");
        }
    }

    @DeleteMapping("/proveedores/{id}")
    public void borrarProveedor(@PathVariable long id) {
        db.update("DELETE FROM proveedores WHERE id = ?", id);
    }

    // ---------- PRODUCTOS ----------
    @GetMapping("/productos")
    public List<Map<String, Object>> productos() {
        return db.queryForList("SELECT * FROM productos ORDER BY nombre");
    }

    @PostMapping("/productos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crearProducto(@RequestBody Map<String, Object> b) {
        try {
            return db.queryForMap(
                    "INSERT INTO productos (sku, nombre, costo_compra, precio, stock, proveedor_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?) RETURNING *",
                    req(b, "sku"), req(b, "nombre"), n(b, "costo_compra"), n(b, "precio"), i(b, "stock"), provId(b));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un producto con ese SKU");
        }
    }

    @PutMapping("/productos/{id}")
    public Map<String, Object> editarProducto(@PathVariable long id, @RequestBody Map<String, Object> b) {
        List<Map<String, Object>> r = db.queryForList(
                "UPDATE productos SET sku = ?, nombre = ?, costo_compra = ?, precio = ?, stock = ?, proveedor_id = ? "
                        + "WHERE id = ? RETURNING *",
                req(b, "sku"), req(b, "nombre"), n(b, "costo_compra"), n(b, "precio"), i(b, "stock"), provId(b), id);
        if (r.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        return r.get(0);
    }

    @DeleteMapping("/productos/{id}")
    public void borrarProducto(@PathVariable long id) {
        db.update("DELETE FROM productos WHERE id = ?", id);
    }

    // ---------- VENTAS / COTIZACIONES ----------
    @GetMapping("/ventas")
    public List<Map<String, Object>> ventas() {
        List<Map<String, Object>> ventas = db.queryForList("SELECT * FROM ventas ORDER BY id DESC");
        for (Map<String, Object> v : ventas) {
            v.put("detalle", db.queryForList("SELECT * FROM venta_detalle WHERE venta_id = ? ORDER BY id", v.get("id")));
        }
        return ventas;
    }

    @PostMapping("/ventas")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> crearVenta(@RequestBody Map<String, Object> b) {
        String tipo = req(b, "tipo");
        Integer folio = db.queryForObject("SELECT COALESCE(MAX(folio_numero), 0) + 1 FROM ventas", Integer.class);
        Map<String, Object> venta = db.queryForMap(
                "INSERT INTO ventas (folio_numero, cliente, tipo, pago, fecha, subtotal, iva, total) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING *",
                folio, req(b, "cliente"), tipo, req(b, "pago"), req(b, "fecha"),
                n(b, "subtotal"), n(b, "iva"), n(b, "total"));
        Object ventaId = venta.get("id");

        for (Map<String, Object> d : lista(b, "detalle")) {
            db.update(
                    "INSERT INTO venta_detalle (venta_id, producto_id, producto, cantidad, precio, importe) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    ventaId, s(d, "producto_id"), req(d, "producto"), i(d, "cantidad"), n(d, "precio"), n(d, "importe"));
            // Solo las ventas (no las cotizaciones) descuentan inventario
            String pid = s(d, "producto_id");
            if ("venta".equalsIgnoreCase(tipo) && pid != null && pid.matches("\\d+")) {
                db.update("UPDATE productos SET stock = stock - ? WHERE id = ?",
                        i(d, "cantidad"), Long.valueOf(pid));
            }
        }
        return venta;
    }

    // ---------- COMPRAS ----------
    @GetMapping("/compras")
    public List<Map<String, Object>> compras() {
        List<Map<String, Object>> compras = db.queryForList("SELECT * FROM compras ORDER BY id DESC");
        for (Map<String, Object> c : compras) {
            c.put("detalle", db.queryForList("SELECT * FROM compra_detalle WHERE compra_id = ? ORDER BY id", c.get("id")));
        }
        return compras;
    }

    @PostMapping("/compras")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> crearCompra(@RequestBody Map<String, Object> b) {
        Map<String, Object> compra;
        try {
            compra = db.queryForMap(
                    "INSERT INTO compras (factura, proveedor, fecha, subtotal, iva, total) "
                            + "VALUES (?, ?, ?, ?, ?, ?) RETURNING *",
                    req(b, "factura"), req(b, "proveedor"), req(b, "fecha"),
                    n(b, "subtotal"), n(b, "iva"), n(b, "total"));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una compra con esa factura");
        }
        Object compraId = compra.get("id");

        for (Map<String, Object> d : lista(b, "detalle")) {
            db.update(
                    "INSERT INTO compra_detalle (compra_id, producto_id, sku, nombre, costo, cantidad, importe) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    compraId, s(d, "producto_id"), req(d, "sku"), req(d, "nombre"),
                    n(d, "costo"), i(d, "cantidad"), n(d, "importe"));
            // La compra aumenta el inventario y actualiza el costo
            db.update("UPDATE productos SET stock = stock + ?, costo_compra = ? WHERE sku = ?",
                    i(d, "cantidad"), n(d, "costo"), req(d, "sku"));
        }
        return compra;
    }

    // ---------- CITAS ----------
    @GetMapping("/citas")
    public List<Map<String, Object>> citas() {
        return db.queryForList("SELECT * FROM citas ORDER BY id DESC");
    }

    @PostMapping("/citas")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crearCita(@RequestBody Map<String, Object> b) {
        String modalidad = s(b, "modalidad") == null ? "Cita Previa" : s(b, "modalidad");
        try {
            return db.queryForMap(
                    "INSERT INTO citas (folio, cliente, telefono, correo, mecanico, moto, placas, servicio, modalidad, fecha_hora) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *",
                    req(b, "folio"), req(b, "cliente"), s(b, "telefono"), s(b, "correo"), s(b, "mecanico"),
                    req(b, "moto"), req(b, "placas"), req(b, "servicio"), modalidad, req(b, "fecha_hora"));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cita con ese folio");
        }
    }

    @DeleteMapping("/citas/{id}")
    public void borrarCita(@PathVariable long id) {
        db.update("DELETE FROM citas WHERE id = ?", id);
    }
}