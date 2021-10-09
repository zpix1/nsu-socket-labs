package ru.nsu.fit.ibaksheev.game;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import me.ippolitov.fit.snakes.SnakesProto.GameMessage;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NetManager {
    private final static int NODE_TIMEOUT_MS = 5000;
    private final static int ACK_CHECK_MS = 2000;
    private final static int BUF_SIZE = 65000;

    @Builder
    public static class ToSendMessageWrapper {
        @Getter
        @Setter
        private long msgSeq;
        @Getter
        private GameMessage.Builder builder;
        @Getter
        private String ip;
        @Getter
        private Integer port;
        @Getter
        @Setter
        private Long sentAt;
    }

    @Builder
    public static class ReceivedMessageWrapper {
        @Getter
        private GameMessage message;
        @Getter
        private String ip;
        @Getter
        private Integer port;
    }

    private final DatagramSocket socket;
    private long msgSeq = 0;

    private final BlockingQueue<ToSendMessageWrapper> sendQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<ReceivedMessageWrapper> receiveQueue = new LinkedBlockingQueue<>();
    private final List<ToSendMessageWrapper> sentList = new ArrayList<>();

    private static final Logger logger = Logger.getLogger(NetManager.class.getName());

    private final Thread sendWorkerThread;
    private final Thread receiveWorkerThread;
    private final Thread ackCheckWorkerThread;

    public NetManager(int listenPort) throws SocketException {
        this.socket = new DatagramSocket(listenPort);

        sendWorkerThread = new Thread(this::sendWorker);
        sendWorkerThread.start();
        receiveWorkerThread = new Thread(this::receiveWorker);
        receiveWorkerThread.start();
        ackCheckWorkerThread = new Thread(this::ackCheckWorker);
        ackCheckWorkerThread.start();
    }

    public void sendPacket(ToSendMessageWrapper wrapper) {
        sendQueue.add(wrapper);
    }

    public ReceivedMessageWrapper receivePacket() throws InterruptedException {
        return receiveQueue.take();
    }

    private void receiveWorker() {
        byte[] receiveBuffer = new byte[BUF_SIZE];
        while (true) {
            var receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            try {
                socket.receive(receivePacket);
                byte[] bytes = new byte[receivePacket.getLength()];
                System.arraycopy(receiveBuffer, 0, bytes, 0, receivePacket.getLength());
                var gameMessage = GameMessage.parseFrom(bytes);
                receiveQueue.add(ReceivedMessageWrapper.builder().message(gameMessage).ip(receivePacket.getAddress().toString()).build());

                receivePacket.setLength(receiveBuffer.length);

                if (gameMessage.hasAck()) {
                    synchronized (sentList) {
                        var elem = sentList.stream()
                                .filter(wrapper -> wrapper.getMsgSeq() == gameMessage.getMsgSeq())
                                .findAny();
                        if (elem.isPresent()) {
                            sentList.remove(elem.get());
                            logger.info("acked packet, very good");
                        } else {
                            logger.log(Level.WARNING, "got ack to a non-existent packet");
                        }
                    }
                } else {
                    var ackData = GameMessage.newBuilder()
                            .setAck(GameMessage.AckMsg.getDefaultInstance())
                            .setMsgSeq(gameMessage.getMsgSeq())
                            .build()
                            .toByteArray();
                    var ackPacket = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(), receivePacket.getPort());
                    socket.send(ackPacket);
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage());
            }
        }
    }

    private void ackCheckWorker() {
        while (true) {
            try {
                Thread.sleep(ACK_CHECK_MS);
            } catch (InterruptedException e) {
                break;
            }
            var currentTime = System.currentTimeMillis();
            synchronized (sentList) {
                sentList.stream().filter(wrapper -> currentTime - wrapper.getSentAt() > ACK_CHECK_MS).forEach(wrapper -> {
                    logger.warning("Got packet without ack, resending");
                    sendQueue.add(wrapper);
                });
                sentList.removeIf(wrapper -> currentTime - wrapper.getSentAt() > ACK_CHECK_MS);
            }
        }
    }

    private void sendWorker() {
        while (true) {
            ToSendMessageWrapper wrapper;
            try {
                wrapper = sendQueue.take();
            } catch (InterruptedException e) {
                break;
            }

            msgSeq++;

            var sendData = wrapper.getBuilder().setMsgSeq(msgSeq).build().toByteArray();
            wrapper.setMsgSeq(msgSeq);
            wrapper.setSentAt(System.currentTimeMillis());

            try {
                var packet = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(wrapper.getIp()), wrapper.getPort());

                synchronized (sentList) {
                    sentList.add(wrapper);
                }

                socket.send(packet);

                logger.info("Sent " + packet);
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage());
            }
        }
    }
}
