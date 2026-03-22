package com.aspavi.auth.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves the current Hibernate tenant identifier from {@link TenantContext}.
 *
 * <p>Implements {@link HibernatePropertiesCustomizer} so that Hibernate receives
 * the already-constructed Spring bean rather than instantiating it via reflection.
 */
@Component
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    private static final String DEFAULT_TENANT = "public";

    // -----------------------------------------------------------------------
    // HibernatePropertiesCustomizer — wires this bean into Hibernate
    // -----------------------------------------------------------------------

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }

    // -----------------------------------------------------------------------
    // CurrentTenantIdentifierResolver
    // -----------------------------------------------------------------------

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.getTenantId();
        return (tenantId != null && !tenantId.isBlank()) ? tenantId : DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
