package com.fabrica.app.infra.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** Tokens JWT HS256 del perfil de desarrollo, con el mismo formato que uno del IdP. */
@Component
public class TokenDev {

    private static final String EMISOR = "fabrica-dev";
    private static final Base64.Encoder CODIFICADOR = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODIFICADOR = Base64.getUrlDecoder();

    private final JsonMapper json = JsonMapper.builder().build();
    private final byte[] clave;

    /** Clave de AUTH_DEV_SECRETO o una aleatoria al arrancar (los tokens vencen al reiniciar). Nunca se versiona. */
    public TokenDev(@Value("${AUTH_DEV_SECRETO:}") String secreto) {
        if (secreto.isBlank()) {
            clave = new byte[32];
            new SecureRandom().nextBytes(clave);
        } else {
            clave = secreto.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String firmar(Usuario usuario, long segundos) {
        long ahora = Instant.now().getEpochSecond();
        String cabecera = codificar(Map.of("alg", "HS256", "typ", "JWT"));
        String cuerpo = codificar(Map.of("sub", usuario.sub(), "roles", usuario.roles(), "iss", EMISOR,
                "iat", ahora, "exp", ahora + segundos));
        return cabecera + "." + cuerpo + "." + firma(cabecera + "." + cuerpo);
    }

    /** El usuario del token, o vacío si la firma, el emisor o el vencimiento no son válidos. */
    public Optional<Usuario> verificar(String token) {
        String[] partes = token.split("\\.", -1);
        if (partes.length != 3) {
            return Optional.empty();
        }
        byte[] esperada = firma(partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(esperada, partes[2].getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        try {
            Map<?, ?> cabecera = json.readValue(DECODIFICADOR.decode(partes[0]), Map.class);
            Map<?, ?> datos = json.readValue(DECODIFICADOR.decode(partes[1]), Map.class);
            if (!"HS256".equals(cabecera.get("alg")) || !EMISOR.equals(datos.get("iss"))) {
                return Optional.empty();
            }
            if (!(datos.get("exp") instanceof Number exp) || exp.longValue() <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            List<String> roles = datos.get("roles") instanceof List<?> lista
                    ? lista.stream().map(String::valueOf).toList()
                    : List.of();
            return Optional.of(new Usuario(String.valueOf(datos.get("sub")), roles));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private String codificar(Object valor) {
        return CODIFICADOR.encodeToString(json.writeValueAsBytes(valor));
    }

    private String firma(String datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return CODIFICADOR.encodeToString(mac.doFinal(datos.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }
}
