package hospital.model;

/** Abstract base class for people stored in the system. */
public abstract class Person {
    protected final int id;
    protected final String name;
    protected final String phone;

    protected Person(int id, String name, String phone) {
        this.id = id;
        this.name = name;
        this.phone = phone;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }

    public abstract String describe();

    @Override public String toString() { return describe(); }
}
