package ru.nsu.fit.ibaksheev.game;

import lombok.AllArgsConstructor;
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

public class UnicastMsgManager {
    private final static int NODE_TIMEOUT_MS = 5000;

    @AllArgsConstructor
    public static class GameMessageWrapper {
        @Getter
        private GameMessage message;
        @Getter
        private String senderIp;
        @Getter
        private int senderPort;
        @Getter
        @Setter
        private long sentAt;
    }

    private int listenPort;
    private DatagramSocket sendSocket;
    private DatagramSocket receiveSocket;

    private final BlockingQueue<GameMessageWrapper> sendQueue = new LinkedBlockingQueue<>();
    private final List<GameMessageWrapper> sentList = new ArrayList<>();

    private static final Logger logger = Logger.getLogger(UnicastMsgManager.class.getName());

    public UnicastMsgManager(int listenPort) throws SocketException {
        this.listenPort = listenPort;
        this.sendSocket = new DatagramSocket();
        this.receiveSocket = new DatagramSocket(listenPort);
    }

    public void sendPacket(GameMessageWrapper wrapper) {
        sendQueue.add(wrapper);
    }

    private void receiveWorker() {
        byte[] receiveBuffer = new byte[GameMessage.getDefaultInstance().getSerializedSize()];
        while (true) {
            var receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            try {
                receiveSocket.receive(receivePacket);
                var gameMessage = GameMessage.parseFrom(receivePacket.getData());
                if (gameMessage.hasAck()) {
                    synchronized (sentList) {
                        var elem = sentList.stream()
                                .filter(wrapper -> wrapper.getMessage().getMsgSeq() == gameMessage.getMsgSeq())
                                .findAny();
                        if (elem.isPresent()) {
                            sentList.remove(elem.get());
                        } else {
                            logger.log(Level.WARNING, "got ack to non-existent packet");
                        }
                    }
                } else {
                    var ackData = GameMessage.newBuilder()
                            .setAck(GameMessage.AckMsg.getDefaultInstance())
                            .setMsgSeq(gameMessage.getMsgSeq())
                            .build()
                            .toByteArray();
                    var ackPacket = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(), receivePacket.getPort());
                    sendSocket.send(ackPacket);
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
            var wrapper = sendQueue.poll();
            if (wrapper != null) {
                var sendData = wrapper.getMessage().toByteArray();
                try {
                    var packet = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(wrapper.getSenderIp()), wrapper.getSenderPort());
                    wrapper.setSentAt(System.currentTimeMillis());
                    sendSocket.send(packet);
                    synchronized (sentList) {
                        sentList.add(wrapper);
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE, e.getLocalizedMessage());
                }
            } else {
                logger.log(Level.SEVERE, "got null message in send queue");
            }
        }
    }
}
