package com.example.miapp;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IntentosLoginService {

    private static final int MAXIMO_FALLOS = 5;
    private static final long BLOQUEO_MS = 10 * 60 * 1000L;
    private static final long VENTANA_MS = 15 * 60 * 1000L;

    private record Registro(int fallos, long ultimoFallo, long bloqueadoHasta) {}

    private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();

    private String clave(String usuario) {
        return usuario.toLowerCase(Locale.ROOT);
    }

    public long segundosBloqueado(String usuario) {
        Registro r = registros.get(clave(usuario));
        if (r == null) return 0;
        long restante = r.bloqueadoHasta() - System.currentTimeMillis();
        return restante > 0 ? (restante + 999) / 1000 : 0;
    }

    public void fallo(String usuario) {
        long ahora = System.currentTimeMillis();
        if (registros.size() > 1000) {
            registros.entrySet().removeIf(e ->
                    e.getValue().bloqueadoHasta() < ahora && ahora - e.getValue().ultimoFallo() > VENTANA_MS);
        }
        registros.merge(clave(usuario), new Registro(1, ahora, 0), (viejo, nuevo) -> {
            int fallos = (ahora - viejo.ultimoFallo() > VENTANA_MS) ? 1 : viejo.fallos() + 1;
            if (fallos >= MAXIMO_FALLOS) {
                return new Registro(0, ahora, ahora + BLOQUEO_MS);
            }
            return new Registro(fallos, ahora, 0);
        });
    }

    public void exito(String usuario) {
        registros.remove(clave(usuario));
    }
}