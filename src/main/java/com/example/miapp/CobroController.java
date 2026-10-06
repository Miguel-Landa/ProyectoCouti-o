package com.example.miapp;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cobros")
public class CobroController {
    private final CobroService cobroService;

    public CobroController(CobroService cobroService) {
        this.cobroService = cobroService;
    }

    @GetMapping
    public List<CobroResponse> listar() {
        return cobroService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CobroResponse registrar(@RequestBody CobroRequest solicitud) {
        return cobroService.registrar(solicitud);
    }
}
