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

    // DTO para deserializar credenciales de login
    public static class LoginRequest {
        private String usuario;
        private String password;

        public String getUsuario() { return usuario; }
        public void setUsuario(String usuario) { this.usuario = usuario; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    // DTO para registrar un cliente
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

    // Endpoint para procesar el formulario de registro del cliente
    @PostMapping("/clientes/registro")
    public ResponseEntity<Map<String, Object>> registrarCliente(@RequestBody ClienteRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        // Simulación de guardado exitoso
        response.put("success", true);
        response.put("message", "Cliente y motocicleta registrados con éxito.");
        return ResponseEntity.ok(response);
    }
}