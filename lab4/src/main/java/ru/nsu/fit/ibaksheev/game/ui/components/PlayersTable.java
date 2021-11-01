package ru.nsu.fit.ibaksheev.game.ui.components;

import ru.nsu.fit.ibaksheev.game.io.PlayersManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class PlayersTable extends JTable {
    public PlayersTable(PlayersManager playersManager) {
        playersManager.getPlayersSubject().subscribe(players -> SwingUtilities.invokeLater(() -> {
                    var model = new DefaultTableModel();
                    model.setColumnCount(6);
                    model.setRowCount(players.size() + 1);
                    var i = 0;

                    model.setValueAt("id", i, 0);
                    model.setValueAt("Name", i, 1);
                    model.setValueAt("Score", i, 2);
                    model.setValueAt("IP", i, 3);
                    model.setValueAt("Port", i, 4);
                    model.setValueAt("Role", i, 5);
                    i++;
                    for (var player : players) {
                        model.setValueAt(player.getId() == playersManager.getMyId() ? "*" + player.getId() : player.getId(), i, 0);
                        model.setValueAt(player.getScore(), i, 2);
                        model.setValueAt(player.getName(), i, 1);
                        model.setValueAt(player.getIpAddress(), i, 3);
                        model.setValueAt(player.getPort(), i, 4);
                        model.setValueAt(player.getRole(), i, 5);
                        i++;
                    }
                    setModel(model);
                }
        ));
    }
}
