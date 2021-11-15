import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public class Socks5Proxy implements Runnable {
    private static final int BUFFER_SIZE = 1 << 16;

    private final int port;
    private final String host;

    private static class Attachment {
        public ByteBuffer in;
        public ByteBuffer out;
        public SelectionKey peer;
    }

    public Socks5Proxy(int port, String host) {
        this.port = port;
        this.host = host;
    }

    @Override
    public void run() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: ./socks5 PORT");
            return;
        }

        var port = Integer.parseInt(args[0]);
        var host = "127.0.0.1";

        var proxy = new Socks5Proxy(port, host);

        proxy.run();
    }
}
