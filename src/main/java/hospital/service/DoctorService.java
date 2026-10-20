package hospital.service;

import hospital.BookingException;
import hospital.Database;
import hospital.model.Doctor;

import java.sql.*;
import java.time.*;
import java.util.*;

public class DoctorService {

    public Doctor addDoctor(String name, String specialization, String phone) throws SQLException {
        String sql = "INSERT INTO doctors(name, specialization, phone) VALUES (?, ?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, specialization);
            ps.setString(3, phone);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return new Doctor(rs.getInt(1), name, specialization, phone);
            }
        }
    }

    public List<Doctor> listDoctors() throws SQLException {
        List<Doctor> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, name, specialization, phone FROM doctors ORDER BY id")) {
            while (rs.next())
                list.add(new Doctor(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        }
        return list;
    }

    /** Adds a weekly availability window (dayOfWeek: 1=Mon..7=Sun). Rejects overlapping windows. */
    public void addAvailability(int doctorId, int dayOfWeek, LocalTime start, LocalTime end, int slotMinutes)
            throws SQLException, BookingException {
        if (!start.isBefore(end)) throw new BookingException("Start time must be before end time.");
        if (slotMinutes <= 0) throw new BookingException("Slot length must be positive.");
        if (dayOfWeek < 1 || dayOfWeek > 7) throw new BookingException("Day of week must be 1-7.");

        try (Connection c = Database.getConnection()) {
            if (!AppointmentService.exists(c, "doctors", doctorId))
                throw new BookingException("Doctor not found.");

            String check = "SELECT start_time, end_time FROM doctor_availability WHERE doctor_id=? AND day_of_week=?";
            try (PreparedStatement ps = c.prepareStatement(check)) {
                ps.setInt(1, doctorId);
                ps.setInt(2, dayOfWeek);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalTime s = LocalTime.parse(rs.getString(1));
                        LocalTime e = LocalTime.parse(rs.getString(2));
                        if (start.isBefore(e) && s.isBefore(end))
                            throw new BookingException("Overlaps existing availability " + s + "-" + e);
                    }
                }
            }
            String ins = "INSERT INTO doctor_availability(doctor_id, day_of_week, start_time, end_time, slot_minutes) VALUES (?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(ins)) {
                ps.setInt(1, doctorId);
                ps.setInt(2, dayOfWeek);
                ps.setString(3, start.toString());
                ps.setString(4, end.toString());
                ps.setInt(5, slotMinutes);
                ps.executeUpdate();
            }
        }
    }

    /** All slots the doctor works on that date (booked or not). */
    static List<LocalTime> scheduledSlots(Connection c, int doctorId, LocalDate date) throws SQLException {
        List<LocalTime> slots = new ArrayList<>();
        String sql = "SELECT start_time, end_time, slot_minutes FROM doctor_availability WHERE doctor_id=? AND day_of_week=? ORDER BY start_time";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setInt(2, date.getDayOfWeek().getValue());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalTime t = LocalTime.parse(rs.getString(1));
                    LocalTime end = LocalTime.parse(rs.getString(2));
                    int len = rs.getInt(3);
                    while (!t.plusMinutes(len).isAfter(end)) {
                        slots.add(t);
                        t = t.plusMinutes(len);
                    }
                }
            }
        }
        return slots;
    }

    /** Slots that are still free (not booked, not in the past). */
    public List<LocalTime> freeSlots(int doctorId, LocalDate date) throws SQLException {
        try (Connection c = Database.getConnection()) {
            List<LocalTime> slots = scheduledSlots(c, doctorId, date);
            String sql = "SELECT slot_time FROM appointments WHERE doctor_id=? AND appt_date=? AND status <> 'CANCELLED'";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, doctorId);
                ps.setString(2, date.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) slots.remove(LocalTime.parse(rs.getString(1)));
                }
            }
            if (date.equals(LocalDate.now())) slots.removeIf(t -> t.isBefore(LocalTime.now()));
            return slots;
        }
    }
}
