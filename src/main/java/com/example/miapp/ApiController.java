package com.example.miapp;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        if (v == null) return null;
        String t = v.toString().trim();
        return t.isEmpty() ? null : t;
    }

    private String req(Map<String, Object> m, String k) {
        String v = s(m, k);
        if (v == null) {
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

    private boolean activoDe(Map<String, Object> b) {
        Object v = b.get("activo");
        return v == null || Boolean.parseBoolean(v.toString());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lista(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? List.of() : (List<Map<String, Object>>) v;
    }

    private String siguienteFolio() {
        Integer sig = db.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(folio FROM 6) AS INTEGER)), 104) + 1 "
                        + "FROM citas WHERE folio ~ '^CITA-[0-9]+$'", Integer.class);
        return "CITA-" + sig;
    }

    // ================= PROVEEDORES =================
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

    @PutMapping("/proveedores/{id}")
    public Map<String, Object> editarProveedor(@PathVariable long id, @RequestBody Map<String, Object> b) {
        try {
            List<Map<String, Object>> r = db.queryForList(
                    "UPDATE proveedores SET nombre = ?, rfc = ?, telefono = ?, email = ?, direccion = ? "
                            + "WHERE id = ? RETURNING *",
                    req(b, "nombre"), s(b, "rfc"), s(b, "telefono"), s(b, "email"), s(b, "direccion"), id);
            if (r.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado");
            return r.get(0);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un proveedor con ese RFC");
        }
    }

    @DeleteMapping("/proveedores/{id}")
    public void borrarProveedor(@PathVariable long id) {
        db.update("DELETE FROM proveedores WHERE id = ?", id);
    }

    // ================= PRODUCTOS =================
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
        try {
            List<Map<String, Object>> r = db.queryForList(
                    "UPDATE productos SET sku = ?, nombre = ?, costo_compra = ?, precio = ?, stock = ?, proveedor_id = ? "
                            + "WHERE id = ? RETURNING *",
                    req(b, "sku"), req(b, "nombre"), n(b, "costo_compra"), n(b, "precio"), i(b, "stock"), provId(b), id);
            if (r.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
            return r.get(0);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un producto con ese SKU");
        }
    }

    @DeleteMapping("/productos/{id}")
    public void borrarProducto(@PathVariable long id) {
        db.update("DELETE FROM productos WHERE id = ?", id);
    }

    // ================= VENTAS Y COTIZACIONES =================
    // tipo = 'VENTA' o 'COTIZACION'
    @GetMapping("/ventas")
    public List<Map<String, Object>> ventas() {
        List<Map<String, Object>> ventas = db.queryForList("SELECT * FROM ventas ORDER BY id DESC");
        for (Map<String, Object> v : ventas) {
            v.put("detalle", db.queryForList(
                    "SELECT * FROM venta_detalle WHERE venta_id = ? ORDER BY id", v.get("id")));
        }
        return ventas;
    }

    @PostMapping("/ventas")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> crearVenta(@RequestBody Map<String, Object> b) {
        String tipo = req(b, "tipo").toUpperCase();
        if (!tipo.equals("VENTA") && !tipo.equals("COTIZACION")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo inválido");
        }
        List<Map<String, Object>> detalle = lista(b, "detalle");
        if (detalle.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agrega al menos un producto.");
        }

        Integer folio = db.queryForObject(
                "SELECT COALESCE(MAX(folio_numero), 0) + 1 FROM ventas", Integer.class);
        Map<String, Object> venta = db.queryForMap(
                "INSERT INTO ventas (folio_numero, cliente, tipo, pago, fecha, subtotal, iva, total) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING *",
                folio, req(b, "cliente"), tipo, req(b, "pago"), req(b, "fecha"),
                n(b, "subtotal"), n(b, "iva"), n(b, "total"));
        Object ventaId = venta.get("id");

        for (Map<String, Object> d : detalle) {
            String pid = s(d, "producto_id");
            db.update(
                    "INSERT INTO venta_detalle (venta_id, producto_id, producto, cantidad, precio, importe) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    ventaId, pid, req(d, "producto"), i(d, "cantidad"), n(d, "precio"), n(d, "importe"));

            // Solo las ventas descuentan inventario (las cotizaciones no)
            if (tipo.equals("VENTA") && pid != null && pid.matches("\\d+")) {
                int cant = i(d, "cantidad");
                int filas = db.update(
                        "UPDATE productos SET stock = stock - ? WHERE id = ? AND stock >= ?",
                        cant, Long.valueOf(pid), cant);
                if (filas == 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Stock insuficiente para: " + req(d, "producto"));
                }
            }
        }

        // Si la venta viene de convertir una cotización, esa cotización se elimina
        String cot = s(b, "cotizacion_id");
        if (cot != null && cot.matches("\\d+")) {
            db.update("DELETE FROM ventas WHERE id = ? AND tipo = 'COTIZACION'", Long.valueOf(cot));
        }
        return venta;
    }

    @DeleteMapping("/ventas/{id}")
    public void borrarVenta(@PathVariable long id) {
        db.update("DELETE FROM ventas WHERE id = ?", id);
    }

    // ================= COMPRAS =================
    @GetMapping("/compras")
    public List<Map<String, Object>> compras() {
        List<Map<String, Object>> compras = db.queryForList("SELECT * FROM compras ORDER BY id DESC");
        for (Map<String, Object> c : compras) {
            c.put("detalle", db.queryForList(
                    "SELECT * FROM compra_detalle WHERE compra_id = ? ORDER BY id", c.get("id")));
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
            db.update("UPDATE productos SET stock = stock + ?, costo_compra = ? WHERE sku = ?",
                    i(d, "cantidad"), n(d, "costo"), req(d, "sku"));
        }
        return compra;
    }

    @DeleteMapping("/compras/{id}")
    @Transactional
    public void borrarCompra(@PathVariable long id) {
        List<Map<String, Object>> det = db.queryForList(
                "SELECT sku, cantidad FROM compra_detalle WHERE compra_id = ?", id);
        for (Map<String, Object> d : det) {
            db.update("UPDATE productos SET stock = GREATEST(0, stock - ?) WHERE sku = ?",
                    ((Number) d.get("cantidad")).intValue(), d.get("sku"));
        }
        db.update("DELETE FROM compras WHERE id = ?", id);
    }

    // ================= CITAS =================
    @GetMapping("/citas")
    public List<Map<String, Object>> citas() {
        return db.queryForList("SELECT * FROM citas ORDER BY id DESC");
    }

    @GetMapping("/citas/siguiente-folio")
    public Map<String, Object> siguienteFolioCita() {
        return Map.of("folio", siguienteFolio());
    }

    // Público para la pantalla del cliente: solo devuelve fechas ocupadas, sin datos personales
    @GetMapping("/citas/horarios")
    public List<String> horariosOcupados() {
        return db.queryForList("SELECT fecha_hora FROM citas WHERE modalidad = 'Cita Previa'", String.class);
    }

    @GetMapping("/citas/folio/{folio}")
    public Map<String, Object> citaPorFolio(@PathVariable String folio) {
        List<Map<String, Object>> r = db.queryForList(
                "SELECT folio, cliente, moto, placas, servicio, modalidad, fecha_hora FROM citas WHERE folio = ?",
                folio.trim().toUpperCase());
        if (r.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada");
        }
        return r.get(0);
    }

    @PostMapping("/citas")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crearCita(@RequestBody Map<String, Object> b) {
        String modalidad = s(b, "modalidad") == null ? "Cita Previa" : s(b, "modalidad");
        String fechaHora = req(b, "fecha_hora");

        if (modalidad.equals("Cita Previa")) {
            Integer ocupadas = db.queryForObject(
                    "SELECT COUNT(*) FROM citas WHERE fecha_hora = ? AND modalidad = 'Cita Previa'",
                    Integer.class, fechaHora);
            if (ocupadas != null && ocupadas > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese horario ya está ocupado.");
            }
        }

        String folio = s(b, "folio") != null ? s(b, "folio") : siguienteFolio();
        try {
            return db.queryForMap(
                    "INSERT INTO citas (folio, cliente, telefono, correo, mecanico, moto, placas, servicio, modalidad, fecha_hora) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *",
                    folio, req(b, "cliente"), s(b, "telefono"), s(b, "correo"), s(b, "mecanico"),
                    req(b, "moto"), req(b, "placas"), req(b, "servicio"), modalidad, fechaHora);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cita con ese folio");
        }
    }

    @PutMapping("/citas/folio/{folio}")
    public Map<String, Object> reagendarCita(@PathVariable String folio, @RequestBody Map<String, Object> b) {
        String nueva = req(b, "fecha_hora");
        Integer ocupadas = db.queryForObject(
                "SELECT COUNT(*) FROM citas WHERE fecha_hora = ? AND folio <> ? AND modalidad = 'Cita Previa'",
                Integer.class, nueva, folio);
        if (ocupadas != null && ocupadas > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese horario ya está ocupado.");
        }
        List<Map<String, Object>> r = db.queryForList(
                "UPDATE citas SET fecha_hora = ?, servicio = ? WHERE folio = ? RETURNING *",
                nueva, req(b, "servicio"), folio);
        if (r.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada");
        }
        return r.get(0);
    }

    // Orden de servicio del mecánico: actualiza la cita si existe, o crea un registro directo (sin cita)
    @PostMapping("/citas/orden")
    public Map<String, Object> guardarOrden(@RequestBody Map<String, Object> b) {
        String folio = req(b, "folio");
        Integer km = s(b, "kilometraje") == null ? null : i(b, "kilometraje");

        List<Map<String, Object>> existe = db.queryForList("SELECT id FROM citas WHERE folio = ?", folio);
        if (!existe.isEmpty()) {
            return db.queryForMap(
                    "UPDATE citas SET cliente = ?, placas = ?, servicio = ?, modalidad = ?, "
                            + "kilometraje = ?, diagnostico = ?, detalle_orden = ? WHERE folio = ? RETURNING *",
                    req(b, "cliente"), req(b, "placas").toUpperCase(), req(b, "servicio"), req(b, "modalidad"),
                    km, s(b, "diagnostico"), s(b, "detalle_orden"), folio);
        }
        String ahora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String moto = s(b, "moto") == null ? "Motocicleta ingresada" : s(b, "moto");
        return db.queryForMap(
                "INSERT INTO citas (folio, cliente, moto, placas, servicio, modalidad, fecha_hora, "
                        + "kilometraje, diagnostico, detalle_orden) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *",
                folio, req(b, "cliente"), moto, req(b, "placas").toUpperCase(), req(b, "servicio"),
                req(b, "modalidad"), ahora, km, s(b, "diagnostico"), s(b, "detalle_orden"));
    }

    @DeleteMapping("/citas/{id}")
    public void borrarCita(@PathVariable long id) {
        db.update("DELETE FROM citas WHERE id = ?", id);
    }

    // ================= MECANICOS =================
    @GetMapping("/mecanicos")
    public List<Map<String, Object>> mecanicos() {
        return db.queryForList("SELECT * FROM mecanicos ORDER BY nombre");
    }

    @PostMapping("/mecanicos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crearMecanico(@RequestBody Map<String, Object> b) {
        try {
            return db.queryForMap(
                    "INSERT INTO mecanicos (nombre, telefono, email, especialidad, fecha_ingreso, activo, notas) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING *",
                    req(b, "nombre"), s(b, "telefono"), s(b, "email"), s(b, "especialidad"),
                    s(b, "fecha_ingreso"), activoDe(b), s(b, "notas"));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un mecánico con ese nombre");
        }
    }

    @PutMapping("/mecanicos/{id}")
    public Map<String, Object> editarMecanico(@PathVariable long id, @RequestBody Map<String, Object> b) {
        try {
            List<Map<String, Object>> r = db.queryForList(
                    "UPDATE mecanicos SET nombre = ?, telefono = ?, email = ?, especialidad = ?, "
                            + "fecha_ingreso = ?, activo = ?, notas = ? WHERE id = ? RETURNING *",
                    req(b, "nombre"), s(b, "telefono"), s(b, "email"), s(b, "especialidad"),
                    s(b, "fecha_ingreso"), activoDe(b), s(b, "notas"), id);
            if (r.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mecánico no encontrado");
            return r.get(0);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un mecánico con ese nombre");
        }
    }

    @DeleteMapping("/mecanicos/{id}")
    public void borrarMecanico(@PathVariable long id) {
        db.update("DELETE FROM mecanicos WHERE id = ?", id);
    }
}

