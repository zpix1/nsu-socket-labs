package ru.nsu.fit.ibaksheev.game;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import me.ippolitov.fit.snakes.SnakesProto;
import ru.nsu.fit.ibaksheev.game.datatypes.MessageWithSender;
import ru.nsu.fit.ibaksheev.game.datatypes.PlayerSignature;

import java.io.IOException;
import java.net.Inet4Address;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PlayerController {

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

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private static final Logger logger = Logger.getLogger(UnicastManager.class.getName());

    private final PlayerSignature mySignature;

    public PlayerController(String name, int listenPort, SnakesProto.NodeRole role) throws IOException, InterruptedException {
        this.name = name;
        this.role = role;
        mySignature = new PlayerSignature(Inet4Address.getLocalHost().getHostAddress(), listenPort);
        logger.info(name + " started with " + mySignature);

        unicastManager = new UnicastManager(listenPort);
        multicastManager = new MulticastManager();
        playersManager = new PlayersManager(mySignature, this::onPlayerDeadListener);
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
                    mySignature,
                    SnakesProto.GamePlayer.newBuilder()
                            .setName(name)
                            .setId(1)
                            .setIpAddress(mySignature.getIp())
                            .setPort(listenPort)
                            .setScore(0)
                            .setRole(role)
                            .build()
            );
        }

        if (role != SnakesProto.NodeRole.MASTER) {
            Observable.timer(3000, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).subscribe(
                    time -> {
                        var game = availableGamesManager.getGames().stream().findFirst();
                        game.ifPresent(messageWithSender -> joinGame(messageWithSender.getMessage().getAnnouncement()));
                    }
            );
        }
    }

    public void stop() {
        stopped.set(true);

        unicastManager.stop();
        multicastManager.stop();
        playersManager.stop();
        availableGamesManager.stop();

        announceWorkerThread.interrupt();
        listenUnicastWorkerThread.interrupt();
        listenMulticastWorkerThread.interrupt();
        sendGameStateWorkerThread.interrupt();
    }

    private void onPlayerDeadListener(SnakesProto.GamePlayer deadPlayer) {
//        logger.warning(String.format("me is %s, he is %s", mySignature, new PlayerSignature(deadPlayer)));
//        logger.warning(String.format("%s (%s): %s (%s) is dead", name, role, deadPlayer.getName(), deadPlayer.getRole()));
        // Узел с ролью MASTER заметил, что отвалился DEPUTY. Тогда он выбирает нового DEPUTY среди NORMAL-ов, и сообщает об этом самому DEPUTY сообщением RoleChangeMsg (остальные узнают о новом DEPUTY из планового StatusMsg, им это знать не срочно).
        if (role == SnakesProto.NodeRole.MASTER) {
            if (deadPlayer.getRole() == SnakesProto.NodeRole.DEPUTY) {
                logger.info("MASTER saw DEPUTY dead, he selects NORMAL as DEPUTY");
                playersManager.getNormal().ifPresent(
                        newDeputy -> {
                            playersManager.changeRole(new PlayerSignature(newDeputy), SnakesProto.NodeRole.DEPUTY);
                            unicastManager.sendPacket(
                                    newDeputy.getIpAddress(),
                                    newDeputy.getPort(),
                                    SnakesProto.GameMessage.newBuilder()
                                            .setMsgSeq(0)
                                            .setRoleChange(SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                                                    .setReceiverRole(SnakesProto.NodeRole.DEPUTY)
                                                    .build()
                                            )
                                            .build()
                            );
                        }
                );
            }
        }
        // Узел с ролью NORMAL заметил, что отвалился MASTER. Тогда он заменяет информацию о центральном узле на заместителя (DEPUTY), т.е начинает посылать все unicast-сообщения в сторону DEPUTY.
        if (role == SnakesProto.NodeRole.NORMAL) {
            if (deadPlayer.getRole() == SnakesProto.NodeRole.MASTER) {
                logger.info("NORMAL saw MASTER dead, he selects DEPUTY as MASTER");
                playersManager.getDeputy().ifPresent(
                        deputy -> {
                            playersManager.changeRole(new PlayerSignature(deputy), SnakesProto.NodeRole.MASTER);
                        }
                );
            }
        }
        // Узел с ролью DEPUTY заметил, что отвалился MASTER. Тогда он сам становится MASTER-ом (берёт управление игрой на себя), выбирает нового DEPUTY, и сообщает об этом каждому игроку сообщением RoleChangeMsg.
        if (role == SnakesProto.NodeRole.DEPUTY) {
            if (deadPlayer.getRole() == SnakesProto.NodeRole.MASTER) {
                logger.info("DEPUTY saw MASTER dead, he becomes MASTER");
                role = SnakesProto.NodeRole.MASTER;
                playersManager.getNormal().ifPresent(
                        newDeputy -> {
                            playersManager.changeRole(mySignature, SnakesProto.NodeRole.MASTER);
                            playersManager.changeRole(new PlayerSignature(newDeputy), SnakesProto.NodeRole.DEPUTY);
                            // если мастер сдох, а депутат это увидел раньше и назначил нормала депутатом до того как он понял что мастер сдох
                            // то нормал-депутат как только увидит что мастер сдох станет мастером (а мастер уже есть)
//                            unicastManager.sendPacket(
//                                    newDeputy.getIpAddress(),
//                                    newDeputy.getPort(),
//                                    SnakesProto.GameMessage.newBuilder()
//                                            .setMsgSeq(0)
//                                            .setRoleChange(
//                                                    SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
//                                                            .setReceiverRole(SnakesProto.NodeRole.DEPUTY)
//                                                            .build()
//                                            )
//                                            .build()
//                            );
                        }
                );
            }
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
                .takeUntil(unused -> !stopped.get())
                .subscribeOn(Schedulers.io())
                .subscribe(time -> {
                    if (role == SnakesProto.NodeRole.MASTER) {
                        playersManager.getPlayers().forEach(this::sendPing);
                    } else {
                        playersManager.getMaster().ifPresent(this::sendPing);
                    }
                });
    }

    private @NonNull Disposable infoWorker() {
        return Observable.interval(5000, TimeUnit.MILLISECONDS)
//                .takeUntil(time -> !stopped.get())
                .subscribe(time -> {
                    logger.info(
                            String.format("%s%s: is %s, game players: %d (%s)",
                                    stopped.get() ? "DEAD, " : "",
                                    name,
                                    role,
                                    playersManager.getPlayers().size(),
                                    playersManager.getPlayers().stream()
                                            .map(player -> player.getName() + " (" + player.getRole() + ")")
                                            .collect(Collectors.joining(", "))
                            )
                    );
                }
                );
    }

    private void listenUnicastWorker() {
        while (true) {
//            logger.info(String.format("%s: stopped is %s", name, stopped));
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
                    if (playersManager.getDeputy().isEmpty()) {
                        playersManager.addPlayer(
                                signature,
                                SnakesProto.GamePlayer.newBuilder()
                                        .setName(msg.getMessage().getJoin().getName())
                                        .setRole(SnakesProto.NodeRole.DEPUTY)
                                        .setId(1)
                                        .setIpAddress(msg.getIp())
                                        .setPort(msg.getPort())
                                        .setScore(0)
                                        .build()
                        );
                        unicastManager.sendPacket(
                                signature.getIp(),
                                signature.getPort(),
                                SnakesProto.GameMessage.newBuilder()
                                        .setMsgSeq(0)
                                        .setRoleChange(
                                                SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                                                        .setReceiverRole(SnakesProto.NodeRole.DEPUTY)
                                                        .build()
                                        )
                                        .build()
                        );
                    } else {
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
            }
            if (role != SnakesProto.NodeRole.MASTER) {
                if (msg.getMessage().hasState()) {
                    var state = msg.getMessage().getState().getState();
                    state.getPlayers().getPlayersList().forEach(player -> playersManager.updatePlayer(
                            new PlayerSignature(player), player
                    ));
                    playersManager.getMyself().ifPresent(gamePlayer -> role = gamePlayer.getRole());
                }
                if (msg.getMessage().hasRoleChange()) {
                    var roleChange = msg.getMessage().getRoleChange();
                    if (roleChange.hasReceiverRole()) {
                        logger.info("switched from " + role + " to " + roleChange.getReceiverRole());
                        role = roleChange.getReceiverRole();
                    }
                    if (roleChange.hasSenderRole()) {
                        logger.info("switched sender to " + roleChange.getReceiverRole());
                        playersManager.changeRole(signature, roleChange.getReceiverRole());
                    }
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
                Thread.sleep(Config.ANNOUNCE_INTERVAL_MS);
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
                Thread.sleep(Config.ANNOUNCE_INTERVAL_MS);
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
