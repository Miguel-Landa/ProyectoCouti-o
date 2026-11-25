package com.example.miapp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/empleados")
public class EmpleadosController {

    private final JdbcTemplate jdbc;

    public EmpleadosController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private String texto(Map<String, Object> cuerpo, String clave) {
        Object valor = cuerpo.get(clave);
        if (valor == null) {
            return "";
        }
        return valor.toString().trim();
    }

    private ResponseEntity<Map<String, Object>> mensaje(HttpStatus estado, String texto) {
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("message", texto);
        return ResponseEntity.status(estado).body(respuesta);
    }

    private boolean esActivo(Map<String, Object> cuerpo) {
        Object valor = cuerpo.get("activo");
        if (valor == null) {
            return true;
        }
        return Boolean.parseBoolean(valor.toString());
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return jdbc.queryForList(
                "SELECT id, nombre, telefono, email, puesto, fecha_ingreso, activo, notas FROM empleados ORDER BY nombre");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, Object> cuerpo) {
        String nombre = texto(cuerpo, "nombre");
        if (nombre.isEmpty()) {
            return mensaje(HttpStatus.BAD_REQUEST, "El nombre del empleado es obligatorio.");
        }
        try {
            Long id = jdbc.queryForObject(
                    "INSERT INTO empleados (nombre, telefono, email, puesto, fecha_ingreso, activo, notas) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                    Long.class,
                    nombre,
                    texto(cuerpo, "telefono"),
                    texto(cuerpo, "email"),
                    texto(cuerpo, "puesto"),
                    texto(cuerpo, "fecha_ingreso"),
                    esActivo(cuerpo),
                    texto(cuerpo, "notas"));
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("id", id);
            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        } catch (DuplicateKeyException e) {
            return mensaje(HttpStatus.CONFLICT, "Ya existe un empleado con ese nombre.");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable Long id,
            @RequestBody Map<String, Object> cuerpo) {
        String nombre = texto(cuerpo, "nombre");
        if (nombre.isEmpty()) {
            return mensaje(HttpStatus.BAD_REQUEST, "El nombre del empleado es obligatorio.");
        }
        try {
            int filas = jdbc.update(
                    "UPDATE empleados SET nombre = ?, telefono = ?, email = ?, puesto = ?, "
                            + "fecha_ingreso = ?, activo = ?, notas = ? WHERE id = ?",
                    nombre,
                    texto(cuerpo, "telefono"),
                    texto(cuerpo, "email"),
                    texto(cuerpo, "puesto"),
                    texto(cuerpo, "fecha_ingreso"),
                    esActivo(cuerpo),
                    texto(cuerpo, "notas"),
                    id);
            if (filas == 0) {
                return mensaje(HttpStatus.NOT_FOUND, "El empleado no existe.");
            }
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("id", id);
            return ResponseEntity.ok(respuesta);
        } catch (DuplicateKeyException e) {
            return mensaje(HttpStatus.CONFLICT, "Ya existe un empleado con ese nombre.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        int filas = jdbc.update("DELETE FROM empleados WHERE id = ?", id);
        if (filas == 0) {
            return mensaje(HttpStatus.NOT_FOUND, "El empleado no existe.");
        }
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("id", id);
        return ResponseEntity.ok(respuesta);
    }
}