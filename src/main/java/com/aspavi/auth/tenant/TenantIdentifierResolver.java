package com.aspavi.auth.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Resolves the current Hibernate tenant identifier from {@link TenantContext}.
 *
 * <p>Registered as a Spring bean via {@link com.aspavi.auth.config.AspavIAuthAutoConfiguration},
 * which also wires it into Hibernate via HibernatePropertiesCustomizer.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    private static final String DEFAULT_TENANT = "public";

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
