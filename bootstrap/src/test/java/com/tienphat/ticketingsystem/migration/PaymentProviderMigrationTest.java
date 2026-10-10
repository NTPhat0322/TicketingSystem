package com.tienphat.ticketingsystem.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentProviderMigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Test
    void updatesLegacyConstraintAndPreservesExistingPayments() throws SQLException {
        String schema = newSchema("legacy_payment_");
        createSchema(schema);

        try {
            try (Connection connection = open(schema); Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE payments (
                            provider VARCHAR(32) NOT NULL,
                            CONSTRAINT payments_provider_check
                                CHECK (provider IN ('VNPAY', 'MOMO', 'STRIPE'))
                        )
                        """);
                statement.execute("INSERT INTO payments(provider) VALUES ('VNPAY')");
            }

            int migrationsExecuted = migrate(schema);

            assertEquals(1, migrationsExecuted);
            try (Connection connection = open(schema); Statement statement = connection.createStatement()) {
                try (ResultSet result = statement.executeQuery(
                        "SELECT COUNT(*) FROM payments WHERE provider = 'VNPAY'")) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }
                statement.execute("INSERT INTO payments(provider) VALUES ('LOCAL')");
                assertThrows(SQLException.class,
                        () -> statement.execute("INSERT INTO payments(provider) VALUES ('UNKNOWN')"));
            }
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void canRunAgainstAnEmptySchemaBeforeHibernateCreatesTables() throws SQLException {
        String schema = newSchema("empty_payment_");
        createSchema(schema);

        try {
            assertEquals(1, migrate(schema));

            try (Connection connection = open(schema);
                 PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?)")) {
                statement.setString(1, schema + ".payments");
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertNull(result.getString(1));
                }
            }
        } finally {
            dropSchema(schema);
        }
    }

    private static int migrate(String schema) {
        return Flyway.configure()
                .dataSource(jdbcUrl(schema), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate()
                .migrationsExecuted;
    }

    private static void createSchema(String schema) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
    }

    private static void dropSchema(String schema) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private static Connection open(String schema) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(schema), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String jdbcUrl(String schema) {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        return POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
    }

    private static String newSchema(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
