package com.aspavi.auth.config;

import com.aspavi.auth.tenant.TenantConnectionProvider;
import com.aspavi.auth.tenant.TenantIdentifierResolver;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
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
 *   <li>{@link TenantConnectionProvider} — sets app.current_tenant on each Hibernate connection</li>
 *   <li>{@link TenantIdentifierResolver} — resolves the current tenant from TenantContext</li>
 *   <li>{@link HibernatePropertiesCustomizer} — wires the two tenant beans into Hibernate
 *       so it never tries to instantiate them via reflection</li>
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
    // Multi-tenancy beans
    // -------------------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    public TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    // -------------------------------------------------------------------------
    // Hibernate wiring — passes the Spring-managed beans directly to Hibernate
    // so it never tries to instantiate them via reflection (which would fail
    // because TenantConnectionProvider requires a DataSource constructor arg).
    // -------------------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean(name = "aspavIHibernateMultiTenancyCustomizer")
    public HibernatePropertiesCustomizer aspavIHibernateMultiTenancyCustomizer(
            TenantConnectionProvider tenantConnectionProvider,
            TenantIdentifierResolver tenantIdentifierResolver) {

        return hibernateProperties -> {
            hibernateProperties.put(
                    AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, tenantConnectionProvider);
            hibernateProperties.put(
                    AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        };
    }
}
