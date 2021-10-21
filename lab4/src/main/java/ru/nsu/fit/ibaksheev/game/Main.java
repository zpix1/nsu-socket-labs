package ru.nsu.fit.ibaksheev.game;

import me.ippolitov.fit.snakes.SnakesProto;

import java.io.IOException;
import java.net.SocketException;
import java.util.logging.LogManager;

public class Main {
    static void recv() {
        try {
            MulticastManager receiver = new MulticastManager();
            var msg = receiver.receivePacket();
            System.out.println(msg);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void testUnicast() throws InterruptedException, SocketException {
        var sender = new UnicastManager(5000);
        var receiver = new UnicastManager(6000);

        var messageBuilder = SnakesProto.GameMessage.newBuilder()
                .setPing(SnakesProto.GameMessage.PingMsg.getDefaultInstance());

        sender.sendPacket("localhost", 6000, messageBuilder.build());

        System.out.println(receiver.receivePacket().getMessage());
    }

    public static void testMulticast() throws IOException {
        var sender = new MulticastManager();

        var message = SnakesProto.GameMessage.newBuilder()
                .setPing(SnakesProto.GameMessage.PingMsg.getDefaultInstance())
                .setMsgSeq(5)
                .build();

        new Thread(Main::recv).start();
        new Thread(Main::recv).start();
        new Thread(Main::recv).start();

        sender.sendPacket(message);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));

        var player1 = new PlayerController(5001, PlayerController.Role.MASTER);
        var player2 = new PlayerController(5002, PlayerController.Role.NORMAL);

        Thread.sleep(3000);
    }
}
