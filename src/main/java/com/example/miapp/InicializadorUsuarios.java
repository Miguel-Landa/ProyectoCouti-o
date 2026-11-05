package com.example.miapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class InicializadorUsuarios implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorUsuarios.class);

    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final Environment env;

    public InicializadorUsuarios(JdbcTemplate db, PasswordEncoder encoder, Environment env) {
        this.db = db;
        this.encoder = encoder;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        sincronizar("admin", "ADMIN_PASSWORD", "Administrador General", "ADMIN");
        sincronizar("vendedor", "VENDEDOR_PASSWORD", "Vendedor de Mostrador", "VENDEDOR");
        sincronizar("mecanico", "MECANICO_PASSWORD", "Mecánico de Taller", "MECANICO");
    }

    private void sincronizar(String usuario, String variable, String nombre, String rol) {
        String nueva = env.getProperty(variable);
        boolean hayNueva = nueva != null && !nueva.isBlank();
        if (hayNueva && nueva.length() < 10) {
            log.warn("La variable {} tiene menos de 10 caracteres y se ignora.", variable);
            hayNueva = false;
        }

        List<Map<String, Object>> r = db.queryForList("SELECT password FROM usuarios WHERE username = ?", usuario);

        if (r.isEmpty()) {
            if (hayNueva) {
                db.update("INSERT INTO usuarios (username, password, nombre, rol, activo) VALUES (?, ?, ?, ?, TRUE)",
                        usuario, encoder.encode(nueva), nombre, rol);
            } else {
                log.warn("El usuario {} no existe y no se creó porque falta la variable {}.", usuario, variable);
            }
            return;
        }

        String actual = r.get(0).get("password").toString();
        boolean esHash = actual.startsWith("$2");

        if (hayNueva) {
            if (!esHash || !encoder.matches(nueva, actual)) {
                db.update("UPDATE usuarios SET password = ? WHERE username = ?", encoder.encode(nueva), usuario);
            }
        } else if (!esHash) {
            db.update("UPDATE usuarios SET password = ? WHERE username = ?", encoder.encode(actual), usuario);
            log.warn("El usuario {} conserva su contraseña anterior. Define la variable {}.", usuario, variable);
        }
    }
}