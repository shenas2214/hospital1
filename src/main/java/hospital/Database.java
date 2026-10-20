package hospital;

import java.sql.*;

/** Central place for JDBC connections and schema creation. */
public final class Database {
    private static final String URL = "jdbc:sqlite:hospital.db";

    private Database() {}

    public static Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
        }
        return c;
    }

    public static boolean isUniqueViolation(SQLException e) {
        String m = e.getMessage();
        return m != null && (m.contains("UNIQUE") || m.contains("constraint"));
    }

    public static void init() throws SQLException {
        String[] ddl = {
            """
            CREATE TABLE IF NOT EXISTS doctors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                specialization TEXT NOT NULL,
                phone TEXT)""",
            """
            CREATE TABLE IF NOT EXISTS patients (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT)""",
            // Weekly availability: day_of_week 1=Mon ... 7=Sun
            """
            CREATE TABLE IF NOT EXISTS doctor_availability (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                doctor_id INTEGER NOT NULL REFERENCES doctors(id),
                day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
                start_time TEXT NOT NULL,
                end_time TEXT NOT NULL,
                slot_minutes INTEGER NOT NULL CHECK (slot_minutes > 0))""",
            """
            CREATE TABLE IF NOT EXISTS appointments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                doctor_id INTEGER NOT NULL REFERENCES doctors(id),
                patient_id INTEGER NOT NULL REFERENCES patients(id),
                appt_date TEXT NOT NULL,
                slot_time TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'BOOKED')""",
            // DB-level protection against double booking (even under concurrent access)
            """
            CREATE UNIQUE INDEX IF NOT EXISTS uq_doctor_slot
                ON appointments(doctor_id, appt_date, slot_time) WHERE status <> 'CANCELLED'""",
            """
            CREATE UNIQUE INDEX IF NOT EXISTS uq_patient_slot
                ON appointments(patient_id, appt_date, slot_time) WHERE status <> 'CANCELLED'""",
            """
            CREATE TABLE IF NOT EXISTS queue_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                appointment_id INTEGER UNIQUE REFERENCES appointments(id),
                doctor_id INTEGER NOT NULL REFERENCES doctors(id),
                patient_id INTEGER NOT NULL REFERENCES patients(id),
                queue_date TEXT NOT NULL,
                token_no INTEGER NOT NULL,
                priority INTEGER NOT NULL DEFAULT 2,   -- 1 = emergency, 2 = normal
                status TEXT NOT NULL DEFAULT 'WAITING',
                check_in_time TEXT NOT NULL,
                UNIQUE (doctor_id, queue_date, token_no))"""
        };
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            for (String sql : ddl) s.executeUpdate(sql);
        }
    }
}
