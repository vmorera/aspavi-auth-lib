package com.aspavi.auth.config;

import com.aspavi.auth.tenant.TenantConnectionProvider;
import com.aspavi.auth.tenant.TenantIdentifierResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Spring Boot auto-configuration for the Aspavi Auth library.
 *
 * <p>Registers the following beans automatically when the library is on the classpath:
 * <ul>
 *   <li>{@link MultiTenantJwtDecoder} — decodes JWTs from multiple Keycloak realms</li>
 *   <li>{@link JwtAuthenticationConverter} — maps Keycloak realm roles to Spring Security
 *       {@code ROLE_} authorities</li>
 *   <li>{@link TenantConnectionProvider} — routes Hibernate connections to per-tenant schemas</li>
 *   <li>{@link TenantIdentifierResolver} — resolves the current Hibernate schema from
 *       {@link com.aspavi.auth.tenant.TenantContext}</li>
 * </ul>
 *
 * <p>Every bean is annotated with {@link ConditionalOnMissingBean} so consuming services
 * can override any individual component without disabling the rest.
 */
@AutoConfiguration
@EnableConfigurationProperties(AuthProperties.class)
public class AspavIAuthAutoConfiguration {

    // -------------------------------------------------------------------------
    // JWT decoding
    // -------------------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(AuthProperties properties) {
        return new MultiTenantJwtDecoder(properties.getTenants());
    }

    // -------------------------------------------------------------------------
    // Keycloak role mapping
    // -------------------------------------------------------------------------

    /**
     * Converts Keycloak realm roles (found under {@code realm_access.roles}) into
     * Spring Security {@code ROLE_<UPPERCASE_ROLE>} granted authorities.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName("realm_access.roles");
        converter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
        return jwtConverter;
    }

    // -------------------------------------------------------------------------
    // Multi-tenancy (Hibernate schema-per-tenant)
    // -------------------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    public TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    // NOTE: TenantConnectionProvider requires a DataSource, so it is declared
    // as a @Component inside the class itself to let Spring handle injection.
    // Consuming services that do not use JPA will simply never instantiate it.
}
