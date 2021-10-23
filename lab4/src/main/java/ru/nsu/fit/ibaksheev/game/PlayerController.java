package ru.nsu.fit.ibaksheev.game;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import me.ippolitov.fit.snakes.SnakesProto;
import ru.nsu.fit.ibaksheev.game.datatypes.PlayerSignature;
import ru.nsu.fit.ibaksheev.game.datatypes.MessageWithSender;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PlayerController {
    private static final int ANNOUNCE_INTERVAL_MS = 1000;

    private final UnicastManager unicastManager;
    private final MulticastManager multicastManager;
    private final PlayersManager playersManager;
    private final AvailableGamesManager availableGamesManager;

    private volatile SnakesProto.NodeRole role;

    private final Thread announceWorkerThread;
    private final Thread listenUnicastWorkerThread;
    private final Thread listenMulticastWorkerThread;
    private final Thread sendGameStateWorkerThread;

    private final Disposable pingDisposable;

    private final String name;

    private static final Logger logger = Logger.getLogger(UnicastManager.class.getName());

    public PlayerController(String name, int listenPort, SnakesProto.NodeRole role) throws IOException, InterruptedException {
        this.name = name;
        this.role = role;

        unicastManager = new UnicastManager(listenPort);
        multicastManager = new MulticastManager();
        playersManager = new PlayersManager(this::onPlayerDeadListener);
        availableGamesManager = new AvailableGamesManager();

        pingDisposable = pingWorker();
        infoWorker();

        announceWorkerThread = new Thread(this::announceWorker);
        announceWorkerThread.start();

        listenUnicastWorkerThread = new Thread(this::listenUnicastWorker);
        listenUnicastWorkerThread.start();

        listenMulticastWorkerThread = new Thread(this::listenMulticastWorker);
        listenMulticastWorkerThread.start();

        sendGameStateWorkerThread = new Thread(this::sendGameStateWorker);
        sendGameStateWorkerThread.start();

        if (role == SnakesProto.NodeRole.MASTER) {
            playersManager.addPlayer(
                    new PlayerSignature(Config.LOCALHOST_IP, listenPort),
                    SnakesProto.GamePlayer.newBuilder()
                            .setName("MASTER NAME")
                            .setId(1)
                            .setIpAddress(Config.LOCALHOST_IP)
                            .setPort(listenPort)
                            .setScore(0)
                            .setRole(role)
                            .build()
            );
        }

        if (role == SnakesProto.NodeRole.NORMAL) {
            Thread.sleep(3000);
            var game = availableGamesManager.getGames().stream().findFirst();
            game.ifPresent(messageWithSender -> joinGame(messageWithSender.getMessage().getAnnouncement()));
        }
    }

    private void onPlayerDeadListener(SnakesProto.GamePlayer player) {
        if (role == SnakesProto.NodeRole.MASTER) {
            if (player.getRole() != SnakesProto.NodeRole.MASTER) {
                logger.warning("master saw other player dead");
            }
        } else {
            logger.warning("user saw " + player.getRole() + " dead");
        }
    }

    void sendPing(SnakesProto.GamePlayer player) {
        unicastManager.sendPacket(player.getIpAddress(), player.getPort(), SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(0)
                .setPing(SnakesProto.GameMessage.PingMsg.getDefaultInstance())
                .build()
        );
    }

    private @NonNull Disposable pingWorker() {
        return Observable.interval(Config.PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .subscribe(time -> {
                    if (role == SnakesProto.NodeRole.MASTER) {
                        playersManager.getMaster().ifPresent(gamePlayer -> {
//                            logger.info("master present?");
                            playersManager.touchPlayer(new PlayerSignature(gamePlayer));
                        });
                        playersManager.getPlayers().forEach(this::sendPing);
                    } else {
                        playersManager.getMaster().ifPresent(this::sendPing);
                    }
                });
    }

    private @NonNull Disposable infoWorker() {
        return Observable.interval(5000, TimeUnit.MILLISECONDS).subscribe(time -> logger.info(
                String.format("%s: is %s, game players: %d (%s)",
                        name,
                        role,
                        playersManager.getPlayers().size(),
                        playersManager.getPlayers().stream().map(SnakesProto.GamePlayer::getName).map(Objects::toString).collect(Collectors.joining(", "))
                )
        ));
    }

    private void listenUnicastWorker() {
        while (true) {
            MessageWithSender msg;
            try {
                msg = unicastManager.receivePacket();
            } catch (InterruptedException e) {
                break;
            }
            var signature = new PlayerSignature(msg.getIp(), msg.getPort());
            playersManager.touchPlayer(signature);
            if (role == SnakesProto.NodeRole.MASTER) {
                if (msg.getMessage().hasJoin()) {
                    logger.info("new player: " + msg.getMessage().getJoin().getName() + " (" + signature + ")");
                    playersManager.addPlayer(
                            signature,
                            SnakesProto.GamePlayer.newBuilder()
                                    .setName(msg.getMessage().getJoin().getName())
                                    .setRole(SnakesProto.NodeRole.NORMAL)
                                    .setId(1)
                                    .setIpAddress(msg.getIp())
                                    .setPort(msg.getPort())
                                    .setScore(0)
                                    .build()
                    );
                }
            }
            if (role == SnakesProto.NodeRole.NORMAL) {
                if (msg.getMessage().hasState()) {
                    var state = msg.getMessage().getState().getState();
                    state.getPlayers().getPlayersList().forEach(player -> playersManager.touchOrAddPlayer(
                            new PlayerSignature(player), player
                    ));
                }
            }
        }
    }

    private void listenMulticastWorker() {

    }

    private void sendGameStateWorker() {
        while (true) {
            if (role == SnakesProto.NodeRole.MASTER) {
//                logger.info("sending game state to " + playersManager.getPlayers().size() + " players");
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
            if (role == SnakesProto.NodeRole.MASTER) {
                availableGamesManager.announce(
                        SnakesProto.GameMessage.AnnouncementMsg.
                                newBuilder()
                                .setCanJoin(true)
                                .setConfig(SnakesProto.GameConfig.getDefaultInstance())
                                .setPlayers(
                                        SnakesProto.GamePlayers.newBuilder()
                                                .addAllPlayers(playersManager.getPlayers())
                                )
                                .build()
                );
            }
            try {
                Thread.sleep(ANNOUNCE_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void joinGame(SnakesProto.GameMessage.AnnouncementMsg msg) {
        var master = msg.getPlayers().getPlayersList()
                .stream().filter(
                        player -> player.getRole() == SnakesProto.NodeRole.MASTER
                ).findAny();
        master.ifPresent(gamePlayer -> joinGame(gamePlayer.getIpAddress(), gamePlayer.getPort()));
    }

    private void joinGame(String ip, int port) {
        unicastManager.sendPacket(
                ip,
                port,
                SnakesProto.GameMessage.newBuilder().setJoin(
                                SnakesProto.GameMessage.JoinMsg.newBuilder().setName(name).build()
                        )
                        .setMsgSeq(0)
                        .build()
        );
    }
}
