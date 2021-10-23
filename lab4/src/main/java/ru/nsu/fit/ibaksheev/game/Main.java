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

        var player1 = new PlayerController("Master roflan", 5001, SnakesProto.NodeRole.MASTER);
        var player2 = new PlayerController("Deputy roflan", 5002, SnakesProto.NodeRole.NORMAL);
        Thread.sleep(1000);
        var player3 = new PlayerController("Just roflan", 5003, SnakesProto.NodeRole.NORMAL);

        Thread.sleep(11000);

        System.out.println("killing");
        player3.stop();
        System.out.println("killed");
    }
}
