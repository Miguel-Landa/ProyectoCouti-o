package com.example.miapp;

import java.math.BigDecimal;
import java.util.List;

public record CobroRequest(
        String folio,
        String cliente,
        String motocicleta,
        String placas,
        String servicioSolicitado,
        String servicioRealizado,
        String mecanico,
        BigDecimal manoObra,
        String servicioExtraDescripcion,
        BigDecimal servicioExtraPrecio,
        List<Refaccion> refacciones
) {
    public record Refaccion(
            String sku,
            String nombre,
            BigDecimal precioUnitario,
            Integer cantidad
    ) {}
}
