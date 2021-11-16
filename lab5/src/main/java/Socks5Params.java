public final class Socks5Params {
    public static final byte VERSION = 0x05;
    public static final byte NO_AUTH = 0x00;
    public static final byte CONNECTION_COMMAND = 0x01;

    public static final byte[] AUTH_NO_AUTH_REPLY = new byte[]{VERSION, NO_AUTH};

    public static final byte ADDR_TYPE_IPV4 = 0x01;
    public static final byte ADDR_TYPE_HOST = 0x03;

    public static final byte STATUS_CODE_OK = 0x00;
    public static final byte STATUS_CODE_ERROR = 0x01;

    public static final byte[] CONNECTION_OK_REPLY = new byte[]{
            VERSION,
            STATUS_CODE_OK,
            0x00,
            ADDR_TYPE_IPV4,
            0x00, // dummy ip
            0x00,
            0x00,
            0x00,
            0x00,
            0x00 // dummy port
    };
}
