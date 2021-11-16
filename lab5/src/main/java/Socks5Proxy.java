import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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

    private void accept(SelectionKey key) throws IOException {
        var channel = (SocketChannel) key.channel();
        channel.configureBlocking(false);
        channel.register(key.selector(), SelectionKey.OP_READ);
    }

    private void read(SelectionKey key) throws IOException {
        var channel = (SocketChannel) key.channel();
        var attachment = (Attachment) key.attachment();

        if (attachment == null) {
            attachment = new Attachment();
            attachment.in = ByteBuffer.allocate(BUFFER_SIZE);
            key.attach(attachment);
        }

        // All data is read
        if (channel.read(attachment.in) <= 0) {
            close(key);
        }
        // First read, there is no second end, so read
        else if (attachment.peer == null) {
            readClientRequest(key);
        }
        // we know second send, so proxy bytes
        else {
            // start writing to second end
            attachment.peer.interestOpsOr(SelectionKey.OP_WRITE);
            // stop reading while buffer is not written
            key.interestOpsAnd(SelectionKey.OP_READ);
            attachment.in.flip();
        }
    }

    private void readClientRequest(SelectionKey key) {
        var channel = (SocketChannel) key.channel();
        var attachment = (Attachment) key.attachment();
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
