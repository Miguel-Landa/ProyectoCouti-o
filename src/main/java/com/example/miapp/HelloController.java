package com.example.miapp;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HelloController {

    private final JdbcTemplate db;

    public HelloController(JdbcTemplate db) {
        this.db = db;
    }

    public static class LoginRequest {
        private String usuario;
        private String password;

        public String getUsuario() { return usuario; }
        public void setUsuario(String usuario) { this.usuario = usuario; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class ClienteRequest {
        private String nombre;
        private String telefono;
        private String correo;
        private String marca;
        private String modelo;
        private String placas;

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getTelefono() { return telefono; }
        public void setTelefono(String telefono) { this.telefono = telefono; }
        public String getCorreo() { return correo; }
        public void setCorreo(String correo) { this.correo = correo; }
        public String getMarca() { return marca; }
        public void setMarca(String marca) { this.marca = marca; }
        public String getModelo() { return modelo; }
        public void setModelo(String modelo) { this.modelo = modelo; }
        public String getPlacas() { return placas; }
        public void setPlacas(String placas) { this.placas = placas; }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> r = db.queryForList(
                "SELECT rol FROM usuarios WHERE username = ? AND password = ? AND activo = TRUE",
                request.getUsuario(), request.getPassword());

        if (r.isEmpty()) {
            response.put("success", false);
            response.put("message", "Usuario o contraseña incorrectos.");
            return ResponseEntity.status(401).body(response);
        }

        String rol = r.get(0).get("rol").toString();
        String pagina = switch (rol) {
            case "ADMIN" -> "admin.html";
            case "MECANICO" -> "mecanico.html";
            default -> "vendedor.html";
        };

        response.put("success", true);
        response.put("role", rol);
        response.put("redirectUrl", pagina);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/clientes/registro")
    public ResponseEntity<Map<String, Object>> registrarCliente(@RequestBody ClienteRequest request) {
        Map<String, Object> response = new HashMap<>();

        if (request.getNombre() == null || request.getNombre().isBlank()
                || request.getPlacas() == null || request.getPlacas().isBlank()) {
            response.put("success", false);
            response.put("message", "Nombre y placas son obligatorios.");
            return ResponseEntity.badRequest().body(response);
        }

        String folio = "REG-" + System.currentTimeMillis();
        String moto = ((request.getMarca() == null ? "" : request.getMarca()) + " "
                + (request.getModelo() == null ? "" : request.getModelo())).trim();
        String fechaHora = java.time.LocalDateTime.now().toString();

        db.update(
                "INSERT INTO citas (folio, cliente, telefono, correo, moto, placas, servicio, modalidad, fecha_hora) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                folio, request.getNombre().trim(), request.getTelefono(), request.getCorreo(),
                moto.isBlank() ? "Sin especificar" : moto, request.getPlacas().trim(),
                "Registro de cliente", "Sin cita", fechaHora);

        response.put("success", true);
        response.put("message", "Cliente y motocicleta registrados con éxito.");
        response.put("folio", folio);
        return ResponseEntity.ok(response);
    }
}
