package hospital.model;

public class QueueEntry {
    private final int id;
    private final int tokenNo;
    private final String patientName;
    private final int priority;
    private final QueueStatus status;

    public QueueEntry(int id, int tokenNo, String patientName, int priority, QueueStatus status) {
        this.id = id; this.tokenNo = tokenNo; this.patientName = patientName;
        this.priority = priority; this.status = status;
    }

    public int getId() { return id; }
    public int getTokenNo() { return tokenNo; }
    public String getPatientName() { return patientName; }
    public QueueStatus getStatus() { return status; }

    @Override public String toString() {
        return "Token " + tokenNo + "  " + patientName
                + (priority == 1 ? "  (EMERGENCY)" : "") + "  [" + status + "]";
    }
}
