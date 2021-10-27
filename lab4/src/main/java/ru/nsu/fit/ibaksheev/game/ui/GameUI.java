package ru.nsu.fit.ibaksheev.game.ui;

import me.ippolitov.fit.snakes.SnakesProto;
import ru.nsu.fit.ibaksheev.game.io.PlayerController;
import ru.nsu.fit.ibaksheev.game.io.datatypes.MessageWithSender;
import ru.nsu.fit.ibaksheev.game.ui.components.GamesList;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.logging.LogManager;

public class GameUI {
    PlayerController player;

    private void joinGame(MessageWithSender gameMessage) {
        System.out.println("joined " + gameMessage);
    }

    private void initUI() {
        // Init
        var frame = new JFrame("Game");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        var contents = new JPanel();
        contents.setLayout(new BorderLayout());

        // region control panel
        var controlPanel = new JPanel(new GridLayout(3, 1));

        var yourInfoPanel = new JPanel();
        yourInfoPanel.setBorder(BorderFactory.createTitledBorder("About you"));
        yourInfoPanel.setLayout(new FlowLayout());
        yourInfoPanel.add(new JLabel("Name: " + player.getName()));

        controlPanel.add(yourInfoPanel);

        var joinPanel = new JPanel();
        joinPanel.setBorder(BorderFactory.createTitledBorder("Join or create a game"));
        joinPanel.setLayout(new GridLayout(2, 1));
        joinPanel.add(
                new JScrollPane(new GamesList(player.getAvailableGamesManager(), this::joinGame))
        );
        joinPanel.add(
                new JButton("Create game")
        );

        controlPanel.add(joinPanel);

        contents.add(controlPanel, BorderLayout.EAST);
        // endregion

        // Final
        frame.setContentPane(contents);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setSize(400, 500);
        frame.setVisible(true);
    }

    GameUI() throws IOException {
        player = new PlayerController("Game Player", 5003, SnakesProto.NodeRole.NORMAL);
        SwingUtilities.invokeLater(this::initUI);
    }

    public static void main(String[] args) throws IOException {
        LogManager.getLogManager().readConfiguration(GameUI.class.getResourceAsStream("/logging.properties"));

        new PlayerController("master", 5004, SnakesProto.NodeRole.MASTER);
        new GameUI();
    }
}