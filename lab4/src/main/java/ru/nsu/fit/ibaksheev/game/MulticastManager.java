package ru.nsu.fit.ibaksheev.game;

import me.ippolitov.fit.snakes.SnakesProto;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MulticastManager {
    private final static int BUF_SIZE = 65000;
    private final static int multicastPort = 9192;
    private final static String multicastGroupIp = "239.192.0.4";

    private final BlockingQueue<SnakesProto.GameMessage> sendQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<SnakesProto.GameMessage> receiveQueue = new LinkedBlockingQueue<>();

    private final MulticastSocket socket;

    private final Thread sendWorkerThread;
    private final Thread receiveWorkerThread;

    private static final Logger logger = Logger.getLogger(UnicastManager.class.getName());

    public MulticastManager() throws IOException {
        this.socket = new MulticastSocket(multicastPort);
        socket.joinGroup(InetAddress.getByName(multicastGroupIp));

        sendWorkerThread = new Thread(this::sendWorker);
        sendWorkerThread.start();
        receiveWorkerThread = new Thread(this::receiveWorker);
        receiveWorkerThread.start();
    }

    public void sendPacket(SnakesProto.GameMessage message) {
        sendQueue.add(message);
    }

    public SnakesProto.GameMessage receivePacket() throws InterruptedException {
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
                receiveQueue.add(gameMessage);
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
                        InetAddress.getByName(multicastGroupIp),
                        multicastPort
                );

                socket.send(packet);
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage());
            }
        }
    }
}
