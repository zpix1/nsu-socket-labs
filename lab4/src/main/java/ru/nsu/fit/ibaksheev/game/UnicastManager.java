package ru.nsu.fit.ibaksheev.game;

import me.ippolitov.fit.snakes.SnakesProto.GameMessage;
import ru.nsu.fit.ibaksheev.game.datatypes.MessageWithSender;
import ru.nsu.fit.ibaksheev.game.datatypes.ToSendMessageWrapper;

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

public class UnicastManager {
    private final static int ACK_CHECK_MS = 2000;
    private final static int BUF_SIZE = 65000;

    private final DatagramSocket socket;
    private long msgSeq = 0;

    private final BlockingQueue<ToSendMessageWrapper> sendQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<MessageWithSender> receiveQueue = new LinkedBlockingQueue<>();
    private final List<ToSendMessageWrapper> sentList = new ArrayList<>();

    private static final Logger logger = Logger.getLogger(UnicastManager.class.getName());

    private final Thread sendWorkerThread;
    private final Thread receiveWorkerThread;
    private final Thread ackCheckWorkerThread;

    public UnicastManager(int listenPort) throws SocketException {
        this.socket = new DatagramSocket(listenPort);

        sendWorkerThread = new Thread(this::sendWorker);
        sendWorkerThread.start();
        receiveWorkerThread = new Thread(this::receiveWorker);
        receiveWorkerThread.start();
        ackCheckWorkerThread = new Thread(this::ackCheckWorker);
        ackCheckWorkerThread.start();
    }

    public void sendPacket(String ip, int port, GameMessage msg) {
        sendQueue.add(ToSendMessageWrapper.builder().ip(ip).port(port).message(msg).build());
    }

    public MessageWithSender receivePacket() throws InterruptedException {
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
                receiveQueue.add(MessageWithSender.builder().message(gameMessage).port(receivePacket.getPort()).ip(receivePacket.getAddress().getHostAddress()).build());

                receivePacket.setLength(receiveBuffer.length);

                if (gameMessage.hasAck()) {
                    synchronized (sentList) {
                        var elem = sentList.stream()
                                .filter(wrapper -> wrapper.getMsgSeq() == gameMessage.getMsgSeq())
                                .findAny();
                        if (elem.isPresent()) {
                            sentList.remove(elem.get());
//                            logger.info("acked packet, very good");
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
                sentList.stream()
                        .filter(wrapper -> currentTime - wrapper.getSentAt() > ACK_CHECK_MS)
                        .forEach(wrapper -> {
                            if (wrapper.getRetryCount() > 0) {
                                wrapper.setRetryCount(wrapper.getRetryCount() - 1);
                                logger.warning("Got packet without ack, resending, retry n. " + wrapper.getRetryCount());
                                sendQueue.add(wrapper);
                            }
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

            var sendData = GameMessage.newBuilder(wrapper.getMessage()).setMsgSeq(msgSeq).build().toByteArray();
            wrapper.setMsgSeq(msgSeq);
            wrapper.setSentAt(System.currentTimeMillis());

            try {
                var packet = new DatagramPacket(
                        sendData,
                        sendData.length,
                        InetAddress.getByName(wrapper.getIp()),
                        wrapper.getPort()
                );

                synchronized (sentList) {
                    sentList.add(wrapper);
                }

                socket.send(packet);

//                logger.info("Sent " + packet);
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage());
            }
        }
    }
}
