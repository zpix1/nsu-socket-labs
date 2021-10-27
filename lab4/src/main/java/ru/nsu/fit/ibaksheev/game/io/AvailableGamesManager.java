package ru.nsu.fit.ibaksheev.game.io;

import com.google.common.collect.EvictingQueue;
import me.ippolitov.fit.snakes.SnakesProto;
import ru.nsu.fit.ibaksheev.game.io.datatypes.MessageWithSender;
import ru.nsu.fit.ibaksheev.game.io.datatypes.PlayerSignature;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

public class AvailableGamesManager {
    // WF
    private final Queue<MessageWithSender> games = EvictingQueue.create(5);

    private final ConcurrentHashMap<PlayerSignature, MessageWithSender> allGames = new ConcurrentHashMap<>();

    private final Thread gamesListenerWorkerThread;

    private final MulticastManager multicastManager;

    public AvailableGamesManager(MulticastManager multicastManager) {
        this.multicastManager = multicastManager;

        gamesListenerWorkerThread = new Thread(this::gamesListenerWorker);
        gamesListenerWorkerThread.start();
    }

    private void gamesListenerWorker() {
        while (true) {
            MessageWithSender msg;
            try {
                msg = multicastManager.receivePacket();
            } catch (InterruptedException e) {
                break;
            }
            if (msg.getMessage().hasAnnouncement()) {
                allGames.put(new PlayerSignature(msg.getIp(), msg.getPort()), msg);
                games.add(msg);
            }
        }
    }

    public void announce(SnakesProto.GameMessage.AnnouncementMsg announcementMsg) {
        multicastManager.sendPacket(
                SnakesProto.GameMessage
                        .newBuilder()
                        .setAnnouncement(
                                announcementMsg
                        )
                        .setMsgSeq(0)
                        .build()
        );
    }

    public Collection<MessageWithSender> getGames() {
        return games;
    }

    public Collection<MessageWithSender> getAllGames() {
        return allGames.values();
    }

    void stop() {
        gamesListenerWorkerThread.interrupt();
    }
}
