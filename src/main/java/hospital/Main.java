package hospital;

import hospital.model.*;
import hospital.service.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Console front-end for the Hospital Appointment & Queue Manager. */
public class Main {
    private static final Scanner in = new Scanner(System.in);
    private static final DoctorService doctors = new DoctorService();
    private static final PatientService patients = new PatientService();
    private static final AppointmentService appointments = new AppointmentService();
    private static final QueueService queue = new QueueService();

    public static void main(String[] args) throws Exception {
        Database.init();
        while (true) {
            System.out.println("""

                ===== HOSPITAL APPOINTMENT & QUEUE MANAGER =====
                 1. Add doctor              7. Cancel appointment
                 2. Set doctor availability 8. Check-in (appointment)
                 3. Register patient        9. Add walk-in / emergency
                 4. List doctors           10. View queue
                 5. Show free slots        11. Call next patient
                 6. Book appointment       12. Complete consultation
                                  13. Day schedule   0. Exit""");
            String choice = ask("Choose");
            try {
                switch (choice) {
                    case "1" -> System.out.println("Added: " + doctors.addDoctor(ask("Name"), ask("Specialization"), ask("Phone")));
                    case "2" -> {
                        int d = num("Doctor id");
                        doctors.addAvailability(d, num("Day of week (1=Mon..7=Sun)"),
                                LocalTime.parse(ask("Start (HH:mm)")), LocalTime.parse(ask("End (HH:mm)")),
                                num("Slot length (minutes)"));
                        System.out.println("Availability saved.");
                    }
                    case "3" -> System.out.println("Registered: " + patients.register(ask("Name"), ask("Phone")));
                    case "4" -> doctors.listDoctors().forEach(System.out::println);
                    case "5" -> {
                        List<LocalTime> free = doctors.freeSlots(num("Doctor id"), LocalDate.parse(ask("Date (yyyy-MM-dd)")));
                        System.out.println(free.isEmpty() ? "No free slots." : "Free slots: " + free);
                    }
                    case "6" -> {
                        int id = appointments.book(num("Doctor id"), num("Patient id"),
                                LocalDate.parse(ask("Date (yyyy-MM-dd)")), LocalTime.parse(ask("Time (HH:mm)")));
                        System.out.println("Booked. Appointment id = " + id);
                    }
                    case "7" -> { appointments.cancel(num("Appointment id")); System.out.println("Cancelled."); }
                    case "8" -> System.out.println("Checked in: " + queue.checkIn(num("Appointment id")));
                    case "9" -> System.out.println("Queued: " + queue.addWalkIn(num("Doctor id"), num("Patient id"),
                            ask("Emergency? (y/n)").equalsIgnoreCase("y")));
                    case "10" -> {
                        List<QueueEntry> q = queue.viewQueue(num("Doctor id"));
                        System.out.println(q.isEmpty() ? "Queue is empty." : "");
                        q.forEach(System.out::println);
                    }
                    case "11" -> System.out.println(queue.callNext(num("Doctor id"))
                            .map(e -> "Now calling: " + e).orElse("No patients waiting."));
                    case "12" -> { queue.complete(num("Doctor id")); System.out.println("Consultation completed."); }
                    case "13" -> appointments.listForDoctor(num("Doctor id"), LocalDate.parse(ask("Date (yyyy-MM-dd)")))
                            .forEach(System.out::println);
                    case "0" -> { System.out.println("Bye."); return; }
                    default -> System.out.println("Invalid choice.");
                }
            } catch (BookingException e) {
                System.out.println("✗ " + e.getMessage());
            } catch (java.time.format.DateTimeParseException | NumberFormatException e) {
                System.out.println("✗ Invalid input format.");
            } catch (java.sql.SQLException e) {
                System.out.println("✗ Database error: " + e.getMessage());
            }
        }
    }

    private static String ask(String label) { System.out.print(label + ": "); return in.nextLine().trim(); }
    private static int num(String label) { return Integer.parseInt(ask(label)); }
}
