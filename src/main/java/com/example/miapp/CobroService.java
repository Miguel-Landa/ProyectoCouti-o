package com.example.miapp;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class CobroService {
    private static final BigDecimal IVA = new BigDecimal("0.16");
    private static final BigDecimal CERO = new BigDecimal("0.00");

    private final JdbcTemplate jdbcTemplate;

    public CobroService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public CobroResponse registrar(CobroRequest solicitud) {
        validarTexto(solicitud.folio(), "folio", 100);
        validarTexto(solicitud.cliente(), "cliente", 200);
        validarTexto(solicitud.motocicleta(), "motocicleta", 200);
        validarTexto(solicitud.placas(), "placas", 50);
        validarTexto(solicitud.servicioSolicitado(), "servicio solicitado", 500);
        validarTexto(solicitud.servicioRealizado(), "servicio realizado", 500);
        validarTexto(solicitud.mecanico(), "mecánico", 200);
        Integer cobrosExistentes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cobros WHERE folio = ?",
                Integer.class,
                solicitud.folio().trim()
        );
        if (cobrosExistentes != null && cobrosExistentes > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un cobro registrado para este folio."
            );
        }

        BigDecimal manoObra = validarImporte(solicitud.manoObra(), "mano de obra", true);
        BigDecimal precioExtra = solicitud.servicioExtraPrecio() == null
                ? CERO
                : validarImporte(solicitud.servicioExtraPrecio(), "servicio extra", true);
        String descripcionExtra = solicitud.servicioExtraDescripcion() == null
                ? ""
                : solicitud.servicioExtraDescripcion().trim();
        if (precioExtra.signum() > 0 && descripcionExtra.isBlank()) {
            throw solicitudInvalida("La descripción del servicio extra es obligatoria.");
        }
        if (descripcionExtra.length() > 500) {
            throw solicitudInvalida("La descripción del servicio extra supera los 500 caracteres.");
        }

        List<CobroResponse.Refaccion> refacciones = (solicitud.refacciones() == null
                ? List.<CobroRequest.Refaccion>of()
                : solicitud.refacciones()).stream().map(this::validarRefaccion).toList();

        BigDecimal subtotalRefacciones = refacciones.stream()
                .map(CobroResponse.Refaccion::importe)
                .reduce(CERO, BigDecimal::add);
        BigDecimal subtotal = dinero(manoObra.add(precioExtra).add(subtotalRefacciones));
        BigDecimal iva = dinero(subtotal.multiply(IVA));
        BigDecimal total = dinero(subtotal.add(iva));
        String id = UUID.randomUUID().toString();
        long fechaPago = System.currentTimeMillis();

        jdbcTemplate.update("""
                INSERT INTO cobros (
                    id, folio, cliente, motocicleta, placas, servicio_solicitado,
                    servicio_realizado, mecanico, fecha_pago, mano_obra,
                    servicio_extra_descripcion, servicio_extra_precio, subtotal, iva, total
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, solicitud.folio().trim(), solicitud.cliente().trim(),
                solicitud.motocicleta().trim(), solicitud.placas().trim(),
                solicitud.servicioSolicitado().trim(), solicitud.servicioRealizado().trim(),
                solicitud.mecanico().trim(), fechaPago, manoObra, descripcionExtra,
                precioExtra, subtotal, iva, total
        );

        for (CobroResponse.Refaccion refaccion : refacciones) {
            jdbcTemplate.update("""
                    INSERT INTO cobro_refacciones (
                        cobro_id, sku, nombre, precio_unitario, cantidad, importe
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    id, refaccion.sku(), refaccion.nombre(),
                    refaccion.precioUnitario(), refaccion.cantidad(), refaccion.importe()
            );
        }

        return new CobroResponse(
                id, solicitud.folio().trim(), solicitud.cliente().trim(),
                solicitud.motocicleta().trim(), solicitud.placas().trim(),
                solicitud.servicioSolicitado().trim(), solicitud.servicioRealizado().trim(),
                solicitud.mecanico().trim(), fechaPago, manoObra, descripcionExtra,
                precioExtra, subtotal, iva, total, refacciones
        );
    }

    public List<CobroResponse> listar() {
        return jdbcTemplate.query("""
                SELECT id, folio, cliente, motocicleta, placas, servicio_solicitado,
                       servicio_realizado, mecanico, fecha_pago, mano_obra,
                       servicio_extra_descripcion, servicio_extra_precio, subtotal, iva, total
                FROM cobros
                ORDER BY fecha_pago DESC, id DESC
                """, (rs, rowNum) -> {
            String id = rs.getString("id");
            List<CobroResponse.Refaccion> refacciones = jdbcTemplate.query("""
                    SELECT sku, nombre, precio_unitario, cantidad, importe
                    FROM cobro_refacciones
                    WHERE cobro_id = ?
                    ORDER BY id
                    """, (itemRs, itemRowNum) -> new CobroResponse.Refaccion(
                    itemRs.getString("sku"),
                    itemRs.getString("nombre"),
                    itemRs.getBigDecimal("precio_unitario"),
                    itemRs.getInt("cantidad"),
                    itemRs.getBigDecimal("importe")
            ), id);
            return new CobroResponse(
                    id, rs.getString("folio"), rs.getString("cliente"),
                    rs.getString("motocicleta"), rs.getString("placas"),
                    rs.getString("servicio_solicitado"), rs.getString("servicio_realizado"),
                    rs.getString("mecanico"), rs.getLong("fecha_pago"),
                    rs.getBigDecimal("mano_obra"), rs.getString("servicio_extra_descripcion"),
                    rs.getBigDecimal("servicio_extra_precio"), rs.getBigDecimal("subtotal"),
                    rs.getBigDecimal("iva"), rs.getBigDecimal("total"), refacciones
            );
        });
    }

    private CobroResponse.Refaccion validarRefaccion(CobroRequest.Refaccion refaccion) {
        if (refaccion == null) {
            throw solicitudInvalida("Se recibió una refacción vacía.");
        }
        validarTexto(refaccion.sku(), "SKU de refacción", 100);
        validarTexto(refaccion.nombre(), "nombre de refacción", 300);
        if (refaccion.cantidad() == null || refaccion.cantidad() < 1) {
            throw solicitudInvalida("La cantidad de cada refacción debe ser mayor que cero.");
        }
        BigDecimal precio = validarImporte(refaccion.precioUnitario(), "precio de refacción", true);
        BigDecimal importe = dinero(precio.multiply(BigDecimal.valueOf(refaccion.cantidad())));
        return new CobroResponse.Refaccion(
                refaccion.sku().trim(), refaccion.nombre().trim(),
                precio, refaccion.cantidad(), importe
        );
    }

    private BigDecimal validarImporte(BigDecimal importe, String campo, boolean permitirCero) {
        if (importe == null || importe.scale() > 2 || importe.signum() < 0
                || (!permitirCero && importe.signum() == 0)) {
            throw solicitudInvalida("El importe de " + campo + " debe ser válido y tener máximo dos decimales.");
        }
        return dinero(importe);
    }

    private void validarTexto(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank() || valor.trim().length() > maximo) {
            throw solicitudInvalida("El campo " + campo + " es obligatorio y admite hasta " + maximo + " caracteres.");
        }
    }

    private BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private ResponseStatusException solicitudInvalida(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
