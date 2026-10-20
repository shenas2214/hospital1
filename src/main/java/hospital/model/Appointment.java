package hospital.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class Appointment {
    private final int id;
    private final int doctorId;
    private final int patientId;
    private final String patientName;
    private final LocalDate date;
    private final LocalTime time;
    private final AppointmentStatus status;

    public Appointment(int id, int doctorId, int patientId, String patientName,
                       LocalDate date, LocalTime time, AppointmentStatus status) {
        this.id = id; this.doctorId = doctorId; this.patientId = patientId;
        this.patientName = patientName; this.date = date; this.time = time; this.status = status;
    }

    public int getId() { return id; }
    public int getDoctorId() { return doctorId; }
    public int getPatientId() { return patientId; }
    public LocalDate getDate() { return date; }
    public LocalTime getTime() { return time; }
    public AppointmentStatus getStatus() { return status; }

    @Override public String toString() {
        return "#" + id + "  " + date + " " + time + "  " + patientName + "  [" + status + "]";
    }
}
