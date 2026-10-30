package com.fabrica.app.infra.auth;

import java.util.List;

/** Usuario autenticado de la petición: {@code @RequestAttribute(Usuario.ATRIBUTO) Usuario usuario}. */
public record Usuario(String sub, List<String> roles) {

    public static final String ATRIBUTO = "usuario";

    public boolean tieneRol(String rol) {
        return roles.contains(rol);
    }
}
