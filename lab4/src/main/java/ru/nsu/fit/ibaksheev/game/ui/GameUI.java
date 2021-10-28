package ru.nsu.fit.ibaksheev.game.ui;

import me.ippolitov.fit.snakes.SnakesProto;
import ru.nsu.fit.ibaksheev.game.io.PlayerController;
import ru.nsu.fit.ibaksheev.game.io.datatypes.MessageWithSender;
import ru.nsu.fit.ibaksheev.game.snake.SnakeView;
import ru.nsu.fit.ibaksheev.game.snake.SnakeViewController;
import ru.nsu.fit.ibaksheev.game.ui.components.GamesList;
import ru.nsu.fit.ibaksheev.game.ui.components.PlayersTable;
import ru.nsu.fit.ibaksheev.game.ui.components.SnakeCanvas;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.BindException;
import java.util.logging.LogManager;

public class GameUI {
    PlayerController player;
    SnakeView snakeView;

    private void joinGame(MessageWithSender gameMessage) {
        player.joinGame(gameMessage);
    }

    private void initUI() {
        // Init
        var frame = new JFrame("Game");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        var contents = new JPanel();
        contents.setLayout(new BorderLayout());

        // region control panel
        var controlPanel = new JPanel(new GridLayout(3, 1));

        var playersPanel = new JPanel(new GridLayout(4, 1));
        playersPanel.setBorder(BorderFactory.createTitledBorder("Players"));
        playersPanel.setLayout(new GridLayout(1, 1));

        playersPanel.add(
//                new JScrollPane(
                        new PlayersTable(player.getPlayersManager())
//                )
        );

        controlPanel.add(playersPanel);

        var joinPanel = new JPanel();
        joinPanel.setBorder(BorderFactory.createTitledBorder("Join or create a game"));
        joinPanel.setLayout(new GridLayout(2, 1));
        joinPanel.add(
                new JScrollPane(
                        new GamesList(player.getAvailableGamesManager(), this::joinGame)
                )
        );
        var createButton = new JButton("Create game");
        createButton.addActionListener(v -> {
            player.createGame();
        });
        joinPanel.add(createButton);

        controlPanel.add(joinPanel);

        contents.add(controlPanel, BorderLayout.EAST);
        // endregion

        // region snake canvas
        contents.add((Component) snakeView, BorderLayout.WEST);
        // endregion

        // Final
        frame.setContentPane(contents);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setSize(1000, 600);
        frame.setVisible(true);
    }

    GameUI() throws IOException {
        for (int i = 0; i < 10; i++) {
            try {
                player = new PlayerController("Game Player", 5000 + i, SnakesProto.NodeRole.NORMAL);
            } catch (BindException e) {
                continue;
            }
            break;
        }
        snakeView = new SnakeCanvas();
        snakeView.setState(SnakesProto.GameState.getDefaultInstance());
        new SnakeViewController(player, snakeView);
        SwingUtilities.invokeLater(this::initUI);
    }

    public static void main(String[] args) throws IOException {
        LogManager.getLogManager().readConfiguration(GameUI.class.getResourceAsStream("/logging.properties"));

//        try {
//            new PlayerController("master", 4999, SnakesProto.NodeRole.MASTER);
//        } catch (BindException e) {
//
//        }
        new GameUI();
    }
}