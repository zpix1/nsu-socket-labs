package ru.nsu.fit.ibaksheev.game.io;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import me.ippolitov.fit.snakes.SnakesProto;
import ru.nsu.fit.ibaksheev.game.io.datatypes.PlayerSignature;

import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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

    @Getter
    private Subject<Collection<SnakesProto.GamePlayer>> playersSubject = BehaviorSubject.create();

    private static final Logger logger = Logger.getLogger(UnicastManager.class.getName());

    private final HashMap<Integer, PlayerWrapper> players;

    private final Consumer<SnakesProto.GamePlayer> onPlayerDeadListener;
    private final Thread checkDeadWorkerThread;

    private int maxPlayerId = 0;
    @Setter
    @Getter
    private volatile int myId = -1;

    public PlayersManager(Consumer<SnakesProto.GamePlayer> onPlayerDeadListener) {
        this.players = new HashMap<>();
        this.onPlayerDeadListener = onPlayerDeadListener;
        checkDeadWorkerThread = new Thread(this::checkDeadWorker);
        checkDeadWorkerThread.start();
        Observable.interval(500, TimeUnit.MILLISECONDS).subscribe(time -> playersSubject.onNext(getPlayers()));
    }

    void stop() {
        checkDeadWorkerThread.interrupt();
    }

    public Optional<Integer> getIdBySignature(PlayerSignature signature) {
        return players.values().stream().filter(playerWrapper -> new PlayerSignature(playerWrapper.getPlayer()).equals(signature)).map(playerWrapper -> playerWrapper.getPlayer().getId()).findAny();
    }

    void touchPlayer(PlayerSignature signature) {
        players.values().stream().filter(playerWrapper -> new PlayerSignature(playerWrapper.getPlayer()).equals(signature)).findAny().ifPresent(
                playerWrapper -> playerWrapper.setLastSeen(System.currentTimeMillis())
        );
    }

    public int getNextPlayerId() {
        return maxPlayerId + 1;
    }

    void updatePlayer(SnakesProto.GamePlayer player) {
//        logger.info(signature.toString());
        maxPlayerId = Math.max(maxPlayerId, player.getId());
        synchronized (players) {
            players.put(
                    player.getId(),
                    PlayerWrapper.builder()
                            .player(player)
                            .lastSeen(System.currentTimeMillis())
                            .build()
            );
        }
    }

    void updatePlayerWithoutTouch(SnakesProto.GamePlayer player) {
//        logger.info(signature.toString());
        maxPlayerId = Math.max(maxPlayerId, player.getId());
        synchronized (players) {
            players.put(
                    player.getId(),
                    PlayerWrapper.builder()
                            .player(player)
                            .lastSeen(players.containsKey(player.getId()) ? players.get(player.getId()).getLastSeen() : System.currentTimeMillis())
                            .build()
            );
        }
    }

    void changeRole(int playerId, SnakesProto.NodeRole role) {
        synchronized (players) {
            var player = players.get(playerId);
            if (player != null) {
                players.put(playerId,
                        new PlayerWrapper(
                                SnakesProto.GamePlayer.newBuilder(player.getPlayer()).setRole(role).build(),
                                player.getLastSeen()
                        )
                );
            }
        }
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

    void checkDeadWorker() {
        while (true) {
            try {
                Thread.sleep(Config.NODE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                break;
            }
            synchronized (players) {
                if (myId == -1) {
                    continue;
                }
//                // System.out.println(players.values().stream().map(e -> e.getLastSeen().toString()).collect(Collectors.joining(", ")));
                var currentTime = System.currentTimeMillis();
                players.entrySet().stream()
                        .filter(e -> e.getValue().getPlayer().getId() != myId)
                        .filter(e -> currentTime - e.getValue().getLastSeen() > Config.NODE_TIMEOUT_MS)
//                        .peek(// System.out::println)
                        .max((a, b) -> a.getValue().getPlayer().getRole() == SnakesProto.NodeRole.MASTER ? 1 : b.getValue().getPlayer().getRole() == SnakesProto.NodeRole.MASTER ? -1 : 0)
                        .ifPresent(e -> {
//                            logger.warning("Player dead: " + e.getValue().getPlayer().getRole());
                            players.remove(e.getKey());
                            this.onPlayerDeadListener.accept(e.getValue().getPlayer());
                        });
            }
        }
    }
}
