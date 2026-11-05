package com.example.miapp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuracion) throws Exception {
        return configuracion.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(
                        antMatcher("/api/citas"),
                        antMatcher("/api/citas/folio/*"),
                        antMatcher("/api/clientes/registro")));

        http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);

        http.logout(salida -> salida
                .logoutUrl("/api/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(new ManejadoresSeguridad.SalidaCorrecta()));

        http.headers(cabeceras -> cabeceras
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));

        http.authorizeHttpRequests(reglas -> reglas
                .requestMatchers("/", "/index.html", "/registro-cliente.html", "/error").permitAll()
                .requestMatchers("/*.css", "/*.js", "/*.jpg", "/*.jpeg", "/*.png", "/*.svg", "/*.ico").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/login", "/api/logout").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/clientes/registro", "/api/citas").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/citas/horarios", "/api/citas/folio/*").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/citas/folio/*").permitAll()
                .requestMatchers("/admin.html").hasRole("ADMIN")
                .requestMatchers("/vendedor.html").hasRole("VENDEDOR")
                .requestMatchers("/mecanico.html").hasRole("MECANICO")
                .requestMatchers(HttpMethod.GET, "/api/productos/**").hasAnyRole("VENDEDOR", "MECANICO", "ADMIN")
                .requestMatchers("/api/productos/**").hasRole("ADMIN")
                .requestMatchers("/api/proveedores/**").hasRole("ADMIN")
                .requestMatchers("/api/compras/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/mecanicos/**").hasAnyRole("MECANICO", "ADMIN")
                .requestMatchers("/api/mecanicos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/ventas/**").hasAnyRole("VENDEDOR", "ADMIN")
                .requestMatchers("/api/ventas/**").hasRole("VENDEDOR")
                .requestMatchers(HttpMethod.GET, "/api/caja/cortes/**").hasAnyRole("VENDEDOR", "ADMIN")
                .requestMatchers("/api/caja/**").hasRole("VENDEDOR")
                .requestMatchers(HttpMethod.GET, "/api/cobros/**").hasAnyRole("MECANICO", "ADMIN")
                .requestMatchers("/api/cobros/**").hasRole("MECANICO")
                .requestMatchers("/api/citas/**").hasAnyRole("MECANICO", "ADMIN")
                .anyRequest().denyAll());

        http.exceptionHandling(errores -> errores
                .authenticationEntryPoint(new ManejadoresSeguridad.SinSesion())
                .accessDeniedHandler(new ManejadoresSeguridad.SinPermiso()));

        return http.build();
    }
}