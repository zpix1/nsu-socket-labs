package ru.nsu.fit.ibaksheev.game.ui.components;

import ru.nsu.fit.ibaksheev.game.io.AvailableGamesManager;
import ru.nsu.fit.ibaksheev.game.io.datatypes.MessageWithSender;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.function.Consumer;

public class GamesList extends JList<String> {
    public GamesList(AvailableGamesManager availableGamesManager, Consumer<MessageWithSender> onJoinListener) {
        super();
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int selected = locationToIndex(e.getPoint());
                    // System.out.println(selected);
                    onJoinListener.accept(availableGamesManager.getAllGames().toArray(MessageWithSender[]::new)[selected]);
                }
            }
        });
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
                var copy = new ArrayList<>(availableGamesManager.getAllGames());
                setListData(copy.stream().map(messageWithSender ->
                        String.format("%s:%d %d players",
                                messageWithSender.getIp(),
                                messageWithSender.getPort(),
                                messageWithSender.getMessage().getAnnouncement().getPlayers().getPlayersCount()
                        )
                ).toArray(String[]::new));
            }
        }).start();
    }
}