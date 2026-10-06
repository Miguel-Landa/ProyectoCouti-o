package com.example.miapp;

import java.math.BigDecimal;
import java.util.List;

public record CobroResponse(
        String id,
        String folio,
        String cliente,
        String motocicleta,
        String placas,
        String servicioSolicitado,
        String servicioRealizado,
        String mecanico,
        long fechaPago,
        BigDecimal manoObra,
        String servicioExtraDescripcion,
        BigDecimal servicioExtraPrecio,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal total,
        List<Refaccion> refacciones
) {
    public record Refaccion(
            String sku,
            String nombre,
            BigDecimal precioUnitario,
            int cantidad,
            BigDecimal importe
    ) {}
}
