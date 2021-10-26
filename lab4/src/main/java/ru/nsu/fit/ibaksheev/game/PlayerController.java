package ru.nsu.fit.ibaksheev.game;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import me.ippolitov.fit.snakes.SnakesProto;
import ru.nsu.fit.ibaksheev.game.datatypes.MessageWithSender;
import ru.nsu.fit.ibaksheev.game.datatypes.PlayerSignature;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PlayerController {
    private int myId;

    private final UnicastManager unicastManager;
    private final MulticastManager multicastManager;
    private final PlayersManager playersManager;
    private final AvailableGamesManager availableGamesManager;

    private volatile SnakesProto.NodeRole role;

    private final Thread announceWorkerThread;
    private final Thread listenUnicastWorkerThread;
    private final Thread listenMulticastWorkerThread;
    private final Thread sendGameStateWorkerThread;

    private final String name;

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private static final Logger logger = Logger.getLogger(UnicastManager.class.getName());

    private final PlayerSignature mySignature;

    public PlayerController(String name, int listenPort, SnakesProto.NodeRole role) throws IOException, InterruptedException {
        this.name = name;
        this.role = role;
        mySignature = new PlayerSignature(Inet4Address.getLocalHost().getHostAddress(), listenPort);
        logger.info(name + " started with " + mySignature);

        DatagramSocket socket = new DatagramSocket(listenPort);
        unicastManager = new UnicastManager(socket);
        multicastManager = new MulticastManager(socket);
        playersManager = new PlayersManager(mySignature, this::onPlayerDeadListener);
        availableGamesManager = new AvailableGamesManager(multicastManager);

        new Thread(this::infoWorker).start();
        new Thread(this::pingWorker).start();

        announceWorkerThread = new Thread(this::announceWorker);
        announceWorkerThread.start();

        listenUnicastWorkerThread = new Thread(this::listenUnicastWorker);
        listenUnicastWorkerThread.start();

        listenMulticastWorkerThread = new Thread(this::listenMulticastWorker);
        listenMulticastWorkerThread.start();

        sendGameStateWorkerThread = new Thread(this::sendGameStateWorker);
        sendGameStateWorkerThread.start();

        myId = playersManager.getNextPlayerId();

        if (role == SnakesProto.NodeRole.MASTER) {
            playersManager.updatePlayer(
                    mySignature,
                    SnakesProto.GamePlayer.newBuilder()
                            .setName(name)
                            .setId(myId)
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
                        game.ifPresent(this::joinGame);
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
        logger.warning(String.format("%s (%s): %s (%s) is dead", name, role, deadPlayer.getName(), deadPlayer.getRole()));
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
                                                    .setSenderRole(SnakesProto.NodeRole.MASTER)
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
                        deputy -> playersManager.changeRole(new PlayerSignature(deputy), SnakesProto.NodeRole.MASTER)
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
                            for (var player : playersManager.getPlayers()) {
                                unicastManager.sendPacket(
                                        newDeputy.getIpAddress(),
                                        newDeputy.getPort(),
                                        SnakesProto.GameMessage.newBuilder()
                                                .setMsgSeq(0)
                                                .setRoleChange(
                                                        SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                                                                .setReceiverRole(SnakesProto.NodeRole.DEPUTY)
                                                                .build()
                                                )
                                                .build()
                                );
                            }
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

    private void pingWorker() {
        while (!stopped.get()) {
            if (role == SnakesProto.NodeRole.MASTER) {
                playersManager.getPlayers().forEach(this::sendPing);
            } else {
                playersManager.getMaster().ifPresent(this::sendPing);
            }
            try {
                Thread.sleep(Config.PING_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void infoWorker() {
        while (!stopped.get()) {
            logger.info(
                    String.format("%s%d (%s/%s), game players: %d (%s)",
                            stopped.get() ? "DEAD, " : "",
                            myId,
                            name,
                            role,
                            playersManager.getPlayers().size(),
                            playersManager.getPlayers().stream()
                                    .map(player -> String.format("%d (%s/%s)", player.getId(), player.getName(), player.getRole()))
                                    .collect(Collectors.joining(", "))
                    )
            );
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
        }
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
                    var playerId = playersManager.getNextPlayerId();
                    unicastManager.sendPacket(
                            signature.getIp(),
                            signature.getPort(),
                            SnakesProto.GameMessage.newBuilder()
                                    .setMsgSeq(0)
                                    .setAck(SnakesProto.GameMessage.AckMsg.getDefaultInstance())
                                    .setReceiverId(playerId)
                                    .build()
                    );
                    if (playersManager.getDeputy().isEmpty()) {
                        playersManager.updatePlayer(
                                signature,
                                SnakesProto.GamePlayer.newBuilder()
                                        .setName(msg.getMessage().getJoin().getName())
                                        .setRole(SnakesProto.NodeRole.DEPUTY)
                                        .setId(playerId)
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
                        playersManager.updatePlayer(
                                signature,
                                SnakesProto.GamePlayer.newBuilder()
                                        .setName(msg.getMessage().getJoin().getName())
                                        .setRole(SnakesProto.NodeRole.NORMAL)
                                        .setId(playerId)
                                        .setIpAddress(msg.getIp())
                                        .setPort(msg.getPort())
                                        .setScore(0)
                                        .build()
                        );
                    }
                }
            }
            if (role != SnakesProto.NodeRole.MASTER) {
                if (msg.getMessage().hasAck() && msg.getMessage().hasReceiverId()) {
                    myId = msg.getMessage().getReceiverId();
                }
                if (msg.getMessage().hasState()) {
                    var state = msg.getMessage().getState().getState();
                    state.getPlayers().getPlayersList().forEach(player -> playersManager.updatePlayer(
                            new PlayerSignature(player), player
                    ));
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

//    private void joinGame(SnakesProto.GameMessage.AnnouncementMsg msg) {
//        var master = msg.getPlayers().getPlayersList()
//                .stream().filter(
//                        player -> player.getRole() == SnakesProto.NodeRole.MASTER
//                ).findAny();
//        master.ifPresent(gamePlayer -> joinGame(gamePlayer.getIpAddress(), gamePlayer.getPort()));
//    }

    private void joinGame(MessageWithSender announceWrapper) {
        joinGame(announceWrapper.getIp(), announceWrapper.getPort());
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
