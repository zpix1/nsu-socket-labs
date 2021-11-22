import java.text.ParseException;

public class Socks5Exception extends ParseException {
    public Socks5Exception(String message, int where) {
        super(message, where);
    }
}
