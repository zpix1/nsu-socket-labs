package ru.nsu.fit.ibaksheev.game;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import me.ippolitov.fit.snakes.SnakesProto;

import java.util.HashMap;
import java.util.logging.Logger;

public class PlayersManager {
    private final static int NODE_TIMEOUT_MS = 5000;

    @Data
    public static class PlayerSignature {
        private int port;
        private String ip;
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
        players.put(
                signature,
                PlayerWrapper.builder()
                        .player(player)
                        .lastSeen(System.currentTimeMillis())
                        .build()
        );
    }

    void checkDeadWorker() {
        while (true) {
            try {
                Thread.sleep(NODE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                break;
            }
            var currentTime = System.currentTimeMillis();
            synchronized (players) {
                players.entrySet().stream()
                        .filter(e -> currentTime - e.getValue().getLastSeen() > NODE_TIMEOUT_MS)
                        .forEach(e -> logger.warning("Player dead: " + e.getKey()));
                players.entrySet().removeIf(e -> currentTime - e.getValue().getLastSeen() > NODE_TIMEOUT_MS);
            }
        }
    }
}
