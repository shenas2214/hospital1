package hospital.service;

import hospital.BookingException;
import hospital.Database;
import hospital.model.QueueEntry;
import hospital.model.QueueStatus;

import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * Daily queue per doctor. Tokens are sequential; emergencies (priority 1)
 * are called before normal patients (priority 2); inside a priority it is FIFO.
 */
public class QueueService {

    /** Patient with a booked appointment arrives at the hospital. */
    public QueueEntry checkIn(int appointmentId) throws BookingException, SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int doctorId, patientId;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT doctor_id, patient_id, appt_date, status FROM appointments WHERE id=?")) {
                    ps.setInt(1, appointmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BookingException("Appointment not found.");
                        doctorId = rs.getInt(1);
                        patientId = rs.getInt(2);
                        if (!LocalDate.parse(rs.getString(3)).equals(LocalDate.now()))
                            throw new BookingException("Check-in is only allowed on the appointment day.");
                        if (!"BOOKED".equals(rs.getString(4)))
                            throw new BookingException("Appointment is " + rs.getString(4) + ", cannot check in.");
                    }
                }
                QueueEntry e = enqueue(c, appointmentId, doctorId, patientId, 2);
                try (PreparedStatement ps = c.prepareStatement("UPDATE appointments SET status='CHECKED_IN' WHERE id=?")) {
                    ps.setInt(1, appointmentId);
                    ps.executeUpdate();
                }
                c.commit();
                return e;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /** Walk-in / emergency patient without an appointment. */
    public QueueEntry addWalkIn(int doctorId, int patientId, boolean emergency)
            throws BookingException, SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (!AppointmentService.exists(c, "doctors", doctorId)) throw new BookingException("Doctor not found.");
                if (!AppointmentService.exists(c, "patients", patientId)) throw new BookingException("Patient not found.");
                if (!emergency && DoctorService.scheduledSlots(c, doctorId, LocalDate.now()).isEmpty())
                    throw new BookingException("Doctor does not work today.");
                QueueEntry e = enqueue(c, null, doctorId, patientId, emergency ? 1 : 2);
                c.commit();
                return e;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    private QueueEntry enqueue(Connection c, Integer appointmentId, int doctorId, int patientId, int priority)
            throws SQLException, BookingException {
        String today = LocalDate.now().toString();

        // the same patient may not be waiting twice for the same doctor
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM queue_entries WHERE doctor_id=? AND patient_id=? AND queue_date=? AND status <> 'DONE'")) {
            ps.setInt(1, doctorId); ps.setInt(2, patientId); ps.setString(3, today);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) throw new BookingException("Patient is already in this doctor's queue.");
            }
        }

        int token;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(token_no),0)+1 FROM queue_entries WHERE doctor_id=? AND queue_date=?")) {
            ps.setInt(1, doctorId); ps.setString(2, today);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); token = rs.getInt(1); }
        }

        int id;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO queue_entries(appointment_id, doctor_id, patient_id, queue_date, token_no, priority, status, check_in_time) "
                        + "VALUES (?,?,?,?,?,?, 'WAITING', ?)", Statement.RETURN_GENERATED_KEYS)) {
            if (appointmentId == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, appointmentId);
            ps.setInt(2, doctorId);
            ps.setInt(3, patientId);
            ps.setString(4, today);
            ps.setInt(5, token);
            ps.setInt(6, priority);
            ps.setString(7, LocalDateTime.now().toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); id = rs.getInt(1); }
        }
        String name;
        try (PreparedStatement ps = c.prepareStatement("SELECT name FROM patients WHERE id=?")) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); name = rs.getString(1); }
        }
        return new QueueEntry(id, token, name, priority, QueueStatus.WAITING);
    }

    /** Doctor calls the next patient. Only one patient can be IN_PROGRESS per doctor. */
    public Optional<QueueEntry> callNext(int doctorId) throws BookingException, SQLException {
        String today = LocalDate.now().toString();
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM queue_entries WHERE doctor_id=? AND queue_date=? AND status='IN_PROGRESS'")) {
                    ps.setInt(1, doctorId); ps.setString(2, today);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) throw new BookingException("Complete the current consultation first.");
                    }
                }
                QueueEntry next = null;
                int priority = 2;
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT q.id, q.token_no, p.name, q.priority FROM queue_entries q
                        JOIN patients p ON p.id = q.patient_id
                        WHERE q.doctor_id=? AND q.queue_date=? AND q.status='WAITING'
                        ORDER BY q.priority, q.token_no LIMIT 1""")) {
                    ps.setInt(1, doctorId); ps.setString(2, today);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            priority = rs.getInt(4);
                            next = new QueueEntry(rs.getInt(1), rs.getInt(2), rs.getString(3), priority, QueueStatus.IN_PROGRESS);
                        }
                    }
                }
                if (next != null) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE queue_entries SET status='IN_PROGRESS' WHERE id=?")) {
                        ps.setInt(1, next.getId());
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return Optional.ofNullable(next);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /** Finish the current consultation. */
    public void complete(int doctorId) throws BookingException, SQLException {
        String today = LocalDate.now().toString();
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int queueId;
                Integer apptId = null;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id, appointment_id FROM queue_entries WHERE doctor_id=? AND queue_date=? AND status='IN_PROGRESS'")) {
                    ps.setInt(1, doctorId); ps.setString(2, today);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BookingException("No consultation in progress.");
                        queueId = rs.getInt(1);
                        int a = rs.getInt(2);
                        if (!rs.wasNull()) apptId = a;
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE queue_entries SET status='DONE' WHERE id=?")) {
                    ps.setInt(1, queueId);
                    ps.executeUpdate();
                }
                if (apptId != null) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE appointments SET status='COMPLETED' WHERE id=?")) {
                        ps.setInt(1, apptId);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /** Current queue (in service + waiting) in calling order. */
    public List<QueueEntry> viewQueue(int doctorId) throws SQLException {
        List<QueueEntry> list = new ArrayList<>();
        String sql = """
            SELECT q.id, q.token_no, p.name, q.priority, q.status FROM queue_entries q
            JOIN patients p ON p.id = q.patient_id
            WHERE q.doctor_id=? AND q.queue_date=? AND q.status <> 'DONE'
            ORDER BY CASE q.status WHEN 'IN_PROGRESS' THEN 0 ELSE 1 END, q.priority, q.token_no""";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setString(2, LocalDate.now().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(new QueueEntry(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4),
                            QueueStatus.valueOf(rs.getString(5))));
            }
        }
        return list;
    }
}
