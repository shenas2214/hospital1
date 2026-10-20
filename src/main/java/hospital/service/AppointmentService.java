package hospital.service;

import hospital.BookingException;
import hospital.Database;
import hospital.model.Appointment;
import hospital.model.AppointmentStatus;

import java.sql.*;
import java.time.*;
import java.util.*;

public class AppointmentService {

    static boolean exists(Connection c, String table, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM " + table + " WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /**
     * Books a slot. Three layers of conflict protection:
     *  1. slot must be a real slot inside the doctor's availability,
     *  2. patient must not already hold another appointment at the same time,
     *  3. unique indexes in the DB reject double booking even under concurrent requests.
     */
    public int book(int doctorId, int patientId, LocalDate date, LocalTime time)
            throws BookingException, SQLException {
        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now()))
            throw new BookingException("Cannot book a slot in the past.");

        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (!exists(c, "doctors", doctorId)) throw new BookingException("Doctor not found.");
                if (!exists(c, "patients", patientId)) throw new BookingException("Patient not found.");

                if (!DoctorService.scheduledSlots(c, doctorId, date).contains(time))
                    throw new BookingException("Doctor is not available at " + date + " " + time + ".");

                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM appointments WHERE patient_id=? AND appt_date=? AND slot_time=? AND status <> 'CANCELLED'")) {
                    ps.setInt(1, patientId);
                    ps.setString(2, date.toString());
                    ps.setString(3, time.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) throw new BookingException("Patient already has an appointment at that time.");
                    }
                }

                int id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO appointments(doctor_id, patient_id, appt_date, slot_time, status) VALUES (?,?,?,?, 'BOOKED')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, doctorId);
                    ps.setInt(2, patientId);
                    ps.setString(3, date.toString());
                    ps.setString(4, time.toString());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); id = rs.getInt(1); }
                }
                c.commit();
                return id;
            } catch (SQLException e) {
                c.rollback();
                if (Database.isUniqueViolation(e))
                    throw new BookingException("That slot has just been booked by someone else.");
                throw e;
            } catch (BookingException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void cancel(int appointmentId) throws BookingException, SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE appointments SET status='CANCELLED' WHERE id=? AND status='BOOKED'")) {
            ps.setInt(1, appointmentId);
            if (ps.executeUpdate() == 0)
                throw new BookingException("Only existing BOOKED appointments can be cancelled.");
        }
    }

    public List<Appointment> listForDoctor(int doctorId, LocalDate date) throws SQLException {
        List<Appointment> list = new ArrayList<>();
        String sql = """
            SELECT a.id, a.doctor_id, a.patient_id, p.name, a.appt_date, a.slot_time, a.status
            FROM appointments a JOIN patients p ON p.id = a.patient_id
            WHERE a.doctor_id=? AND a.appt_date=? ORDER BY a.slot_time""";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setString(2, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(new Appointment(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4),
                            LocalDate.parse(rs.getString(5)), LocalTime.parse(rs.getString(6)),
                            AppointmentStatus.valueOf(rs.getString(7))));
            }
        }
        return list;
    }
}
