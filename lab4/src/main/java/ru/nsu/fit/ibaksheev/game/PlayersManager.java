package ru.nsu.fit.ibaksheev.game;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import me.ippolitov.fit.snakes.SnakesProto;
import ru.nsu.fit.ibaksheev.game.datatypes.PlayerSignature;

import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PlayersManager {

    @Builder
    private static class PlayerWrapper {
        @Getter
        private SnakesProto.GamePlayer player;
        @Getter
        @Setter
        private Long lastSeen;
    }

    private static final Logger logger = Logger.getLogger(UnicastManager.class.getName());

    private final HashMap<PlayerSignature, PlayerWrapper> players;

    private final Consumer<SnakesProto.GamePlayer> onPlayerDeadListener;
    private final Thread checkDeadWorkerThread;

    private final PlayerSignature mySignature;

    public PlayersManager(PlayerSignature mySignature, Consumer<SnakesProto.GamePlayer> onPlayerDeadListener) {
        this.mySignature = mySignature;
        this.players = new HashMap<>();
        this.onPlayerDeadListener = onPlayerDeadListener;
        checkDeadWorkerThread = new Thread(this::checkDeadWorker);
        checkDeadWorkerThread.start();
    }

    void stop() {
        checkDeadWorkerThread.interrupt();
    }

    void touchPlayer(PlayerSignature signature) {
        var playerWrapper = players.get(signature);
        if (playerWrapper != null) {
            playerWrapper.setLastSeen(System.currentTimeMillis());
        }
    }

    void updatePlayer(PlayerSignature signature, SnakesProto.GamePlayer player) {
//        logger.info(signature.toString());
        players.put(
                signature,
                PlayerWrapper.builder()
                        .player(player)
                        .lastSeen(System.currentTimeMillis())
                        .build()
        );
    }

    void addPlayer(PlayerSignature signature, SnakesProto.GamePlayer player) {
//        logger.info("new player" + signature.toString());
        players.put(
                signature,
                PlayerWrapper.builder()
                        .player(player)
                        .lastSeen(System.currentTimeMillis())
                        .build()
        );
    }

    void changeRole(PlayerSignature signature, SnakesProto.NodeRole role) {
        synchronized (players) {
            var player = players.get(signature);
            if (player != null) {
                players.put(signature,
                        new PlayerWrapper(
                                SnakesProto.GamePlayer.newBuilder(player.getPlayer()).setRole(role).build(),
                                player.getLastSeen()
                        )
                );
            }
        }
    }

    Set<PlayerSignature> getSignatures() {
        return players.keySet();
    }

    Collection<SnakesProto.GamePlayer> getPlayers() {
        return players.values().stream()
                .map(PlayerWrapper::getPlayer)
                .collect(Collectors.toList());
    }

    public Optional<SnakesProto.GamePlayer> getMaster() {
        return players.values().stream().map(PlayerWrapper::getPlayer).filter(
                player -> player.getRole() == SnakesProto.NodeRole.MASTER
        ).findAny();
    }

    public Optional<SnakesProto.GamePlayer> getDeputy() {
        return players.values().stream().map(PlayerWrapper::getPlayer).filter(
                player -> player.getRole() == SnakesProto.NodeRole.DEPUTY
        ).findAny();
    }

    public Optional<SnakesProto.GamePlayer> getNormal() {
        return players.values().stream().map(PlayerWrapper::getPlayer).filter(
                player -> player.getRole() == SnakesProto.NodeRole.NORMAL
        ).findAny();
    }

    public Optional<SnakesProto.GamePlayer> getMyself() {
        return players.values().stream().map(PlayerWrapper::getPlayer).filter(
                player -> new PlayerSignature(player) == mySignature
        ).findAny();
    }

    void checkDeadWorker() {
        while (true) {
            try {
                Thread.sleep(Config.NODE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                break;
            }
            synchronized (players) {
                var currentTime = System.currentTimeMillis();
                players.entrySet().stream()
                        .filter(e -> e.getKey() != mySignature)
                        .filter(e -> currentTime - e.getValue().getLastSeen() > Config.NODE_TIMEOUT_MS)
                        .max((a, b) -> a.getValue().getPlayer().getRole() == SnakesProto.NodeRole.MASTER ? 1 : b.getValue().getPlayer().getRole() == SnakesProto.NodeRole.MASTER ? -1 : 0)
                        .ifPresent(e -> {
                            logger.warning("Player dead: " + e.getValue().getPlayer().getRole());
                            this.onPlayerDeadListener.accept(e.getValue().getPlayer());
                            players.remove(e.getKey());
                        });
            }
        }
    }
}
