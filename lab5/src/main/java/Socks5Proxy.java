import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;

public class Socks5Proxy implements Runnable {
    private static final int BUFFER_SIZE = 1 << 16;

    private final int port;
    private final String host;

    private static class Attachment {
        public ByteBuffer in;
        public ByteBuffer out;
        public SelectionKey peer;
        public boolean isDNS;
    }

    public Socks5Proxy(String host, int port) {
        this.port = port;
        this.host = host;
    }

    @Override
    public void run() {
        try {
            var selector = SelectorProvider.provider().openSelector();
            var serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(host, port));

            while (true) {
                selector.select();
                var iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    var key = iterator.next();
                    iterator.remove();

                    if (key.isValid()) {
                        try {
                            if (key.isAcceptable()) {
                                accept(key);
                            } else if (key.isConnectable()) {
                                connect(key);
                            } else if (key.isReadable()) {
                                read(key);
                            } else if (key.isWritable()) {
                                write(key);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            close(key);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: ./socks5 PORT");
            return;
        }

        var port = Integer.parseInt(args[0]);
        var host = "127.0.0.1";

        var proxy = new Socks5Proxy(host, port);

        proxy.run();
    }
}
