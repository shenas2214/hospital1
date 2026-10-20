# Hospital Appointment & Queue Manager (Java + JDBC + SQLite)

## Run
    mvn compile exec:java          # Java 17+, Maven; DB file hospital.db is created automatically

## Suggested first run
1. Add doctor -> 2. Set availability (e.g. doctor 1, day 1 = Monday, 09:00-12:00, 15 min slots)
3. Register patient -> 5. Show free slots -> 6. Book -> try booking the same slot again (rejected)
4. On the appointment day: 8. Check-in -> 10. View queue -> 11. Call next -> 12. Complete

## Tables
doctors, patients, doctor_availability, appointments, queue_entries

## How conflicts are prevented
* Slot must exist inside the doctor's availability window.
* Unique partial index (doctor_id, appt_date, slot_time) for non-cancelled appointments.
* Unique partial index (patient_id, appt_date, slot_time) -> patient cannot be double-booked.
* Booking runs in a transaction; a unique-constraint failure is turned into a friendly BookingException.
* Queue: unique (doctor_id, queue_date, token_no); one IN_PROGRESS patient per doctor.
