package hospital.model;

public class Doctor extends Person {
    private final String specialization;

    public Doctor(int id, String name, String specialization, String phone) {
        super(id, name, phone);
        this.specialization = specialization;
    }

    public String getSpecialization() { return specialization; }

    @Override public String describe() {
        return "[" + id + "] Dr. " + name + " (" + specialization + ")";
    }
}
