package com.example.miapp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HelloController {

    private final JdbcTemplate db;
    private final AuthenticationManager authManager;
    private final IntentosLoginService intentos;
    private final SecurityContextRepository contextoRepo = new HttpSessionSecurityContextRepository();

    public HelloController(JdbcTemplate db, AuthenticationManager authManager, IntentosLoginService intentos) {
        this.db = db;
        this.authManager = authManager;
        this.intentos = intentos;
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
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request,
                                                      HttpServletRequest http,
                                                      HttpServletResponse resp) {
        Map<String, Object> response = new HashMap<>();

        String usuario = "";
        if (request.getUsuario() != null) {
            usuario = request.getUsuario().trim();
        }
        String password = "";
        if (request.getPassword() != null) {
            password = request.getPassword();
        }

        if (usuario.isEmpty() || password.isEmpty() || usuario.length() > 100 || password.length() > 200) {
            response.put("success", false);
            response.put("message", "Usuario o contraseña incorrectos.");
            return ResponseEntity.status(401).body(response);
        }

        long espera = intentos.segundosBloqueado(usuario);
        if (espera > 0) {
            long minutos = (espera + 59) / 60;
            response.put("success", false);
            response.put("message", "Demasiados intentos fallidos. Intenta de nuevo en " + minutos + " minuto(s).");
            return ResponseEntity.status(429).body(response);
        }

        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(usuario, password));
            intentos.exito(usuario);

            HttpSession anterior = http.getSession(false);
            if (anterior != null) {
                anterior.invalidate();
            }
            http.getSession(true);

            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(auth);
            SecurityContextHolder.setContext(contexto);
            contextoRepo.saveContext(contexto, http, resp);

            String rol = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
            String pagina = "vendedor.html";
            if (rol.equals("ADMIN")) {
                pagina = "admin.html";
            } else if (rol.equals("MECANICO")) {
                pagina = "mecanico.html";
            }

            response.put("success", true);
            response.put("role", rol);
            response.put("redirectUrl", pagina);
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            intentos.fallo(usuario);
            response.put("success", false);
            response.put("message", "Usuario o contraseña incorrectos.");
            return ResponseEntity.status(401).body(response);
        }
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

        String marca = "";
        if (request.getMarca() != null) {
            marca = request.getMarca();
        }
        String modelo = "";
        if (request.getModelo() != null) {
            modelo = request.getModelo();
        }
        String moto = (marca + " " + modelo).trim();
        if (moto.isBlank()) {
            moto = "Sin especificar";
        }

        String folio = "REG-" + System.currentTimeMillis();
        String fechaHora = java.time.LocalDateTime.now().toString();

        db.update(
                "INSERT INTO citas (folio, cliente, telefono, correo, moto, placas, servicio, modalidad, fecha_hora) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                folio, request.getNombre().trim(), request.getTelefono(), request.getCorreo(),
                moto, request.getPlacas().trim(),
                "Registro de cliente", "Sin cita", fechaHora);

        response.put("success", true);
        response.put("message", "Cliente y motocicleta registrados con éxito.");
        response.put("folio", folio);
        return ResponseEntity.ok(response);
    }
}