package ru.nsu.fit.ibaksheev.game;

import me.ippolitov.fit.snakes.SnakesProto;

import java.io.IOException;
import java.util.logging.LogManager;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException {
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));

        var sender = new UnicastMsgManager(5000);
        var receiver = new UnicastMsgManager(6000);
        var messageBuilder = SnakesProto.GameMessage.newBuilder()
                .setPing(SnakesProto.GameMessage.PingMsg.getDefaultInstance());

        sender.sendPacket(UnicastMsgManager.ToSendMessageWrapper.builder().ip("localhost").port(6000).builder(messageBuilder).build());

        System.out.println(receiver.receivePacket().getMessage());
    }
}
