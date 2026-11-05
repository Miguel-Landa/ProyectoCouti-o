package com.example.miapp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ServicioUsuarios implements UserDetailsService {

    private final JdbcTemplate db;

    public ServicioUsuarios(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<Map<String, Object>> filas = db.queryForList(
                "SELECT username, password, rol FROM usuarios WHERE LOWER(username) = LOWER(?) AND activo = TRUE",
                username);

        if (filas.isEmpty()) {
            throw new UsernameNotFoundException("Usuario no encontrado");
        }

        Map<String, Object> fila = filas.get(0);
        String nombre = fila.get("username").toString();
        String clave = fila.get("password").toString();
        String rol = fila.get("rol").toString();

        return User.withUsername(nombre).password(clave).roles(rol).build();
    }
}