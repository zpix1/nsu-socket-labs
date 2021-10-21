package ru.nsu.fit.ibaksheev.game;

import com.google.common.collect.EvictingQueue;
import me.ippolitov.fit.snakes.SnakesProto;
import ru.nsu.fit.ibaksheev.game.datatypes.MessageWithSender;

import java.io.IOException;
import java.util.Collection;
import java.util.Queue;

public class AvailableGamesManager {
    // WF
    private final Queue<MessageWithSender> games = EvictingQueue.create(5);

    private final Thread gamesListenerWorkerThread;

    private final MulticastManager multicastManager;

    public AvailableGamesManager() throws IOException {
        multicastManager = new MulticastManager();

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
}
