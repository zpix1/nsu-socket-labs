package ru.nsu.fit.ibaksheev.game;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import me.ippolitov.fit.snakes.SnakesProto;
import me.ippolitov.fit.snakes.SnakesProto.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class UnicastMsgManager {
    @AllArgsConstructor
    public static class GameMessageWrapper {
        @Getter
        private GameMessage message;
        @Getter
        private String senderIp;
        @Getter
        private int senderPort;
    }

    private final Set<GamePlayer> activePlayers = new HashSet<>();

    // listen for any GameMessages on port, send Acks for each received message
    public Observable<GameMessageWrapper> getMessageListener(int port) {
        ObservableOnSubscribe<GameMessageWrapper> handler = emitter -> {
            var receiveSocket = new DatagramSocket(port);
            var sendSocket = new DatagramSocket();
            byte[] receiveBuffer = new byte[GameMessage.getDefaultInstance().getSerializedSize()];
            while (true) {
                var receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                receiveSocket.receive(receivePacket);
                var gameMessage = GameMessage.parseFrom(receivePacket.getData());
                var sendData = GameMessage.newBuilder()
                        .setAck(GameMessage.AckMsg.getDefaultInstance())
                        .setMsgSeq(gameMessage.getMsgSeq())
                        .build()
                        .toByteArray();
                var ackPacket = new DatagramPacket(sendData, sendData.length, receivePacket.getAddress(), receivePacket.getPort());
                sendSocket.send(ackPacket);
                emitter.onNext(new GameMessageWrapper(
                        gameMessage,
                        receivePacket.getAddress().toString(),
                        receivePacket.getPort()
                ));
            }
        };
        return Observable.create(handler).subscribeOn(Schedulers.io());
    }

    public PublishProcessor<GameMessageWrapper> getMessagePublisher() {
        PublishProcessor<GameMessageWrapper> processor = PublishProcessor.create();
        processor.observeOn(Schedulers.io())
                .doAfterNext(e -> System.out.println(e.getSenderIp()))
                .subscribe();
        return processor;
    }
}
