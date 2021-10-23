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
    private final static int NODE_TIMEOUT_MS = 5000;

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

    public PlayersManager(Consumer<SnakesProto.GamePlayer> onPlayerDeadListener) {
        this.players = new HashMap<>();
        this.onPlayerDeadListener = onPlayerDeadListener;
        checkDeadWorkerThread = new Thread(this::checkDeadWorker);
        checkDeadWorkerThread.start();
    }

    void touchPlayer(PlayerSignature signature) {
        var playerWrapper = players.get(signature);
        if (playerWrapper != null) {
            playerWrapper.setLastSeen(System.currentTimeMillis());
        }
    }

    void touchOrAddPlayer(PlayerSignature signature, SnakesProto.GamePlayer player) {
//        logger.info(signature.toString());
        if (players.containsKey(signature)) {
            touchPlayer(signature);
        } else {
            addPlayer(signature, player);
        }
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

    void checkDeadWorker() {
        while (true) {
            try {
                Thread.sleep(NODE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                break;
            }
            synchronized (players) {
                var currentTime = System.currentTimeMillis();
                players.entrySet().stream()
                        .filter(e -> currentTime - e.getValue().getLastSeen() > NODE_TIMEOUT_MS)
                        .forEach(e -> {
                            logger.warning("Player dead: " + e.getValue().getPlayer());
                            this.onPlayerDeadListener.accept(e.getValue().getPlayer());
                        });
                players.entrySet().removeIf(e -> currentTime - e.getValue().getLastSeen() > NODE_TIMEOUT_MS);
            }
        }
    }
}
