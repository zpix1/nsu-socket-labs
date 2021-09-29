package ru.nsu.fit.ibaksheev.game;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import me.ippolitov.fit.snakes.SnakesProto;
import me.ippolitov.fit.snakes.SnakesProto.GameMessage;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UnicastMsgManager {
    private final static int NODE_TIMEOUT_MS = 5000;
    private final static int BUF_SIZE = 4048;

    @Builder
    public static class ToSendMessageWrapper {
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

    private int listenPort;
    private DatagramSocket sendSocket;
    private DatagramSocket receiveSocket;
    private int seq = 0;

    private final Map<Integer, SnakesProto.GamePlayer> users = new HashMap<>();
    private final BlockingQueue<ToSendMessageWrapper> sendQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<ReceivedMessageWrapper> receiveQueue = new LinkedBlockingQueue<>();
    private final List<ToSendMessageWrapper> sentList = new ArrayList<>();

    private static final Logger logger = Logger.getLogger(UnicastMsgManager.class.getName());

    private Thread sendWorkerThread;
    private Thread receiveWorkerThread;

    public UnicastMsgManager(int listenPort) throws SocketException {
        this.listenPort = listenPort;
        this.sendSocket = new DatagramSocket();
        this.receiveSocket = new DatagramSocket(listenPort);

        sendWorkerThread = new Thread(this::sendWorker);
        sendWorkerThread.start();
        receiveWorkerThread = new Thread(this::receiveWorker);
        receiveWorkerThread.start();
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
                receiveSocket.receive(receivePacket);
                byte[] bytes = new byte[receivePacket.getLength()];
                System.arraycopy(receiveBuffer, 0, bytes, 0, receivePacket.getLength());
                var gameMessage = GameMessage.parseFrom(bytes);
                receiveQueue.add(ReceivedMessageWrapper.builder().message(gameMessage).ip(receivePacket.getAddress().toString()).build());

                receivePacket.setLength(receiveBuffer.length);

                if (gameMessage.hasAck()) {
//                    synchronized (sentList) {
//                        var elem = sentList.stream()
//                                .filter(wrapper -> wrapper.getMessage().getMsgSeq() == gameMessage.getMsgSeq())
//                                .findAny();
//                        if (elem.isPresent()) {
//                            sentList.remove(elem.get());
//                        } else {
//                            logger.log(Level.WARNING, "got ack to non-existent packet");
//                        }
//                    }
                } else {
//                    var ackData = GameMessage.newBuilder()
//                            .setAck(GameMessage.AckMsg.getDefaultInstance())
//                            .setMsgSeq(gameMessage.getMsgSeq())
//                            .build()
//                            .toByteArray();
//                    var ackPacket = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(), receivePacket.getPort());
//                    sendSocket.send(ackPacket);
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage());
            }
        }
    }

    private void ackCheckWorker() {
        while (true) {
            try {
                Thread.sleep(NODE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                break;
            }
            var currentTime = System.currentTimeMillis();
            synchronized (sentList) {
                sentList.stream().filter(wrapper -> currentTime - wrapper.getSentAt() > NODE_TIMEOUT_MS).forEach(wrapper -> {
                    sendQueue.add(wrapper);
                    sentList.remove(wrapper);
                });
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
            var sendData = wrapper.getBuilder().setMsgSeq(++seq).build().toByteArray();
            try {
                var packet = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(wrapper.getIp()), wrapper.getPort());
                wrapper.setSentAt(System.currentTimeMillis());
                sendSocket.send(packet);
                logger.info("Sent " + packet);
//                    synchronized (sentList) {
//                        sentList.add(wrapper);
//                    }
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage());
            }
        }
    }
}
