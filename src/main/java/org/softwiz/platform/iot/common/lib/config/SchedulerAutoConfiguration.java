package org.softwiz.platform.iot.common.lib.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.TransactionSystemException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

@Slf4j
@Configuration
@ConditionalOnClass({
        EnableSchedulerLock.class,
        EnableScheduling.class,
        JdbcTemplateLockProvider.class
})
@ConditionalOnProperty(
        prefix = "scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class SchedulerAutoConfiguration {

    @Value("${datasource.readonly.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${datasource.readonly.retry.interval-ms:2000}")
    private long retryIntervalMs;

    @Bean
    @ConditionalOnClass(name = "javax.sql.DataSource")
    public LockProvider commonLockProvider(DataSource dataSource) {

        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            log.info("Creating common LockProvider with DB connection recovery");

            JdbcTemplateLockProvider jdbcProvider =
                    new JdbcTemplateLockProvider(
                            JdbcTemplateLockProvider.Configuration.builder()
                                    .withJdbcTemplate(jdbcTemplate)
                                    .build()
                    );

            return new RecoverableLockProvider(
                    jdbcProvider,
                    dataSource,
                    maxRetryAttempts,
                    retryIntervalMs
            );

        } catch (Exception e) {
            log.warn("Database connection failed - using NoOpLockProvider. Error: {}",
                    e.getMessage());
            return new NoOpLockProvider();
        }
    }

    /**
     * DB 에러 복구 + DuplicateKey 안전 처리 LockProvider
     */
    private static class RecoverableLockProvider implements LockProvider {

        private final JdbcTemplateLockProvider delegate;
        private final DataSource dataSource;
        private final int maxRetryAttempts;
        private final long retryIntervalMs;

        public RecoverableLockProvider(JdbcTemplateLockProvider delegate,
                                       DataSource dataSource,
                                       int maxRetryAttempts,
                                       long retryIntervalMs) {
            this.delegate = delegate;
            this.dataSource = dataSource;
            this.maxRetryAttempts = maxRetryAttempts;
            this.retryIntervalMs = retryIntervalMs;
        }

        @Override
        public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {

            try {
                // 1차 시도
                return delegate.lock(lockConfiguration);

            } catch (DuplicateKeyException e) {
                // 멀티 인스턴스 환경 정상 케이스
                log.debug("Lock already acquired by another instance for: {}",
                        lockConfiguration.getName());
                return Optional.empty();

            } catch (Exception e) {

                // DuplicateKey가 감싸진 경우까지 방어
                if (isDuplicateKey(e)) {
                    log.debug("Lock already acquired (wrapped) for: {}",
                            lockConfiguration.getName());
                    return Optional.empty();
                }

                if (isRecoverableException(e)) {

                    log.warn("Recoverable database error for lock: {}. Attempting recovery... Error: {}",
                            lockConfiguration.getName(),
                            getErrorMessage(e));

                    if (attemptRecovery()) {
                        try {
                            return delegate.lock(lockConfiguration);

                        } catch (DuplicateKeyException retryDupEx) {
                            log.debug("Lock already acquired after recovery: {}",
                                    lockConfiguration.getName());
                            return Optional.empty();

                        } catch (Exception retryEx) {
                            log.warn("Lock acquisition failed after recovery for: {}. Skipping.",
                                    lockConfiguration.getName());
                            return Optional.empty();
                        }
                    }

                    log.warn("Database unavailable, skipping lock for: {}",
                            lockConfiguration.getName());
                    return Optional.empty();
                }

                throw e;
            }
        }

        private boolean isDuplicateKey(Throwable e) {
            while (e != null) {
                if (e instanceof DuplicateKeyException) {
                    return true;
                }
                e = e.getCause();
            }
            return false;
        }

        private boolean isRecoverableException(Exception e) {
            return isReadOnlyException(e)
                    || isReplicationHookException(e)
                    || isConnectionException(e)
                    || e instanceof TransactionSystemException;
        }

        private boolean isReadOnlyException(Exception e) {
            Throwable cause = e;
            while (cause != null) {

                if (cause instanceof SQLException) {
                    SQLException sqlEx = (SQLException) cause;
                    if (sqlEx.getErrorCode() == 1290) {
                        return true;
                    }
                }

                String message = cause.getMessage();
                if (message != null &&
                        (message.contains("read-only")
                                || message.contains("READ-ONLY")
                                || message.contains("error code [1290]"))) {
                    return true;
                }

                cause = cause.getCause();
            }
            return false;
        }

        private boolean isReplicationHookException(Exception e) {
            Throwable cause = e;
            while (cause != null) {
                String message = cause.getMessage();
                if (message != null &&
                        (message.contains("replication hook")
                                || message.contains("before_commit")
                                || message.contains("Error on observer"))) {
                    return true;
                }
                cause = cause.getCause();
            }
            return false;
        }

        private boolean isConnectionException(Exception e) {
            Throwable cause = e;
            while (cause != null) {

                String message = cause.getMessage();
                if (message != null &&
                        (message.contains("Connection refused")
                                || message.contains("Communications link failure")
                                || message.contains("Connection is not available")
                                || message.contains("HikariPool")
                                || message.contains("CannotCreateTransactionException"))) {
                    return true;
                }

                String className = cause.getClass().getName();
                if (className.contains("CommunicationsException")
                        || className.contains("SQLTransientConnectionException")
                        || className.contains("ConnectException")) {
                    return true;
                }

                cause = cause.getCause();
            }
            return false;
        }

        private String getErrorMessage(Exception e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause.getMessage() != null) {
                    return cause.getMessage();
                }
                cause = cause.getCause();
            }
            return e.getClass().getSimpleName();
        }

        private boolean attemptRecovery() {

            if (!(dataSource instanceof HikariDataSource)) {
                return false;
            }

            HikariDataSource hikariDS = (HikariDataSource) dataSource;

            for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {

                try {
                    log.info("Recovery attempt {}/{} - evicting connections...",
                            attempt, maxRetryAttempts);

                    hikariDS.getHikariPoolMXBean().softEvictConnections();
                    Thread.sleep(retryIntervalMs);

                    if (isReadWriteMode()) {
                        log.info("Successfully recovered to READ-WRITE mode");
                        return true;
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (Exception ex) {
                    log.debug("Recovery attempt {} failed: {}",
                            attempt, ex.getMessage());
                }
            }

            log.warn("Failed to recover after {} attempts",
                    maxRetryAttempts);
            return false;
        }

        private boolean isReadWriteMode() {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT @@read_only, @@super_read_only")) {

                if (rs.next()) {
                    boolean readOnly = rs.getBoolean(1);
                    boolean superReadOnly = rs.getBoolean(2);
                    return !readOnly && !superReadOnly;
                }

            } catch (Exception e) {
                log.debug("Failed to check mode: {}",
                        e.getMessage());
            }

            return false;
        }
    }

    private static class NoOpLockProvider implements LockProvider {

        @Override
        public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {

            return Optional.of(new SimpleLock() {
                @Override
                public void unlock() {
                    // no-op
                }
            });
        }
    }
}