package com.aspavi.auth.config;

import com.aspavi.auth.tenant.TenantTransactionAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

@AutoConfiguration
@EnableConfigurationProperties(AuthProperties.class)
public class AspavIAuthAutoConfiguration {

    // -------------------------------------------------------------------------
    // JWT decoding — multi-tenant, per-issuer caching
    // -------------------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(AuthProperties properties) {
        return new MultiTenantJwtDecoder(properties.getTenants());
    }

    // -------------------------------------------------------------------------
    // Keycloak role mapping
    //
    // JwtGrantedAuthoritiesConverter does NOT support dot-notation paths like
    // "realm_access.roles". We must extract the nested claim manually.
    // -------------------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !realmAccess.containsKey("roles")) {
                return Collections.emptyList();
            }
            @SuppressWarnings("unchecked")
            Collection<String> roles = (Collection<String>) realmAccess.get("roles");
            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
        });
        return jwtConverter;
    }

    // -------------------------------------------------------------------------
    // Tenant AOP — sets PostgreSQL app.current_tenant before every transaction
    //
    // Only registered when AspectJ is on the classpath (spring-boot-starter-aop)
    // and a DataSource is available. ConditionalOnClass guards against libraries
    // that include aspavi-auth-lib but don't use Spring Data JPA.
    // -------------------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    public TenantTransactionAspect tenantTransactionAspect(DataSource dataSource) {
        return new TenantTransactionAspect(dataSource);
    }
}
