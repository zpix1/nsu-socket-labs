import org.xbill.DNS.Record;
import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Socks5Proxy implements Runnable {
    private static final Logger log = Logger.getLogger(Socks5Proxy.class.getName());

    private static final int BUFFER_SIZE = 1 << 16;

    private final int port;
    private final String host;

    private enum Type {
        // socks request and its body, also reply from server
        READ,
        WRITE,
        // dns request and reply
        DNS_READ,
        DNS_WRITE,
        // auth request and reply
        AUTH_READ,
        AUTH_WRITE
    }

    private static class Attachment {
        Type type;

        public ByteBuffer in;
        public ByteBuffer out;
        public SelectionKey peer;

        int port;
    }

    public Socks5Proxy(String host, int port) {
        this.port = port;
        this.host = host;
    }

    void debug(String text, ByteBuffer b) {
//        var ar = b.array();
//        StringBuilder s = new StringBuilder(text);
//        s.append(": ");
//        int to = b.remaining() == 0 ? b.position() : b.remaining();
//        to = Math.min(to, 1000);
//        for (var i = 0; i < to; i++) {
//            s.append(String.format(" %02x", ar[i]));
//        }
//        log.info(s.toString());
    }

    @Override
    public void run() {
        try {
            var selector = SelectorProvider.provider().openSelector();
            var serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(host, port));
            serverChannel.register(selector, serverChannel.validOps());

            while (true) {
                selector.select();
                var iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    var key = iterator.next();
                    iterator.remove();

                    if (key.isValid()) {
                        var attachment = (Attachment) key.attachment();
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
        log.info("Accepted client");
        var channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        channel.register(key.selector(), SelectionKey.OP_READ);
    }

    private void connect(SelectionKey key) throws IOException {
        var channel = ((SocketChannel) key.channel());
        var attachment = ((Attachment) key.attachment());
        channel.finishConnect();

        attachment.in = ByteBuffer.allocate(BUFFER_SIZE);
        attachment.in.put(Socks5Params.CONNECTION_OK_REPLY).flip();

        attachment.out = ((Attachment) attachment.peer.attachment()).in;
        ((Attachment) attachment.peer.attachment()).out = attachment.in;

        attachment.peer.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);

        key.interestOps(0);
    }

    private void read(SelectionKey key) throws IOException, Socks5Exception {
        var attachment = (Attachment) key.attachment();
        if (attachment == null) {
            log.info("Created AUTH_READ attachment for client");
            attachment = new Attachment();
            attachment.type = Type.AUTH_READ;
            attachment.in = ByteBuffer.allocate(BUFFER_SIZE);
            key.attach(attachment);
        }

        if (attachment.type == Type.DNS_READ) {
            var channel = (DatagramChannel) key.channel();
            if (channel.read(attachment.in) <= 0) {
                close(key);
                throw new IOException("Invalid DNS reply");
            } else {
                var message = new Message(attachment.in.array());
                var maybeRecord = message.getSection(Section.ANSWER).stream().findAny();
                if (maybeRecord.isPresent()) {
                    var ipAddr = InetAddress.getByName(maybeRecord.get().rdataToString());
                    log.info("Resolved: " + maybeRecord.get().rdataToString());
                    registerPeer(ipAddr, attachment.port, attachment.peer);
                    key.interestOps(0);
                    closeEnd(key);
                } else {
                    log.warning(message.toString());
                    close(key);
                    throw new RuntimeException("Host cannot be resolved");
                }
            }
        } else {
            var channel = (SocketChannel) key.channel();

            // All data is read
            if (channel.read(attachment.in) <= 0) {
                log.info("Read all data, closing connection");
                close(key);
            }
            // First read, there is no second end, so read auth request
            else if (attachment.type == Type.AUTH_READ) {
                log.info("Reading header");
                scanAndReplyAuthRequest(key);
            } else if (attachment.peer == null) {
                scanAndReplyConnectionRequest(key);
            }
            // We know second send, so proxy bytes
            else {
                debug("Read", attachment.in);
                attachment.peer.interestOps(attachment.peer.interestOps() | SelectionKey.OP_WRITE);
                key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
                attachment.in.flip();
            }
        }
    }

    private void scanAndReplyAuthRequest(SelectionKey key) throws Socks5Exception {
        var attachment = (Attachment) key.attachment();

        var len = attachment.in.position();
        if (len < 2) {
            log.warning(len + " is too small for header, waiting");
            return;
        }

        var data = attachment.in.array();

        if (data[0] != Socks5Params.VERSION) {
            throw new Socks5Exception("Auth request has invalid version, only SOCKS5 is supported", 0);
        }

        var methods_count = data[1];
        if (len - 2 != methods_count) {
            log.warning(len - 2 + " is too small or too big for " + methods_count + " auth methods, waiting");
            return;
        }

        // Find no auth method
        boolean isNoAuthMethodFound = false;
        for (int method_i = 0; method_i < methods_count; method_i++) {
            var method = data[method_i + 2];
            if (method == Socks5Params.NO_AUTH) {
                isNoAuthMethodFound = true;
                break;
            }
        }

        if (!isNoAuthMethodFound) {
            throw new Socks5Exception("Auth request has no no auth method, only no auth method is supported", len);
        }

        log.info("Successfully parsed auth header: " + String.format("%X %X %X %X", data[0], data[1], data[2], data[3]));

        // Reply with no auth choice
        attachment.out = attachment.in;
        attachment.out.clear();
        attachment.out.put(Socks5Params.AUTH_NO_AUTH_REPLY).flip();
        attachment.type = Type.AUTH_WRITE;

        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void scanAndReplyConnectionRequest(SelectionKey key) throws Socks5Exception, IOException {
        var attachment = (Attachment) key.attachment();
        var len = attachment.in.position();
        if (len < 4) {
            log.warning(len + " is too small for connection request, waiting");
            return;
        }

        var data = attachment.in.array();
        if (data[0] != Socks5Params.VERSION) {
            throw new Socks5Exception("Connection request has invalid version, only SOCKS5 is supported", 0);
        }

        if (data[1] != Socks5Params.CONNECTION_COMMAND) {
            throw new Socks5Exception("0x%02x command is not supported, only 0x01 (connect) is supported", 1);
        }

        if (data[3] == Socks5Params.ADDR_TYPE_IPV4) {
            var connectAddrBytes = new byte[]{data[4], data[5], data[6], data[7]};
            var connectAddr = InetAddress.getByAddress(connectAddrBytes);
            var portPos = 8;
            var connectPort = ((data[portPos] & 0xFF) << 8) + (data[portPos + 1] & 0xFF);

            registerPeer(connectAddr, connectPort, key);
            key.interestOps(0);
        } else if (data[3] == Socks5Params.ADDR_TYPE_HOST) {
            var hostLen = data[4];
            var hostStart = 5;
            if (len < hostLen + hostStart + 2) {
                log.warning("waiting for more data");
                return;
            } else if (len > hostLen + hostStart + 2) {
                throw new Socks5Exception("invalid connection request", 4);
            }
            var host = new String(Arrays.copyOfRange(data, hostStart, hostStart + hostLen));
            var portPos = hostStart + hostLen;
            var connectPort = ((data[portPos] & 0xFF) << 8) + (data[portPos + 1] & 0xFF);

            log.info(String.format("Resolving host %s:%d", host, connectPort));

            key.interestOps(0);

            requestHostResolve(host, connectPort, key);
        }

        log.info("Successfully parsed connection header");
    }

    private void registerPeer(InetAddress connectAddr, int connectPort, SelectionKey backKey) throws IOException {
        log.info(String.format("Connecting to addr %s:%d", connectAddr, connectPort));

        var peer = SocketChannel.open();
        peer.configureBlocking(false);
        peer.connect(new InetSocketAddress(connectAddr, connectPort));
        var peerKey = peer.register(backKey.selector(), SelectionKey.OP_CONNECT);

        ((Attachment) backKey.attachment()).peer = peerKey;
        Attachment peerAttachment = new Attachment();
        peerAttachment.peer = backKey;
        peerKey.attach(peerAttachment);

        ((Attachment) backKey.attachment()).in.clear();
    }

    private void requestHostResolve(String host, int backPort, SelectionKey backKey) throws IOException {
        var peer = DatagramChannel.open();
        peer.connect(ResolverConfig.getCurrentConfig().server());
        peer.configureBlocking(false);

        var key = peer.register(backKey.selector(), SelectionKey.OP_WRITE);

        Attachment attachment = new Attachment();
        attachment.type = Type.DNS_WRITE;
        attachment.port = backPort;
        attachment.peer = backKey;
        attachment.in = ByteBuffer.allocate(BUFFER_SIZE);

        var message = new Message();
        var record = Record.newRecord(Name.fromString(host + '.').canonicalize(), org.xbill.DNS.Type.A, DClass.IN);
        message.addRecord(record, Section.QUESTION);

        var header = message.getHeader();
//        header.setFlag(Flags.Q);
        header.setFlag(Flags.AD);
        header.setFlag(Flags.RD);

        attachment.in.put(message.toWire());
        attachment.in.flip();
        attachment.out = attachment.in;

        key.attach(attachment);

        log.info("Registered write to server dns request");
    }

    private void write(SelectionKey key) throws IOException {
        var attachment = ((Attachment) key.attachment());
        if (attachment.type == Type.DNS_WRITE) {
            var channel = ((DatagramChannel) key.channel());

            debug("DNS request", attachment.out);

            if (channel.write(attachment.out) == -1) {
                close(key);
            } else if (attachment.out.remaining() == 0) {
                log.info("Sent DNS request");
                attachment.out.clear();
                attachment.type = Type.DNS_READ;
                key.interestOpsOr(SelectionKey.OP_READ);
                key.interestOpsAnd(~SelectionKey.OP_WRITE);
            }
        } else {
            var channel = ((SocketChannel) key.channel());

            debug("Write", attachment.out);

            if (channel.write(attachment.out) == -1) {
                close(key);
            } else if (attachment.out.remaining() == 0) {
                if (attachment.type == Type.AUTH_WRITE) {
                    log.info("Writing auth reply");
                    attachment.out.clear();
                    key.interestOps(SelectionKey.OP_READ);
                    attachment.type = Type.READ;
                } else if (attachment.peer == null) {
                    close(key);
                } else {
                    attachment.out.clear();
                    attachment.peer.interestOps(attachment.peer.interestOps() | SelectionKey.OP_READ);
                    key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
                }
            }
        }
    }

    private static void closeEnd(SelectionKey key) throws IOException {
        key.cancel();
        key.channel().close();
    }

    private static void close(SelectionKey key) throws IOException {
        key.cancel();
        key.channel().close();
        SelectionKey peerKey = ((Attachment) key.attachment()).peer;
        if (peerKey != null) {
            ((Attachment) peerKey.attachment()).peer = null;
            if ((peerKey.interestOps() & SelectionKey.OP_WRITE) == 0) {
                ((Attachment) peerKey.attachment()).out.flip();
            }
            peerKey.interestOps(SelectionKey.OP_WRITE);
        }
    }

    public static void main(String[] args) throws IOException {
        LogManager.getLogManager().readConfiguration(Socks5Proxy.class.getResourceAsStream("/logging.properties"));

        if (args.length != 1) {
            System.err.println("usage: ./socks5 PORT");
            return;
        }

        var port = Integer.parseInt(args[0]);
        var host = "localhost";

        log.info("Started at " + host + ":" + port);
        var proxy = new Socks5Proxy(host, port);

        proxy.run();
    }
}
