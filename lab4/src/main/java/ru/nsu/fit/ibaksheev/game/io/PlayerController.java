package ru.nsu.fit.ibaksheev.game.io;

import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import lombok.Getter;
import me.ippolitov.fit.snakes.SnakesProto;
import ru.nsu.fit.ibaksheev.game.io.datatypes.MessageWithSender;
import ru.nsu.fit.ibaksheev.game.io.datatypes.PlayerSignature;
import ru.nsu.fit.ibaksheev.game.snake.SnakeMasterController;
import ru.nsu.fit.ibaksheev.game.snake.SnakeView;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PlayerController {
    private final int listenPort;
    @Getter
    private int myId;

    private final UnicastManager unicastManager;
    private final MulticastManager multicastManager;
    @Getter
    private final PlayersManager playersManager;
    @Getter
    private final AvailableGamesManager availableGamesManager;

    private final Lock roleLock = new ReentrantLock();
    @Getter
    private volatile SnakesProto.NodeRole role;

    private final Thread announceWorkerThread;
    private final Thread listenUnicastWorkerThread;
    private final Thread listenMulticastWorkerThread;
    private final Thread sendGameStateWorkerThread;

    @Getter
    private final String name;

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private static final Logger logger = Logger.getLogger(UnicastManager.class.getName());

    private final PlayerSignature mySignature;

    @Getter
    private final Subject<MessageWithSender> newMessageSubject = PublishSubject.create();
    @Getter
    private final Subject<SnakeView.Control> controlSubject = PublishSubject.create();
    @Getter
    private final Subject<SnakesProto.NodeRole> roleSubject = PublishSubject.create();

    private final SnakeMasterController snakeMasterController;

    private SnakesProto.GameState state;

    public PlayerController(String name, int listenPort, SnakesProto.NodeRole role) throws IOException {
        this.listenPort = listenPort;
        this.name = name;
        this.role = role;
        roleSubject.onNext(role);
        mySignature = new PlayerSignature(Inet4Address.getLocalHost().getHostAddress(), listenPort);
        logger.info(name + " started with " + mySignature);

        snakeMasterController = new SnakeMasterController(controlSubject);
        state = SnakesProto.GameState.newBuilder()
                .setStateOrder(0)
                .setConfig(SnakesProto.GameConfig.getDefaultInstance())
                .setPlayers(SnakesProto.GamePlayers.getDefaultInstance())
                .build();


        DatagramSocket socket = new DatagramSocket(listenPort);
        unicastManager = new UnicastManager(socket);
        multicastManager = new MulticastManager(socket);
        playersManager = new PlayersManager(this::onPlayerDeadListener);
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
        playersManager.setMyId(myId);



//        if (role != SnakesProto.NodeRole.MASTER) {
//            Observable.timer(3000, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).subscribe(
//                    time -> {
//                        var game = availableGamesManager.getGames().stream().findFirst();
//                        game.ifPresent(this::joinGame);
//                    }
//            );
//        }
    }

    public void createGame() {
        roleLock.lock();
        role = SnakesProto.NodeRole.MASTER;
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
        roleLock.unlock();
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
                System.out.println(playersManager.getPlayers().size());

                roleLock.lock();
                role = SnakesProto.NodeRole.MASTER;
                roleSubject.onNext(role);
                roleLock.unlock();
                playersManager.changeRole(mySignature, SnakesProto.NodeRole.MASTER);


                // если мастер сдох, а депутат это увидел раньше и назначил нормала депутатом до того как он понял, что мастер сдох
                // то нормал-депутат как только увидит что мастер сдох, станет мастером (а мастер уже есть)
                // WF
                boolean hasDeputy = false;
                for (var player : playersManager.getPlayers()) {
                    if (player.getId() == myId) {
                        continue;
                    }
                    if (!hasDeputy) {
                        playersManager.changeRole(new PlayerSignature(player), SnakesProto.NodeRole.DEPUTY);
                    }
                    System.out.printf("Telling %s he is %s%n", player.getName(), !hasDeputy ? SnakesProto.NodeRole.DEPUTY : SnakesProto.NodeRole.NORMAL);
                    unicastManager.sendPacket(
                            player.getIpAddress(),
                            player.getPort(),
                            SnakesProto.GameMessage.newBuilder()
                                    .setMsgSeq(0)
                                    .setRoleChange(
                                            SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                                                    .setSenderRole(SnakesProto.NodeRole.MASTER)
                                                    .setReceiverRole(!hasDeputy ? SnakesProto.NodeRole.DEPUTY : SnakesProto.NodeRole.NORMAL)
                                                    .build()
                                    )
                                    .setSenderId(myId)
                                    .setReceiverId(player.getId())
                                    .build()
                    );
                    hasDeputy = true;
                }

                playersManager.getNormal().ifPresent(
                        newDeputy -> {
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
            roleLock.lock();
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
                if (msg.getMessage().hasState()) {
                    var state = msg.getMessage().getState().getState();
                    state.getPlayers().getPlayersList().forEach(player -> playersManager.updatePlayer(
                            new PlayerSignature(player), player
                    ));
                }
            }
            if (msg.getMessage().hasAck() && msg.getMessage().hasReceiverId()) {
                myId = msg.getMessage().getReceiverId();
                playersManager.setMyId(myId);
            }
            if (msg.getMessage().hasRoleChange()) {
                var roleChange = msg.getMessage().getRoleChange();
                if (roleChange.hasReceiverRole()) {
                    logger.info("switched from " + role + " to " + roleChange.getReceiverRole());
                    role = roleChange.getReceiverRole();
                    roleSubject.onNext(role);
                }
                if (roleChange.hasSenderRole()) {
                    logger.info("switched sender to " + roleChange.getSenderRole());
                    playersManager.changeRole(signature, roleChange.getSenderRole());
                }
            }
            newMessageSubject.onNext(msg);
            roleLock.unlock();
        }
    }

    private void listenMulticastWorker() {

    }

    private void sendGameStateWorker() {
        while (true) {
            if (role == SnakesProto.NodeRole.MASTER) {
//                logger.info("sending game state to " + playersManager.getPlayers().size() + " players");
                var oldState = SnakesProto.GameState.newBuilder(state)
                        .setPlayers(
                                SnakesProto.GamePlayers.newBuilder()
                                        .addAllPlayers(playersManager.getPlayers())
                        )
                        .setStateOrder(0)
                        .build();
                var sendState = snakeMasterController.getNextState(oldState);

                var msg = SnakesProto.GameMessage.newBuilder()
                        .setState(
                                SnakesProto.GameMessage.StateMsg.newBuilder()
                                        .setState(sendState)
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
                Thread.sleep(Config.STATE_INTERVAL_MS);
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

    public void joinGame(MessageWithSender announceWrapper) {
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
