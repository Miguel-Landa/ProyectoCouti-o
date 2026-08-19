package com.example.miapp;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HelloController {

    // Clase DTO para deserializar las credenciales enviadas desde el frontend
    public static class LoginRequest {
        private String usuario;
        private String password;

        public String getUsuario() { return usuario; }
        public void setUsuario(String usuario) { this.usuario = usuario; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> response = new HashMap<>();

        // Validación de credenciales para los 3 perfiles
        if ("admin".equals(request.getUsuario()) && "admin123".equals(request.getPassword())) {
            response.put("success", true);
            response.put("role", "ADMIN");
            response.put("redirectUrl", "/admin.html");
            return ResponseEntity.ok(response);
        } else if ("ventas".equals(request.getUsuario()) && "vendedor123".equals(request.getPassword())) {
            response.put("success", true);
            response.put("role", "VENDEDOR");
            response.put("redirectUrl", "/vendedor.html");
            return ResponseEntity.ok(response);
        } else if ("mecanico".equals(request.getUsuario()) && "mecanico123".equals(request.getPassword())) {
            response.put("success", true);
            response.put("role", "MECANICO");
            response.put("redirectUrl", "/mecanico.html");
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Usuario o contraseña incorrectos.");
            return ResponseEntity.status(401).body(response);
        }
    }
}