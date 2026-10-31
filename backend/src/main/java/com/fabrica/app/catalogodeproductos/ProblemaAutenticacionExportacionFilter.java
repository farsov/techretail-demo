package com.fabrica.app.catalogodeproductos;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Completa el ProblemDetail del filtro de autenticación existente, sin autenticar ni retener descargas. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProblemaAutenticacionExportacionFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return !peticion.getRequestURI().substring(peticion.getContextPath().length())
                .equals("/api/productos/exportaciones/excel");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        HttpServletResponseWrapper adaptada = new HttpServletResponseWrapper(respuesta) {
            private PrintWriter descartado;

            @Override
            public PrintWriter getWriter() throws IOException {
                if (getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                    if (descartado == null) {
                        descartado = new PrintWriter(new StringWriter());
                    }
                    return descartado;
                }
                return super.getWriter();
            }
        };
        cadena.doFilter(peticion, adaptada);
        if (respuesta.getStatus() == HttpServletResponse.SC_UNAUTHORIZED && !respuesta.isCommitted()) {
            respuesta.setContentType("application/problem+json");
            respuesta.setCharacterEncoding("UTF-8");
            respuesta.getWriter().write("{\"type\":\"about:blank\",\"title\":\"No autenticado\","
                    + "\"status\":401,\"detail\":\"Inicia sesión para continuar\"}");
        }
    }
}
