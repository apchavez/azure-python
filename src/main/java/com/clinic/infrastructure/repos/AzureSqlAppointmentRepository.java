package com.clinic.infrastructure.repos;

import com.clinic.domain.entities.Appointment;
import com.clinic.domain.ports.AppointmentRelationalRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Azure SQL Database adapter implementing the relational persistence port.
 * Azure equivalent of the AWS project's MySQL store for completed appointments.
 * (Azure SQL is used instead of Azure Database for MySQL because new free
 * subscriptions are temporarily blocked from provisioning MySQL Flexible Server.)
 *
 * Uses HikariCP so the connection is created once per cold start and reused
 * across warm invocations. Schema is managed by Flyway (db/migration/V*.sql).
 * Only this class knows about JDBC/SQL Server. Uses MERGE for idempotent upsert.
 */
public class AzureSqlAppointmentRepository implements AppointmentRelationalRepository {

    private final HikariDataSource dataSource;

    public AzureSqlAppointmentRepository(String host, String database, String user, String password) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlserver://" + host + ":1433;database=" + database
                + ";encrypt=true;trustServerCertificate=false;loginTimeout=30;");
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(5);
        cfg.setConnectionTimeout(30_000);
        cfg.setPoolName("clinic-sql-pool");
        this.dataSource = new HikariDataSource(cfg);
        migrateSchema();
    }

    private void migrateSchema() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Override
    public void persist(Appointment a) {
        String sql = """
                MERGE appointments AS target
                USING (SELECT ? AS appointment_id) AS source
                ON target.appointment_id = source.appointment_id
                WHEN MATCHED THEN
                    UPDATE SET status = ?, completed_at = ?
                WHEN NOT MATCHED THEN
                    INSERT (appointment_id, insured_id, schedule_id, country_iso, status, created_at, completed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?);
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, a.getAppointmentId());
            ps.setString(2, a.getStatus().name());
            ps.setTimestamp(3, toTs(a.getCompletedAt()));
            ps.setString(4, a.getAppointmentId());
            ps.setString(5, a.getInsuredId());
            ps.setInt(6, a.getScheduleId());
            ps.setString(7, a.getCountryISO().name());
            ps.setString(8, a.getStatus().name());
            ps.setTimestamp(9, toTs(a.getCreatedAt()));
            ps.setTimestamp(10, toTs(a.getCompletedAt()));
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to persist appointment to Azure SQL: " + e.getMessage(), e);
        }
    }

    private Timestamp toTs(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
