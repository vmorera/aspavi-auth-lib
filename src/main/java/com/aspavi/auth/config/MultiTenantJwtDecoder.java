package com.aspavi.auth.config;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A composite {@link JwtDecoder} that selects the correct per-tenant decoder
 * based on the {@code iss} (issuer) claim found in the unverified JWT header/payload.
 *
 * <p>Decoders are created lazily and cached for reuse. Each tenant decoder is
 * configured from its Keycloak issuer URI via OIDC discovery.
 */
public class MultiTenantJwtDecoder implements JwtDecoder {

    /** Pre-configured map of issuer URI → tenant id (from AuthProperties). */
    private final Map<String, String> issuerToTenant;

    /** Pre-configured map of tenant id → issuer URI (from AuthProperties). */
    private final Map<String, String> tenantToIssuer;

    /** Lazily-initialised decoders, keyed by issuer URI. */
    private final ConcurrentHashMap<String, JwtDecoder> decoderCache = new ConcurrentHashMap<>();

    public MultiTenantJwtDecoder(Map<String, String> tenants) {
        this.tenantToIssuer = Map.copyOf(tenants);
        Map<String, String> reverse = new ConcurrentHashMap<>();
        tenants.forEach((tenant, issuer) -> reverse.put(issuer, tenant));
        this.issuerToTenant = Map.copyOf(reverse);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String issuer = extractIssuerUnchecked(token);
        if (issuer == null || !issuerToTenant.containsKey(issuer)) {
            throw new JwtException("Unknown or missing issuer in token: " + issuer);
        }
        return decoderCache
                .computeIfAbsent(issuer, this::buildDecoder)
                .decode(token);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the issuer claim from the JWT payload without verifying the signature.
     * The real verification is delegated to the {@link NimbusJwtDecoder} per tenant.
     */
    private String extractIssuerUnchecked(String token) {
        try {
            // JWT format: header.payload.signature  (all base64url-encoded)
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(padBase64(parts[1])));
            // simple extraction — avoids pulling in a JSON library just for this
            int idx = payloadJson.indexOf("\"iss\"");
            if (idx == -1) return null;
            int colon = payloadJson.indexOf(':', idx);
            int start = payloadJson.indexOf('"', colon) + 1;
            int end   = payloadJson.indexOf('"', start);
            return payloadJson.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private String padBase64(String base64url) {
        return switch (base64url.length() % 4) {
            case 2  -> base64url + "==";
            case 3  -> base64url + "=";
            default -> base64url;
        };
    }

    private JwtDecoder buildDecoder(String issuerUri) {
        return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    }
}
