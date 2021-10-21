package ru.nsu.fit.ibaksheev.game;

import me.ippolitov.fit.snakes.SnakesProto;

import java.io.IOException;
import java.util.logging.Logger;

public class PlayerController {
    private static final int ANNOUNCE_INTERVAL_MS = 1000;

    public enum Role {
        MASTER,
        DEPUTY,
        NORMAL
    }

    private final UnicastManager unicastManager;
    private final MulticastManager multicastManager;
    private final PlayersManager playersManager;

    private volatile Role role;

    private final Thread announceWorkerThread;
    private final Thread listenUnicastWorkerThread;
    private final Thread listenMulticastWorkerThread;
    private final Thread sendGameStateWorkerThread;

    private static final Logger logger = Logger.getLogger(UnicastManager.class.getName());

    public PlayerController(int listenPort, Role role) throws IOException {
        this.role = role;

        unicastManager = new UnicastManager(listenPort);
        multicastManager = new MulticastManager();
        playersManager = new PlayersManager();

        announceWorkerThread = new Thread(this::announceWorker);
        announceWorkerThread.start();

        listenUnicastWorkerThread = new Thread(this::listenUnicastWorker);
        listenUnicastWorkerThread.start();

        listenMulticastWorkerThread = new Thread(this::listenMulticastWorker);
        listenMulticastWorkerThread.start();

        sendGameStateWorkerThread = new Thread(this::sendGameStateWorker);
        sendGameStateWorkerThread.start();
    }

    private void listenUnicastWorker() {
        while (true) {
            UnicastManager.ReceivedMessageWrapper msg;
            try {
                msg = unicastManager.receivePacket();
            } catch (InterruptedException e) {
                break;
            }
            if (role == Role.MASTER) {
                if (msg.getMessage().hasJoin()) {
                    playersManager.addPlayer(
                            new PlayersManager.PlayerSignature(msg.getIp(), msg.getPort()),
                            SnakesProto.GamePlayer.newBuilder()
                                    .setName(msg.getMessage().getJoin().getName())
                                    .setId(1)
                                    .setIpAddress(msg.getIp())
                                    .setPort(msg.getPort())
                                    .setScore(0)
                                    .build()
                    );
                }
            }
        }
    }

    private void listenMulticastWorker() {

    }

    private void sendGameStateWorker() {
        while (true) {
            if (role == Role.MASTER) {
                logger.info("sending game state to " + playersManager.getPlayers().size() + " players");
                var msg = SnakesProto.GameMessage.newBuilder()
                        .setState(
                                SnakesProto.GameMessage.StateMsg.newBuilder()
                                        .setState(SnakesProto.GameState.newBuilder()
                                                .setConfig(SnakesProto.GameConfig.getDefaultInstance())
                                                .setPlayers(
                                                        SnakesProto.GamePlayers.newBuilder()
                                                                .addAllPlayers(playersManager.getPlayers())
                                                )
                                                .setStateOrder(0)
                                                .build()
                                        )
                                        .build()
                        )
                        .setMsgSeq(0)
                        .build();
                for (var playerSignature : playersManager.getSignatures()) {
                    unicastManager.sendPacket(
                            playerSignature.getIp(),
                            playerSignature.getPort(),
                            msg
                    );
                }
            } else {
                // TODO: recv packet
            }
            try {
                Thread.sleep(ANNOUNCE_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void announceWorker() {
        while (true) {
            if (role == Role.MASTER) {
                multicastManager.sendPacket(
                        SnakesProto.GameMessage
                                .newBuilder()
                                .setAnnouncement(
                                        SnakesProto.GameMessage.AnnouncementMsg.
                                                newBuilder()
                                                .setCanJoin(true)
                                                .setConfig(SnakesProto.GameConfig.getDefaultInstance())
                                                .setPlayers(
                                                        SnakesProto.GamePlayers.newBuilder()
                                                                .addAllPlayers(playersManager.getPlayers())
                                                )
                                                .build()
                                )
                                .setMsgSeq(0)
                                .build()
                );
            } else {
                // TODO: recv packet
            }
            try {
                Thread.sleep(ANNOUNCE_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
