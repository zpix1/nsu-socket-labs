package ru.nsu.fit.ibaksheev.game.io;

import me.ippolitov.fit.snakes.SnakesProto;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.logging.LogManager;

public class Main {
    static void recv() {
        try {
            MulticastManager receiver = new MulticastManager(new DatagramSocket(5000));
            var msg = receiver.receivePacket();
            System.out.println(msg.getIp());
            System.out.println(msg.getPort());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void testUnicast() throws InterruptedException, SocketException {
        var sender = new UnicastManager(new DatagramSocket(5000));
        var receiver = new UnicastManager(new DatagramSocket(6000));

        var messageBuilder = SnakesProto.GameMessage.newBuilder()
                .setPing(SnakesProto.GameMessage.PingMsg.getDefaultInstance());

        sender.sendPacket("localhost", 6000, messageBuilder.build());

        System.out.println(receiver.receivePacket().getMessage());
    }

    public static void testMulticast() throws IOException {
        var sender = new MulticastManager(new DatagramSocket(5000));

        var message = SnakesProto.GameMessage.newBuilder()
                .setPing(SnakesProto.GameMessage.PingMsg.getDefaultInstance())
                .setMsgSeq(5)
                .build();

        new Thread(Main::recv).start();
        new Thread(Main::recv).start();
        new Thread(Main::recv).start();

        sender.sendPacket(message);
    }

    public static void killPlayer(PlayerController p) {
        System.out.println("Killing id=" + p.getMyId());
        p.stop();
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));

//        testMulticast();
        var player1 = new PlayerController("Master roflan", 5001, SnakesProto.NodeRole.MASTER);
        var player2 = new PlayerController("Deputy roflan", 5002, SnakesProto.NodeRole.NORMAL);
        Thread.sleep(1000);
        var player3 = new PlayerController("Just roflan", 5003, SnakesProto.NodeRole.NORMAL);

        Thread.sleep(11000);

        killPlayer(player2);

//        Thread.sleep(15000);

//        var player4 = new PlayerController("New chel", 5004, SnakesProto.NodeRole.NORMAL);
//
//        Thread.sleep(10000);
//
//        killPlayer(player2);
    }
}
