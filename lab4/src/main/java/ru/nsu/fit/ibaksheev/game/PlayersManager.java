package ru.nsu.fit.ibaksheev.game;

import lombok.*;
import me.ippolitov.fit.snakes.SnakesProto;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PlayersManager {
    private final static int NODE_TIMEOUT_MS = 5000;

    @Data
    @AllArgsConstructor
    public static class PlayerSignature {
        private String ip;
        private int port;
    }

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

    private final Thread checkDeadWorkerThread;

    public PlayersManager() {
        this.players = new HashMap<>();
        checkDeadWorkerThread = new Thread(this::checkDeadWorker);
        checkDeadWorkerThread.start();
    }

    void updatePlayer(PlayerSignature signature) {
        players.get(signature).setLastSeen(System.currentTimeMillis());
    }

    void addPlayer(PlayerSignature signature, SnakesProto.GamePlayer player) {
        logger.info("new player" + signature.toString());
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
        return players.values().stream().map(PlayerWrapper::getPlayer).collect(Collectors.toList());
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
                        .forEach(e -> logger.warning("Player dead: " + e.getKey()));
                players.entrySet().removeIf(e -> currentTime - e.getValue().getLastSeen() > NODE_TIMEOUT_MS);
            }
        }
    }
}
