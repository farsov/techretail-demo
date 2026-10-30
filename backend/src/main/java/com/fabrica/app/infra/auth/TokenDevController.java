package com.fabrica.app.infra.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo en el modo de desarrollo (auth.modo=dev, por defecto): emite el token de un usuario de prueba, porque en
 * local no hay IdP. La validación con el IdP de producción está pendiente.
 */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "auth.modo", havingValue = "dev", matchIfMissing = true)
public class TokenDevController {

    private static final long SEGUNDOS = 3600;

    private final TokenDev tokens;
    private final String usuarioPorDefecto;
    private final String rolesPorDefecto;

    public TokenDevController(TokenDev tokens, @Value("${AUTH_DEV_USUARIO:demo}") String usuarioPorDefecto,
            @Value("${AUTH_DEV_ROLES:usuario}") String rolesPorDefecto) {
        this.tokens = tokens;
        this.usuarioPorDefecto = usuarioPorDefecto;
        this.rolesPorDefecto = rolesPorDefecto;
    }

    @GetMapping("/token")
    public Map<String, Object> token(@RequestParam(name = "usuario", required = false) String usuario,
            @RequestParam(name = "roles", required = false) String roles) {
        String sub = usuario == null || usuario.isBlank() ? usuarioPorDefecto : usuario;
        List<String> lista = Arrays.stream((roles == null ? rolesPorDefecto : roles).split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .toList();
        return Map.of("token", tokens.firmar(new Usuario(sub, lista), SEGUNDOS), "usuario", sub, "roles", lista,
                "expira_en", SEGUNDOS);
    }
}
