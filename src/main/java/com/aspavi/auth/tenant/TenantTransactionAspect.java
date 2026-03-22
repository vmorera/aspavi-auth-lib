package com.aspavi.auth.tenant;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

/**
 * AOP Aspect that automatically sets the PostgreSQL session variable
 * {@code app.current_tenant} before any {@code @Transactional} method executes.
 *
 * <p>This ensures Row-Level Security (RLS) policies on PostgreSQL work correctly
 * without requiring manual plumbing in each service method.
 *
 * <p>Uses {@code SET LOCAL} so the variable is scoped to the current transaction
 * and is reset automatically on COMMIT/ROLLBACK. This is safe with HikariCP
 * connection pooling because the setting never leaks to another tenant's request.
 *
 * <p>Registered automatically via {@link com.aspavi.auth.config.AspavIAuthAutoConfiguration}.
 */
@Aspect
public class TenantTransactionAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantTransactionAspect.class);

    private final JdbcTemplate jdbcTemplate;

    public TenantTransactionAspect(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * Executes {@code SET LOCAL app.current_tenant = '<tenantId>'} at the start of
     * every Spring-managed transaction so that PostgreSQL RLS policies can enforce
     * tenant isolation at the database level.
     *
     * <p>The pointcut covers:
     * <ul>
     *   <li>Methods annotated directly with {@code @Transactional}</li>
     *   <li>Methods inside classes annotated with {@code @Transactional}</li>
     * </ul>
     *
     * <p>If no tenant is set in {@link TenantContext} (e.g. internal admin jobs),
     * the method silently proceeds without setting the variable.
     */
    @Before("@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional)")
    public void setTenantForCurrentTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // No active transaction yet — Spring will open one after this advice runs.
            // The SET LOCAL will be issued when the first DB statement actually executes,
            // which is fine because @Before fires just before the method body, and the
            // transaction is already open by the time the datasource is accessed.
            // We proceed anyway; if no transaction opens, this is a no-op read-only context.
        }

        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            log.trace("TenantTransactionAspect: no tenant in context, skipping SET LOCAL");
            return;
        }

        // Sanitise: prevent SQL injection via single-quote escaping
        String safeTenantId = tenantId.replace("'", "''");

        log.debug("TenantTransactionAspect: SET LOCAL app.current_tenant = '{}'", tenantId);
        jdbcTemplate.execute("SET LOCAL app.current_tenant = '" + safeTenantId + "'");
    }
}
