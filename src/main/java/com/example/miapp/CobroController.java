package com.example.miapp;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cobros")
public class CobroController {
    private final CobroService servicio;

    public CobroController(CobroService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CobroResponse registrar(@RequestBody CobroRequest solicitud) {
        return servicio.registrar(solicitud);
    }

    @GetMapping
    public List<CobroResponse> listar() {
        return servicio.listar();
    }
}