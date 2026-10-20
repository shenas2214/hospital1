package hospital;

/** Business-rule violation (conflict, invalid slot, wrong state...). */
public class BookingException extends Exception {
    public BookingException(String message) { super(message); }
}
