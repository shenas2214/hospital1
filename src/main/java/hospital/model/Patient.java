package hospital.model;

public class Patient extends Person {
    public Patient(int id, String name, String phone) { super(id, name, phone); }

    @Override public String describe() {
        return "[" + id + "] " + name + (phone == null ? "" : " - " + phone);
    }
}
