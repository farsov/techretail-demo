package com.fabrica.app.infra.auth;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Toda ruta /api/** exige {@code Authorization: Bearer <token>}, salvo /api/salud y /api/dev/**. */
@Component
public class FiltroAutenticacion extends OncePerRequestFilter {

    private static final List<String> PUBLICAS = List.of("/api/salud", "/api/dev/");

    private final TokenDev tokens;

    public FiltroAutenticacion(TokenDev tokens) {
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI().substring(peticion.getContextPath().length());
        return !ruta.startsWith("/api/") || PUBLICAS.stream().anyMatch(ruta::startsWith)
                || "OPTIONS".equals(peticion.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String cabecera = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        Optional<Usuario> usuario = cabecera != null && cabecera.startsWith("Bearer ")
                ? tokens.verificar(cabecera.substring(7))
                : Optional.empty();
        if (usuario.isEmpty()) {
            respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            respuesta.setContentType("application/problem+json");
            respuesta.setCharacterEncoding("UTF-8");
            respuesta.getWriter().write(
                    "{\"title\":\"No autenticado\",\"status\":401,\"detail\":\"Inicia sesión para continuar\"}");
            return;
        }
        peticion.setAttribute(Usuario.ATRIBUTO, usuario.get());
        cadena.doFilter(peticion, respuesta);
    }
}
