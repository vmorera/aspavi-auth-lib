package com.aspavi.auth.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides JDBC connections scoped to the current tenant by setting the PostgreSQL
 * session variable app.current_tenant on each connection checkout.
 *
 * <p>Registered as a Spring bean via {@link com.aspavi.auth.config.AspavIAuthAutoConfiguration},
 * which also wires it into Hibernate via HibernatePropertiesCustomizer.
 */
public class TenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    public TenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

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
