package ru.nsu.fit.ibaksheev.game;

import me.ippolitov.fit.snakes.SnakesProto;
import ru.nsu.fit.ibaksheev.game.datatypes.MessageWithSender;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MulticastManager {
    private final static int BUF_SIZE = 65000;

    private final BlockingQueue<SnakesProto.GameMessage> sendQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<MessageWithSender> receiveQueue = new LinkedBlockingQueue<>();

    private final MulticastSocket socket;

    private final Thread sendWorkerThread;
    private final Thread receiveWorkerThread;

    private static final Logger logger = Logger.getLogger(UnicastManager.class.getName());

    public MulticastManager() throws IOException {
        this.socket = new MulticastSocket(Config.MULTICAST_PORT);
        socket.joinGroup(InetAddress.getByName(Config.MULTICAST_GROUP_IP));

        sendWorkerThread = new Thread(this::sendWorker);
        sendWorkerThread.start();
        receiveWorkerThread = new Thread(this::receiveWorker);
        receiveWorkerThread.start();
    }

    public void sendPacket(SnakesProto.GameMessage message) {
        sendQueue.add(message);
    }

    // WF
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
                var gameMessage = SnakesProto.GameMessage.parseFrom(bytes);
                receiveQueue.add(
                        MessageWithSender.builder()
                                .ip(receivePacket.getAddress().toString())
                                .port(receivePacket.getPort())
                                .message(gameMessage)
                                .build()
                );
            } catch (IOException e) {
                logger.warning(e.getLocalizedMessage());
            }
        }
    }

    private void sendWorker() {
        while (true) {
            SnakesProto.GameMessage message;
            try {
                message = sendQueue.take();
            } catch (InterruptedException e) {
                break;
            }

            var sendData = message.toByteArray();

            try {
                var packet = new DatagramPacket(
                        sendData,
                        sendData.length,
                        InetAddress.getByName(Config.MULTICAST_GROUP_IP),
                        Config.MULTICAST_PORT
                );

                socket.send(packet);
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage());
            }
        }
    }
}
