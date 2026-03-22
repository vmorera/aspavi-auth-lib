package com.aspavi.auth.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Provides JDBC connections scoped to the current tenant by setting the PostgreSQL
 * session variable app.current_tenant on each connection checkout.
 *
 * <p>Implements {@link HibernatePropertiesCustomizer} so that Hibernate receives
 * the already-constructed Spring bean (with its DataSource) rather than trying to
 * instantiate it via reflection — which would fail because there is no no-arg constructor.
 */
@Component
public class TenantConnectionProvider
        implements MultiTenantConnectionProvider<String>, HibernatePropertiesCustomizer {

    private final DataSource dataSource;

    public TenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // -----------------------------------------------------------------------
    // HibernatePropertiesCustomizer — wires this bean into Hibernate
    // -----------------------------------------------------------------------

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, this);
    }

    // -----------------------------------------------------------------------
    // MultiTenantConnectionProvider
    // -----------------------------------------------------------------------

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        String safeTenant = tenantIdentifier.replace("'", "''");
        connection.createStatement().execute(
                "SET app.current_tenant = '" + safeTenant + "'"
        );
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            connection.createStatement().execute("RESET app.current_tenant");
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException(
                "Cannot unwrap TenantConnectionProvider as " + unwrapType.getName());
    }
}
